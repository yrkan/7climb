package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ClimbStats(
    val vamRolling: Int = 0,
    val vamOverall: Int = 0,
    val energyKj: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val avgPower: Int = 0,
    val maxPower: Int = 0,
    val avgHR: Int = 0,
    val maxHR: Int = 0,
    val avgCadence: Int = 0,
    val wKg: Double = 0.0,
    val avgWKg: Double = 0.0,
    val isTracking: Boolean = false
)
