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

class NextClimbGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "next-climb") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> NextClimbSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> NextClimbSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM -> NextClimbMedium(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> NextClimbMediumWide(state)
                BaseDataType.LayoutSize.LARGE -> NextClimbLarge(state)
                BaseDataType.LayoutSize.NARROW -> NextClimbNarrow(state)
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters.toInt()}m"
}

@Composable
private fun NextClimbSmall(state: ClimbDisplayState) {
    val nc = state.nextClimb
    val value = if (nc.hasNext) formatDistance(nc.distanceToStart) else BaseDataType.NO_DATA
    val color = if (nc.hasNext) GlanceColors.categoryColor(nc.climbCategory) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT", fontSize = 10)
            ValueText(value, color, 24)
        }
    }
}

@Composable
private fun NextClimbSmallWide(state: ClimbDisplayState) {
    val nc = state.nextClimb
    if (!nc.hasNext) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
        }
        return
    }

    val color = GlanceColors.categoryColor(nc.climbCategory)
    val eta = if (nc.etaSeconds > 0) PhysicsUtils.formatTime(nc.etaSeconds) else "-"

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DualMetric(
            "DIST", formatDistance(nc.distanceToStart), color,
            "ETA", eta, GlanceColors.White,
            valueFontSize = 22
        )
    }
}

@Composable
private fun NextClimbMedium(state: ClimbDisplayState) {
    val nc = state.nextClimb
    val dist = if (nc.hasNext) formatDistance(nc.distanceToStart) else BaseDataType.NO_DATA
    val color = if (nc.hasNext) GlanceColors.categoryColor(nc.climbCategory) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT CLIMB", fontSize = 11)
            ValueText(dist, color, 24)
            if (nc.hasNext && nc.climbName.isNotEmpty()) {
                LabelText(nc.climbName, fontSize = 11)
            }
        }
    }
}

@Composable
private fun NextClimbMediumWide(state: ClimbDisplayState) {
    val nc = state.nextClimb
    if (!nc.hasNext) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("NEXT CLIMB")
                ValueText("No climbs ahead", GlanceColors.Label, 16)
            }
        }
        return
    }

    val color = GlanceColors.categoryColor(nc.climbCategory)
    val eta = if (nc.etaSeconds > 0) PhysicsUtils.formatTime(nc.etaSeconds) else "-"
    val elev = "+${nc.climbElevation.toInt()}m"

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (nc.climbName.isNotEmpty()) {
                LabelText(nc.climbName, fontSize = 12)
            }
            TripleMetric(
                "DIST", formatDistance(nc.distanceToStart), color,
                "ETA", eta, GlanceColors.White,
                "ELEV", elev, GlanceColors.Label,
                valueFontSize = 16
            )
        }
    }
}

@Composable
private fun NextClimbLarge(state: ClimbDisplayState) {
    val nc = state.nextClimb
    if (!nc.hasNext) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("NEXT CLIMB", fontSize = 12)
                ValueText("No climbs ahead", GlanceColors.Label, 20)
            }
        }
        return
    }

    val color = GlanceColors.categoryColor(nc.climbCategory)
    val eta = if (nc.etaSeconds > 0) PhysicsUtils.formatTime(nc.etaSeconds) else "-"
    val catLabel = when (nc.climbCategory) {
        1 -> "HC"; 2 -> "Cat 1"; 3 -> "Cat 2"; 4 -> "Cat 3"; 5 -> "Cat 4"; else -> ""
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT CLIMB", fontSize = 12)
            if (nc.climbName.isNotEmpty()) {
                ValueText(nc.climbName, GlanceColors.White, 18)
            }
            if (catLabel.isNotEmpty()) {
                ValueText(catLabel, color, 16)
            }
            GlanceDivider()
            MetricValueRow("DIST", formatDistance(nc.distanceToStart), color, valueFontSize = 24, labelFontSize = 12)
            MetricValueRow("ETA", eta, GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("ELEV", "+${nc.climbElevation.toInt()}m", GlanceColors.Label, valueFontSize = 16, labelFontSize = 12)
            MetricValueRow("LENGTH", formatDistance(nc.climbLength), GlanceColors.Label, valueFontSize = 16, labelFontSize = 12)
            MetricValueRow("AVG", "%.1f%%".format(nc.climbAvgGrade), GlanceColors.gradeColor(nc.climbAvgGrade), valueFontSize = 16, labelFontSize = 12)
        }
    }
}

@Composable
private fun NextClimbNarrow(state: ClimbDisplayState) {
    val nc = state.nextClimb
    val dist = if (nc.hasNext) formatDistance(nc.distanceToStart) else BaseDataType.NO_DATA
    val color = if (nc.hasNext) GlanceColors.categoryColor(nc.climbCategory) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT", fontSize = 11)
            ValueText(dist, color, 28)
            if (nc.hasNext) {
                if (nc.climbName.isNotEmpty()) {
                    LabelText(nc.climbName, fontSize = 11)
                }
                val eta = if (nc.etaSeconds > 0) PhysicsUtils.formatTime(nc.etaSeconds) else "-"
                LabelText("ETA $eta", fontSize = 12)
            }
        }
    }
}
