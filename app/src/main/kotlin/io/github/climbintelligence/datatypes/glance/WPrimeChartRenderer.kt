package io.github.climbintelligence.datatypes.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import io.github.climbintelligence.data.model.WPrimeSample
import kotlin.math.max
import kotlin.math.min

/**
 * Off-screen Canvas chart renderer for the W' balance history field.
 *
 * Glance widgets cannot draw smooth curves with their composable primitives
 * (Box/Row/Column). The accepted Glance pattern for non-primitive graphics is
 * to render to a Bitmap off-screen, then wrap it in androidx.glance.Image
 * with an ImageProvider. This object encapsulates that.
 *
 * Rendering rules (matches the intervals.icu reference):
 *   - Single-color line ABOVE the zero baseline: WPrimeFresh (green).
 *   - Single-color line BELOW the zero baseline: WPrimeDeficit (purple).
 *     This is the "reserve depth" — the model's signal that W'max is
 *     calibrated too low.
 *   - Fill area between the line and the zero baseline at ~20% alpha,
 *     colored to match its side.
 *   - Dashed zero reference line straight across the plot.
 *   - FullDualAxis mode: kJ labels on the LEFT axis, percent labels on
 *     the RIGHT axis. Single line plotted once; two axis-label sets at
 *     the chart edges. Time-axis labels (start / mid / now) along the
 *     bottom. Current-value pill anchored to the right edge of the line.
 *
 * Pure object: same inputs always produce a byte-identical Bitmap. Caller
 * is responsible for trimming `samples` to the visible window before
 * calling (the renderer plots whatever is passed in).
 */
sealed class ChartMode {
    object Sparkline : ChartMode()        // line + fill only, no axes, no labels
    object MiniWithCurrent : ChartMode()  // line + fill + current value pill (right edge)
    object FullDualAxis : ChartMode()     // full chart: dual Y-axes, time axis, current pill
    object Vertical : ChartMode()         // rotated 90° for NARROW slots
}

object WPrimeChartRenderer {

    /**
     * Overrender factor. Glance scales bitmaps to fill the widget slot on
     * the Karoo screen; bilinear upscaling softens text and lines, which is
     * why other (Text-based) Glance fields look crisper than this chart.
     * Rendering at this multiple of the caller's logical dimensions and
     * letting Glance scale DOWN (a clean operation) closes the gap. The
     * bitmap memory cost scales with the square: 280 × 160 × 3 × 3 × 4 B
     * ≈ 1.5 MB, well within budget for a per-tick re-render.
     */
    private const val RENDER_SCALE = 3
    private const val FILL_ALPHA = 51            // ~20% alpha for fill area
    private const val LINE_STROKE_PX = 2.5f
    private const val ZERO_LINE_STROKE_PX = 1.0f
    private const val AXIS_LABEL_TEXT_SIZE_PX = 13f
    private const val PILL_TEXT_SIZE_PX = 13f
    private const val FULL_LEFT_PAD = 28f        // room for kJ labels
    private const val FULL_RIGHT_PAD = 44f       // room for percent labels ("100%" at 13px would clip at 32)
    private const val FULL_BOTTOM_PAD = 14f      // room for time labels
    private const val FULL_TOP_PAD = 4f
    private const val MINI_PAD = 2f
    private const val PILL_RIGHT_PAD = 4f
    private const val PILL_HEIGHT_PX = 18f

