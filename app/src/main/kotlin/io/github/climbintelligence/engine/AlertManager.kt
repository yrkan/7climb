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

    @Volatile
    private var alertsEnabled = true
    @Volatile
    private var soundEnabled = false
    @Volatile
    private var cooldownMs = 30_000L

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

        // Monitor W' state for critical alerts
        scope?.launch {
            extension.wPrimeEngine.state.collect { wPrimeState ->
                if (!alertsEnabled) return@collect
                if (wPrimeState.status == WPrimeStatus.CRITICAL || wPrimeState.status == WPrimeStatus.EMPTY) {
                    dispatchWithCooldown(
                        lastAlert = wPrimeLastAlert,
                        alertId = ALERT_WPRIME,
                        title = extension.getString(R.string.alert_wprime_critical_title),
                        detail = extension.getString(R.string.alert_wprime_critical_detail),
                        urgent = true
                    )
                }
            }
        }
    }

    fun dispatchClimbStarted(name: String, lengthKm: Double, avgGrade: Double) {
        if (!alertsEnabled) return
        dispatchWithCooldown(
            lastAlert = climbStartLastAlert,
            alertId = ALERT_CLIMB_START,
            title = extension.getString(R.string.alert_climb_started_title),
            detail = extension.getString(R.string.alert_climb_started_detail, name, lengthKm, avgGrade),
            urgent = false
        )
    }

    fun dispatchSummitApproaching(distanceM: Int) {
        if (!alertsEnabled) return
        dispatchWithCooldown(
            lastAlert = summitLastAlert,
            alertId = ALERT_SUMMIT,
            title = extension.getString(R.string.alert_summit_title),
            detail = extension.getString(R.string.alert_summit_detail, distanceM),
            urgent = false
        )
    }

    fun dispatchPR(timeDelta: String) {
        if (!alertsEnabled) return
        try {
            extension.karooSystem.dispatch(TurnScreenOn)
            extension.karooSystem.dispatch(
                InRideAlert(
                    id = ALERT_PR,
                    icon = R.drawable.ic_climb,
                    title = extension.getString(R.string.alert_pr_title),
                    detail = extension.getString(R.string.alert_pr_detail, timeDelta),
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
    }

    fun destroy() {
        stopMonitoring()
    }
}
