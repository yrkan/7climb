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
        private const val TOLERANCE_WATTS = 10
    }

    private val _target = MutableStateFlow(PacingTarget())
    val target: StateFlow<PacingTarget> = _target.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var profile = AthleteProfile()
    private var mode = PacingMode.STEADY

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
    }

    fun update(state: LiveClimbState, climb: ClimbInfo?) {
        if (!profile.isConfigured || !state.hasData) return

        val grade = state.grade
        val altitude = state.altitude
        val speed = state.speed

        // Calculate target power based on physics
        val targetPower = calculateTargetPower(grade, altitude, speed)
        if (targetPower <= 0) {
            _target.value = PacingTarget()
            return
        }

        val delta = state.power - targetPower
        val advice = when {
            delta > TOLERANCE_WATTS -> PacingAdvice.EASE_OFF
            delta < -TOLERANCE_WATTS -> PacingAdvice.PUSH
            kotlin.math.abs(delta) <= 5 -> PacingAdvice.PERFECT
            else -> PacingAdvice.STEADY
        }

        val projectedTime = if (climb != null && climb.isActive && speed > 0.5) {
            (climb.distanceToTop / speed).toLong()
        } else 0L

        _target.value = PacingTarget(
            targetPower = targetPower,
            rangeLow = targetPower - TOLERANCE_WATTS,
            rangeHigh = targetPower + TOLERANCE_WATTS,
            delta = delta,
            advice = advice,
            projectedTimeSeconds = projectedTime,
            mode = mode
        )
    }

    private fun calculateTargetPower(grade: Double, altitude: Double, speed: Double): Int {
        if (grade < 1.0) return 0 // Only pace on climbs

        val mass = profile.totalMass
        val ftp = profile.ftp.toDouble()

        // Physics-based forces
        val gravity = PhysicsUtils.gravityForce(mass, grade)
        val rolling = PhysicsUtils.rollingResistance(mass, grade, profile.crr)
        val aero = PhysicsUtils.aeroDrag(profile.cda, altitude, speed)

        val totalForce = gravity + rolling + aero
        val targetSpeed = when (mode) {
            PacingMode.STEADY -> speed.coerceIn(2.0, 8.0)
            PacingMode.RACE -> speed.coerceIn(2.5, 10.0)
            PacingMode.SURVIVAL -> speed.coerceIn(1.5, 6.0)
        }

        var targetPower = (totalForce * targetSpeed).toInt()

        // Apply mode factors
        targetPower = when (mode) {
            PacingMode.STEADY -> targetPower.coerceIn((ftp * 0.6).toInt(), (ftp * 1.05).toInt())
            PacingMode.RACE -> targetPower.coerceIn((ftp * 0.7).toInt(), (ftp * 1.15).toInt())
            PacingMode.SURVIVAL -> targetPower.coerceIn((ftp * 0.5).toInt(), (ftp * 0.9).toInt())
        }

        return targetPower
    }
}
