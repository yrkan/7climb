package io.github.climbintelligence.util

import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

    // ── Route coordinate geometry (GPS → distance-along-route) ───────────────

    /** A point on the route line with its cumulative distance from route start. */
    data class RoutePoint(
        val lat: Double,
        val lng: Double,
        val distanceAlongRoute: Double  // meters from route start
    )

    /**
     * Decode a Google-encoded lat/lng polyline (precision 5 — `routePolyline` in
     * NavigationState) into route points carrying cumulative distance-along-route
     * (haversine between consecutive vertices).
     *
     * Lets us map a live GPS fix to a distance along the loaded route, which is
     * independent of ride/recording distance — that desyncs when a route is added
     * mid-ride or the rider joins partway. Returns empty on null/empty/garbage.
     */
    fun buildRouteGeometry(encoded: String?): List<RoutePoint> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return try {
            val coords = decodeLatLng(encoded)
            if (coords.isEmpty()) return emptyList()
            // Reject garbage (e.g. wrong precision or 32-bit varint wrap) rather
            // than silently producing nonsense distances downstream.
            if (coords.any { it.first !in -90.0..90.0 || it.second !in -180.0..180.0 }) {
                Log.w(TAG, "Route polyline decoded out-of-range coords; ignoring")
                return emptyList()
            }
            val out = ArrayList<RoutePoint>(coords.size)
            var cumulative = 0.0
            var prev: Pair<Double, Double>? = null
            for (c in coords) {
                prev?.let { cumulative += haversineMeters(it.first, it.second, c.first, c.second) }
                out.add(RoutePoint(c.first, c.second, cumulative))
                prev = c
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "Route geometry decode failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Nearest route vertex to (lat, lon) → its distance-along-route (m), or null
     * if the geometry is empty. Nearest-vertex (not segment-projection): precision-5
     * route polylines are dense enough that the vertex distance is accurate to a few
     * metres, which is well within climb-matching tolerance.
     *
     * @param nearDistance when set, only vertices within [windowM] of this
     *   distance-along-route are considered — a continuity constraint so a looping
     *   or out-and-back route (where the same coordinate appears twice) cannot snap
     *   to a different pass. Falls back to a global search when nothing lies in the
     *   window (a large jump, or the first fix).
     *
     * Ranking uses a cos(lat)-scaled planar metric — invalid across the ±180°
     * antimeridian and degenerate at the poles, both irrelevant for cycling routes.
     */
    fun nearestDistanceAlong(
        geometry: List<RoutePoint>,
        lat: Double,
        lon: Double,
        nearDistance: Double? = null,
        windowM: Double = 2_000.0,
        maxSnapM: Double = 50.0
    ): Double? {
        if (geometry.isEmpty()) return null
        // Single pass: track the geographically nearest vertex overall, and the nearest
        // within the continuity window (if a hint is given). Snap distance is real metres
        // (haversine) so we can gate on "is the rider actually on the route here?".
        var windowedBest: RoutePoint? = null
        var windowedM = Double.MAX_VALUE
        var globalBest: RoutePoint? = null
        var globalM = Double.MAX_VALUE
        for (p in geometry) {
            val d = haversineMeters(lat, lon, p.lat, p.lng)
            if (d < globalM) { globalM = d; globalBest = p }
            if (nearDistance != null &&
                kotlin.math.abs(p.distanceAlongRoute - nearDistance) <= windowM &&
                d < windowedM
            ) {
                windowedM = d; windowedBest = p
            }
        }
        // Prefer the continuity-windowed vertex ONLY if the rider is genuinely near it.
        // If it's far, the rider jumped (e.g. left the route and rejoined elsewhere) —
        // re-acquire from the global nearest instead of staying locked to the old spot.
        if (windowedBest != null && windowedM <= maxSnapM) return windowedBest.distanceAlongRoute
        if (globalBest != null && globalM <= maxSnapM) return globalBest.distanceAlongRoute
        // GPS is farther than maxSnapM from the entire route → off-route.
        return null
    }

    /** Decode a Google polyline (precision 5) to (lat, lng) pairs. */
    private fun decodeLatLng(encoded: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var byte: Int

            do {
                if (index >= encoded.length) break
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                if (index >= encoded.length) break
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(Pair(lat / 1e5, lng / 1e5))
        }
        return points
    }

    /** Great-circle distance between two coordinates, in meters. */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
