package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.climbintelligence.data.model.PacingAdvice
import io.github.climbintelligence.datatypes.BaseDataType

@Composable
fun DataFieldContainer(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceColors.Frame)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceColors.Background)
                .padding(4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun GlanceDivider(
    modifier: GlanceModifier = GlanceModifier,
    verticalPadding: Int = 4,
    horizontalPadding: Int = 8
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding.dp, horizontal = horizontalPadding.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GlanceColors.Divider)
        ) {}
    }
}

@Composable
fun GlanceVerticalDivider(
    modifier: GlanceModifier = GlanceModifier,
    height: Int = 16
) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height.dp)
            .background(GlanceColors.Divider)
    ) {}
}

@Composable
fun LabelText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 12
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            color = ColorProvider(GlanceColors.Label)
        ),
        maxLines = 1
    )
}

@Composable
fun ValueText(
    text: String,
    color: Color = GlanceColors.White,
    fontSize: Int = 18,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(color),
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

@Composable
fun MetricValueRow(
    label: String,
    value: String,
    valueColor: Color = GlanceColors.White,
    modifier: GlanceModifier = GlanceModifier,
    valueFontSize: Int = 18,
    labelFontSize: Int = 12
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelText(label, GlanceModifier.padding(end = 4.dp), labelFontSize)
        ValueText(value, valueColor, valueFontSize)
    }
}

/**
 * Proportional progress bar using 20 quantized segments.
 * Each segment gets defaultWeight() so Glance distributes evenly.
 */
@Composable
fun ProgressBar(
    progress: Double,
    color: Color = GlanceColors.Optimal,
    height: Int = 6,
    modifier: GlanceModifier = GlanceModifier
) {
    val pct = progress.coerceIn(0.0, 1.0)
    val totalSegments = 20
    val filledSegments = (pct * totalSegments).toInt().coerceIn(0, totalSegments)

    Box(
        modifier = modifier.fillMaxWidth().height(height.dp).background(GlanceColors.Frame)
    ) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            for (i in 0 until totalSegments) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .defaultWeight()
                        .background(if (i < filledSegments) color else GlanceColors.Background)
                ) {}
            }
        }
    }
}

/**
 * Segment-colored climb profile bar with rider position marker.
 * Each segment gets a proportional color based on its gradient.
 * The rider position is shown as a white vertical marker.
 */
@Composable
fun SegmentProfileBar(
    segments: List<io.github.climbintelligence.data.model.ClimbSegment>,
    progress: Double,
    height: Int = 8,
    modifier: GlanceModifier = GlanceModifier
) {
    if (segments.isEmpty()) {
        ProgressBar(progress, GlanceColors.Optimal, height, modifier)
        return
    }

    val totalSegments = segments.size
    val riderIndex = (progress * totalSegments).toInt().coerceIn(0, totalSegments - 1)

    Row(
        modifier = modifier.fillMaxWidth().height(height.dp).padding(vertical = 1.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            val segColor = GlanceColors.gradeColor(segment.grade)
            // Show rider position as white segment
            val displayColor = if (index == riderIndex) GlanceColors.White else segColor
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .background(displayColor)
            ) {}
        }
    }
}

/**
 * Two-column metric display with labels.
 */
@Composable
fun DualMetric(
    label1: String,
    value1: String,
    color1: Color,
    label2: String,
    value2: String,
    color2: Color,
    valueFontSize: Int = 24
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.defaultWeight()
        ) {
            LabelText(label1)
            ValueText(value1, color1, valueFontSize)
        }
        GlanceVerticalDivider(height = valueFontSize + 4)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.defaultWeight()
        ) {
            LabelText(label2)
            ValueText(value2, color2, valueFontSize)
        }
    }
}

/**
 * Three-column metric display.
 */
@Composable
fun TripleMetric(
    label1: String, value1: String, color1: Color,
    label2: String, value2: String, color2: Color,
    label3: String, value3: String, color3: Color,
    valueFontSize: Int = 16
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.defaultWeight()) {
            LabelText(label1)
            ValueText(value1, color1, valueFontSize)
        }
        GlanceVerticalDivider(height = valueFontSize + 4)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.defaultWeight()) {
            LabelText(label2)
            ValueText(value2, color2, valueFontSize)
        }
        GlanceVerticalDivider(height = valueFontSize + 4)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.defaultWeight()) {
            LabelText(label3)
            ValueText(value3, color3, valueFontSize)
        }
    }
}

fun getDisplayValue(hasData: Boolean, value: () -> String): String {
    return if (hasData) value() else BaseDataType.NO_DATA
}

fun pacingAdviceText(advice: PacingAdvice): String = when (advice) {
    PacingAdvice.EASE_OFF -> "EASE"
    PacingAdvice.PUSH -> "PUSH"
    PacingAdvice.STEADY -> "STEADY"
    PacingAdvice.PERFECT -> "PERFECT"
}

fun pacingDeltaText(delta: Int): String {
    val sign = if (delta > 0) "+" else ""
    return "${sign}${delta}W"
}
