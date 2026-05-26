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
    val cp: Int = 0,
    /** Pmax — peak instantaneous power, in watts. Drives the Morton 3-parameter
     *  critical-power model: when > 0 and [useThreeParamModel] is true, the
     *  W' engine reports Maximum Power Available (MPA) alongside the standard
     *  W' balance. 0 disables MPA reporting. */
    val maxPower: Int = 0,
    /** When true, [effectiveFtp] and [effectiveWeight] read from the Karoo's
     *  rider-profile values ([karooFtp], [karooWeight]) instead of the locally-
     *  typed [ftp] and [weight]. Default ON — the Karoo's profile is the
     *  single source of truth most riders already maintain. */
    val useKarooProfile: Boolean = true,
    /** Last-seen FTP value from the Karoo's UserProfile stream. Updated by
     *  ClimbIntelligenceExtension on every UserProfile event. */
    val karooFtp: Int = 0,
    /** Last-seen body-weight value (kg) from the Karoo's UserProfile stream.
     *  Updated alongside [karooFtp] in the same UserProfile consumer. */
    val karooWeight: Double = 0.0,
    /** Toggle Morton 3-parameter (Pmax-aware) model on top of the Skiba
     *  2-param defaults. Off by default — needs a configured maxPower. */
    val useThreeParamModel: Boolean = false,
) {
    /** FTP actually used by engines — prefers Karoo's value when [useKarooProfile]. */
    val effectiveFtp: Int get() = if (useKarooProfile && karooFtp > 0) karooFtp else ftp

    /** Body weight (kg) actually used by engines — prefers Karoo's value when [useKarooProfile]. */
    val effectiveWeight: Double get() = if (useKarooProfile && karooWeight > 0.0) karooWeight else weight

    /** CP actually used by engines — prefers explicit cp, else 95% of effective FTP. */
    val effectiveCp: Int get() = if (cp > 0) cp else (effectiveFtp * 0.95).toInt()
    val isConfigured: Boolean get() = effectiveFtp > 0 && effectiveWeight > 0
    val totalMass: Double get() = effectiveWeight + bikeWeight
}
