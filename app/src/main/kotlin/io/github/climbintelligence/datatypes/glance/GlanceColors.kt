package io.github.climbintelligence.datatypes.glance

import androidx.compose.ui.graphics.Color
import io.github.climbintelligence.data.model.PacingAdvice
import io.github.climbintelligence.engine.TacticalAnalyzer

object GlanceColors {
    val White = Color(0xFFFFFFFF)
    val Optimal = Color(0xFF4CAF50)
    val Attention = Color(0xFFFF9800)
    val Problem = Color(0xFFF44336)
    val Label = Color(0xFFAAAAAA)
    val Separator = Color(0xFF666666)
    val Divider = Color(0xFF555555)
    val Background = Color(0xFF000000)
    val Frame = Color(0xFF1A1A1A)

    // Grade-specific colors (optimized for black background + outdoor sunlight)
    val GradeEasy = Color(0xFF4CAF50)      // < 4%  — green
    val GradeModerate = Color(0xFFFFC107)  // 4-8%  — amber (softer than yellow, most common grade)
    val GradeHard = Color(0xFFFF9800)      // 8-12% — orange
    val GradeSteep = Color(0xFFF44336)     // 12-18% — red
    val GradeExtreme = Color(0xFFB388FF)   // > 18% — bright violet (high contrast on black)

    // W' status colors
    val WPrimeFresh = Color(0xFF4CAF50)
    val WPrimeGood = Color(0xFF8BC34A)
    val WPrimeWorking = Color(0xFFFFC107)
    val WPrimeDepleting = Color(0xFFFF9800)
    val WPrimeCritical = Color(0xFFF44336)
    val WPrimeEmpty = Color(0xFF9E9E9E)

    // PR delta
    val Ahead = Color(0xFF4CAF50)
    val Behind = Color(0xFFF44336)

    fun gradeColor(grade: Double): Color = when {
        grade < 4.0 -> GradeEasy
        grade < 8.0 -> GradeModerate
        grade < 12.0 -> GradeHard
        grade < 18.0 -> GradeSteep
        else -> GradeExtreme
    }

    fun vamColor(vam: Int): Color = when {
        vam < 600 -> GradeEasy
        vam < 1000 -> GradeModerate
        vam < 1500 -> GradeHard
        vam < 1800 -> GradeSteep
        else -> GradeExtreme
    }

    fun wKgColor(wkg: Double): Color = when {
        wkg < 2.0 -> GradeEasy
        wkg < 3.0 -> GradeModerate
        wkg < 4.0 -> GradeHard
        wkg < 5.0 -> GradeSteep
        else -> GradeExtreme
    }

    fun wPrimeColor(percentage: Double): Color = when {
        percentage > 90 -> WPrimeFresh
        percentage > 70 -> WPrimeGood
        percentage > 50 -> WPrimeWorking
        percentage > 30 -> WPrimeDepleting
        percentage > 10 -> WPrimeCritical
        else -> WPrimeEmpty
    }

    fun pacingColor(advice: PacingAdvice): Color = when (advice) {
        PacingAdvice.EASE_OFF -> Attention
        PacingAdvice.PUSH -> Optimal
        PacingAdvice.STEADY -> White
        PacingAdvice.PERFECT -> Optimal
    }

    fun insightColor(type: TacticalAnalyzer.InsightType): Color = when (type) {
        TacticalAnalyzer.InsightType.STEEP_SECTION -> Problem
        TacticalAnalyzer.InsightType.DANGEROUS_SECTION -> Problem
        TacticalAnalyzer.InsightType.ATTACK_POINT -> Optimal
        TacticalAnalyzer.InsightType.FINAL_KICK -> Optimal
        TacticalAnalyzer.InsightType.RECOVERY_ZONE -> Attention
        TacticalAnalyzer.InsightType.EASY_SECTION -> Attention
        TacticalAnalyzer.InsightType.GRADIENT_CHANGE -> White
    }
}
