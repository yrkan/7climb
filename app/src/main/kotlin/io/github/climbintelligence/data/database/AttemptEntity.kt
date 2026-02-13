package io.github.climbintelligence.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val climbId: String,
    val date: Long = System.currentTimeMillis(),
    val timeMs: Long,
    val avgPower: Int = 0,
    val normalizedPower: Int = 0,
    val avgHR: Int = 0,
    val maxHR: Int = 0,
    val isPR: Boolean = false
)
