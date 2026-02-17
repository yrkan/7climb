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

/**
 * No-climb state: show live grade + power + W' instead of blank.
 */
@Composable
private fun NoClimbContent(state: ClimbDisplayState, fontSize: Int = 20) {
    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)
    val hasData = state.live.hasData

    if (!hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("No data", GlanceColors.Label, fontSize)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Large grade as primary value
            ValueText("%.1f%%".format(state.live.grade), gradeColor, fontSize + 4)
            if (fontSize >= 18) {
                GlanceDivider()
                // Power + W' row
                DualMetric(
                    label1 = "POWER",
                    value1 = if (state.live.power > 0) "${state.live.power}W" else "-",
                    color1 = GlanceColors.White,
                    label2 = "W'BAL",
                    value2 = "%.0f%%".format(state.wPrime.percentage),
                    color2 = wColor,
                    valueFontSize = fontSize - 4
                )
            }
        }
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
        NoClimbContent(state, 16)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueText("%.0f%%".format(state.live.grade), gradeColor, 20)
            GlanceVerticalDivider(
                height = 16,
                modifier = GlanceModifier.padding(horizontal = 4.dp)
            )
            ValueText("%.0f%%".format(state.wPrime.percentage), wColor, 20)
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
        NoClimbContent(state, 18)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(20))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueText("%.0f%%".format(state.live.grade), gradeColor, 24)
                GlanceVerticalDivider(
                    height = 18,
                    modifier = GlanceModifier.padding(horizontal = 6.dp)
                )
                ValueText("W'%.0f%%".format(state.wPrime.percentage), wColor, 24)
            }
            // Detected climbs: show distance climbed instead of progress bar
            if (climb.isDetectedClimb) {
                LabelText("%.1fkm climbed".format(climb.distanceClimbedKm))
            }
        }
    }
}

/**
 * MEDIUM_WIDE: Name + DualMetric(GRADE, W'BAL) + progress bar or detected metrics.
 */
@Composable
private fun OverviewMediumWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent(state)
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
                valueFontSize = 26
            )
            if (climb.hasRouteMetrics) {
                ProgressBar(climb.progress, GlanceColors.Optimal)
            } else {
                // Detected climb: show climbed + gained
                DualMetric(
                    label1 = "CLIMBED",
                    value1 = "%.1fkm".format(climb.distanceClimbedKm),
                    color1 = GlanceColors.White,
                    label2 = "GAINED",
                    value2 = "${climb.elevation.toInt()}m",
                    color2 = GlanceColors.White,
                    valueFontSize = 16
                )
            }
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
        NoClimbContent(state)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(20), fontSize = 12)
            ValueText("%.1f%%".format(state.live.grade), gradeColor, 26)
            GlanceDivider()
            ValueText("W' %.0f%%".format(state.wPrime.percentage), wColor, 20)
            if (climb.hasRouteMetrics) {
                ProgressBar(climb.progress, GlanceColors.Optimal)
            }
        }
    }
}

/**
 * LARGE: Full layout with all available information.
 * Route climbs: DIST, ELEV, ETA + progress bar.
 * Detected climbs: CLIMBED, GAINED, ELAPSED, GRADE.
 * Both: ACTUAL + TARGET power with pacing advice.
 */
@Composable
private fun OverviewLarge(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        NoClimbContent(state)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Climb name
            LabelText(climb.name, fontSize = 12)

            // Category
            if (climb.categoryLabel.isNotEmpty()) {
                LabelText("CAT ${climb.categoryLabel}", fontSize = 12)
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

            if (climb.hasRouteMetrics) {
                // Route climb: profile bar + progress + DIST/ELEV/ETA
                SegmentProfileBar(
                    segments = climb.segments,
                    progress = climb.progress,
                    height = 10
                )
                LabelText("%.0f%%".format(climb.progressPercent))

                GlanceDivider()

                val distToTop = "%.1fkm".format(climb.distanceToTopKm)
                val elevToTop = "${climb.elevationToTop.toInt()}m"
                val eta = if (state.pacing.projectedTimeSeconds > 0) {
                    PhysicsUtils.formatTime(state.pacing.projectedTimeSeconds)
                } else "--:--"

                TripleMetric(
                    label1 = "DIST", value1 = distToTop, color1 = GlanceColors.White,
                    label2 = "ELEV", value2 = elevToTop, color2 = GlanceColors.White,
                    label3 = "ETA", value3 = eta, color3 = GlanceColors.White,
                    valueFontSize = 16
                )
            } else {
                // Detected climb: show what we know
                GlanceDivider()

                DualMetric(
                    label1 = "CLIMBED",
                    value1 = "%.1fkm".format(climb.distanceClimbedKm),
                    color1 = GlanceColors.White,
                    label2 = "GAINED",
                    value2 = "${climb.elevation.toInt()}m",
                    color2 = GlanceColors.White,
                    valueFontSize = 16
                )
                DualMetric(
                    label1 = "ELAPSED",
                    value1 = PhysicsUtils.formatTime(climb.elapsedSeconds),
                    color1 = GlanceColors.White,
                    label2 = "GRADE",
                    value2 = "%.1f%%".format(climb.avgGrade),
                    color2 = GlanceColors.gradeColor(climb.avgGrade),
                    valueFontSize = 16
                )
            }

            // Pacing target section: show ACTUAL + TARGET
            if (state.pacing.hasTarget) {
                GlanceDivider()
                val pColor = GlanceColors.pacingColor(state.pacing.advice)
                DualMetric(
                    label1 = "ACTUAL",
                    value1 = "${state.live.power}W",
                    color1 = GlanceColors.White,
                    label2 = "TARGET",
                    value2 = "${state.pacing.targetPower}W",
                    color2 = pColor,
                    valueFontSize = 16
                )
                ValueText(
                    pacingAdviceText(state.pacing.advice),
                    GlanceColors.pacingColor(state.pacing.advice),
                    14
                )
            }

            // Tactical insight at bottom
            val insight = state.tacticalInsight
            if (insight != null) {
                val iColor = GlanceColors.insightColor(insight.type)
                ValueText(insight.description, iColor, 12)
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
        NoClimbContent(state, 18)
        return
    }

    val gradeColor = GlanceColors.gradeColor(state.live.grade)
    val wColor = GlanceColors.wPrimeColor(state.wPrime.percentage)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(15), fontSize = 12)
            ValueText("%.1f%%".format(state.live.grade), gradeColor, 26)
            GlanceDivider()
            ValueText("W' %.0f%%".format(state.wPrime.percentage), wColor, 20)
            if (climb.hasRouteMetrics) {
                ProgressBar(climb.progress, GlanceColors.Optimal)
                LabelText("%.1fkm".format(climb.distanceToTopKm), fontSize = 12)
            } else {
                LabelText("%.1fkm climbed".format(climb.distanceClimbedKm), fontSize = 12)
            }
        }
    }
}
