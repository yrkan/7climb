package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.WPrimeSample
import io.github.climbintelligence.data.model.WPrimeState
import io.github.climbintelligence.data.model.WPrimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.pow

/**
 * Skiba differential W' Balance model.
 *
 * dW'/dt = recovery - expenditure
 * if P > CP: dW' = ((W'max - W') / tau - (P - CP)) * dt
 * if P <= CP: dW' = ((W'max - W') / tau) * dt
 * tau = 546.0 (Skiba constant)
 *
 * As of the W'-history-chart PR, balance is allowed to be negative — the
 * Skiba math predicts depletion past zero whenever power > CP for long
 * enough, and that signal is the model's way of saying "W'max is calibrated
 * too low for this rider." A sanity floor at -wMax keeps a stuck-high sensor
 * from driving the value to absurd negatives.
 */
class WPrimeEngine(private val preferencesRepository: PreferencesRepository) {

    companion object {
        private const val TAG = "WPrimeEngine"
        /** Skiba recovery time-constant baseline. With the dynamic-τ formula
         *  enabled, this is the long-coast asymptote ceiling (when the rider
         *  is exactly at CP the formula reduces to TAU_BASE + TAU_FLOOR). */
        private const val TAU_BASE = 546.0
        /** Floor added to keep τ from collapsing to zero on deep-rest coasting. */
        private const val TAU_FLOOR = 316.0
        private const val DT = 1.0    // 1 second update interval
        /** Ring-buffer cap for per-tick history. 86400 = 24h at 1Hz, ~2.8 MB. */
        const val MAX_HISTORY_SAMPLES = 86400
        /** Trailing window over which the W'-balance trend is measured to
         *  drive the TTE/TTF horizon. Direction + rate both come from the
         *  *observed* balance slope over this window — not from smoothed
         *  power, which lagged badly (a window full of pre-effort low power
         *  claimed "recovering" while the balance was visibly falling). The
         *  balance is the integral of (power − CP), so its slope is already
         *  the right, naturally-smooth quantity. */
        private const val TREND_WINDOW_MS = 30_000L
        /** Deadband (J/s) on the balance slope — below this the rider is
         *  holding steady; no horizon is shown. Avoids a flickery TTE/TTF
         *  when W' is essentially flat. */
        private const val TREND_DEADBAND_JPS = 3.0
        /** Hold the published TTE/TTF horizon steady for this long, then
         *  refresh. Even with 30 s-smoothed inputs the second-by-second value
         *  drifts; the field is consumed at a glance, so a 5 s refresh cadence
         *  reads as "stable" rather than "counting". */
        private const val HORIZON_PUBLISH_INTERVAL_MS = 5_000L
        /** Settle delay after the horizon direction changes (emptying ↔
         *  filling). Right at a switch the 30 s power window still holds the
         *  prior regime's samples, so the projection spikes to the 1 h cap
         *  before the new effort registers. Withholding the number for 5 s
         *  lets the window fill, so the value shown is real, not the cap
         *  artifact. During the settle the field shows the direction symbol
         *  with a "--" placeholder (no layout jump). Parameter-free — no
         *  athlete-specific tuning. */
        private const val HORIZON_SETTLE_MS = 5_000L
        /** At or above this percentage the rider is treated as fully recovered
         *  — the TTE/TTF horizon is hidden (nothing to recover to, not yet
         *  spending). 99.5 because recovery asymptotes toward wMax and the
         *  field rounds the percentage to a whole number. */
        private const val FULL_PCT = 99.5
    }

    private val _state = MutableStateFlow(WPrimeState())
    val state: StateFlow<WPrimeState> = _state.asStateFlow()

    /**
     * Per-tick W' history for the current ride. Append-on-update, ring-buffered
     * at MAX_HISTORY_SAMPLES. Cleared on reset(); NOT restored across crashes
     * (CheckpointManager restores current balance but not history — chart
     * resumes from the current value after a restart).
     */
    private val _wPrimeHistory = MutableStateFlow<List<WPrimeSample>>(emptyList())
    val wPrimeHistory: StateFlow<List<WPrimeSample>> = _wPrimeHistory.asStateFlow()

    private val historyBuffer = ArrayDeque<WPrimeSample>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var wBalance: Double = 20000.0
    private var wMax: Double = 20000.0
    private var cp: Int = 0
    private var pMax: Int = 0
    private var useThreeParamModel: Boolean = false
    private var lastUpdateTime: Long = 0L
    private var profileLoaded = false

