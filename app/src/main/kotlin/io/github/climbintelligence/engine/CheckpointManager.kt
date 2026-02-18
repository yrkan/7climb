package io.github.climbintelligence.engine

import android.content.Context
import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.WPrimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CheckpointData(
    val wPrimeBalance: Double = 0.0,
    val wasRecording: Boolean = false,
    val timestamp: Long = 0L
)

class CheckpointManager(
    private val preferencesRepository: PreferencesRepository,
    private val context: Context
) {

    companion object {
        private const val TAG = "CheckpointManager"
        private const val CHECKPOINT_INTERVAL_MS = 60_000L // 1 minute
        private const val CHECKPOINT_KEY = "checkpoint_data"
        private const val PREFS_NAME = "climb_checkpoints"
        private const val MAX_CHECKPOINT_AGE_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
            prefs.edit().putString(CHECKPOINT_KEY, json.encodeToString(checkpoint)).apply()
            android.util.Log.d(TAG, "Checkpoint saved to disk")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to save checkpoint: ${e.message}")
        }
    }

    fun emergencySave(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?) {
        try {
            val checkpoint = CheckpointData(
                wPrimeBalance = wPrimeEngine?.state?.value?.balance ?: 0.0,
                wasRecording = true,
                timestamp = System.currentTimeMillis()
            )
            // commit() is synchronous — ensures data is written before process dies
            prefs.edit().putString(CHECKPOINT_KEY, json.encodeToString(checkpoint)).commit()
            android.util.Log.i(TAG, "Emergency checkpoint saved to disk")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Emergency save failed: ${e.message}")
        }
    }

    fun tryRestore(wPrimeEngine: WPrimeEngine?, climbDataService: ClimbDataService?): Boolean {
        val data = prefs.getString(CHECKPOINT_KEY, null) ?: return false
        return try {
            val checkpoint = json.decodeFromString<CheckpointData>(data)

            // Only restore if checkpoint is recent (within 2 hours)
            val age = System.currentTimeMillis() - checkpoint.timestamp
            if (age > MAX_CHECKPOINT_AGE_MS) {
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
        prefs.edit().remove(CHECKPOINT_KEY).apply()
    }

    private fun clearCheckpointSync() {
        prefs.edit().remove(CHECKPOINT_KEY).commit()
    }
}
