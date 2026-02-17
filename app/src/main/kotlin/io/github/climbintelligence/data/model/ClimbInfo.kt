package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ClimbInfo(
    val id: String = "",
    val name: String = "",
    val category: Int = 0,
    val length: Double = 0.0,
    val elevation: Double = 0.0,
    val avgGrade: Double = 0.0,
    val maxGrade: Double = 0.0,
    val segments: List<ClimbSegment> = emptyList(),
    val distanceToTop: Double = 0.0,
    val elevationToTop: Double = 0.0,
    val progress: Double = 0.0,
    val isActive: Boolean = false,
    val isFromRoute: Boolean = false,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    /** Distance from route start where this climb begins (meters). Only set for route climbs. */
    val startDistance: Double = 0.0,
    /** Timestamp when climb detection started (millis). Only set for detected climbs. */
    val startTimestamp: Long = 0L
) {
    val distanceToTopKm: Double get() = distanceToTop / 1000.0
    val progressPercent: Double get() = (progress * 100.0).coerceIn(0.0, 100.0)
    val categoryLabel: String get() = when (category) {
        1 -> "HC"
        2 -> "1"
        3 -> "2"
        4 -> "3"
        5 -> "4"
        else -> ""
    }

    /** True if this climb has route-provided metrics (distance to top, etc.) */
    val hasRouteMetrics: Boolean get() = isFromRoute && distanceToTop > 0

    /** True if this is a real-time detected climb (not from route) */
    val isDetectedClimb: Boolean get() = !isFromRoute

    /** Distance climbed so far in km */
    val distanceClimbedKm: Double get() = length / 1000.0

    /** Elapsed seconds since climb started */
    val elapsedSeconds: Long get() {
        if (startTimestamp <= 0L) return 0L
        return (System.currentTimeMillis() - startTimestamp) / 1000L
    }
}
