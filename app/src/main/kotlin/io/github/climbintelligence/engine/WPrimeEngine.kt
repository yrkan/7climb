package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.WPrimeState
import io.github.climbintelligence.data.model.WPrimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Skiba differential W' Balance model.
 *
 * dW'/dt = recovery - expenditure
 * if P > CP: dW' = ((W'max - W') / tau - (P - CP)) * dt
 * if P <= CP: dW' = ((W'max - W') / tau) * dt
 * tau = 546.0 (Skiba constant)
 */
class WPrimeEngine(private val preferencesRepository: PreferencesRepository) {

    companion object {
        private const val TAG = "WPrimeEngine"
        private const val TAU = 546.0 // Skiba recovery time constant
        private const val DT = 1.0    // 1 second update interval
    }

    private val _state = MutableStateFlow(WPrimeState())
    val state: StateFlow<WPrimeState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var wBalance: Double = 20000.0
    private var wMax: Double = 20000.0
    private var cp: Int = 0
    private var lastUpdateTime: Long = 0L
    private var profileLoaded = false

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { profile ->
                if (profile.isConfigured) {
                    wMax = profile.wPrimeMax.toDouble()
                    cp = profile.cp
                    if (!profileLoaded) {
                        wBalance = wMax
                        profileLoaded = true
                    }
                }
            }
        }
    }

    fun update(state: LiveClimbState) {
        if (!profileLoaded || cp == 0) return
        if (!state.hasData || state.power == 0) return

        val now = state.timestamp
        if (lastUpdateTime == 0L) {
            lastUpdateTime = now
            return
        }

        val dt = ((now - lastUpdateTime) / 1000.0).coerceIn(0.0, 5.0)
        lastUpdateTime = now
        if (dt <= 0) return

        val power = state.power.toDouble()
        val recovery = (wMax - wBalance) / TAU

        val dW = if (power > cp) {
            (recovery - (power - cp)) * dt
        } else {
            recovery * dt
        }

        wBalance = (wBalance + dW).coerceIn(0.0, wMax)

        val pct = wBalance / wMax * 100.0
        val depletionRate = if (power > cp) (power - cp) else 0.0
        val recoveryRateVal = if (power <= cp) recovery else 0.0

        val timeToEmpty = if (depletionRate > recoveryRateVal && depletionRate > 0) {
            (wBalance / (depletionRate - recoveryRateVal)).toLong()
        } else -1L

        val timeToFull = if (recoveryRateVal > 0 && wBalance < wMax) {
            ((wMax - wBalance) / recoveryRateVal).toLong()
        } else -1L

        _state.value = WPrimeState(
            balance = wBalance,
            maxBalance = wMax,
            percentage = pct,
            depletionRate = depletionRate,
            recoveryRate = recoveryRateVal,
            timeToEmpty = timeToEmpty,
            timeToFull = timeToFull,
            status = when {
                pct > 90 -> WPrimeStatus.FRESH
                pct > 70 -> WPrimeStatus.GOOD
                pct > 50 -> WPrimeStatus.WORKING
                pct > 30 -> WPrimeStatus.DEPLETING
                pct > 10 -> WPrimeStatus.CRITICAL
                else -> WPrimeStatus.EMPTY
            }
        )
    }

    fun reset() {
        wBalance = wMax
        lastUpdateTime = 0L
        _state.value = WPrimeState(balance = wMax, maxBalance = wMax)
    }

    fun restore(balance: Double) {
        wBalance = balance.coerceIn(0.0, wMax)
        _state.value = WPrimeState.fromBalance(wBalance, wMax)
    }
}
