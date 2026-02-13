package io.github.climbintelligence.util

import android.util.Log

/**
 * Decodes Google Encoded Polyline format for elevation profile data.
 *
 * Karoo provides routeElevationPolyline as part of NavigationState.
 * Format: pairs of (distance_delta, elevation_delta) encoded with variable-length encoding.
 *
 * Karoo SDK uses precision 1 for routeElevationPolyline (values are raw meters).
 * Falls back to 1e5 (standard Google polyline) if values look invalid.
 */
object ElevationPolylineDecoder {

    private const val TAG = "ElevPolyDecoder"
    private const val DEFAULT_PRECISION = 1.0
    private const val ALT_PRECISION = 1e5

    data class ElevationPoint(
        val distance: Double,  // meters from start
        val elevation: Double  // meters altitude
    )

    sealed class DecodeResult {
        data class Success(val points: List<ElevationPoint>) : DecodeResult()
        data class Error(val message: String) : DecodeResult()
    }

    /**
     * Decode with automatic precision detection and validation.
     */
    fun decodeSafe(encoded: String?): DecodeResult {
        if (encoded.isNullOrEmpty()) {
            return DecodeResult.Error("Empty polyline")
        }

        return try {
            val points = decodeWithPrecision(encoded, DEFAULT_PRECISION)

            if (points.isEmpty()) {
                return DecodeResult.Error("Decoded to empty list")
            }

            val hasInvalid = points.any { p ->
                p.distance < 0 || p.distance > 1_000_000 ||
                        p.elevation < -500 || p.elevation > 9000
            }

            if (hasInvalid) {
                Log.w(TAG, "Default precision gave invalid values, trying alternative")
                val altPoints = decodeWithPrecision(encoded, ALT_PRECISION)
                val altInvalid = altPoints.any { p ->
                    p.distance < 0 || p.elevation < -500
                }
                if (!altInvalid && altPoints.isNotEmpty()) {
                    return DecodeResult.Success(altPoints)
                }
                return DecodeResult.Error("Invalid decoded values with both precisions")
            }

            DecodeResult.Success(points)
        } catch (e: Exception) {
            Log.e(TAG, "Decode failed: ${e.message}")
            DecodeResult.Error(e.message ?: "Unknown error")
        }
    }

    fun decode(encoded: String): List<ElevationPoint> {
        return decodeWithPrecision(encoded, DEFAULT_PRECISION)
    }

    private fun decodeWithPrecision(encoded: String, precision: Double): List<ElevationPoint> {
        val points = mutableListOf<ElevationPoint>()
        var index = 0
        var distance = 0.0
        var elevation = 0.0

        while (index < encoded.length) {
            // Decode distance delta
            var shift = 0
            var result = 0
            var byte: Int

            do {
                if (index >= encoded.length) break
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)

            val distDelta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            distance += distDelta / precision

            // Decode elevation delta
            shift = 0
            result = 0

            do {
                if (index >= encoded.length) break
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)

            val elevDelta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            elevation += elevDelta / precision

            points.add(ElevationPoint(distance, elevation))
        }

        return points
    }

    /**
     * Smooth elevation profile to reduce GPS noise.
     * Simple moving average over the given window.
     */
    fun smooth(points: List<ElevationPoint>, windowSize: Int = 5): List<ElevationPoint> {
        if (points.size < windowSize) return points

        return points.mapIndexed { i, point ->
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(points.size, i + windowSize / 2 + 1)
            val avg = points.subList(start, end).map { it.elevation }.average()
            point.copy(elevation = avg)
        }
    }

    /**
     * Calculate per-segment gradient percentages between consecutive points.
     */
    fun calculateGrades(points: List<ElevationPoint>): List<Double> {
        if (points.size < 2) return emptyList()

        return points.zipWithNext { a, b ->
            val dist = b.distance - a.distance
            val elev = b.elevation - a.elevation
            if (dist > 0) (elev / dist) * 100.0 else 0.0
        }
    }

    /**
     * Extract a sub-profile for a specific climb within a route.
     *
     * @param points full route elevation profile
     * @param startDistance climb start distance from route start (m)
     * @param climbLength climb length (m)
     * @return sub-list of points within the climb range
     */
    fun extractClimbProfile(
        points: List<ElevationPoint>,
        startDistance: Double,
        climbLength: Double
    ): List<ElevationPoint> {
        val endDistance = startDistance + climbLength
        return points
            .filter { it.distance in startDistance..endDistance }
            .map { it.copy(distance = it.distance - startDistance) }
    }

    /**
     * Build 100m segments from elevation points, each with average gradient.
     */
    fun buildSegments(
        points: List<ElevationPoint>,
        totalLength: Double,
        segmentLength: Double = 100.0
    ): List<SegmentData> {
        if (points.size < 2) return emptyList()

        val segments = mutableListOf<SegmentData>()
        var segStart = 0.0

        while (segStart < totalLength) {
            val segEnd = minOf(segStart + segmentLength, totalLength)

            // Find points within this segment
            val startPoint = points.lastOrNull { it.distance <= segStart }
            val endPoint = points.firstOrNull { it.distance >= segEnd }
                ?: points.lastOrNull()

            val grade = if (startPoint != null && endPoint != null && endPoint.distance > startPoint.distance) {
                val dist = endPoint.distance - startPoint.distance
                val elev = endPoint.elevation - startPoint.elevation
                (elev / dist) * 100.0
            } else {
                0.0
            }

            val startElev = startPoint?.elevation ?: 0.0
            val endElev = endPoint?.elevation ?: startElev

            segments.add(
                SegmentData(
                    startDistance = segStart,
                    endDistance = segEnd,
                    length = segEnd - segStart,
                    grade = grade,
                    elevation = endElev - startElev
                )
            )

            segStart = segEnd
        }

        return segments
    }

    data class SegmentData(
        val startDistance: Double,
        val endDistance: Double,
        val length: Double,
        val grade: Double,
        val elevation: Double
    )

    /**
     * Generate a simplified linear profile when no elevation polyline is available.
     */
    fun generateLinearProfile(
        length: Double,
        totalElevation: Double,
        avgGrade: Double
    ): List<ElevationPoint> {
        val count = (length / 50.0).toInt().coerceIn(10, 200)
        val distStep = length / count
        val elevStep = totalElevation / count

        return (0..count).map { i ->
            ElevationPoint(
                distance = i * distStep,
                elevation = i * elevStep
            )
        }
    }
}
