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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var profile = AthleteProfile()
    private var mode = PacingMode.STEADY
    private var toleranceWatts = 10
    var effectiveFtpOverride: Int = 0
        set(value) {
            if (field != value) { field = value; invalidateCache() }
        }
    var wPrimePercent: Double = 100.0

    // Physics cache
    private var lastGrade = Double.NaN
    private var lastAltitude = Double.NaN
    private var lastTargetPower = 0
    private var lastCacheFtp = 0
    private var lastCacheMode = mode

    private fun invalidateCache() {
        lastGrade = Double.NaN
    }

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { p ->
                profile = p
                invalidateCache()
            }
        }
        scope.launch {
            preferencesRepository.pacingModeFlow.collect { m ->
                mode = m
                invalidateCache()
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

        // Compute whole-climb strategy for active route climbs with segment data
        val (phase, firstHalf, secondHalf) = if (
            climb != null && climb.isActive && climb.isFromRoute && climb.segments.isNotEmpty()
        ) {
            computeClimbStrategy(climb, targetPower)
        } else Triple("", 0, 0)

        _target.value = PacingTarget(
            targetPower = targetPower,
            rangeLow = targetPower - tolerance,
            rangeHigh = targetPower + tolerance,
            delta = delta,
            advice = advice,
            projectedTimeSeconds = projectedTime,
            mode = mode,
            strategyPhase = phase,
            firstHalfTarget = firstHalf,
            secondHalfTarget = secondHalf
        )
    }

    /**
     * Whole-climb pacing strategy: negative-split approach adjusted by W' state.
     * Returns (phase, firstHalfTarget, secondHalfTarget).
     */
    private fun computeClimbStrategy(climb: ClimbInfo, basePower: Int): Triple<String, Int, Int> {
        val progress = climb.progress

        val phase = when {
            progress > 0.90 -> "SPRINT"
            progress > 0.60 -> "PUSH"
            progress > 0.30 -> "BUILD"
            else -> "SAVE"
        }

        // Adjust split based on W' balance — conserve when low
        val splitBias = when {
            wPrimePercent < 30 -> 0.88
            wPrimePercent < 60 -> 0.93
            else -> 0.97
        }

        val firstHalf = (basePower * splitBias).toInt()
        // Cap second half: when W' is low, don't push above basePower
        val secondHalfBias = if (wPrimePercent < 60) {
            ((splitBias + 1.0) / 2.0).coerceAtMost(1.0)
        } else {
            2.0 - splitBias
        }
        val secondHalf = (basePower * secondHalfBias).toInt()

        return Triple(phase, firstHalf, secondHalf)
    }

    /**
     * Physics-based pacing using CdA, Crr, mass, and altitude-adjusted air density.
     * CdA/Crr/bike weight now directly affect pacing targets.
     */
    private fun calculateTargetPower(grade: Double, altitude: Double): Int {
        if (grade < 1.0) return 0 // Only pace on climbs

        val ftp = (if (effectiveFtpOverride > 0) effectiveFtpOverride else profile.ftp).toDouble()
        if (ftp <= 0) return 0

        // Reuse cached result if inputs unchanged
        val ftpInt = ftp.toInt()
        if (kotlin.math.abs(grade - lastGrade) < 0.1 &&
            kotlin.math.abs(altitude - lastAltitude) < 5.0 &&
            ftpInt == lastCacheFtp && mode == lastCacheMode &&
            lastTargetPower > 0) {
            return lastTargetPower
        }

        val mass = profile.totalMass
        val cda = profile.cda
        val crr = profile.crr

        // Speed that FTP produces at current conditions
        val ftpSpeed = PhysicsUtils.speedFromPower(ftp, mass, grade, crr, cda, altitude)

        // Mode-specific speed target relative to FTP speed
        val targetSpeed = when (mode) {
            PacingMode.STEADY -> ftpSpeed * 0.92
            PacingMode.RACE -> ftpSpeed * 1.00
            PacingMode.SURVIVAL -> ftpSpeed * 0.80
        }

        // Power required for target speed
        val targetPower = PhysicsUtils.powerRequired(mass, grade, crr, cda, altitude, targetSpeed).toInt()

        // Safety clamp to mode-specific FTP-relative ranges
        val result = when (mode) {
            PacingMode.STEADY -> targetPower.coerceIn((ftp * 0.6).toInt(), (ftp * 1.05).toInt())
            PacingMode.RACE -> targetPower.coerceIn((ftp * 0.7).toInt(), (ftp * 1.15).toInt())
            PacingMode.SURVIVAL -> targetPower.coerceIn((ftp * 0.5).toInt(), (ftp * 0.9).toInt())
        }

        lastGrade = grade
        lastAltitude = altitude
        lastCacheFtp = ftpInt
        lastCacheMode = mode
        lastTargetPower = result
        return result
    }
}
