package io.github.climbintelligence.data.model

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

data class PacingTarget(
    val targetPower: Int = 0,
    val rangeLow: Int = 0,
    val rangeHigh: Int = 0,
    val delta: Int = 0,
    val advice: PacingAdvice = PacingAdvice.STEADY,
    val projectedTimeSeconds: Long = 0L,
    val mode: PacingMode = PacingMode.STEADY,
    val strategyPhase: String = "",
    val firstHalfTarget: Int = 0,
    val secondHalfTarget: Int = 0
) {
    val hasTarget: Boolean get() = targetPower > 0
    val hasStrategy: Boolean get() = strategyPhase.isNotEmpty()
}
