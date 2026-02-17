package io.github.climbintelligence.data

import io.github.climbintelligence.data.database.AttemptDao
import io.github.climbintelligence.data.database.AttemptEntity
import io.github.climbintelligence.data.database.ClimbDao
import io.github.climbintelligence.data.database.ClimbEntity

data class SaveResult(
    val attemptId: Long,
    /** True when this attempt beat a previous record (not first attempt) */
    val isPR: Boolean,
    /** Milliseconds faster than previous PR (only set when isPR=true) */
    val improvedByMs: Long = 0L
)

class ClimbRepository(
    private val climbDao: ClimbDao,
    private val attemptDao: AttemptDao
) {

    suspend fun saveAttempt(
        climbId: String,
        climbName: String,
        latitude: Double,
        longitude: Double,
        length: Double,
        elevation: Double,
        avgGrade: Double,
        timeMs: Long,
        avgPower: Int,
        avgHR: Int
    ): SaveResult {
        // Ensure climb exists in database
        val existingClimb = climbDao.getClimb(climbId)
        if (existingClimb == null) {
            climbDao.insertClimb(
                ClimbEntity(
                    id = climbId,
                    name = climbName,
                    latitude = latitude,
                    longitude = longitude,
                    length = length,
                    elevation = elevation,
                    avgGrade = avgGrade
                )
            )
        }

        // Check if this beats a previous record
        val currentFastest = attemptDao.getFastest(climbId)
        val isFirstOrFastest = currentFastest == null || timeMs < currentFastest.timeMs
        val beatsPrevious = currentFastest != null && timeMs < currentFastest.timeMs
        val improvedByMs = if (beatsPrevious) currentFastest!!.timeMs - timeMs else 0L

        // Save attempt
        val attemptId = attemptDao.insertAttempt(
            AttemptEntity(
                climbId = climbId,
                timeMs = timeMs,
                avgPower = avgPower,
                avgHR = avgHR,
                isPR = isFirstOrFastest
            )
        )

        // Update PR flags
        if (isFirstOrFastest) {
            attemptDao.clearPR(climbId)
            attemptDao.markAsPR(attemptId)
        }

        return SaveResult(attemptId, beatsPrevious, improvedByMs)
    }

    suspend fun getPR(climbId: String): AttemptEntity? {
        return attemptDao.getPR(climbId)
    }

    suspend fun getAttempts(climbId: String): List<AttemptEntity> {
        return attemptDao.getAttempts(climbId)
    }

    suspend fun getAllClimbs(): List<ClimbEntity> {
        return climbDao.getAllClimbs()
    }

    suspend fun getRecentAttempts(limit: Int = 50): List<AttemptEntity> {
        return attemptDao.getRecentAttempts(limit)
    }

    suspend fun findNearbyClimb(lat: Double, lng: Double): ClimbEntity? {
        return climbDao.findNear(lat, lng)
    }
}
