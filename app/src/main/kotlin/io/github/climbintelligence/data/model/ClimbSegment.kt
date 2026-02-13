package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ClimbSegment(
    val startDistance: Double = 0.0,
    val endDistance: Double = 0.0,
    val grade: Double = 0.0,
    val length: Double = 0.0,
    val elevation: Double = 0.0
)
