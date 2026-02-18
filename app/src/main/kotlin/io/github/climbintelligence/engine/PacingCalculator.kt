package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.PacingAdvice
import io.github.climbintelligence.data.model.PacingMode
import io.github.climbintelligence.data.model.PacingTarget
import io.github.climbintelligence.util.PhysicsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PacingCalculator(private val preferencesRepository: PreferencesRepository) {

    companion object {
        private const val TAG = "PacingCalculator"
    }

    private val _target = MutableStateFlow(PacingTarget())
    val target: StateFlow<PacingTarget> = _target.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var profile = AthleteProfile()
    private var mode = PacingMode.STEADY
    @Volatile
    private var toleranceWatts = 10

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { p ->
                profile = p
            }
        }
        scope.launch {
            preferencesRepository.pacingModeFlow.collect { m ->
                mode = m
            }
        }
        scope.launch {
            preferencesRepository.pacingToleranceWattsFlow.collect { w ->
                toleranceWatts = w
            }
        }
    }

    fun update(state: LiveClimbState, climb: ClimbInfo?) {
        if (!profile.isConfigured || !state.hasData) return

        val grade = state.grade
        val altitude = state.altitude
        val speed = state.speed

        // Calculate target power based on FTP and gradient physics
        val targetPower = calculateTargetPower(grade, altitude)
        if (targetPower <= 0) {
            _target.value = PacingTarget()
            return
        }

        val tolerance = toleranceWatts
        val delta = state.power - targetPower
        val advice = when {
            delta > tolerance -> PacingAdvice.EASE_OFF
            delta < -tolerance -> PacingAdvice.PUSH
            kotlin.math.abs(delta) <= tolerance / 2 -> PacingAdvice.PERFECT
            else -> PacingAdvice.STEADY
        }

        val projectedTime = if (climb != null && climb.hasRouteMetrics && speed > 0.5) {
            (climb.distanceToTop / speed).toLong()
        } else 0L

        _target.value = PacingTarget(
            targetPower = targetPower,
            rangeLow = targetPower - tolerance,
            rangeHigh = targetPower + tolerance,
            delta = delta,
            advice = advice,
            projectedTimeSeconds = projectedTime,
            mode = mode
        )
    }

    /**
     * FTP-based pacing with gradient adjustment.
     *
     * On steep grades aerodynamic drag is negligible (slow speed), so pushing
     * slightly above base is optimal. On shallow grades aero matters more,
     * so ease slightly to maintain efficiency.
     */
    private fun calculateTargetPower(grade: Double, altitude: Double): Int {
        if (grade < 1.0) return 0 // Only pace on climbs

        val ftp = profile.ftp.toDouble()

        // Base FTP fraction per pacing mode
        val baseFraction = when (mode) {
            PacingMode.STEADY -> 0.90
            PacingMode.RACE -> 1.00
            PacingMode.SURVIVAL -> 0.75
        }

        // Gradient adjustment: push harder on steep (all gravity, negligible aero),
        // ease slightly on shallow (aero still matters)
        val gradientFactor = when {
            grade >= 10.0 -> 1.05
            grade >= 6.0 -> 1.02
            grade >= 3.0 -> 1.00
            else -> 0.97
        }

        // Altitude correction: VO2max drops ~6.5% per 1000m above 1500m
        // Reduces effective FTP proportionally (capped at 25% reduction)
        val altitudeFactor = if (altitude > 1500) {
            1.0 - ((altitude - 1500) / 1000.0 * 0.065).coerceAtMost(0.25)
        } else 1.0

        val targetPower = (ftp * baseFraction * gradientFactor * altitudeFactor).toInt()

        // Clamp to mode-specific range
        return when (mode) {
            PacingMode.STEADY -> targetPower.coerceIn((ftp * 0.6).toInt(), (ftp * 1.05).toInt())
            PacingMode.RACE -> targetPower.coerceIn((ftp * 0.7).toInt(), (ftp * 1.15).toInt())
            PacingMode.SURVIVAL -> targetPower.coerceIn((ftp * 0.5).toInt(), (ftp * 0.9).toInt())
        }
    }
}
