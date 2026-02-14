package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AthleteProfile(
    val ftp: Int = 0,
    val weight: Double = 0.0,
    val wPrimeMax: Int = 20000,
    val cda: Double = 0.321,
    val crr: Double = 0.005,
    val bikeWeight: Double = 8.0,
    val cp: Int = 0
) {
    val effectiveCp: Int get() = if (cp > 0) cp else (ftp * 0.95).toInt()
    val isConfigured: Boolean get() = ftp > 0 && weight > 0
    val totalMass: Double get() = weight + bikeWeight
}