    /**
     * Render the chart for the given sample window.
     *
     * @param samples ordered by timestampMs ascending; may be empty
     * @param width target Bitmap width in pixels
     * @param height target Bitmap height in pixels
     * @param mode layout mode determining axes / labels / current-value pill
     * @param wMax athlete's W' max in joules — used to compute percent axis
     *             and to size the positive plot area even when no positive
     *             samples are present
     */
    fun renderChart(
        samples: List<WPrimeSample>,
        width: Int,
        height: Int,
        mode: ChartMode,
        wMax: Double
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        // Overrender at RENDER_SCALE so Glance's Image scaler downsamples
        // rather than upsamples — crisp text + lines at the device's DPI.
        val bitmap = Bitmap.createBitmap(w * RENDER_SCALE, h * RENDER_SCALE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(RENDER_SCALE.toFloat(), RENDER_SCALE.toFloat())

        if (samples.isEmpty() || wMax <= 0.0) {
            return bitmap // blank for empty input
        }

        when (mode) {
            is ChartMode.Vertical -> drawVertical(canvas, w, h, samples, wMax)
            else -> drawHorizontal(canvas, w, h, samples, wMax, mode)
        }

        return bitmap
    }

    // ─── horizontal modes (Sparkline / MiniWithCurrent / FullDualAxis) ───

    private fun drawHorizontal(
        canvas: Canvas,
        width: Int,
        height: Int,
        samples: List<WPrimeSample>,
        wMax: Double,
        mode: ChartMode
    ) {
        val pad = layoutPadding(mode)
        val plotLeft = pad.left
        val plotTop = pad.top
        val plotRight = width - pad.right
        val plotBottom = height - pad.bottom
        if (plotRight <= plotLeft || plotBottom <= plotTop) return

        val (yMin, yMax) = computeYRange(samples, wMax)
        val plotW = (plotRight - plotLeft).toFloat()
        val plotH = (plotBottom - plotTop).toFloat()

        fun xPx(index: Int): Float = if (samples.size > 1) {
            plotLeft + plotW * index / (samples.size - 1).toFloat()
        } else {
            plotLeft + plotW / 2f
        }
        fun yPx(balance: Double): Float {
            val frac = (balance - yMin) / (yMax - yMin)
            return (plotBottom - frac * plotH).toFloat()
        }
        val yZero = yPx(0.0).coerceIn(plotTop.toFloat(), plotBottom.toFloat())

        drawFills(canvas, samples, ::xPx, ::yPx, yZero, plotLeft.toFloat(), plotRight.toFloat())
        drawZeroLine(canvas, plotLeft.toFloat(), plotRight.toFloat(), yZero)
        drawLine(canvas, samples, ::xPx, ::yPx, yZero)

        if (mode is ChartMode.MiniWithCurrent) {
            // Pill only on Mini sizes — FullDualAxis conveys current via the
            // dual axes themselves; an extra pill would crowd the right-axis labels.
            drawCurrentPill(canvas, samples.last(), plotRight.toFloat(), plotTop.toFloat(), plotBottom.toFloat())
        }
        if (mode is ChartMode.FullDualAxis) {
            drawDualAxes(canvas, width, height, plotLeft.toFloat(), plotRight.toFloat(),
                plotTop.toFloat(), plotBottom.toFloat(), yMin, yMax, wMax, samples)
        }
    }

    // ─── vertical mode (rotated 90°) ───────────────────────────────────────

    private fun drawVertical(
        canvas: Canvas,
        width: Int,
        height: Int,
        samples: List<WPrimeSample>,
        wMax: Double
    ) {
        // Rotate so the time axis runs vertically. Swap effective dimensions.
        canvas.save()
        canvas.rotate(-90f, width / 2f, height / 2f)
        // After rotation, the canvas drawable area is (height × width) — call the
        // horizontal sparkline renderer with swapped dimensions.
        drawHorizontal(canvas, height, width, samples, wMax, ChartMode.Sparkline)
        canvas.restore()
    }

    // ─── primitives ───────────────────────────────────────────────────────

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun layoutPadding(mode: ChartMode): Padding = when (mode) {
        ChartMode.Sparkline -> Padding(MINI_PAD.toInt(), MINI_PAD.toInt(), MINI_PAD.toInt(), MINI_PAD.toInt())
        ChartMode.MiniWithCurrent -> Padding(MINI_PAD.toInt(), MINI_PAD.toInt(), (MINI_PAD + 36f).toInt(), MINI_PAD.toInt())
        ChartMode.FullDualAxis -> Padding(FULL_LEFT_PAD.toInt(), FULL_TOP_PAD.toInt(), FULL_RIGHT_PAD.toInt(), FULL_BOTTOM_PAD.toInt())
        ChartMode.Vertical -> Padding(MINI_PAD.toInt(), MINI_PAD.toInt(), MINI_PAD.toInt(), MINI_PAD.toInt())
    }

    /**
     * Compute Y range so 0 is always visible and the visible vertical extent
     * matches the data — no imaginary negative headroom on positive-only rides.
     *
     *   - yMax: at least 20 % of wMax above zero so a deep-deficit ride still
     *     shows positive headroom for visual context.
     *   - yMin: 0 when no sample is negative (curve uses full plot height,
     *     no `-10 %` label for data that never existed). When any sample IS
     *     negative, expand to `-10 % of wMax` below zero so deficit values
     *     get visible headroom under them.
     */
    private fun computeYRange(samples: List<WPrimeSample>, wMax: Double): Pair<Double, Double> {
        val minSample = samples.minOf { it.balance }
        val maxSample = samples.maxOf { it.balance }
        val yMax = max(maxSample, wMax * 0.2)
        val yMin = if (minSample < 0) min(minSample, -wMax * 0.1) else 0.0
        return yMin to yMax
    }

    private fun drawFills(
        canvas: Canvas,
        samples: List<WPrimeSample>,
        xPx: (Int) -> Float,
        yPx: (Double) -> Float,
        yZero: Float,
        plotLeft: Float,
        plotRight: Float
    ) {
        // Build a path that traces the line and closes down to the zero
        // baseline. We then clip to above-zero / below-zero regions and fill
        // each in its respective color at 20% alpha.
        val fillPath = Path()
        fillPath.moveTo(xPx(0), yZero)
        for (i in samples.indices) {
            fillPath.lineTo(xPx(i), yPx(samples[i].balance))
        }
        fillPath.lineTo(xPx(samples.lastIndex), yZero)
        fillPath.close()

        val greenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlanceColors.WPrimeFresh.toArgb()
            alpha = FILL_ALPHA
            style = Paint.Style.FILL
        }
        val purpleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlanceColors.WPrimeDeficit.toArgb()
            alpha = FILL_ALPHA
            style = Paint.Style.FILL
        }

