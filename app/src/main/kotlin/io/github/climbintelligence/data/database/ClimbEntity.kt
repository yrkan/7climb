package io.github.climbintelligence.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "climbs")
data class ClimbEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val length: Double,
    val elevation: Double,
    val avgGrade: Double,
    val maxGrade: Double = 0.0,
    val category: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
