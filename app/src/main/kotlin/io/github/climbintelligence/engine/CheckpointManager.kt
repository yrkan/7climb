package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.WPrimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CheckpointData(
    val wPrimeBalance: Double = 0.0,
    val wasRecording: Boolean = false,
    val timestamp: Long = 0L
)

class CheckpointManager(private val preferencesRepository: PreferencesRepository) {

    companion object {
        private const val TAG = "CheckpointManager"
        private const val CHECKPOINT_INTERVAL_MS = 60_000L // 1 minute
        private const val CHECKPOINT_KEY = "checkpoint_data"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    // In-memory checkpoint store (use DataStore for persistence in production)
    @Volatile
    private var savedCheckpoint: String? = null

    fun startPeriodicCheckpoints(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?) {
        stopPeriodicCheckpoints()
        periodicJob = scope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                saveCheckpoint(wPrimeEngine, climbDataService)
            }
        }
    }

    fun stopPeriodicCheckpoints() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun saveCheckpoint(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?) {
        try {
            val checkpoint = CheckpointData(
                wPrimeBalance = wPrimeEngine?.state?.value?.balance ?: 0.0,
                wasRecording = true,
                timestamp = System.currentTimeMillis()
            )
            savedCheckpoint = json.encodeToString(checkpoint)
            android.util.Log.d(TAG, "Checkpoint saved")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to save checkpoint: ${e.message}")
        }
    }

    fun emergencySave(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?) {
        try {
            runBlocking {
                saveCheckpoint(wPrimeEngine, climbDataService)
            }
            android.util.Log.i(TAG, "Emergency checkpoint saved")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Emergency save failed: ${e.message}")
        }
    }

    fun tryRestore(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?): Boolean {
        val data = savedCheckpoint ?: return false
        return try {
            val checkpoint = json.decodeFromString<CheckpointData>(data)

            // Only restore if checkpoint is recent (within 30 minutes)
            val age = System.currentTimeMillis() - checkpoint.timestamp
            if (age > 30 * 60 * 1000L) {
                clearCheckpointSync()
                return false
            }

            wPrimeEngine?.restore(checkpoint.wPrimeBalance)
            android.util.Log.i(TAG, "Restored checkpoint (age: ${age / 1000}s)")
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to restore: ${e.message}")
            false
        }
    }

    suspend fun clearCheckpoint() {
        savedCheckpoint = null
    }

    private fun clearCheckpointSync() {
        savedCheckpoint = null
    }
}
