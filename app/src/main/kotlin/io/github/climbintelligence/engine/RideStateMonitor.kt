package io.github.climbintelligence.engine

import io.github.climbintelligence.ClimbIntelligenceExtension
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class RideStateMonitor(
    private val extension: ClimbIntelligenceExtension,
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

    /**
     * Active while the Karoo ride is in [RideState.Paused]. Ticks
     * [WPrimeEngine.tickPauseRecovery] at 1 Hz so pause time accumulates
     * toward W' refill as if the rider were coasting.
     */
    @Volatile
    private var pauseTickerJob: Job? = null

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
                // Cover the resume-from-pause edge: Recording fires whether
                // this is a fresh ride start or a resume — in either case
                // the pause ticker must be off.
                stopPauseTicker()
                extension.wPrimeEngine.setPaused(false)

                if (!wasRecording) {
                    wasRecording = true
                    rideStartTimeMs = System.currentTimeMillis()

                    // Start alert monitoring
                    extension.alertManager.startMonitoring()

                    // Clear saved climb IDs from previous ride
                    extension.clearSavedClimbIds()

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
                // Mark engine paused + drive recovery at 1 Hz. The Skiba
                // model says recovery applies whenever P ≤ CP, and during
                // pause the rider's effective power is 0, so pause time
                // must accumulate toward W' refill the same as coasting.
                extension.wPrimeEngine.setPaused(true)
                startPauseTicker()
            }

            is RideState.Idle -> {
                stopPauseTicker()
                extension.wPrimeEngine.setPaused(false)

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
                    extension.metricsEngine.reset()
                    extension.matchBurnEngine.reset()

                    android.util.Log.i(TAG, "Ride ended")
                }
            }
        }
    }

    private suspend fun saveClimbAttempt(climb: io.github.climbintelligence.data.model.ClimbInfo) {
        // Delegate to Extension's save method (which deduplicates via savedClimbIds)
        extension.saveClimbAndCheckPR(climb)
    }

    fun isRecording(): Boolean = wasRecording

    private fun startPauseTicker() {
        if (pauseTickerJob?.isActive == true) return
        pauseTickerJob = scope.launch {
            while (isActive) {
                extension.wPrimeEngine.tickPauseRecovery(System.currentTimeMillis())
                delay(1000)
            }
        }
        android.util.Log.i(TAG, "Pause recovery ticker started")
    }

    private fun stopPauseTicker() {
        pauseTickerJob?.let {
            it.cancel()
            android.util.Log.i(TAG, "Pause recovery ticker stopped")
        }
        pauseTickerJob = null
    }

    fun destroy() {
        stopPauseTicker()
        stopMonitoring()
        scope.cancel()
    }
}
