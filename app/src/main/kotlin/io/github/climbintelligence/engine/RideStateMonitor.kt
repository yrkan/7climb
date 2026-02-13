package io.github.climbintelligence.engine

import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.ClimbRepository
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class RideStateMonitor(
    private val extension: ClimbIntelligenceExtension,
    private val climbRepository: ClimbRepository,
    private val checkpointManager: CheckpointManager?
) {
    companion object {
        private const val TAG = "RideStateMonitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rideStateConsumerId = AtomicReference<String?>(null)

    @Volatile
    private var wasRecording = false

    @Volatile
    private var rideStartTimeMs = 0L

    fun startMonitoring() {
        if (!extension.karooSystem.connected) return

        try {
            val consumerId = extension.karooSystem.addConsumer { rideState: RideState ->
                handleRideStateChange(rideState)
            }
            rideStateConsumerId.set(consumerId)
            android.util.Log.i(TAG, "Started monitoring ride state")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to add ride state consumer: ${e.message}")
        }
    }

    fun stopMonitoring() {
        rideStateConsumerId.getAndSet(null)?.let { id ->
            try {
                extension.karooSystem.removeConsumer(id)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to remove consumer: ${e.message}")
            }
        }
    }

    private fun handleRideStateChange(rideState: RideState) {
        android.util.Log.d(TAG, "Ride state: $rideState")

        when (rideState) {
            is RideState.Recording -> {
                if (!wasRecording) {
                    wasRecording = true
                    rideStartTimeMs = System.currentTimeMillis()

                    // Start alert monitoring
                    extension.alertManager.startMonitoring()

                    // Start periodic checkpoints
                    checkpointManager?.startPeriodicCheckpoints(
                        extension.wPrimeEngine,
                        extension.climbDataService
                    )

                    android.util.Log.i(TAG, "Ride started")
                }
            }

            is RideState.Paused -> {
                // Save checkpoint immediately
                scope.launch {
                    checkpointManager?.saveCheckpoint(
                        extension.wPrimeEngine,
                        extension.climbDataService
                    )
                }
            }

            is RideState.Idle -> {
                if (wasRecording) {
                    wasRecording = false

                    // Stop alerts
                    extension.alertManager.stopMonitoring()

                    // Stop checkpoints and clear
                    checkpointManager?.stopPeriodicCheckpoints()
                    scope.launch {
                        checkpointManager?.clearCheckpoint()
                    }

                    // Save climb attempt if there was an active climb
                    val climb = extension.climbDataService.activeClimb.value
                    if (climb != null && climb.isActive) {
                        scope.launch {
                            saveClimbAttempt(climb)
                        }
                    }

                    // Reset engines
                    extension.wPrimeEngine.reset()
                    extension.climbDetector.reset()
                    extension.alertManager.reset()

                    android.util.Log.i(TAG, "Ride ended")
                }
            }
        }
    }

    private suspend fun saveClimbAttempt(climb: io.github.climbintelligence.data.model.ClimbInfo) {
        try {
            val durationMs = System.currentTimeMillis() - rideStartTimeMs
            val state = extension.climbDataService.liveState.value

            climbRepository.saveAttempt(
                climbId = climb.id,
                climbName = climb.name,
                latitude = climb.startLatitude,
                longitude = climb.startLongitude,
                length = climb.length,
                elevation = climb.elevation,
                avgGrade = climb.avgGrade,
                timeMs = durationMs,
                avgPower = state.power,
                avgHR = state.heartRate
            )
            android.util.Log.i(TAG, "Saved climb attempt: ${climb.name}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save attempt: ${e.message}")
        }
    }

    fun isRecording(): Boolean = wasRecording

    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}
