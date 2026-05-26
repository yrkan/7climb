package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.model.WPrimeSample
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.hammerhead.karooext.models.ViewConfig

/**
 * Karoo data field rendering the W' balance history as a chart. The line goes
 * green above the zero baseline and purple below (Luigi's DEFICIT color) so
 * the rider sees reserve-depth excursions at a glance. Underlying drawing is
 * done in [WPrimeChartRenderer] (off-screen Bitmap + Canvas — the only way
 * Glance widgets can render smooth curves).
 *
 * v1 keeps it simple: every 1Hz tick re-renders the full-ride history.
 * Throttle + rolling-window trim are deferred until ride feedback motivates
 * them — both prefs already exist in [PreferencesRepository], they just
 * aren't read yet.
 */
class WPrimeHistoryGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "wprime-history") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> Render(state, 100, 60, ChartMode.Sparkline)
                BaseDataType.LayoutSize.SMALL_WIDE -> Render(state, 200, 60, ChartMode.MiniWithCurrent)
                BaseDataType.LayoutSize.MEDIUM -> Render(state, 160, 100, ChartMode.MiniWithCurrent)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> Render(state, 240, 80, ChartMode.MiniWithCurrent)
                BaseDataType.LayoutSize.LARGE -> Render(state, 280, 160, ChartMode.FullDualAxis)
                BaseDataType.LayoutSize.NARROW -> Render(state, 60, 140, ChartMode.Vertical)
            }
        }
    }
}

@Composable
private fun Render(state: ClimbDisplayState, width: Int, height: Int, mode: ChartMode) {
    val samples: List<WPrimeSample> = state.wPrimeHistory
    val wMax = state.wPrime.maxBalance

    if (samples.isEmpty() || wMax <= 0.0 || !state.live.hasData) {
        // Show "—" placeholder when there's no data yet
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ValueText("—", GlanceColors.Label, 24)
        }
        return
    }

    val bitmap = WPrimeChartRenderer.renderChart(samples, width, height, mode, wMax)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}
