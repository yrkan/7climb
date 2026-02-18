package io.github.climbintelligence.engine

import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.WPrimeStatus
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class AlertManager(
    private val extension: ClimbIntelligenceExtension,
    private val preferencesRepository: PreferencesRepository
) {
    companion object {
        private const val TAG = "AlertManager"
        private const val ALERT_WPRIME = "climb_wprime"
        private const val ALERT_STEEP = "climb_steep"
        private const val ALERT_SUMMIT = "climb_summit"
        private const val ALERT_CLIMB_START = "climb_started"
        private const val ALERT_PR = "climb_pr"
    }

    private var scope: CoroutineScope? = null

    private val wPrimeLastAlert = AtomicLong(0)
    private val steepLastAlert = AtomicLong(0)
    private val summitLastAlert = AtomicLong(0)
    private val climbStartLastAlert = AtomicLong(0)

    @Volatile private var alertsEnabled = true
    @Volatile private var soundEnabled = false
    @Volatile private var cooldownMs = 30_000L
    @Volatile private var wPrimeAlertThreshold = 20.0
    @Volatile private var alertWPrimeEnabled = true
    @Volatile private var alertSteepEnabled = true
    @Volatile private var alertSummitEnabled = true
    @Volatile private var alertClimbStartEnabled = true

    /** Track whether W' was previously below threshold — alert only fires on crossing */
    @Volatile private var wasWPrimeBelowThreshold = false

    fun startMonitoring() {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope?.launch {
            preferencesRepository.alertsEnabledFlow.collect { alertsEnabled = it }
        }
        scope?.launch {
            preferencesRepository.alertSoundFlow.collect { soundEnabled = it }
        }
        scope?.launch {
            preferencesRepository.alertCooldownFlow.collect { cooldownMs = it * 1000L }
        }
        scope?.launch {
            preferencesRepository.wPrimeAlertThresholdFlow.collect { wPrimeAlertThreshold = it.toDouble() }
        }
        scope?.launch {
            preferencesRepository.alertWPrimeFlow.collect { alertWPrimeEnabled = it }
        }
        scope?.launch {
            preferencesRepository.alertSteepFlow.collect { alertSteepEnabled = it }
        }
        scope?.launch {
            preferencesRepository.alertSummitFlow.collect { alertSummitEnabled = it }
        }
        scope?.launch {
            preferencesRepository.alertClimbStartFlow.collect { alertClimbStartEnabled = it }
        }

        // Monitor W' state for critical alerts — only fire on threshold crossing
        scope?.launch {
            extension.wPrimeEngine.state.collect { wPrimeState ->
                if (!alertsEnabled || !alertWPrimeEnabled) return@collect

                val isBelow = wPrimeState.percentage <= wPrimeAlertThreshold
                if (isBelow && !wasWPrimeBelowThreshold) {
                    // Just crossed below threshold — dispatch alert
                    val pct = wPrimeState.percentage.toInt()
                    val targetPower = extension.pacingCalculator.target.value.targetPower

                    val title = extension.getString(R.string.alert_wprime_title, pct)
                    val detail = when {
                        wPrimeState.status == WPrimeStatus.EMPTY ->
                            extension.getString(R.string.alert_wprime_empty)
                        targetPower > 0 && wPrimeState.timeToEmpty > 0 ->
                            extension.getString(
                                R.string.alert_wprime_detail_full,
                                targetPower,
                                wPrimeState.timeToEmpty
                            )
                        targetPower > 0 ->
                            extension.getString(R.string.alert_wprime_detail_power, targetPower)
                        wPrimeState.timeToEmpty > 0 ->
                            extension.getString(R.string.alert_wprime_detail_time, wPrimeState.timeToEmpty)
                        else ->
                            extension.getString(R.string.alert_wprime_detail_basic)
                    }

                    dispatchWithCooldown(
                        lastAlert = wPrimeLastAlert,
                        alertId = ALERT_WPRIME,
                        title = title,
                        detail = detail,
                        urgent = true
                    )
                }
                wasWPrimeBelowThreshold = isBelow
            }
        }

        // Monitor for steep sections ahead (from TacticalAnalyzer)
        scope?.launch {
            extension.climbDataService.activeClimb.collect { climb ->
                if (!alertsEnabled || !alertSteepEnabled) return@collect
                if (climb == null || !climb.isActive || climb.segments.isEmpty()) return@collect

                val insight = extension.tacticalAnalyzer.getPrimaryInsight(climb, climb.progress)
                if (insight != null && insight.type == TacticalAnalyzer.InsightType.STEEP_SECTION
                    && insight.distanceAhead < 300
                ) {
                    val distM = insight.distanceAhead.toInt()
                    val title = extension.getString(R.string.alert_steep_title, insight.description, distM)
                    val detail = if (distM < 100) {
                        extension.getString(R.string.alert_steep_detail_now)
                    } else {
                        extension.getString(R.string.alert_steep_detail_ahead)
                    }

                    dispatchWithCooldown(
                        lastAlert = steepLastAlert,
                        alertId = ALERT_STEEP,
                        title = title,
                        detail = detail,
                        urgent = false
                    )
                }
            }
        }
    }

    fun dispatchClimbStarted(name: String, lengthKm: Double, avgGrade: Double, elevationM: Double) {
        if (!alertsEnabled || !alertClimbStartEnabled) return

        val targetPower = extension.pacingCalculator.target.value.targetPower
        val title = extension.getString(R.string.alert_climb_title, avgGrade, lengthKm)
        val detail = if (targetPower > 0) {
            extension.getString(R.string.alert_climb_detail_power, elevationM.toInt(), targetPower)
        } else {
            extension.getString(R.string.alert_climb_detail_basic, elevationM.toInt())
        }

        dispatchWithCooldown(
            lastAlert = climbStartLastAlert,
            alertId = ALERT_CLIMB_START,
            title = title,
            detail = detail,
            urgent = false
        )
    }

    fun dispatchSummitApproaching(distanceM: Int) {
        if (!alertsEnabled || !alertSummitEnabled) return

        val wPrime = extension.wPrimeEngine.state.value
        val title = extension.getString(R.string.alert_summit_title, distanceM)

        val detail = when {
            wPrime.status == WPrimeStatus.CRITICAL || wPrime.status == WPrimeStatus.EMPTY ->
                extension.getString(R.string.alert_summit_detail_save, wPrime.percentage.toInt())
            wPrime.percentage > 50 ->
                extension.getString(R.string.alert_summit_detail_push)
            else ->
                extension.getString(R.string.alert_summit_detail_hold)
        }

        dispatchWithCooldown(
            lastAlert = summitLastAlert,
            alertId = ALERT_SUMMIT,
            title = title,
            detail = detail,
            urgent = false
        )
    }

    fun dispatchPR(climbName: String, timeDelta: String) {
        if (!alertsEnabled) return
        val title = extension.getString(R.string.alert_pr_title, timeDelta)
        val stats = extension.climbStatsTracker.state.value
        val detail = if (stats.avgPower > 0) {
            extension.getString(R.string.alert_pr_detail_stats, stats.avgPower, stats.avgWKg)
        } else if (climbName.isNotEmpty()) {
            extension.getString(R.string.alert_pr_detail_name, climbName)
        } else {
            extension.getString(R.string.alert_pr_detail_basic)
        }

        try {
            extension.karooSystem.dispatch(TurnScreenOn)
            extension.karooSystem.dispatch(
                InRideAlert(
                    id = ALERT_PR,
                    icon = R.drawable.ic_climb,
                    title = title,
                    detail = detail,
                    autoDismissMs = 8000L,
                    backgroundColor = R.color.alert_bg,
                    textColor = R.color.alert_text
                )
            )
            if (soundEnabled) playPRSound()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to dispatch PR alert: ${e.message}")
        }
    }

    private fun dispatchWithCooldown(
        lastAlert: AtomicLong,
        alertId: String,
        title: String,
        detail: String,
        urgent: Boolean
    ) {
        val now = System.currentTimeMillis()
        val last = lastAlert.get()
        if (now - last < cooldownMs) return
        if (!lastAlert.compareAndSet(last, now)) return

        try {
            extension.karooSystem.dispatch(TurnScreenOn)
            extension.karooSystem.dispatch(
                InRideAlert(
                    id = alertId,
                    icon = R.drawable.ic_climb,
                    title = title,
                    detail = detail,
                    autoDismissMs = if (urgent) 8000L else 5000L,
                    backgroundColor = R.color.alert_bg,
                    textColor = R.color.alert_text
                )
            )
            if (soundEnabled) playAlertSound(urgent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to dispatch alert: ${e.message}")
        }
    }

    private fun playAlertSound(urgent: Boolean) {
        try {
            val tones = if (urgent) {
                listOf(
                    PlayBeepPattern.Tone(frequency = 800, durationMs = 150),
                    PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                    PlayBeepPattern.Tone(frequency = 800, durationMs = 150),
                    PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                    PlayBeepPattern.Tone(frequency = 800, durationMs = 150)
                )
            } else {
                listOf(
                    PlayBeepPattern.Tone(frequency = 600, durationMs = 150),
                    PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                    PlayBeepPattern.Tone(frequency = 600, durationMs = 150)
                )
            }
            extension.karooSystem.dispatch(PlayBeepPattern(tones))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to play sound: ${e.message}")
        }
    }

    private fun playPRSound() {
        try {
            extension.karooSystem.dispatch(PlayBeepPattern(listOf(
                PlayBeepPattern.Tone(frequency = 800, durationMs = 100),
                PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                PlayBeepPattern.Tone(frequency = 1000, durationMs = 100),
                PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                PlayBeepPattern.Tone(frequency = 1200, durationMs = 200)
            )))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to play PR sound: ${e.message}")
        }
    }

    fun stopMonitoring() {
        scope?.cancel()
        scope = null
    }

    fun reset() {
        wPrimeLastAlert.set(0)
        steepLastAlert.set(0)
        summitLastAlert.set(0)
        climbStartLastAlert.set(0)
        wasWPrimeBelowThreshold = false
    }

    fun destroy() {
        stopMonitoring()
    }
}
