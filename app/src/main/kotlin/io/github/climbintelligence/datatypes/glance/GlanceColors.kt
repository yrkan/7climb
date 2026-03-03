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

    // Power zone colors (Coggan zones 1-7)
    val Zone1 = Color(0xFF9E9E9E)  // grey — Recovery
    val Zone2 = Color(0xFF4CAF50)  // green — Endurance
    val Zone3 = Color(0xFF8BC34A)  // light green — Tempo
    val Zone4 = Color(0xFFFFC107)  // amber — Threshold
    val Zone5 = Color(0xFFFF9800)  // orange — VO2max
    val Zone6 = Color(0xFFF44336)  // red — Anaerobic
    val Zone7 = Color(0xFFB388FF)  // violet — Neuromuscular

    // Match burn colors
    val MatchLow = Color(0xFF4CAF50)
    val MatchMedium = Color(0xFFFFC107)
    val MatchHigh = Color(0xFFFF9800)
    val MatchExtreme = Color(0xFFF44336)
    val MatchActive = Color(0xFFFF5722)  // deep orange when burning

    // Category colors (climb categories)
    val CatHC = Color(0xFFB388FF)   // violet
    val Cat1 = Color(0xFFF44336)    // red
    val Cat2 = Color(0xFFFF9800)    // orange
    val Cat3 = Color(0xFFFFC107)    // amber
    val Cat4 = Color(0xFF4CAF50)    // green

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

    fun npColor(intensityFactor: Double): Color = when {
        intensityFactor < 0.75 -> GradeEasy
        intensityFactor < 0.90 -> GradeModerate
        intensityFactor < 1.00 -> GradeHard
        intensityFactor < 1.10 -> GradeSteep
        else -> GradeExtreme
    }

    fun ifColor(intensityFactor: Double): Color = npColor(intensityFactor)

    fun zoneColor(zone: Int): Color = when (zone) {
        0 -> Zone1; 1 -> Zone2; 2 -> Zone3; 3 -> Zone4
        4 -> Zone5; 5 -> Zone6; 6 -> Zone7; else -> Label
    }

    fun matchColor(count: Int): Color = when {
        count < 3 -> MatchLow
        count < 6 -> MatchMedium
        count < 10 -> MatchHigh
        else -> MatchExtreme
    }

    fun categoryColor(category: Int): Color = when (category) {
        1 -> CatHC; 2 -> Cat1; 3 -> Cat2; 4 -> Cat3; 5 -> Cat4
        else -> Label
    }
}
