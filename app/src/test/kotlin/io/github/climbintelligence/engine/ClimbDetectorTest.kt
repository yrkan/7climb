package io.github.climbintelligence.engine

import io.github.climbintelligence.data.model.LiveClimbState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the homegrown [ClimbDetector].
 *
 * These prove the detection *logic* independent of the Karoo device. The
 * production issue ("a climb is never detected") was traced to a data-feed
 * gap — `ELEVATION_GRADE` is 0 during a karoo-ride-replay session because
 * grade isn't a pairable sensor — not to a logic bug. The flat-grade test
 * below pins that down: with grade 0 the detector correctly does nothing,
 * so the fix lives in the data feed / a real ride, not here.
 */
class ClimbDetectorTest {

    /** Feed a steady climb: 1 Hz samples, 10 m/sample, given grade. */
    private fun ClimbDetector.rideClimb(
        samples: Int,
        gradePct: Double,
        startDistance: Double = 0.0,
        startAltitude: Double = 100.0,
        metersPerSample: Double = 10.0,
    ): LiveClimbState {
        var dist = startDistance
        var alt = startAltitude
        var last = LiveClimbState()
        repeat(samples) {
            dist += metersPerSample
            alt += metersPerSample * gradePct / 100.0
            last = LiveClimbState(
                grade = gradePct,
                altitude = alt,
                distance = dist,
                hasData = true,
            )
            update(last)
        }
        return last
    }

    @Test
    fun `sustained 7 percent grade confirms a climb`() {
        val detector = ClimbDetector()
        // 30 samples × 10 m = 300 m climbed at 7% → 21 m gain.
        // Clears confirmDistance (200 m) and minElevation (15 m).
        detector.rideClimb(samples = 30, gradePct = 7.0)

        assertEquals(
            ClimbDetector.DetectionState.CONFIRMED_CLIMB,
            detector.detectionState.value
        )
        val climb = detector.detectedClimb.value
        assertNotNull("a climb should be detected", climb)
        assertTrue("climb is active", climb!!.isActive)
        assertTrue("avg grade is in a sane range", climb.avgGrade in 5.0..9.0)
        assertTrue("length reflects the ride", climb.length >= 200.0)
    }

    @Test
    fun `flat grade never detects a climb (reproduces the replay data-gap)`() {
        val detector = ClimbDetector()
        // This is the karoo-ride-replay condition: distance accrues but grade
        // stays 0 (no ELEVATION_GRADE feed). The detector must stay idle.
        detector.rideClimb(samples = 60, gradePct = 0.0)

        assertEquals(
            ClimbDetector.DetectionState.NOT_CLIMBING,
            detector.detectionState.value
        )
        assertNull("no climb without grade", detector.detectedClimb.value)
    }

    @Test
    fun `short steep ramp stays potential, not confirmed`() {
        val detector = ClimbDetector()
        // 10 samples × 10 m = 100 m — above grade threshold but below the
        // 200 m confirm distance, so it should reach POTENTIAL but not CONFIRMED.
        detector.rideClimb(samples = 10, gradePct = 8.0)

        assertEquals(
            ClimbDetector.DetectionState.POTENTIAL_CLIMB,
            detector.detectionState.value
        )
    }

    @Test
    fun `grade dropping flat after a climb ends it`() {
        val detector = ClimbDetector()
        // Confirm a climb…
        val top = detector.rideClimb(samples = 30, gradePct = 7.0)
        assertEquals(
            ClimbDetector.DetectionState.CONFIRMED_CLIMB,
            detector.detectionState.value
        )
        // …then ride flat past the end-distance (150 m) to close it out.
        detector.rideClimb(
            samples = 20, gradePct = 0.0,
            startDistance = top.distance, startAltitude = top.altitude,
        )
        assertEquals(
            ClimbDetector.DetectionState.NOT_CLIMBING,
            detector.detectionState.value
        )
    }
}
