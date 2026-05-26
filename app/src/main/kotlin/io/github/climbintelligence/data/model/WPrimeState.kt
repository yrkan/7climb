package io.github.climbintelligence.data.model

enum class WPrimeStatus {
    FRESH,      // > 90%
    GOOD,       // 70-90%
    WORKING,    // 50-70%
    DEPLETING,  // 30-50%
    CRITICAL,   // 10-30%
    EMPTY,      // 0-10%
    DEFICIT     // < 0%  — model predicts depletion beyond W'max; signals W'max likely under-calibrated
}

data class WPrimeState(
    val balance: Double = 20000.0,
    val maxBalance: Double = 20000.0,
    val percentage: Double = 100.0,
    val depletionRate: Double = 0.0,
    val recoveryRate: Double = 0.0,
    val timeToEmpty: Long = -1L,
    val timeToFull: Long = -1L,
    val status: WPrimeStatus = WPrimeStatus.FRESH,
    /** Maximum Power Available right now (Morton 3-parameter model). 0 when
     *  disabled (athlete profile has no maxPower / useThreeParamModel off).
     *  MPA = Pmax − (Pmax − CP) × ((W'max − W'balance) / W'max)² — so at full
     *  W' it equals Pmax, and decays quadratically toward CP as W' depletes. */
    val mpa: Int = 0,
) {
    companion object {
        /**
         * Build a WPrimeState from a balance value. As of the W'-history-chart PR,
         * balance is allowed to be negative (the Skiba model's signal that W'max is
         * calibrated too low). Percentage is computed without saturation.
         */
        fun fromBalance(balance: Double, maxBalance: Double): WPrimeState {
            val pct = if (maxBalance > 0) balance / maxBalance * 100.0 else 0.0
            val status = statusForPercentage(pct)
            return WPrimeState(
                balance = balance,
                maxBalance = maxBalance,
                percentage = pct,
                status = status
            )
        }

        fun statusForPercentage(pct: Double): WPrimeStatus = when {
            pct > 90 -> WPrimeStatus.FRESH
            pct > 70 -> WPrimeStatus.GOOD
            pct > 50 -> WPrimeStatus.WORKING
            pct > 30 -> WPrimeStatus.DEPLETING
            pct > 10 -> WPrimeStatus.CRITICAL
            pct >= 0 -> WPrimeStatus.EMPTY
            else     -> WPrimeStatus.DEFICIT
        }
    }
}

/**
 * Per-tick snapshot of W' state for history tracking. Sized small (32 bytes per
 * sample) so a 24h ring buffer at 1Hz fits in ~2.8 MB. Fields chosen to make
 * future surge-evaluation analyzers self-sufficient — they shouldn't need to
 * re-instrument the engine to read drawdown patterns.
 */
data class WPrimeSample(
    val timestampMs: Long,
    val balance: Double,
    val percentage: Double,
    val power: Int
)
