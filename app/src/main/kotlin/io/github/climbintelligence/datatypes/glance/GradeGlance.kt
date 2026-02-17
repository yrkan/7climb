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

class GradeGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "grade") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> GradeSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> GradeSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> GradeMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> GradeMedium(state)
                BaseDataType.LayoutSize.LARGE -> GradeLarge(state)
                BaseDataType.LayoutSize.NARROW -> GradeNarrow(state)
            }
        }
    }
}

@Composable
private fun GradeSmall(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText(value, color, 24)
    }
}

@Composable
private fun GradeSmallWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }
    val isClimbing = hasData && grade > 3.0

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("GRADE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isClimbing) {
                    ValueText("\u25B2", color, 18, GlanceModifier.padding(end = 4.dp))
                }
                ValueText(value, color, 28)
            }
        }
    }
}

@Composable
private fun GradeMediumWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }
    val alt = getDisplayValue(hasData) { "${state.live.altitude.toInt()}m" }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("GRADE")
            ValueText(value, color, 32)
            GlanceDivider()
            MetricValueRow("ALT", alt, GlanceColors.White, valueFontSize = 16)
        }
    }
}

@Composable
private fun GradeMedium(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }
    val isClimbing = hasData && grade > 3.0

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("GRADE", fontSize = 11)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isClimbing) {
                    ValueText("\u25B2", color, 16, GlanceModifier.padding(end = 4.dp))
                }
                ValueText(value, color, 24)
            }
        }
    }
}

@Composable
private fun GradeLarge(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }
    val alt = getDisplayValue(hasData) { "${state.live.altitude.toInt()}m" }
    val hr = if (hasData && state.live.heartRate > 0) "${state.live.heartRate}bpm" else BaseDataType.NO_DATA
    val cad = if (hasData && state.live.cadence > 0) "${state.live.cadence}rpm" else BaseDataType.NO_DATA

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("GRADE", fontSize = 12)
            ValueText(value, color, 42)
            GlanceDivider()
            MetricValueRow("ALT", alt, GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("HR", hr, GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("CAD", cad, GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
        }
    }
}

@Composable
private fun GradeNarrow(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val grade = state.live.grade
    val color = if (hasData) GlanceColors.gradeColor(grade) else GlanceColors.Label
    val value = getDisplayValue(hasData) { "%.1f%%".format(grade) }
    val alt = getDisplayValue(hasData) { "${state.live.altitude.toInt()}m" }
    val isClimbing = hasData && grade > 3.0

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("GRADE", fontSize = 11)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isClimbing) {
                    ValueText("\u25B2", color, 20, GlanceModifier.padding(end = 4.dp))
                }
                ValueText(value, color, 32)
            }
            LabelText(alt, fontSize = 12)
        }
    }
}
