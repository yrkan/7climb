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

class ClimbProgressGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "climb-progress") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> ProgressSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> ProgressSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> ProgressMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> ProgressMedium(state)
                BaseDataType.LayoutSize.LARGE -> ProgressLarge(state)
                BaseDataType.LayoutSize.NARROW -> ProgressNarrow(state)
            }
        }
    }
}

/**
 * SMALL: Progress % only, Optimal color, 24sp.
 */
@Composable
private fun ProgressSmall(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 24)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 24)
    }
}

/**
 * SMALL_WIDE: "PROGRESS" + % 28sp + progress bar.
 */
@Composable
private fun ProgressSmallWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("PROGRESS")
                ValueText("-", GlanceColors.Label, 28)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROGRESS")
            ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 28)
            ProgressBar(climb.progress, GlanceColors.Optimal, modifier = GlanceModifier.padding(vertical = 2.dp))
        }
    }
}

/**
 * MEDIUM_WIDE: "PROGRESS" + % 28sp + bar + distance/elev remaining.
 */
@Composable
private fun ProgressMediumWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("PROGRESS")
                ValueText("No climb", GlanceColors.Label, 20)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROGRESS")
            ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 28)
            ProgressBar(climb.progress, GlanceColors.Optimal, modifier = GlanceModifier.padding(vertical = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelText("%.1fkm".format(climb.distanceToTopKm))
                LabelText(" / ${climb.elevationToTop.toInt()}m")
            }
        }
    }
}

/**
 * MEDIUM: "PROGRESS" + % 24sp + bar.
 */
@Composable
private fun ProgressMedium(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 24)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROGRESS", fontSize = 11)
            ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 24)
            ProgressBar(climb.progress, GlanceColors.Optimal, modifier = GlanceModifier.padding(vertical = 2.dp))
        }
    }
}

/**
 * LARGE: "CLIMB PROGRESS" + % 42sp + bar (8px) + divider + REMAINING/ELEVATION/AVG GRADE rows.
 */
@Composable
private fun ProgressLarge(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("CLIMB PROGRESS", fontSize = 12)
                ValueText("No climb", GlanceColors.Label, 20)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("CLIMB PROGRESS", fontSize = 12)
            ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 42)
            ProgressBar(climb.progress, GlanceColors.Optimal, 8, GlanceModifier.padding(vertical = 2.dp))
            GlanceDivider()
            MetricValueRow("REMAINING", "%.1fkm".format(climb.distanceToTopKm), GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("ELEVATION", "${climb.elevationToTop.toInt()}m", GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("AVG GRADE", "%.1f%%".format(climb.avgGrade), GlanceColors.gradeColor(climb.avgGrade), valueFontSize = 18, labelFontSize = 12)
        }
    }
}

/**
 * NARROW: "PROGRESS" + % 28sp + bar + "X.Xkm left".
 */
@Composable
private fun ProgressNarrow(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 28)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROGRESS", fontSize = 11)
            ValueText("%.0f%%".format(climb.progressPercent), GlanceColors.Optimal, 28)
            ProgressBar(climb.progress, GlanceColors.Optimal, modifier = GlanceModifier.padding(vertical = 2.dp))
            LabelText("%.1fkm left".format(climb.distanceToTopKm), fontSize = 12)
        }
    }
}
