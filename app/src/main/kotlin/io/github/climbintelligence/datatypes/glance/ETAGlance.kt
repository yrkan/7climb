package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.github.climbintelligence.util.PhysicsUtils
import io.hammerhead.karooext.models.ViewConfig

class ETAGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "eta") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> ETASmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> ETASmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> ETAMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> ETAMedium(state)
                BaseDataType.LayoutSize.LARGE -> ETALarge(state)
                BaseDataType.LayoutSize.NARROW -> ETANarrow(state)
            }
        }
    }
}

private fun calculateETA(state: ClimbDisplayState): String {
    val climb = state.climb ?: return "--:--"
    if (!climb.isActive || state.live.speed < 0.5) return "--:--"
    return PhysicsUtils.formatTime((climb.distanceToTop / state.live.speed).toLong())
}

/**
 * SMALL: ETA time only, centered, 24sp.
 */
@Composable
private fun ETASmall(state: ClimbDisplayState) {
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText(eta, GlanceColors.White, 24)
    }
}

/**
 * SMALL_WIDE: "SUMMIT ETA" label + time 28sp.
 */
@Composable
private fun ETASmallWide(state: ClimbDisplayState) {
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("SUMMIT ETA")
            ValueText(eta, GlanceColors.White, 28)
        }
    }
}

/**
 * MEDIUM_WIDE: "SUMMIT ETA" + time 32sp + distance remaining.
 */
@Composable
private fun ETAMediumWide(state: ClimbDisplayState) {
    val climb = state.climb
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("SUMMIT ETA")
            ValueText(eta, GlanceColors.White, 32)
            if (climb != null && climb.isActive) {
                LabelText("%.1fkm remaining".format(climb.distanceToTopKm))
            }
        }
    }
}

/**
 * MEDIUM: "ETA" label + time 24sp.
 */
@Composable
private fun ETAMedium(state: ClimbDisplayState) {
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("ETA", fontSize = 11)
            ValueText(eta, GlanceColors.White, 24)
        }
    }
}

/**
 * LARGE: "SUMMIT ETA" 42sp + divider + DIST/ELEV/SPEED metric rows.
 */
@Composable
private fun ETALarge(state: ClimbDisplayState) {
    val climb = state.climb
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("SUMMIT ETA", fontSize = 12)
            ValueText(eta, GlanceColors.White, 42)
            GlanceDivider()
            if (climb != null && climb.isActive) {
                val vam = if (state.climbStats.isTracking && state.climbStats.vamRolling > 0) {
                    "${state.climbStats.vamRolling}m/h"
                } else BaseDataType.NO_DATA
                val vamColor = if (state.climbStats.isTracking && state.climbStats.vamRolling > 0) {
                    GlanceColors.vamColor(state.climbStats.vamRolling)
                } else GlanceColors.Label
                MetricValueRow("DIST", "%.1fkm".format(climb.distanceToTopKm), GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
                MetricValueRow("ELEV", "${climb.elevationToTop.toInt()}m", GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
                MetricValueRow("VAM", vam, vamColor, valueFontSize = 18, labelFontSize = 12)
            } else {
                ValueText("No climb", GlanceColors.Label, 18)
            }
        }
    }
}

/**
 * NARROW: "ETA" + time 28sp + distance remaining.
 */
@Composable
private fun ETANarrow(state: ClimbDisplayState) {
    val climb = state.climb
    val eta = calculateETA(state)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("ETA", fontSize = 11)
            ValueText(eta, GlanceColors.White, 28)
            if (climb != null && climb.isActive) {
                LabelText("%.1fkm".format(climb.distanceToTopKm), fontSize = 12)
            }
        }
    }
}