        // Fill above-zero region (green)
        canvas.save()
        canvas.clipRect(plotLeft, 0f, plotRight, yZero)
        canvas.drawPath(fillPath, greenFill)
        canvas.restore()

        // Fill below-zero region (purple)
        canvas.save()
        canvas.clipRect(plotLeft, yZero, plotRight, Float.MAX_VALUE)
        canvas.drawPath(fillPath, purpleFill)
        canvas.restore()
    }

    private fun drawZeroLine(canvas: Canvas, plotLeft: Float, plotRight: Float, yZero: Float) {
        val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlanceColors.Separator.toArgb()
            strokeWidth = ZERO_LINE_STROKE_PX
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        canvas.drawLine(plotLeft, yZero, plotRight, yZero, zeroPaint)
    }

    private fun drawLine(
        canvas: Canvas,
        samples: List<WPrimeSample>,
        xPx: (Int) -> Float,
        yPx: (Double) -> Float,
        yZero: Float
    ) {
        // Build the line path once, stroke it twice with different clips so
        // the above-zero portion is green and the below-zero portion is purple.
        val linePath = Path()
        linePath.moveTo(xPx(0), yPx(samples[0].balance))
        for (i in 1 until samples.size) {
            linePath.lineTo(xPx(i), yPx(samples[i].balance))
        }

        val greenLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlanceColors.WPrimeFresh.toArgb()
            strokeWidth = LINE_STROKE_PX
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val purpleLine = Paint(greenLine).apply {
            color = GlanceColors.WPrimeDeficit.toArgb()
        }

        canvas.save()
        canvas.clipRect(0f, 0f, Float.MAX_VALUE, yZero)
        canvas.drawPath(linePath, greenLine)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0f, yZero, Float.MAX_VALUE, Float.MAX_VALUE)
        canvas.drawPath(linePath, purpleLine)
        canvas.restore()
    }

    private fun drawCurrentPill(
        canvas: Canvas,
        last: WPrimeSample,
        plotRight: Float,
        plotTop: Float,
        plotBottom: Float
    ) {
        val text = "%.1f kJ".format(last.balance / 1000.0)
        val color = if (last.balance >= 0)
            GlanceColors.WPrimeFresh.toArgb()
        else
            GlanceColors.WPrimeDeficit.toArgb()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = PILL_TEXT_SIZE_PX
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }

        // Anchor pill text to the right edge of the plot, vertically centered
        val centerY = (plotTop + plotBottom) / 2f
        val baselineY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, plotRight + PILL_RIGHT_PAD * 6, baselineY, textPaint)
    }

    private fun drawDualAxes(
        canvas: Canvas,
        width: Int,
        height: Int,
        plotLeft: Float,
        plotRight: Float,
        plotTop: Float,
        plotBottom: Float,
        yMin: Double,
        yMax: Double,
        wMax: Double,
        samples: List<WPrimeSample>
    ) {
        val labelPaintLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GlanceColors.Label.toArgb()
            textSize = AXIS_LABEL_TEXT_SIZE_PX
            textAlign = Paint.Align.RIGHT
        }
        val labelPaintRight = Paint(labelPaintLeft).apply { textAlign = Paint.Align.LEFT }
        val labelPaintCenter = Paint(labelPaintLeft).apply { textAlign = Paint.Align.CENTER }

        // ─── Y-axis labels: kJ on LEFT, percent on RIGHT ───
        // Top label
        val topLabelY = plotTop + AXIS_LABEL_TEXT_SIZE_PX
        canvas.drawText("%.1f".format(yMax / 1000.0), plotLeft - 2f, topLabelY, labelPaintLeft)
        canvas.drawText("%.0f%%".format(yMax / wMax * 100.0), plotRight + 2f, topLabelY, labelPaintRight)

        // Zero baseline label
        val yZero = plotBottom - ((0.0 - yMin) / (yMax - yMin) * (plotBottom - plotTop)).toFloat()
        if (yZero in (plotTop + AXIS_LABEL_TEXT_SIZE_PX)..(plotBottom - AXIS_LABEL_TEXT_SIZE_PX)) {
            canvas.drawText("0", plotLeft - 2f, yZero + AXIS_LABEL_TEXT_SIZE_PX / 2.5f, labelPaintLeft)
            canvas.drawText("0%", plotRight + 2f, yZero + AXIS_LABEL_TEXT_SIZE_PX / 2.5f, labelPaintRight)
        }

        // Bottom label
        val bottomLabelY = plotBottom - 1f
        canvas.drawText("%.1f".format(yMin / 1000.0), plotLeft - 2f, bottomLabelY, labelPaintLeft)
        canvas.drawText("%.0f%%".format(yMin / wMax * 100.0), plotRight + 2f, bottomLabelY, labelPaintRight)

        // Left-axis unit hint — values on the left are pure numbers, so the
        // 'kJ' hint clarifies them. The right axis labels already carry the
        // '%' suffix, so no hint there (was duplicating information and
        // looked like a stacked label).
        val hintPaint = Paint(labelPaintLeft).apply { textSize = AXIS_LABEL_TEXT_SIZE_PX - 1f; alpha = 180 }
        canvas.drawText("kJ", plotLeft - 2f, topLabelY + AXIS_LABEL_TEXT_SIZE_PX, hintPaint)

        // ─── Time axis: start / mid / end labels ───
        if (samples.size >= 2) {
            val t0 = samples.first().timestampMs
            val t1 = samples.last().timestampMs
            val tMid = (t0 + t1) / 2
            val totalSec = (t1 - t0) / 1000L
            val timeBaselineY = (height - 1).toFloat()

            val timePaintLeft = Paint(labelPaintLeft).apply { textAlign = Paint.Align.LEFT }
            val timePaintCenter = Paint(labelPaintCenter)
            val timePaintRight = Paint(labelPaintLeft) // already RIGHT-aligned

            canvas.drawText(formatRideTime(0L, totalSec), plotLeft, timeBaselineY, timePaintLeft)
            canvas.drawText(formatRideTime((tMid - t0) / 1000L, totalSec),
                (plotLeft + plotRight) / 2f, timeBaselineY, timePaintCenter)
            canvas.drawText(formatRideTime(totalSec, totalSec),
                plotRight, timeBaselineY, timePaintRight)
        }
    }

    private fun formatRideTime(seconds: Long, totalSec: Long): String {
        // Use HH:MM:SS if the total ride exceeds 1h; MM:SS otherwise.
        return if (totalSec >= 3600) {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            "%d:%02d".format(h, m)
        } else {
            val m = seconds / 60
            val s = seconds % 60
            "%d:%02d".format(m, s)
        }
    }
}
