package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

enum class PacingAdvice {
    EASE_OFF,
    STEADY,
    PUSH,
    PERFECT
}

enum class PacingMode {
    STEADY,
    RACE,
    SURVIVAL
}

@Serializable
data class PacingTarget(
    val targetPower: Int = 0,
    val rangeLow: Int = 0,
    val rangeHigh: Int = 0,
    val delta: Int = 0,
    val advice: PacingAdvice = PacingAdvice.STEADY,
    val projectedTimeSeconds: Long = 0L,
    val mode: PacingMode = PacingMode.STEADY
) {
    val hasTarget: Boolean get() = targetPower > 0
}
