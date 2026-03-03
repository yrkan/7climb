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
import io.hammerhead.karooext.models.ViewConfig

class MatchBurnGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "match-burn") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> MatchSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> MatchSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM -> MatchMedium(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> MatchMediumWide(state)
                BaseDataType.LayoutSize.LARGE -> MatchLarge(state)
                BaseDataType.LayoutSize.NARROW -> MatchNarrow(state)
            }
        }
    }
}

@Composable
private fun MatchSmall(state: ClimbDisplayState) {
    val m = state.matchBurn
    val value = getDisplayValue(m.hasData) { "${m.totalMatches}" }
    val color = if (m.hasData) {
        if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)
    } else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("MATCH", fontSize = 10)
            ValueText(value, color, 24)
        }
    }
}

@Composable
private fun MatchSmallWide(state: ClimbDisplayState) {
    val m = state.matchBurn
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
        }
        return
    }

    val color = if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DualMetric(
            "MATCHES", "${m.totalMatches}", color,
            "kJ>CP", "%.1f".format(m.totalKjAboveCp), GlanceColors.White,
            valueFontSize = 22
        )
    }
}

@Composable
private fun MatchMedium(state: ClimbDisplayState) {
    val m = state.matchBurn
    val value = getDisplayValue(m.hasData) { "${m.totalMatches}" }
    val color = if (m.hasData) {
        if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)
    } else GlanceColors.Label
    val kjText = getDisplayValue(m.hasData) { "%.1f kJ".format(m.totalKjAboveCp) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("MATCHES", fontSize = 11)
            ValueText(value, color, 24)
            LabelText(kjText)
            if (m.activeMatch) {
                LabelText("BURNING ${m.currentMatchDurationSeconds}s", fontSize = 10)
            }
        }
    }
}

@Composable
private fun MatchMediumWide(state: ClimbDisplayState) {
    val m = state.matchBurn
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 32)
        }
        return
    }

    val color = if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)
    val recoveryText = if (m.lastMatchRecoverySeconds > 0) "${m.lastMatchRecoverySeconds}s" else "-"

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("MATCH BURN")
            TripleMetric(
                "MATCHES", "${m.totalMatches}", color,
                "kJ>CP", "%.1f".format(m.totalKjAboveCp), GlanceColors.White,
                "RECOV", recoveryText, GlanceColors.Label,
                valueFontSize = 16
            )
        }
    }
}

@Composable
private fun MatchLarge(state: ClimbDisplayState) {
    val m = state.matchBurn
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 42)
        }
        return
    }

    val color = if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("MATCH BURN", fontSize = 12)
            ValueText("${m.totalMatches}", color, 42)
            GlanceDivider()
            MetricValueRow("kJ > CP", "%.1f".format(m.totalKjAboveCp), GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            if (m.lastMatchRecoverySeconds > 0) {
                MetricValueRow("RECOVERY", "${m.lastMatchRecoverySeconds}s", GlanceColors.Label, valueFontSize = 18, labelFontSize = 12)
            }
            if (m.activeMatch) {
                MetricValueRow("BURNING", "${m.currentMatchDurationSeconds}s", GlanceColors.MatchActive, valueFontSize = 18, labelFontSize = 12)
                if (m.currentMatchPeak > 0) {
                    MetricValueRow("PEAK", "${m.currentMatchPeak}W", GlanceColors.Attention, valueFontSize = 16, labelFontSize = 12)
                }
            } else if (m.recentMatches.isNotEmpty()) {
                val last = m.recentMatches.last()
                MetricValueRow("LAST", "${last.peakPower}W · ${last.durationSeconds}s", GlanceColors.Label, valueFontSize = 16, labelFontSize = 12)
            }
        }
    }
}

@Composable
private fun MatchNarrow(state: ClimbDisplayState) {
    val m = state.matchBurn
    val value = getDisplayValue(m.hasData) { "${m.totalMatches}" }
    val color = if (m.hasData) {
        if (m.activeMatch) GlanceColors.MatchActive else GlanceColors.matchColor(m.totalMatches)
    } else GlanceColors.Label
    val kjText = getDisplayValue(m.hasData) { "%.1f kJ".format(m.totalKjAboveCp) }
    val recovText = getDisplayValue(m.hasData) {
        if (m.lastMatchRecoverySeconds > 0) "${m.lastMatchRecoverySeconds}s" else "-"
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("MATCHES", fontSize = 11)
            ValueText(value, color, 28)
            LabelText(kjText, fontSize = 14)
            LabelText(recovText, fontSize = 12)
        }
    }
}
