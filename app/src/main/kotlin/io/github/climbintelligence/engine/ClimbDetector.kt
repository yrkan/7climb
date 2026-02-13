package io.github.climbintelligence.engine

import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.ClimbSegment
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
        private const val MIN_GRADE_START = 3.0    // % grade to start detection
        private const val MIN_GRADE_CONTINUE = 2.0  // % grade to continue
        private const val CONFIRM_DISTANCE = 100.0  // meters to confirm climb
        private const val END_DISTANCE = 50.0       // meters of flat to end climb
        private const val SMOOTHING_WINDOW = 5       // points for grade smoothing
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

        when (_detectionState.value) {
            DetectionState.NOT_CLIMBING -> {
                if (smoothedGrade >= MIN_GRADE_START) {
                    _detectionState.value = DetectionState.POTENTIAL_CLIMB
                    potentialStartDistance = state.distance
                    climbStartDistance = state.distance
                    climbStartAltitude = state.altitude
                    climbStartLat = state.latitude
                    climbStartLon = state.longitude
                    flatDistance = 0.0
                }
            }

            DetectionState.POTENTIAL_CLIMB -> {
                val climbDistance = state.distance - potentialStartDistance

                if (smoothedGrade < MIN_GRADE_CONTINUE) {
                    flatDistance += if (distanceBuffer.size >= 2) {
                        state.distance - distanceBuffer[distanceBuffer.size - 2]
                    } else 1.0

                    if (flatDistance > END_DISTANCE) {
                        // False alarm
                        _detectionState.value = DetectionState.NOT_CLIMBING
                        _detectedClimb.value = null
                    }
                } else {
                    flatDistance = 0.0
                    if (climbDistance >= CONFIRM_DISTANCE) {
                        _detectionState.value = DetectionState.CONFIRMED_CLIMB
                        climbCount++
                        updateDetectedClimb(state)
                    }
                }
            }

            DetectionState.CONFIRMED_CLIMB -> {
                if (smoothedGrade < MIN_GRADE_CONTINUE) {
                    flatDistance += if (distanceBuffer.size >= 2) {
                        state.distance - distanceBuffer[distanceBuffer.size - 2]
                    } else 1.0

                    if (flatDistance > END_DISTANCE) {
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
            startLongitude = climbStartLon
        )
    }

    fun reset() {
        _detectionState.value = DetectionState.NOT_CLIMBING
        _detectedClimb.value = null
        altitudeBuffer.clear()
        distanceBuffer.clear()
        gradeBuffer.clear()
        flatDistance = 0.0
    }
}
