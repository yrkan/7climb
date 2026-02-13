package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.model.ClimbSegment
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.hammerhead.karooext.models.ViewConfig

class NextSegmentGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "next-segment") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> NextSegSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> NextSegSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> NextSegMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> NextSegMedium(state)
                BaseDataType.LayoutSize.LARGE -> NextSegLarge(state)
                BaseDataType.LayoutSize.NARROW -> NextSegNarrow(state)
            }
        }
    }
}

/**
 * Find current and next segments using climb-relative distance.
 * Segments use startDistance relative to climb start, so we compute
 * distance on climb from progress (0.0-1.0) * total climb length.
 */
private fun findCurrentAndNextSegment(state: ClimbDisplayState): Pair<ClimbSegment?, ClimbSegment?> {
    val climb = state.climb ?: return Pair(null, null)
    if (!climb.isActive || climb.segments.isEmpty()) return Pair(null, null)

    val distOnClimb = climb.progress * climb.length

    val currentIdx = climb.segments.indexOfLast { it.startDistance <= distOnClimb }
    val current = if (currentIdx >= 0) climb.segments[currentIdx] else null
    val next = if (currentIdx >= 0 && currentIdx + 1 < climb.segments.size) climb.segments[currentIdx + 1] else null

    return Pair(current, next)
}

/**
 * SMALL: Next grade value only, color-coded, 22sp.
 */
@Composable
private fun NextSegSmall(state: ClimbDisplayState) {
    val (_, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 22)
        }
        return
    }

    val color = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText("%.1f%%".format(next.grade), color, 22)
    }
}

/**
 * SMALL_WIDE: "NEXT" label + grade 28sp + segment length.
 */
@Composable
private fun NextSegSmallWide(state: ClimbDisplayState) {
    val (_, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("NEXT")
                ValueText("--", GlanceColors.Label, 28)
            }
        }
        return
    }

    val color = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT")
            ValueText("%.1f%%".format(next.grade), color, 28)
            LabelText("${next.length.toInt()}m")
        }
    }
}

/**
 * MEDIUM_WIDE: "NEXT SEGMENT" + grade 28sp + length + elevation.
 */
@Composable
private fun NextSegMediumWide(state: ClimbDisplayState) {
    val (_, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("NEXT SEGMENT")
                ValueText("--", GlanceColors.Label, 24)
            }
        }
        return
    }

    val color = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT SEGMENT")
            ValueText("%.1f%%".format(next.grade), color, 28)
            DualMetric(
                "LENGTH", "${next.length.toInt()}m", GlanceColors.White,
                "ELEV", "${next.elevation.toInt()}m", GlanceColors.White,
                valueFontSize = 14
            )
        }
    }
}

/**
 * MEDIUM: "NEXT" + grade 22sp + length.
 */
@Composable
private fun NextSegMedium(state: ClimbDisplayState) {
    val (_, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 22)
        }
        return
    }

    val color = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT", fontSize = 11)
            ValueText("%.1f%%".format(next.grade), color, 22)
            LabelText("${next.length.toInt()}m")
        }
    }
}

/**
 * LARGE: "NEXT SEGMENT" + grade 42sp + divider + LENGTH/ELEV rows + CURRENT segment info.
 */
@Composable
private fun NextSegLarge(state: ClimbDisplayState) {
    val (current, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("NEXT SEGMENT", fontSize = 12)
                ValueText("No climb", GlanceColors.Label, 18)
            }
        }
        return
    }

    val nextColor = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT SEGMENT", fontSize = 12)
            ValueText("%.1f%%".format(next.grade), nextColor, 42)
            GlanceDivider()
            MetricValueRow("LENGTH", "${next.length.toInt()}m", GlanceColors.White, valueFontSize = 16, labelFontSize = 11)
            MetricValueRow("ELEV", "${next.elevation.toInt()}m", GlanceColors.White, valueFontSize = 16, labelFontSize = 11)
            if (current != null) {
                GlanceDivider()
                val currentColor = GlanceColors.gradeColor(current.grade)
                MetricValueRow("CURRENT", "%.1f%%".format(current.grade), currentColor, valueFontSize = 16, labelFontSize = 11)
            }
        }
    }
}

/**
 * NARROW: "NEXT" + grade 24sp + length.
 */
@Composable
private fun NextSegNarrow(state: ClimbDisplayState) {
    val (_, next) = findCurrentAndNextSegment(state)
    if (next == null) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 24)
        }
        return
    }

    val color = GlanceColors.gradeColor(next.grade)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("NEXT", fontSize = 11)
            ValueText("%.1f%%".format(next.grade), color, 24)
            LabelText("${next.length.toInt()}m", fontSize = 10)
        }
    }
}
