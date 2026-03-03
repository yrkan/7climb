package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.RideMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow

class MetricsEngine(preferencesRepository: PreferencesRepository) {

    private val _state = MutableStateFlow(RideMetrics())
    val state: StateFlow<RideMetrics> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var ftp = 0
    @Volatile
    private var fatigueEnabled = true
    @Volatile
    private var fatigueDecayRate = 0.03
    @Volatile
    private var fatigueThresholdHours = 2.0

    // NP circular buffer (30s window)
    private val npWindow = IntArray(30)
    private var npWindowIndex = 0
    private var npWindowCount = 0
    private var npSumPow4 = 0.0
    private var npCount = 0L

    // Accumulators
    private var totalPowerSum = 0L
    private var totalPowerCount = 0L
    private var totalEnergyJoules = 0.0
    private var rideStartTime = 0L
    private val zoneSeconds = IntArray(7)

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { p ->
                ftp = p.ftp
            }
        }
        scope.launch {
            preferencesRepository.fatigueEnabledFlow.collect { fatigueEnabled = it }
        }
        scope.launch {
            preferencesRepository.fatigueDecayRateFlow.collect { fatigueDecayRate = it }
        }
        scope.launch {
            preferencesRepository.fatigueThresholdHoursFlow.collect { fatigueThresholdHours = it }
        }
    }

    fun update(state: LiveClimbState) {
        if (!state.hasData) return

        val power = state.power.coerceAtLeast(0)

        // Track ride start
        if (rideStartTime == 0L && power > 0) {
            rideStartTime = System.currentTimeMillis()
        }
        if (rideStartTime == 0L) return

        // Accumulate totals
        totalPowerSum += power
        totalPowerCount++
        totalEnergyJoules += power // 1 sample per second = power * 1s

        // NP: 30s rolling average, then fourth-power accumulation
        val oldPower = npWindow[npWindowIndex]
        npWindow[npWindowIndex] = power
        npWindowIndex = (npWindowIndex + 1) % 30
        npWindowCount = minOf(npWindowCount + 1, 30)

        if (npWindowCount >= 30) {
            // Calculate rolling 30s average
            val rollingAvg = npWindow.sum().toDouble() / 30.0
            // Accumulate fourth power of rolling average
            npSumPow4 += rollingAvg.pow(4)
            npCount++
        }

        // Zone classification
        if (ftp > 0 && power > 0) {
            val zoneIndex = when {
                power < (ftp * 0.55).toInt() -> 0  // Z1 Recovery
                power < (ftp * 0.75).toInt() -> 1  // Z2 Endurance
                power < (ftp * 0.90).toInt() -> 2  // Z3 Tempo
                power < (ftp * 1.05).toInt() -> 3  // Z4 Threshold
                power < (ftp * 1.20).toInt() -> 4  // Z5 VO2max
                power < (ftp * 1.50).toInt() -> 5  // Z6 Anaerobic
                else -> 6                           // Z7 Neuromuscular
            }
            zoneSeconds[zoneIndex]++
        }

        // Compute current zone for display
        val currentZone = if (ftp > 0 && power > 0) {
            when {
                power < (ftp * 0.55).toInt() -> 0
                power < (ftp * 0.75).toInt() -> 1
                power < (ftp * 0.90).toInt() -> 2
                power < (ftp * 1.05).toInt() -> 3
                power < (ftp * 1.20).toInt() -> 4
                power < (ftp * 1.50).toInt() -> 5
                else -> 6
            }
        } else -1

        // Calculate metrics
        val np = if (npCount > 0) (npSumPow4 / npCount).pow(0.25).toInt() else 0
        val avgPower = if (totalPowerCount > 0) (totalPowerSum / totalPowerCount).toInt() else 0
        val elapsedMs = System.currentTimeMillis() - rideStartTime
        val elapsedSeconds = elapsedMs / 1000L
        val hours = elapsedSeconds / 3600.0

        val intensityFactor = if (ftp > 0 && np > 0) np.toDouble() / ftp else 0.0
        val tss = if (hours > 0) intensityFactor * intensityFactor * hours * 100.0 else 0.0
        val vi = if (avgPower > 0 && np > 0) np.toDouble() / avgPower else 0.0
        val totalKj = totalEnergyJoules / 1000.0

        _state.value = RideMetrics(
            normalizedPower = np,
            intensityFactor = intensityFactor,
            trainingStressScore = tss,
            variabilityIndex = vi,
            elapsedSeconds = elapsedSeconds,
            avgPower = avgPower,
            totalKj = totalKj,
            powerZones = zoneSeconds.copyOf(),
            currentZone = currentZone,
            hasData = totalPowerCount > 0
        )
    }

    val effectiveFtp: Int get() {
        if (!fatigueEnabled || ftp <= 0) return ftp
        val hours = _state.value.elapsedSeconds / 3600.0
        if (hours <= fatigueThresholdHours) return ftp
        val decay = fatigueDecayRate * (hours - fatigueThresholdHours)
        return (ftp * (1.0 - decay).coerceAtLeast(0.75)).toInt()
    }

    fun reset() {
        npWindow.fill(0)
        npWindowIndex = 0
        npWindowCount = 0
        npSumPow4 = 0.0
        npCount = 0L
        totalPowerSum = 0L
        totalPowerCount = 0L
        totalEnergyJoules = 0.0
        rideStartTime = 0L
        zoneSeconds.fill(0)
        _state.value = RideMetrics()
    }
}
