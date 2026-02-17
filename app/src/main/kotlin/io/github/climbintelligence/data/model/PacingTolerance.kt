package io.github.climbintelligence.data.model

enum class PacingTolerance(val watts: Int) {
    TIGHT(5),
    NORMAL(10),
    RELAXED(20)
}
