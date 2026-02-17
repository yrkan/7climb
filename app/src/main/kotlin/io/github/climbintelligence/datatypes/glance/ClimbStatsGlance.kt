package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.github.climbintelligence.util.PhysicsUtils
import io.hammerhead.karooext.models.ViewConfig

class ClimbStatsGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "climb-stats") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> StatsSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> StatsSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM -> StatsMedium(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> StatsMediumWide(state)
                BaseDataType.LayoutSize.LARGE -> StatsLarge(state)
                BaseDataType.LayoutSize.NARROW -> StatsNarrow(state)
            }
        }
    }
}

private fun vamValue(state: ClimbDisplayState): String {
    return if (state.climbStats.isTracking && state.climbStats.vamRolling > 0) {
        "${state.climbStats.vamRolling}"
    } else BaseDataType.NO_DATA
}

private fun wKgValue(state: ClimbDisplayState): String {
    return if (state.live.hasData && state.climbStats.wKg > 0) {
        "%.1f".format(state.climbStats.wKg)
    } else BaseDataType.NO_DATA
}

private fun vamDisplayColor(state: ClimbDisplayState): Color {
    return if (state.climbStats.isTracking && state.climbStats.vamRolling > 0) {
        GlanceColors.vamColor(state.climbStats.vamRolling)
    } else GlanceColors.Label
}

private fun wKgDisplayColor(state: ClimbDisplayState): Color {
    return if (state.live.hasData && state.climbStats.wKg > 0) {
        GlanceColors.wKgColor(state.climbStats.wKg)
    } else GlanceColors.Label
}

/**
 * SMALL: VAM value only, centered, 24sp.
 */
@Composable
private fun StatsSmall(state: ClimbDisplayState) {
    val vam = vamValue(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("VAM")
            ValueText(vam, vamDisplayColor(state), 24)
        }
    }
}

/**
 * SMALL_WIDE: VAM + W/KG side by side.
 */
@Composable
private fun StatsSmallWide(state: ClimbDisplayState) {
    val vam = vamValue(state)
    val wkg = wKgValue(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DualMetric(
            label1 = "VAM", value1 = vam, color1 = vamDisplayColor(state),
            label2 = "W/KG", value2 = wkg, color2 = wKgDisplayColor(state),
            valueFontSize = 24
        )
    }
}

/**
 * MEDIUM: VAM 24sp + W/kg 16sp stacked.
 */
@Composable
private fun StatsMedium(state: ClimbDisplayState) {
    val vam = vamValue(state)
    val wkg = wKgValue(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("VAM", fontSize = 11)
            ValueText(vam, vamDisplayColor(state), 24)
            MetricValueRow("W/KG", wkg, wKgDisplayColor(state), valueFontSize = 18, labelFontSize = 12)
        }
    }
}

/**
 * MEDIUM_WIDE: Triple metric — VAM, W/KG, kJ.
 */
@Composable
private fun StatsMediumWide(state: ClimbDisplayState) {
    val vam = vamValue(state)
    val wkg = wKgValue(state)
    val kj = if (state.climbStats.isTracking) {
        "%.0f".format(state.climbStats.energyKj)
    } else BaseDataType.NO_DATA

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("CLIMB STATS")
            TripleMetric(
                label1 = "VAM", value1 = vam, color1 = vamDisplayColor(state),
                label2 = "W/KG", value2 = wkg, color2 = wKgDisplayColor(state),
                label3 = "kJ", value3 = kj, color3 = GlanceColors.White,
                valueFontSize = 20
            )
        }
    }
}

/**
 * LARGE: Full stats dashboard.
 * VAM + W/kg (DualMetric 28sp)
 * HR + CAD (DualMetric 20sp)
 * kJ + TIME (DualMetric 16sp)
 * avg W/kg
 */
@Composable
private fun StatsLarge(state: ClimbDisplayState) {
    val stats = state.climbStats
    val hasData = state.live.hasData
    val isTracking = stats.isTracking

    val vam = vamValue(state)
    val wkg = wKgValue(state)

    val hr = if (hasData && state.live.heartRate > 0) "${state.live.heartRate}" else BaseDataType.NO_DATA
    val cad = if (hasData && state.live.cadence > 0) "${state.live.cadence}" else BaseDataType.NO_DATA

    val kj = if (isTracking) "%.0f".format(stats.energyKj) else BaseDataType.NO_DATA
    val time = if (isTracking) PhysicsUtils.formatTime(stats.elapsedSeconds) else BaseDataType.NO_DATA

    val avgWkg = if (isTracking && stats.avgWKg > 0) "avg %.1f W/kg".format(stats.avgWKg) else ""

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DualMetric(
                label1 = "VAM", value1 = vam, color1 = vamDisplayColor(state),
                label2 = "W/KG", value2 = wkg, color2 = wKgDisplayColor(state),
                valueFontSize = 28
            )
            GlanceDivider()
            DualMetric(
                label1 = "HR", value1 = hr, color1 = GlanceColors.White,
                label2 = "CAD", value2 = cad, color2 = GlanceColors.White,
                valueFontSize = 20
            )
            GlanceDivider()
            DualMetric(
                label1 = "kJ", value1 = kj, color1 = GlanceColors.White,
                label2 = "TIME", value2 = time, color2 = GlanceColors.White,
                valueFontSize = 16
            )
            if (avgWkg.isNotEmpty()) {
                LabelText(avgWkg, fontSize = 12)
            }
        }
    }
}

/**
 * NARROW: VAM 28sp + W/kg 16sp + HR 14sp stacked.
 */
@Composable
private fun StatsNarrow(state: ClimbDisplayState) {
    val vam = vamValue(state)
    val wkg = wKgValue(state)
    val hr = if (state.live.hasData && state.live.heartRate > 0) {
        "${state.live.heartRate}"
    } else BaseDataType.NO_DATA

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("VAM", fontSize = 11)
            ValueText(vam, vamDisplayColor(state), 28)
            MetricValueRow("W/KG", wkg, wKgDisplayColor(state), valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("HR", hr, GlanceColors.White, valueFontSize = 16, labelFontSize = 12)
        }
    }
}
