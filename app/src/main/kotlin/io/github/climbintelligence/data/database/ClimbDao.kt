package io.github.climbintelligence.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClimbDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClimb(climb: ClimbEntity)

    @Query("SELECT * FROM climbs WHERE id = :id")
    suspend fun getClimb(id: String): ClimbEntity?

    @Query("SELECT * FROM climbs ORDER BY createdAt DESC")
    suspend fun getAllClimbs(): List<ClimbEntity>

    @Query("""
        SELECT * FROM climbs
        WHERE ABS(latitude - :lat) < :radiusDeg
        AND ABS(longitude - :lng) < :radiusDeg
        ORDER BY ABS(latitude - :lat) + ABS(longitude - :lng) ASC
        LIMIT 1
    """)
    suspend fun findNear(lat: Double, lng: Double, radiusDeg: Double = 0.005): ClimbEntity?

    @Query("DELETE FROM climbs WHERE id = :id")
    suspend fun deleteClimb(id: String)
}
