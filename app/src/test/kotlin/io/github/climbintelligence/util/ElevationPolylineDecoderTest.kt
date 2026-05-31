package io.github.climbintelligence.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the route-coordinate geometry used to map a live GPS fix to a
 * distance-along-route (the GPS-based climb-matching path). The encoded sample
 * is the canonical Google polyline example:
 *   "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
 * which decodes to (38.5, -120.2), (40.7, -120.95), (43.252, -126.453).
 */
class ElevationPolylineDecoderTest {

    private val sample = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun `buildRouteGeometry decodes precision-5 coords with cumulative distance`() {
        val geo = ElevationPolylineDecoder.buildRouteGeometry(sample)

        assertEquals(3, geo.size)
        assertEquals(38.5, geo[0].lat, 1e-4)
        assertEquals(-120.2, geo[0].lng, 1e-4)
        assertEquals(40.7, geo[1].lat, 1e-4)
        assertEquals(-120.95, geo[1].lng, 1e-4)
        assertEquals(43.252, geo[2].lat, 1e-4)
        assertEquals(-126.453, geo[2].lng, 1e-4)

        // distance starts at 0 and increases monotonically along the line
        assertEquals(0.0, geo[0].distanceAlongRoute, 1e-9)
        assertTrue(geo[1].distanceAlongRoute > 0.0)
        assertTrue(geo[2].distanceAlongRoute > geo[1].distanceAlongRoute)
    }

    @Test
    fun `buildRouteGeometry returns empty on null or blank`() {
        assertTrue(ElevationPolylineDecoder.buildRouteGeometry(null).isEmpty())
        assertTrue(ElevationPolylineDecoder.buildRouteGeometry("").isEmpty())
    }

    @Test
    fun `nearestDistanceAlong returns the matching vertex distance`() {
        val geo = ElevationPolylineDecoder.buildRouteGeometry(sample)

        // querying a vertex's own coordinate returns that vertex's distance
        val atSecond = ElevationPolylineDecoder.nearestDistanceAlong(geo, 40.7, -120.95)
        assertEquals(geo[1].distanceAlongRoute, atSecond!!, 1e-6)

        // querying the first vertex's coordinate returns ~0
        val atFirst = ElevationPolylineDecoder.nearestDistanceAlong(geo, 38.5, -120.2)
        assertEquals(0.0, atFirst!!, 1e-6)
    }

    @Test
    fun `nearestDistanceAlong is null on empty geometry`() {
        assertNull(ElevationPolylineDecoder.nearestDistanceAlong(emptyList(), 1.0, 2.0))
    }

    @Test
    fun `buildRouteGeometry handles a single vertex`() {
        val geo = ElevationPolylineDecoder.buildRouteGeometry("_p~iF~ps|U") // (38.5, -120.2)
        assertEquals(1, geo.size)
        assertEquals(0.0, geo[0].distanceAlongRoute, 1e-9)
        assertEquals(0.0, ElevationPolylineDecoder.nearestDistanceAlong(geo, 38.5, -120.2)!!, 1e-9)
    }

    @Test
    fun `nearestDistanceAlong returns null when off-route, re-acquires when back on`() {
        val geo = ElevationPolylineDecoder.buildRouteGeometry(sample)
        // The sample's vertices are hundreds of km apart, so a far GPS fix is beyond the
        // 50 m snap → off-route → null (instead of snapping to an arbitrary far vertex).
        assertNull(ElevationPolylineDecoder.nearestDistanceAlong(geo, 0.0, 0.0))
        // A continuity hint does not override the off-route gate.
        assertNull(ElevationPolylineDecoder.nearestDistanceAlong(geo, 0.0, 0.0, nearDistance = 0.0))
        // Re-acquire: even with a stale hint at distance 0, a fix exactly on the 3rd vertex
        // falls back to the global nearest (the windowed candidate is far) and returns it.
        assertEquals(
            geo[2].distanceAlongRoute,
            ElevationPolylineDecoder.nearestDistanceAlong(geo, 43.252, -126.453, nearDistance = 0.0)!!,
            1e-6
        )
    }

    @Test
    fun `nearestDistanceAlong falls back to global when window is empty`() {
        val geo = ElevationPolylineDecoder.buildRouteGeometry(sample)
        // No vertex within 10 m of an absurd nearDistance → global nearest is returned.
        val r = ElevationPolylineDecoder.nearestDistanceAlong(
            geo, 43.252, -126.453, nearDistance = 9_999_999.0, windowM = 10.0
        )
        assertEquals(geo[2].distanceAlongRoute, r!!, 1e-6)
    }
}
