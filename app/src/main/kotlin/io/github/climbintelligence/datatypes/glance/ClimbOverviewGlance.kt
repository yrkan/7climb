package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.github.climbintelligence.util.PhysicsUtils
import io.hammerhead.karooext.models.ViewConfig

class ClimbOverviewGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "climb-overview") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        ClimbOverviewContent(state, config)
    }
}

/**
 * Main compound climb overview content.
 * Shows all climbing metrics in one view.
 *
 * Layout sizes:
 * - SMALL: Grade + W' side-by-side with vertical divider
 * - SMALL_WIDE: Climb name (truncated) + Grade | W' side-by-side
 * - MEDIUM_WIDE: Name + DualMetric(GRADE, W'BAL) + progress bar
 * - MEDIUM: Grade value + divider + W' value + progress bar
 * - LARGE: Full layout with name, grade, W', profile, progress, metrics, pacing, insight
 * - NARROW: Name + Grade + divider + W' + progress bar + distance remaining
 */
@Composable
fun ClimbOverviewContent(state: ClimbDisplayState, config: ViewConfig) {
    val layoutSize = BaseDataType.getLayoutSize(config)

    DataFieldContainer {
        when (layoutSize) {
            BaseDataType.LayoutSize.SMALL -> OverviewSmall(state)
            BaseDataType.LayoutSize.SMALL_WIDE -> OverviewSmallWide(state)
            BaseDataType.LayoutSize.MEDIUM_WIDE -> OverviewMediumWide(state)
            BaseDataType.LayoutSize.MEDIUM -> OverviewMedium(state)
            BaseDataType.LayoutSize.LARGE -> OverviewLarge(state)
            BaseDataType.LayoutSize.NARROW -> OverviewNarrow(state)
        }
    }
}

@Composable
private fun NoClimbContent(fontSize: Int = 20) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText("No climb", GlanceColors.Label, fontSize)
    }
}

/**
 * SMALL: Grade + W' side-by-side with vertical divider.
 * No labels, compact values only.
 */
@Composable
private fun OverviewSmall(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent(16)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueText("%.0f%%".format(state.live.grade), gradeColor, 18)
            GlanceVerticalDivider(
                height = 16,
                modifier = GlanceModifier.padding(horizontal = 4.dp)
            )
            ValueText("%.0f%%".format(state.wPrime.percentage), wColor, 18)
        }
    }
}

/**
 * SMALL_WIDE: Climb name (truncated) + Grade | W' side-by-side.
 */
@Composable
private fun OverviewSmallWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent(18)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(20))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueText("%.0f%%".format(state.live.grade), gradeColor, 22)
                GlanceVerticalDivider(
                    height = 18,
                    modifier = GlanceModifier.padding(horizontal = 6.dp)
                )
                ValueText("W'%.0f%%".format(state.wPrime.percentage), wColor, 22)
            }
        }
    }
}

/**
 * MEDIUM_WIDE: Name + DualMetric(GRADE, W'BAL) + progress bar.
 */
@Composable
private fun OverviewMediumWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent()
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(25))
            DualMetric(
                label1 = "GRADE",
                value1 = "%.1f%%".format(state.live.grade),
                color1 = gradeColor,
                label2 = "W'BAL",
                value2 = "%.0f%%".format(state.wPrime.percentage),
                color2 = wColor,
                valueFontSize = 24
            )
            ProgressBar(climb.progress, GlanceColors.Optimal)
        }
    }
}

/**
 * MEDIUM: Grade value + divider + W' value + progress bar.
 */
@Composable
private fun OverviewMedium(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent()
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(20), fontSize = 11)
            ValueText("%.1f%%".format(state.live.grade), gradeColor, 24)
            GlanceDivider()
            ValueText("W' %.0f%%".format(state.wPrime.percentage), wColor, 18)
            ProgressBar(climb.progress, GlanceColors.Optimal)
        }
    }
}

/**
 * LARGE: Full layout with all available information.
 * - Climb name + category
 * - DualMetric(GRADE large, W'BAL large)
 * - SegmentProfileBar with rider position
 * - Progress percentage
 * - divider
 * - TripleMetric(DIST, ELEV, ETA)
 * - If pacing target: divider + TARGET power + advice
 * - If tactical insight: insight message at bottom
 */
@Composable
private fun OverviewLarge(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent()
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)
    val distToTop = "%.1fkm".format(climb.distanceToTopKm)
    val elevToTop = "${climb.elevationToTop.toInt()}m"
    val eta = if (state.pacing.projectedTimeSeconds > 0) {
        PhysicsUtils.formatTime(state.pacing.projectedTimeSeconds)
    } else "--:--"

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Climb name
            LabelText(climb.name, fontSize = 12)

            // Category
            if (climb.categoryLabel.isNotEmpty()) {
                LabelText("CAT ${climb.categoryLabel}", fontSize = 10)
            }

            // Grade + W' dual metric
            DualMetric(
                label1 = "GRADE",
                value1 = "%.1f%%".format(state.live.grade),
                color1 = gradeColor,
                label2 = "W'BAL",
                value2 = "%.0f%%".format(state.wPrime.percentage),
                color2 = wColor,
                valueFontSize = 28
            )

            // Segment profile bar with rider position
            SegmentProfileBar(
                segments = climb.segments,
                progress = climb.progress,
                height = 10
            )

            // Progress percentage
            LabelText("%.0f%%".format(climb.progressPercent))

            GlanceDivider()

            // Distance, elevation, ETA
            TripleMetric(
                label1 = "DIST", value1 = distToTop, color1 = GlanceColors.White,
                label2 = "ELEV", value2 = elevToTop, color2 = GlanceColors.White,
                label3 = "ETA", value3 = eta, color3 = GlanceColors.White,
                valueFontSize = 16
            )

            // Pacing target section
            if (state.pacing.hasTarget) {
                GlanceDivider()
                val pColor = GlanceColors.pacingColor(state.pacing.advice)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricValueRow(
                        label = "TARGET",
                        value = "${state.pacing.targetPower}W",
                        valueColor = pColor,
                        valueFontSize = 16,
                        labelFontSize = 11
                    )
                }
                ValueText(
                    pacingAdviceText(state.pacing.advice),
                    GlanceColors.pacingColor(state.pacing.advice),
                    12
                )
            }

            // Tactical insight at bottom
            val insight = state.tacticalInsight
            if (insight != null) {
                val iColor = GlanceColors.insightColor(insight.type)
                ValueText(insight.description, iColor, 11)
            }
        }
    }
}

/**
 * NARROW: Name + Grade + divider + W' + progress bar + distance remaining.
 */
@Composable
private fun OverviewNarrow(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent(18)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(15), fontSize = 11)
            ValueText("%.1f%%".format(state.live.grade), gradeColor, 24)
            GlanceDivider()
            ValueText("W' %.0f%%".format(state.wPrime.percentage), wColor, 18)
            ProgressBar(climb.progress, GlanceColors.Optimal)
            LabelText("%.1fkm".format(climb.distanceToTopKm), fontSize = 10)
        }
    }
}
