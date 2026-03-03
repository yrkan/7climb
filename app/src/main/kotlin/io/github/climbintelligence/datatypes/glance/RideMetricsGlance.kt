package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.github.climbintelligence.util.PhysicsUtils
import io.hammerhead.karooext.models.ViewConfig

class RideMetricsGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "ride-metrics") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> RideMetricsSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> RideMetricsSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM -> RideMetricsMedium(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> RideMetricsMediumWide(state)
                BaseDataType.LayoutSize.LARGE -> RideMetricsLarge(state)
                BaseDataType.LayoutSize.NARROW -> RideMetricsNarrow(state)
            }
        }
    }
}

@Composable
private fun RideMetricsSmall(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val value = getDisplayValue(m.hasData) { "${m.normalizedPower}" }
    val color = if (m.hasData) GlanceColors.npColor(m.intensityFactor) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NP", fontSize = 10)
            ValueText(value, color, 24)
        }
    }
}

@Composable
private fun RideMetricsSmallWide(state: ClimbDisplayState) {
    val m = state.rideMetrics
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
        }
        return
    }

    val npColor = GlanceColors.npColor(m.intensityFactor)
    val ifColor = GlanceColors.ifColor(m.intensityFactor)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DualMetric(
            "NP", "${m.normalizedPower}W", npColor,
            "IF", "%.2f".format(m.intensityFactor), ifColor,
            valueFontSize = 22
        )
    }
}

@Composable
private fun RideMetricsMedium(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val npValue = getDisplayValue(m.hasData) { "${m.normalizedPower}W" }
    val ifValue = getDisplayValue(m.hasData) { "IF %.2f".format(m.intensityFactor) }
    val color = if (m.hasData) GlanceColors.npColor(m.intensityFactor) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NP", fontSize = 11)
            ValueText(npValue, color, 24)
            LabelText(ifValue)
        }
    }
}

@Composable
private fun RideMetricsMediumWide(state: ClimbDisplayState) {
    val m = state.rideMetrics
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 32)
        }
        return
    }

    val npColor = GlanceColors.npColor(m.intensityFactor)
    val ifColor = GlanceColors.ifColor(m.intensityFactor)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("RIDE METRICS")
            TripleMetric(
                "NP", "${m.normalizedPower}W", npColor,
                "IF", "%.2f".format(m.intensityFactor), ifColor,
                "TSS", "%.0f".format(m.trainingStressScore), GlanceColors.White,
                valueFontSize = 18
            )
        }
    }
}

@Composable
private fun RideMetricsLarge(state: ClimbDisplayState) {
    val m = state.rideMetrics
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 42)
        }
        return
    }

    val npColor = GlanceColors.npColor(m.intensityFactor)
    val ifColor = GlanceColors.ifColor(m.intensityFactor)
    val time = PhysicsUtils.formatTime(m.elapsedSeconds)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("RIDE METRICS", fontSize = 12)
            ValueText("${m.normalizedPower}W", npColor, 42)
            GlanceDivider()
            MetricValueRow("IF", "%.2f".format(m.intensityFactor), ifColor, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("TSS", "%.0f".format(m.trainingStressScore), GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("kJ", "%.0f".format(m.totalKj), GlanceColors.White, valueFontSize = 16, labelFontSize = 12)
            MetricValueRow("TIME", time, GlanceColors.Label, valueFontSize = 16, labelFontSize = 12)
            MetricValueRow("VI", "%.2f".format(m.variabilityIndex), GlanceColors.Label, valueFontSize = 16, labelFontSize = 12)
        }
    }
}

@Composable
private fun RideMetricsNarrow(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val npValue = getDisplayValue(m.hasData) { "${m.normalizedPower}W" }
    val ifValue = getDisplayValue(m.hasData) { "IF %.2f".format(m.intensityFactor) }
    val tssValue = getDisplayValue(m.hasData) { "TSS %.0f".format(m.trainingStressScore) }
    val color = if (m.hasData) GlanceColors.npColor(m.intensityFactor) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NP", fontSize = 11)
            ValueText(npValue, color, 28)
            LabelText(ifValue, fontSize = 14)
            LabelText(tssValue, fontSize = 12)
        }
    }
}
