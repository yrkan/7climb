package io.github.climbintelligence.engine

import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.DetectionSettings
import io.github.climbintelligence.data.model.LiveClimbState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time climb detection for rides without a loaded route.
 * Uses altitude and distance data to detect climbs automatically.
 *
 * States: NotClimbing -> PotentialClimb -> ConfirmedClimb
 */
class ClimbDetector {

    companion object {
        private const val TAG = "ClimbDetector"
        private const val SMOOTHING_WINDOW = 7
    }

    enum class DetectionState {
        NOT_CLIMBING,
        POTENTIAL_CLIMB,
        CONFIRMED_CLIMB
    }

    private val _detectedClimb = MutableStateFlow<ClimbInfo?>(null)
    val detectedClimb: StateFlow<ClimbInfo?> = _detectedClimb.asStateFlow()

    private val _detectionState = MutableStateFlow(DetectionState.NOT_CLIMBING)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    // Configurable detection parameters
    @Volatile private var minGradeStart = 4.0
    @Volatile private var minGradeContinue = 2.5
    @Volatile private var confirmDistance = 200.0
    @Volatile private var endDistance = 150.0
    @Volatile private var minElevationGain = 15.0

    // Altitude/distance buffer for smoothing
    private val altitudeBuffer = mutableListOf<Double>()
    private val distanceBuffer = mutableListOf<Double>()
    private val gradeBuffer = mutableListOf<Double>()

    // Climb tracking
    private var climbStartDistance = 0.0
    private var climbStartAltitude = 0.0
    private var climbStartLat = 0.0
    private var climbStartLon = 0.0
    private var potentialStartDistance = 0.0
    private var flatDistance = 0.0
    private var climbCount = 0
    private var totalElevationGain = 0.0
    private var lastAltitude = Double.NaN
    private var startTimestamp = 0L

    fun updateSettings(settings: DetectionSettings) {
        minGradeStart = settings.minGrade
        minGradeContinue = settings.minGradeContinue
        confirmDistance = settings.confirmDistance.toDouble()
        endDistance = settings.endDistance.toDouble()
        minElevationGain = settings.minElevation.toDouble()
    }

    fun update(state: LiveClimbState) {
        if (!state.hasData) return

        // Add to buffers
        gradeBuffer.add(state.grade)
        altitudeBuffer.add(state.altitude)
        distanceBuffer.add(state.distance)

        // Keep buffer size manageable
        if (gradeBuffer.size > SMOOTHING_WINDOW * 2) {
            gradeBuffer.removeAt(0)
            altitudeBuffer.removeAt(0)
            distanceBuffer.removeAt(0)
        }

        // Smooth grade
        val smoothedGrade = if (gradeBuffer.size >= SMOOTHING_WINDOW) {
            gradeBuffer.takeLast(SMOOTHING_WINDOW).average()
        } else {
            state.grade
        }

        // Track elevation gain during potential/confirmed climb
        if (_detectionState.value != DetectionState.NOT_CLIMBING && !lastAltitude.isNaN()) {
            val altDelta = state.altitude - lastAltitude
            if (altDelta > 0) {
                totalElevationGain += altDelta
            }
        }
        lastAltitude = state.altitude

        when (_detectionState.value) {
            DetectionState.NOT_CLIMBING -> {
                if (smoothedGrade >= minGradeStart) {
                    _detectionState.value = DetectionState.POTENTIAL_CLIMB
                    potentialStartDistance = state.distance
                    climbStartDistance = state.distance
                    climbStartAltitude = state.altitude
                    climbStartLat = state.latitude
                    climbStartLon = state.longitude
                    flatDistance = 0.0
                    totalElevationGain = 0.0
                    lastAltitude = state.altitude
                    startTimestamp = System.currentTimeMillis()
                    // Emit early at POTENTIAL so UI wakes up immediately
                    updateDetectedClimb(state)
                }
            }

            DetectionState.POTENTIAL_CLIMB -> {
                val climbDistance = state.distance - potentialStartDistance

                if (smoothedGrade < minGradeContinue) {
                    flatDistance += if (distanceBuffer.size >= 2) {
                        state.distance - distanceBuffer[distanceBuffer.size - 2]
                    } else 1.0

                    if (flatDistance > endDistance) {
                        // False alarm
                        _detectionState.value = DetectionState.NOT_CLIMBING
                        _detectedClimb.value = null
                    }
                } else {
                    flatDistance = 0.0
                    // Require both distance AND elevation gain to confirm
                    if (climbDistance >= confirmDistance && totalElevationGain >= minElevationGain) {
                        _detectionState.value = DetectionState.CONFIRMED_CLIMB
                        climbCount++
                        updateDetectedClimb(state)
                    } else {
                        // Keep emitting at POTENTIAL for responsive UI
                        updateDetectedClimb(state)
                    }
                }
            }

            DetectionState.CONFIRMED_CLIMB -> {
                if (smoothedGrade < minGradeContinue) {
                    flatDistance += if (distanceBuffer.size >= 2) {
                        state.distance - distanceBuffer[distanceBuffer.size - 2]
                    } else 1.0

                    if (flatDistance > endDistance) {
                        // Climb ended
                        _detectionState.value = DetectionState.NOT_CLIMBING
                        _detectedClimb.value = _detectedClimb.value?.copy(isActive = false)
                    }
                } else {
                    flatDistance = 0.0
                    updateDetectedClimb(state)
                }
            }
        }
    }

    private fun updateDetectedClimb(state: LiveClimbState) {
        val climbLength = state.distance - climbStartDistance
        val elevationGain = state.altitude - climbStartAltitude
        val avgGrade = if (climbLength > 0) elevationGain / climbLength * 100.0 else 0.0

        _detectedClimb.value = ClimbInfo(
            id = "detected_$climbCount",
            name = "Climb $climbCount",
            length = climbLength,
            elevation = elevationGain,
            avgGrade = avgGrade,
            maxGrade = gradeBuffer.maxOrNull() ?: 0.0,
            isActive = true,
            isFromRoute = false,
            startLatitude = climbStartLat,
            startLongitude = climbStartLon,
            startTimestamp = startTimestamp
        )
    }

    fun reset() {
        _detectionState.value = DetectionState.NOT_CLIMBING
        _detectedClimb.value = null
        altitudeBuffer.clear()
        distanceBuffer.clear()
        gradeBuffer.clear()
        flatDistance = 0.0
        totalElevationGain = 0.0
        lastAltitude = Double.NaN
        startTimestamp = 0L
    }
}
