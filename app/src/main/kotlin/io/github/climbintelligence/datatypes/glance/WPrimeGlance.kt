package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.github.climbintelligence.data.model.WPrimeStatus
import io.github.climbintelligence.util.PhysicsUtils
import io.hammerhead.karooext.models.ViewConfig

class WPrimeGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "wprime-balance") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> WPrimeSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> WPrimeSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> WPrimeMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> WPrimeMedium(state)
                BaseDataType.LayoutSize.LARGE -> WPrimeLarge(state)
                BaseDataType.LayoutSize.NARROW -> WPrimeNarrow(state)
            }
        }
    }
}

private fun statusText(status: WPrimeStatus): String = when (status) {
    WPrimeStatus.FRESH -> "FRESH"
    WPrimeStatus.GOOD -> "GOOD"
    WPrimeStatus.WORKING -> "WORKING"
    WPrimeStatus.DEPLETING -> "DEPLETING"
    WPrimeStatus.CRITICAL -> "CRITICAL"
    WPrimeStatus.EMPTY -> "EMPTY"
}

private fun timeLabel(state: ClimbDisplayState): String {
    val w = state.wPrime
    return when {
        w.depletionRate > 0 && w.timeToEmpty > 0 ->
            "TTE ${PhysicsUtils.formatTime(w.timeToEmpty)}"
        w.recoveryRate > 0 && w.timeToFull > 0 ->
            "TTF ${PhysicsUtils.formatTime(w.timeToFull)}"
        else -> ""
    }
}

@Composable
private fun WPrimeSmall(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText(value, color, 24)
    }
}

@Composable
private fun WPrimeSmallWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("W'BAL")
            ValueText(value, color, 28)
            if (hasData) {
                WPrimeBar(pct, color)
            }
        }
    }
}

@Composable
private fun WPrimeMediumWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }
    val time = if (hasData) timeLabel(state) else ""

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("W'BAL")
            ValueText(value, color, 32)
            if (hasData) {
                WPrimeBar(pct, color)
            }
            if (time.isNotEmpty()) {
                LabelText(time)
            }
        }
    }
}

@Composable
private fun WPrimeMedium(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }
    val kj = getDisplayValue(hasData) { "%.1fkJ".format(state.wPrime.balance / 1000.0) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("W'BAL", fontSize = 11)
            ValueText(value, color, 24)
            if (hasData) {
                WPrimeBar(pct, color)
            }
            LabelText(kj)
        }
    }
}

@Composable
private fun WPrimeLarge(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }
    val status = getDisplayValue(hasData) { statusText(state.wPrime.status) }
    val balanceKj = getDisplayValue(hasData) { "%.1fkJ".format(state.wPrime.balance / 1000.0) }
    val time = if (hasData) timeLabel(state) else ""

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("W' BALANCE", fontSize = 12)
            ValueText(value, color, 42)
            if (hasData) {
                WPrimeBar(pct, color, 8)
            }
            GlanceDivider()
            MetricValueRow("STATUS", status, color, valueFontSize = 16, labelFontSize = 11)
            MetricValueRow("ENERGY", balanceKj, GlanceColors.White, valueFontSize = 16, labelFontSize = 11)
            if (time.isNotEmpty()) {
                LabelText(time, fontSize = 11)
            }
        }
    }
}

@Composable
private fun WPrimeNarrow(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val pct = state.wPrime.percentage
    val color = if (hasData) GlanceColors.wPrimeColor(pct) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.0f%%".format(pct) }
    val status = getDisplayValue(hasData) { statusText(state.wPrime.status) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("W'BAL", fontSize = 11)
            ValueText(value, color, 32)
            if (hasData) {
                WPrimeBar(pct, color)
            }
            LabelText(status, fontSize = 10)
        }
    }
}

@Composable
private fun WPrimeBar(pct: Double, color: androidx.compose.ui.graphics.Color, height: Int = 6) {
    ProgressBar(
        progress = pct / 100.0,
        color = color,
        height = height,
        modifier = GlanceModifier.padding(vertical = 2.dp)
    )
}
