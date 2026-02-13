package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveClimbState(
    val power: Int = 0,
    val heartRate: Int = 0,
    val cadence: Int = 0,
    val speed: Double = 0.0,
    val altitude: Double = 0.0,
    val grade: Double = 0.0,
    val distance: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val hasData: Boolean = false
) {
    val speedKmh: Double get() = speed * 3.6
    val gradePercent: Double get() = grade
}
