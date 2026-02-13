package io.github.climbintelligence.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttemptDao {

    @Insert
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE climbId = :climbId ORDER BY date DESC")
    suspend fun getAttempts(climbId: String): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE climbId = :climbId AND isPR = 1 LIMIT 1")
    suspend fun getPR(climbId: String): AttemptEntity?

    @Query("SELECT * FROM attempts WHERE climbId = :climbId ORDER BY timeMs ASC LIMIT 1")
    suspend fun getFastest(climbId: String): AttemptEntity?

    @Query("SELECT * FROM attempts ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentAttempts(limit: Int = 50): List<AttemptEntity>

    @Query("SELECT * FROM attempts ORDER BY date DESC")
    suspend fun getAllAttempts(): List<AttemptEntity>

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    @Query("UPDATE attempts SET isPR = 0 WHERE climbId = :climbId")
    suspend fun clearPR(climbId: String)

    @Query("UPDATE attempts SET isPR = 1 WHERE id = :attemptId")
    suspend fun markAsPR(attemptId: Long)
}
