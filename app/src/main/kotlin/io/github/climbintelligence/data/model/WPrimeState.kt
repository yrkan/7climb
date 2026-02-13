package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

enum class WPrimeStatus {
    FRESH,      // > 90%
    GOOD,       // 70-90%
    WORKING,    // 50-70%
    DEPLETING,  // 30-50%
    CRITICAL,   // 10-30%
    EMPTY       // < 10%
}

@Serializable
data class WPrimeState(
    val balance: Double = 20000.0,
    val maxBalance: Double = 20000.0,
    val percentage: Double = 100.0,
    val depletionRate: Double = 0.0,
    val recoveryRate: Double = 0.0,
    val timeToEmpty: Long = -1L,
    val timeToFull: Long = -1L,
    val status: WPrimeStatus = WPrimeStatus.FRESH
) {
    companion object {
        fun fromBalance(balance: Double, maxBalance: Double): WPrimeState {
            val pct = (balance / maxBalance * 100.0).coerceIn(0.0, 100.0)
            val status = when {
                pct > 90 -> WPrimeStatus.FRESH
                pct > 70 -> WPrimeStatus.GOOD
                pct > 50 -> WPrimeStatus.WORKING
                pct > 30 -> WPrimeStatus.DEPLETING
                pct > 10 -> WPrimeStatus.CRITICAL
                else -> WPrimeStatus.EMPTY
            }
            return WPrimeState(
                balance = balance,
                maxBalance = maxBalance,
                percentage = pct,
                status = status
            )
        }
    }
}