    // Rolling stats for the dynamic-τ formula. Reset on reset().
    private var sumPowerBelowCp: Double = 0.0
    private var countPowerBelowCp: Long = 0L

    /** Trailing window of (timestampMs, wBalance) used to measure the W'
     *  trend that drives the TTE/TTF horizon. Trimmed every update() to
     *  TREND_WINDOW_MS. */
    private val recentBalanceWindow = ArrayDeque<Pair<Long, Double>>()

    // Published (held) TTE/TTF horizons — refreshed at most every
    // HORIZON_PUBLISH_INTERVAL_MS so the displayed countdown holds steady
    // between refreshes instead of changing every tick.
    private var publishedTimeToEmpty: Long = -1L
    private var publishedTimeToFull: Long = -1L
    private var lastHorizonPublishMs: Long = 0L

    // Horizon-direction tracking for the settle delay.
    private enum class HorizonDir { NONE, EMPTYING, FILLING }
    private var horizonDir: HorizonDir = HorizonDir.NONE
    private var horizonDirSinceMs: Long = 0L

    /**
     * True while the Karoo ride is in [io.hammerhead.karooext.models.RideState.Paused].
     * Set by [RideStateMonitor]. When paused, [update] is short-circuited and the
     * engine is driven instead by [tickPauseRecovery] at 1 Hz — pause time
     * accumulates toward W' refill at the same rate as coasting (Skiba P ≤ CP
     * recovery branch with P = 0).
     */
    @Volatile
    private var pauseActive: Boolean = false

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { profile ->
                if (profile.isConfigured) {
                    val newMax = profile.wPrimeMax.toDouble()
                    cp = profile.effectiveCp
                    pMax = profile.maxPower
                    useThreeParamModel = profile.useThreeParamModel
                    if (!profileLoaded) {
                        wMax = newMax
                        wBalance = newMax
                        profileLoaded = true
                    } else {
                        wMax = newMax
                        // Clamp balance if W'max decreased mid-ride (prevents >100%)
                        wBalance = wBalance.coerceAtMost(wMax)
                    }
                }
            }
        }
    }

    fun setPaused(paused: Boolean) {
        pauseActive = paused
    }

    fun update(state: LiveClimbState) {
        if (!profileLoaded || cp == 0) return
        // While the ride is paused, the recovery ticker owns the engine —
        // skip real samples so we don't double-count pause time.
        if (pauseActive) return
        // Gate ONLY on the presence of sensor data, not on power being non-zero.
        // power == 0 is a legitimate observation (rider coasting, recovery phase) —
        // the Skiba math handles it correctly (recovery branch applies). The prior
        // code's early-return on power == 0 froze the model AND the history during
        // coasting, hiding the recovery curve and leaving the chart empty for
        // riders who hadn't yet pedaled at full power.
        if (!state.hasData) return

        val now = state.timestamp
        val power = state.power.toDouble()

        // Accumulate average power below CP for the dynamic-τ formula. Done
        // BEFORE computing recovery so the current sample is reflected in τ
        // when the rider is coasting.
        if (power <= cp) {
            sumPowerBelowCp += power
            countPowerBelowCp++
        }

        val recovery = (wMax - wBalance) / currentTau()

        // Compute balance delta only when we have a previous timestamp to diff
        // against. On the very first tick we just seed lastUpdateTime + record
        // the baseline sample — no dW, no compute, but the chart gets its
        // first data point immediately rather than starting one tick late.
        if (lastUpdateTime != 0L) {
            val rawDt = (now - lastUpdateTime) / 1000.0
            // Clamp dt: minimum 0.1s prevents freeze on same-timestamp events,
            // maximum 5s caps large gaps (e.g. after pause)
            val dt = rawDt.coerceIn(0.1, 5.0)

            val dW = if (power > cp) {
                (recovery - (power - cp)) * dt
            } else {
                recovery * dt
            }

            // Allow negative — that's the model's signal that W'max is calibrated too low.
            // Cap above at wMax (can't exceed the model's max capacity) and floor at -wMax
            // (sanity bound — a stuck-high sensor can't drift to absurd negatives).
            wBalance = (wBalance + dW).coerceIn(-wMax, wMax)
        }
        lastUpdateTime = now

        val pct = wBalance / wMax * 100.0
        val depletionRate = if (power > cp) (power - cp) else 0.0
        val recoveryRateVal = if (power <= cp) recovery else 0.0

        // Maintain the balance-trend window, then derive direction + rate from
        // the observed slope of W'balance over the window. This is the same
        // quantity the rider watches move (the % going up or down), so the
        // horizon can never contradict the visible trend — the prior
        // smoothed-power approach could (and did, at ride start).
        recentBalanceWindow.addLast(now to wBalance)
        while (recentBalanceWindow.isNotEmpty() &&
               now - recentBalanceWindow.first().first > TREND_WINDOW_MS) {
            recentBalanceWindow.removeFirst()
        }
        val (oldTs, oldBalance) = recentBalanceWindow.first()
        val spanSeconds = (now - oldTs) / 1000.0
        // J/s: negative = depleting (heading to empty), positive = recovering.
        val trendRate = if (spanSeconds >= 1.0) (wBalance - oldBalance) / spanSeconds else 0.0

        // timeToEmpty / timeToFull from the trend. Rounded to the nearest 5 s
        // so the countdown reads coarsely, not jittery. Capped at 1 h.
        val computedTimeToEmpty = if (trendRate < -TREND_DEADBAND_JPS && wBalance > 0) {
            roundTo5((wBalance / -trendRate).toLong().coerceAtMost(3600L))
        } else -1L

        val computedTimeToFull = if (trendRate > TREND_DEADBAND_JPS && wBalance < wMax) {
            roundTo5(((wMax - wBalance) / trendRate).toLong().coerceAtMost(3600L))
        } else -1L

        // Determine the current horizon direction. At full W' the horizon is
        // hidden outright (nothing to recover to, not yet spending).
        val currentDir = when {
            pct >= FULL_PCT -> HorizonDir.NONE
            computedTimeToEmpty > 0 -> HorizonDir.EMPTYING
            computedTimeToFull > 0 -> HorizonDir.FILLING
            else -> HorizonDir.NONE
        }
        if (currentDir != horizonDir) {
            horizonDir = currentDir
            horizonDirSinceMs = now
            lastHorizonPublishMs = 0L
        }

        if (currentDir == HorizonDir.NONE) {
            // Full, or no clear horizon — hide the line.
            publishedTimeToEmpty = -1L
            publishedTimeToFull = -1L
        } else if (now - horizonDirSinceMs >= HORIZON_SETTLE_MS) {
            // Settled (≥5 s in this direction): refresh the real value on the
            // hold cadence.
            if (lastHorizonPublishMs == 0L || now - lastHorizonPublishMs >= HORIZON_PUBLISH_INTERVAL_MS) {
                publishedTimeToEmpty = if (currentDir == HorizonDir.EMPTYING) computedTimeToEmpty else -1L
                publishedTimeToFull = if (currentDir == HorizonDir.FILLING) computedTimeToFull else -1L
                lastHorizonPublishMs = now
            }
        }
        // else (direction changed, still settling): leave the published values
        // untouched — keep showing the PREVIOUS direction's last value (e.g. a
        // green ▲ recovery time) through the 5 s settle, then swap to the new
        // direction once its value is real. No "--" placeholder, no jump.

        _state.value = WPrimeState(
            balance = wBalance,
            maxBalance = wMax,
            percentage = pct,
            depletionRate = depletionRate,
            recoveryRate = recoveryRateVal,
            timeToEmpty = publishedTimeToEmpty,
            timeToFull = publishedTimeToFull,
            status = WPrimeState.statusForPercentage(pct),
            mpa = computeMpa(),
        )

        // Append to history every tick where sensor data is flowing — including
        // coasting, including the very first tick (baseline). Drives the W'
        // history chart's continuous live view across the entire ride.
        recordHistorySample(now, wBalance, pct, state.power)
    }

    /**
     * Apply one second of pure recovery — pause-time accounting.
     *
     * Driven by [RideStateMonitor]'s 1 Hz ticker while the Karoo ride is in
     * Paused state, where no real power samples flow but Skiba's recovery
     * branch still applies (P = 0 ≤ CP). Pause time accumulates toward
     * W' refill at the same rate as coasting on the bike.
     *
     * Same invariants as [update]: profile-gated, dt clamped, balance kept
     * in [-wMax, wMax], lastUpdateTime advanced, history sample appended at
     * power = 0 so the chart shows a continuous curve through pause.
     */
    fun tickPauseRecovery(now: Long) {
        if (!profileLoaded || cp == 0) return
        if (!pauseActive) return // belt-and-suspenders; only run while paused

        // Pause is power=0 ≤ CP — feed the dynamic-τ rolling average so τ
        // continues to adapt while the rider rests.
        sumPowerBelowCp += 0.0
        countPowerBelowCp++

        val recovery = (wMax - wBalance) / currentTau()

        if (lastUpdateTime != 0L) {
            val rawDt = (now - lastUpdateTime) / 1000.0
            val dt = rawDt.coerceIn(0.1, 5.0)
            wBalance = (wBalance + recovery * dt).coerceIn(-wMax, wMax)
        }
        lastUpdateTime = now

        val pct = wBalance / wMax * 100.0
        val recoveryRateVal = (wMax - wBalance) / currentTau()
        val timeToFull = if (recoveryRateVal > 0 && wBalance < wMax) {
            ((wMax - wBalance) / recoveryRateVal).toLong().coerceAtMost(3600L)
        } else -1L

        _state.value = WPrimeState(
            balance = wBalance,
            maxBalance = wMax,
            percentage = pct,
            depletionRate = 0.0,
            recoveryRate = recoveryRateVal,
            timeToEmpty = -1L,
            timeToFull = timeToFull,
            status = WPrimeState.statusForPercentage(pct),
            mpa = computeMpa(),
        )

        recordHistorySample(now, wBalance, pct, power = 0)
    }

    /**
     * Dynamic τ adapts the recovery time-constant to how far the rider's
     * coasting power is below CP. A bigger deficit (rider truly resting at
     * 50W) means faster recovery (lower τ); a small deficit (just below CP)
     * means slower recovery (longer τ).
     *
     * τ = TAU_BASE · exp(-0.01 · (CP − avgPowerBelowCP)) + TAU_FLOOR
     *
     * Falls back to TAU_BASE + TAU_FLOOR when no below-CP samples exist yet
     * (start of a ride before any coasting).
     */
    /** Round a positive second count to the nearest 5 s, keeping a 5 s floor
     *  so a still-positive horizon never rounds down to 0 (which would make
     *  the field's label vanish for the final ticks). */
    private fun roundTo5(seconds: Long): Long =
        if (seconds <= 0) seconds else maxOf(5L, ((seconds + 2L) / 5L) * 5L)

    private fun currentTau(): Double {
        if (countPowerBelowCp == 0L) return TAU_BASE + TAU_FLOOR
        val avgBelow = sumPowerBelowCp / countPowerBelowCp
        val deltaCp = cp - avgBelow
        return TAU_BASE * exp(-0.01 * deltaCp) + TAU_FLOOR
    }

    /**
     * Morton 3-parameter Maximum Power Available. Returns 0 unless the rider
     * has configured Pmax AND enabled the three-parameter toggle — the model
     * needs a calibrated Pmax to mean anything. Negative W' (DEFICIT) clamps
     * the fatigue ratio at 1.0 so MPA bottoms out at CP, not below.
     */
    private fun computeMpa(): Int {
        if (!useThreeParamModel || pMax <= cp || wMax <= 0.0) return 0
        val fatigueRatio = ((wMax - wBalance) / wMax).coerceIn(0.0, 1.0)
        val mpaWatts = pMax - (pMax - cp) * fatigueRatio.pow(2.0)
        return mpaWatts.toInt().coerceAtLeast(cp)
    }

    private fun recordHistorySample(
        timestampMs: Long,
        balance: Double,
        percentage: Double,
        power: Int
    ) {
        historyBuffer.addLast(
            WPrimeSample(
                timestampMs = timestampMs,
                balance = balance,
                percentage = percentage,
                power = power
            )
        )
        while (historyBuffer.size > MAX_HISTORY_SAMPLES) {
            historyBuffer.removeFirst()
        }
        // Publish an immutable snapshot to the StateFlow. ArrayList copy keeps
        // collectors safe from concurrent mutation of historyBuffer.
        _wPrimeHistory.value = ArrayList(historyBuffer)
    }

    fun reset() {
        wBalance = wMax
        lastUpdateTime = 0L
        sumPowerBelowCp = 0.0
        countPowerBelowCp = 0L
        recentBalanceWindow.clear()
        publishedTimeToEmpty = -1L
        publishedTimeToFull = -1L
        lastHorizonPublishMs = 0L
        horizonDir = HorizonDir.NONE
        horizonDirSinceMs = 0L
        historyBuffer.clear()
        _wPrimeHistory.value = emptyList()
        _state.value = WPrimeState(balance = wMax, maxBalance = wMax)
    }

    /**
     * Restore current balance from a checkpoint. History is NOT restored —
     * the chart resumes from this point with no prior history shown. A future
     * `chore/restore-history-from-fit` PR could read the in-progress FIT
     * file to reconstruct samples up to the crash; out of scope here.
     */
    fun restore(balance: Double) {
        wBalance = balance.coerceIn(-wMax, wMax)
        _state.value = WPrimeState.fromBalance(wBalance, wMax)
    }
}
