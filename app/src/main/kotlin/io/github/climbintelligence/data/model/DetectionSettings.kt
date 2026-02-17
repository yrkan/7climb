package io.github.climbintelligence.data.model

enum class DetectionSensitivity(
    val minGrade: Double,
    val minElevation: Int,
    val confirmDistance: Int,
    val endDistance: Int
) {
    SENSITIVE(3.0, 10, 100, 100),
    BALANCED(4.0, 15, 200, 150),
    CONSERVATIVE(5.0, 25, 300, 200)
}

data class DetectionSettings(
    val sensitivity: DetectionSensitivity = DetectionSensitivity.BALANCED,
    val minGrade: Double = 4.0,
    val minElevation: Int = 15,
    val confirmDistance: Int = 200,
    val endDistance: Int = 150,
    val isCustom: Boolean = false
) {
    val minGradeContinue: Double
        get() = (minGrade - 1.5).coerceAtLeast(2.0)
}
