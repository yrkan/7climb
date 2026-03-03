package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NextClimbInfo(
    val distanceToStart: Double = 0.0,
    val etaSeconds: Long = 0,
    val climbName: String = "",
    val climbCategory: Int = 0,
    val climbLength: Double = 0.0,
    val climbElevation: Double = 0.0,
    val climbAvgGrade: Double = 0.0,
    val hasNext: Boolean = false
)
