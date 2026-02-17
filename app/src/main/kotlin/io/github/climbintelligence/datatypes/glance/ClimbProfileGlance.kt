package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.hammerhead.karooext.models.ViewConfig

class ClimbProfileGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "climb-profile") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> ProfileSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> ProfileSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> ProfileMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> ProfileMedium(state)
                BaseDataType.LayoutSize.LARGE -> ProfileLarge(state)
                BaseDataType.LayoutSize.NARROW -> ProfileNarrow(state)
            }
        }
    }
}

/**
 * SMALL: "PROFILE" label + segment bar + avg grade.
 */
@Composable
private fun ProfileSmall(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 20)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROFILE")
            SegmentProfileBar(climb.segments, climb.progress)
            LabelText("%.1f%% avg".format(climb.avgGrade))
        }
    }
}

/**
 * SMALL_WIDE: Climb name + segment bar (12px) + length/elev/grade summary.
 */
@Composable
private fun ProfileSmallWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("PROFILE")
                ValueText("-", GlanceColors.Label, 18)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(25))
            SegmentProfileBar(climb.segments, climb.progress, 12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelText("%.1fkm".format(climb.length / 1000.0))
                LabelText(" / ${climb.elevation.toInt()}m")
                LabelText(" / %.1f%%".format(climb.avgGrade))
            }
        }
    }
}

/**
 * MEDIUM_WIDE: Name + segment bar (12px) + summary line.
 */
@Composable
private fun ProfileMediumWide(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("PROFILE")
                ValueText("No climb", GlanceColors.Label, 18)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name.take(30))
            SegmentProfileBar(climb.segments, climb.progress, 12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelText("%.1fkm".format(climb.length / 1000.0))
                LabelText(" / ${climb.elevation.toInt()}m")
                LabelText(" / %.1f%%".format(climb.avgGrade))
            }
        }
    }
}

/**
 * MEDIUM: "PROFILE" + segment bar + avg grade.
 */
@Composable
private fun ProfileMedium(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 20)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROFILE", fontSize = 11)
            SegmentProfileBar(climb.segments, climb.progress)
            LabelText("%.1f%% avg".format(climb.avgGrade))
        }
    }
}

/**
 * LARGE: Name + segment bar (16px) + divider + LENGTH/ELEV/AVG/MAX metric rows.
 */
@Composable
private fun ProfileLarge(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LabelText("ELEVATION PROFILE", fontSize = 12)
                ValueText("No climb", GlanceColors.Label, 20)
            }
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(climb.name, fontSize = 12)
            SegmentProfileBar(climb.segments, climb.progress, 16)
            GlanceDivider()
            MetricValueRow("LENGTH", "%.1fkm".format(climb.length / 1000.0), GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("ELEV", "${climb.elevation.toInt()}m", GlanceColors.White, valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("AVG", "%.1f%%".format(climb.avgGrade), GlanceColors.gradeColor(climb.avgGrade), valueFontSize = 18, labelFontSize = 12)
            MetricValueRow("MAX", "%.1f%%".format(climb.maxGrade), GlanceColors.gradeColor(climb.maxGrade), valueFontSize = 18, labelFontSize = 12)
        }
    }
}

/**
 * NARROW: "PROFILE" + segment bar + avg grade.
 */
@Composable
private fun ProfileNarrow(state: ClimbDisplayState) {
    val climb = state.climb
    if (climb == null || !climb.isActive) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText("-", GlanceColors.Label, 20)
        }
        return
    }

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PROFILE", fontSize = 11)
            SegmentProfileBar(climb.segments, climb.progress)
            LabelText("%.1f%%".format(climb.avgGrade), fontSize = 12)
        }
    }
}
