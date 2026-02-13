package io.github.climbintelligence.util

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object PhysicsUtils {

    const val GRAVITY = 9.81 // m/s²
    const val SEA_LEVEL_AIR_DENSITY = 1.225 // kg/m³

    /**
     * Air density adjusted for altitude.
     * rho = 1.225 * exp(-0.0001185 * altitude)
     */
    fun airDensity(altitudeM: Double): Double {
        return SEA_LEVEL_AIR_DENSITY * exp(-0.0001185 * altitudeM)
    }

    /**
     * Gravity force component on a grade.
     * Fg = m * g * sin(atan(grade/100))
     */
    fun gravityForce(totalMassKg: Double, gradePercent: Double): Double {
        val angle = atan(gradePercent / 100.0)
        return totalMassKg * GRAVITY * sin(angle)
    }

    /**
     * Rolling resistance force.
     * Fr = m * g * Crr * cos(atan(grade/100))
     */
    fun rollingResistance(totalMassKg: Double, gradePercent: Double, crr: Double): Double {
        val angle = atan(gradePercent / 100.0)
        return totalMassKg * GRAVITY * crr * cos(angle)
    }

    /**
     * Aerodynamic drag force.
     * Fa = 0.5 * rho * CdA * v²
     */
    fun aeroDrag(cda: Double, altitudeM: Double, speedMs: Double): Double {
        val rho = airDensity(altitudeM)
        return 0.5 * rho * cda * speedMs * speedMs
    }

    /**
     * Total resistive force at given conditions.
     */
    fun totalForce(
        totalMassKg: Double,
        gradePercent: Double,
        crr: Double,
        cda: Double,
        altitudeM: Double,
        speedMs: Double
    ): Double {
        return gravityForce(totalMassKg, gradePercent) +
                rollingResistance(totalMassKg, gradePercent, crr) +
                aeroDrag(cda, altitudeM, speedMs)
    }

    /**
     * Power required to maintain speed on a grade.
     * P = F_total * v
     */
    fun powerRequired(
        totalMassKg: Double,
        gradePercent: Double,
        crr: Double,
        cda: Double,
        altitudeM: Double,
        speedMs: Double
    ): Double {
        return totalForce(totalMassKg, gradePercent, crr, cda, altitudeM, speedMs) * speedMs
    }

    /**
     * Estimated speed from power on a grade (iterative solver).
     */
    fun speedFromPower(
        powerWatts: Double,
        totalMassKg: Double,
        gradePercent: Double,
        crr: Double,
        cda: Double,
        altitudeM: Double
    ): Double {
        if (powerWatts <= 0) return 0.0

        // Newton's method approximation
        var speed = 3.0 // initial guess 3 m/s
        for (i in 0 until 20) {
            val f = totalForce(totalMassKg, gradePercent, crr, cda, altitudeM, speed)
            val pCalc = f * speed
            val error = pCalc - powerWatts

            if (kotlin.math.abs(error) < 0.1) break

            // Derivative: dP/dv ≈ F + v * dF/dv
            val rho = airDensity(altitudeM)
            val dFdv = rho * cda * speed
            val dPdv = f + speed * dFdv

            if (dPdv > 0) {
                speed -= error / dPdv
                speed = speed.coerceIn(0.1, 30.0)
            }
        }
        return speed
    }

    /**
     * Format seconds to MM:SS or H:MM:SS.
     */
    fun formatTime(seconds: Long): String {
        if (seconds < 0) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
}
