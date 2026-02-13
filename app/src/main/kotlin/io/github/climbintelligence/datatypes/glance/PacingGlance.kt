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

class PacingGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "pacing-target") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> PacingSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> PacingSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> PacingMediumWide(state)
                BaseDataType.LayoutSize.MEDIUM -> PacingMedium(state)
                BaseDataType.LayoutSize.LARGE -> PacingLarge(state)
                BaseDataType.LayoutSize.NARROW -> PacingNarrow(state)
            }
        }
    }
}

@Composable
private fun PacingSmall(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 22)
            !hasTarget -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 22)
            else -> {
                val color = GlanceColors.pacingColor(state.pacing.advice)
                ValueText("${state.pacing.targetPower}W", color, 22)
            }
        }
    }
}

@Composable
private fun PacingSmallWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("TARGET")
            when {
                !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
                !hasTarget -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
                else -> {
                    val color = GlanceColors.pacingColor(state.pacing.advice)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ValueText("${state.pacing.targetPower}W", color, 28)
                        ValueText(
                            " ${pacingAdviceText(state.pacing.advice)}",
                            color, 14,
                            GlanceModifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PacingMediumWide(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("TARGET")
            when {
                !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 32)
                !hasTarget -> ValueText("No climb", GlanceColors.Label, 20)
                else -> {
                    val color = GlanceColors.pacingColor(state.pacing.advice)
                    ValueText("${state.pacing.targetPower}W", color, 32)
                    GlanceDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ValueText(pacingAdviceText(state.pacing.advice), color, 14)
                        ValueText(
                            " ${pacingDeltaText(state.pacing.delta)}",
                            color, 14,
                            GlanceModifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PacingMedium(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("TARGET", fontSize = 11)
            when {
                !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 24)
                !hasTarget -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 24)
                else -> {
                    val color = GlanceColors.pacingColor(state.pacing.advice)
                    ValueText("${state.pacing.targetPower}W", color, 24)
                    ValueText(pacingAdviceText(state.pacing.advice), color, 14)
                }
            }
        }
    }
}

@Composable
private fun PacingLarge(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("PACING TARGET", fontSize = 12)
            when {
                !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 42)
                !hasTarget -> ValueText("No climb", GlanceColors.Label, 20)
                else -> {
                    val color = GlanceColors.pacingColor(state.pacing.advice)
                    val actualPower = getDisplayValue(hasData) { "${state.live.power}W" }
                    ValueText("${state.pacing.targetPower}W", color, 42)
                    GlanceDivider()
                    ValueText(pacingAdviceText(state.pacing.advice), color, 18)
                    ValueText(pacingDeltaText(state.pacing.delta), color, 16)
                    GlanceDivider()
                    MetricValueRow(
                        "ACTUAL", actualPower, GlanceColors.White,
                        valueFontSize = 16, labelFontSize = 11
                    )
                    MetricValueRow(
                        "RANGE",
                        "${state.pacing.rangeLow}-${state.pacing.rangeHigh}W",
                        GlanceColors.Label,
                        valueFontSize = 14, labelFontSize = 11
                    )
                }
            }
        }
    }
}

@Composable
private fun PacingNarrow(state: ClimbDisplayState) {
    val hasData = state.live.hasData
    val hasTarget = state.pacing.hasTarget

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("TARGET", fontSize = 11)
            when {
                !hasData -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
                !hasTarget -> ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
                else -> {
                    val color = GlanceColors.pacingColor(state.pacing.advice)
                    ValueText("${state.pacing.targetPower}W", color, 28)
                    ValueText(pacingAdviceText(state.pacing.advice), color, 14)
                    ValueText(pacingDeltaText(state.pacing.delta), color, 12)
                }
            }
        }
    }
}
