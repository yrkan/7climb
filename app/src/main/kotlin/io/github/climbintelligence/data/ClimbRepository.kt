package io.github.climbintelligence.data

import io.github.climbintelligence.data.database.AttemptDao
import io.github.climbintelligence.data.database.AttemptEntity
import io.github.climbintelligence.data.database.ClimbDao
import io.github.climbintelligence.data.database.ClimbEntity

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
    ): Long {
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

        // Check if this is a PR
        val currentFastest = attemptDao.getFastest(climbId)
        val isPR = currentFastest == null || timeMs < currentFastest.timeMs

        // Save attempt
        val attemptId = attemptDao.insertAttempt(
            AttemptEntity(
                climbId = climbId,
                timeMs = timeMs,
                avgPower = avgPower,
                avgHR = avgHR,
                isPR = isPR
            )
        )

        // Update PR flags
        if (isPR) {
            attemptDao.clearPR(climbId)
            attemptDao.markAsPR(attemptId)
        }

        return attemptId
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
