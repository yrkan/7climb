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
import io.hammerhead.karooext.models.ViewConfig

class CompactClimbGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "compact-climb") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        CompactClimbContent(state, config)
    }
}

/**
 * Compact climb data field content.
 * Always shows something useful even without active climb.
 * Grade + W' are always available as base metrics.
 *
 * Layout sizes:
 * - SMALL: Grade | W' compact side-by-side (no labels, 18sp)
 * - SMALL_WIDE: Climb name + DualMetric(GRADE, W') with labels
 * - MEDIUM_WIDE: Name + three-column DualMetric(GRADE, W', TGT) when pacing available
 * - MEDIUM: Grade | W' + progress bar if climb active
 * - LARGE: Name + DualMetric(GRADE, W'BAL) 28sp + divider + progress + distance/elevation + target + insight
 * - NARROW: Grade 22sp + divider + W' 18sp + progress bar + distance
 */
@Composable
fun CompactClimbContent(state: ClimbDisplayState, config: ViewConfig) {
    val layoutSize = BaseDataType.getLayoutSize(config)

    DataFieldContainer {
        when (layoutSize) {
            BaseDataType.LayoutSize.SMALL -> CompactSmall(state)
            BaseDataType.LayoutSize.SMALL_WIDE -> CompactSmallWide(state)
            BaseDataType.LayoutSize.MEDIUM_WIDE -> CompactMediumWide(state)
            BaseDataType.LayoutSize.MEDIUM -> CompactMedium(state)
            BaseDataType.LayoutSize.LARGE -> CompactLarge(state)
            BaseDataType.LayoutSize.NARROW -> CompactNarrow(state)
        }
    }
}

/**
 * SMALL: Grade | W' compact side-by-side.
 * No labels, 18sp values with smaller % suffix.
 * Always works without active climb.
 */
@Composable
private fun CompactSmall(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueText("%.0f".format(state.live.grade), gradeColor, 18)
            ValueText("%", gradeColor, 12)
            GlanceVerticalDivider(
                height = 14,
                modifier = GlanceModifier.padding(horizontal = 3.dp)
            )
            ValueText("%.0f".format(state.wPrime.percentage), wColor, 18)
            ValueText("%", wColor, 12)
        }
    }
}

/**
 * SMALL_WIDE: Climb name + DualMetric(GRADE, W') with labels.
 * Shows climb name if active, otherwise just the metrics.
 */
@Composable
private fun CompactSmallWide(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)
    val climb = state.climb

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (climb != null && climb.isActive) {
                LabelText(climb.name.take(20))
            }
            DualMetric(
                label1 = "GRADE",
                value1 = "%.1f%%".format(state.live.grade),
                color1 = gradeColor,
                label2 = "W'",
                value2 = "%.0f%%".format(state.wPrime.percentage),
                color2 = wColor,
                valueFontSize = 22
            )
        }
    }
}

/**
 * MEDIUM_WIDE: Name + three-column when pacing available, otherwise DualMetric.
 * Shows GRADE, W', and TGT (target power) columns.
 */
@Composable
private fun CompactMediumWide(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)
    val climb = state.climb

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (climb != null && climb.isActive) {
                LabelText(climb.name.take(25))
            }
            if (state.pacing.hasTarget) {
                // Three-column layout: GRADE | W' | TGT
                TripleMetric(
                    label1 = "GRADE",
                    value1 = "%.1f%%".format(state.live.grade),
                    color1 = gradeColor,
                    label2 = "W'",
                    value2 = "%.0f%%".format(state.wPrime.percentage),
                    color2 = wColor,
                    label3 = "TGT",
                    value3 = "${state.pacing.targetPower}W",
                    color3 = GlanceColors.pacingColor(state.pacing.advice),
                    valueFontSize = 22
                )
            } else {
                DualMetric(
                    label1 = "GRADE",
                    value1 = "%.1f%%".format(state.live.grade),
                    color1 = gradeColor,
                    label2 = "W'",
                    value2 = "%.0f%%".format(state.wPrime.percentage),
                    color2 = wColor,
                    valueFontSize = 22
                )
            }
        }
    }
}

/**
 * MEDIUM: Grade | W' + progress bar if climb active.
 * Always shows grade and W' even without a climb.
 */
@Composable
private fun CompactMedium(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueText("%.1f%%".format(state.live.grade), gradeColor, 20)
                GlanceVerticalDivider(
                    height = 18,
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
                ValueText("W'%.0f%%".format(state.wPrime.percentage), wColor, 20)
            }
            if (state.climb != null && state.climb.isActive) {
                ProgressBar(state.climb.progress, GlanceColors.Optimal)
            }
        }
    }
}

/**
 * LARGE: Full compact dashboard.
 * - Name (if climb active)
 * - DualMetric(GRADE, W'BAL) 28sp
 * - divider
 * - progress bar (if climb active)
 * - distance/elevation info (if climb active)
 * - target power (if pacing available)
 * - tactical insight (if available)
 */
@Composable
private fun CompactLarge(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)
    val climb = state.climb

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Climb name
            if (climb != null && climb.isActive) {
                LabelText(climb.name, fontSize = 12)
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

            GlanceDivider()

            // Progress bar + distance/elevation (climb-dependent)
            if (climb != null && climb.isActive) {
                ProgressBar(climb.progress, GlanceColors.Optimal, 8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelText("%.1fkm".format(climb.distanceToTopKm))
                    LabelText(" / ${climb.elevationToTop.toInt()}m")
                }
            }

            // Target power
            if (state.pacing.hasTarget) {
                GlanceDivider()
                val pColor = GlanceColors.pacingColor(state.pacing.advice)
                MetricValueRow(
                    label = "TARGET",
                    value = "${state.pacing.targetPower}W",
                    valueColor = pColor,
                    valueFontSize = 16,
                    labelFontSize = 11
                )
            }

            // Tactical insight
            val insight = state.tacticalInsight
            if (insight != null) {
                val iColor = GlanceColors.insightColor(insight.type)
                ValueText(insight.description, iColor, 11)
            }
        }
    }
}

/**
 * NARROW: Grade 22sp + divider + W' 18sp + progress bar + distance.
 * Always shows grade and W'. Progress and distance only when climb active.
 */
@Composable
private fun CompactNarrow(state: ClimbDisplayState) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ValueText("%.1f%%".format(state.live.grade), gradeColor, 22)
            GlanceDivider()
            ValueText("W'%.0f%%".format(state.wPrime.percentage), wColor, 18)
            if (state.climb != null && state.climb.isActive) {
                ProgressBar(state.climb.progress, GlanceColors.Optimal)
                LabelText("%.1fkm".format(state.climb.distanceToTopKm), fontSize = 10)
            }
        }
    }
}
