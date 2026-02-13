package io.github.climbintelligence.datatypes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.PacingTarget
import io.github.climbintelligence.data.model.WPrimeState
import io.github.climbintelligence.engine.PRComparison
import io.github.climbintelligence.engine.TacticalAnalyzer
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Aggregated state for all DataTypes to consume.
 */
data class ClimbDisplayState(
    val live: LiveClimbState = LiveClimbState(),
    val wPrime: WPrimeState = WPrimeState(),
    val pacing: PacingTarget = PacingTarget(),
    val climb: ClimbInfo? = null,
    val prComparison: PRComparison = PRComparison(),
    val tacticalInsight: TacticalAnalyzer.TacticalInsight? = null
) {
    companion object {
        val PREVIEW = ClimbDisplayState(
            live = LiveClimbState(
                power = 245, heartRate = 155, cadence = 85,
                speed = 4.2, altitude = 850.0, grade = 7.5,
                distance = 12500.0, hasData = true
            ),
            wPrime = WPrimeState(
                balance = 14000.0, maxBalance = 20000.0,
                percentage = 70.0, status = io.github.climbintelligence.data.model.WPrimeStatus.WORKING
            ),
            pacing = PacingTarget(
                targetPower = 230, rangeLow = 220, rangeHigh = 240,
                delta = 15, advice = io.github.climbintelligence.data.model.PacingAdvice.EASE_OFF
            ),
            climb = ClimbInfo(
                name = "Col du Galibier", category = 1, length = 8500.0,
                elevation = 585.0, avgGrade = 6.9, distanceToTop = 3200.0,
                elevationToTop = 220.0, progress = 0.62, isActive = true,
                isFromRoute = true
            )
        )
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
abstract class GlanceDataType(
    protected val climbExtension: ClimbIntelligenceExtension,
    typeId: String
) : DataTypeImpl("climbintelligence", typeId) {

    companion object {
        private const val TAG = "GlanceDataType"
        private const val VIEW_UPDATE_INTERVAL_MS = 1000L
    }

    private val glance = GlanceRemoteViews()

    @Composable
    protected abstract fun Content(state: ClimbDisplayState, config: ViewConfig)

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(
            DataPoint(dataTypeId = dataTypeId, values = emptyMap())
        ))
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        // Preview mode
        if (config.preview) {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val result = glance.compose(context, DpSize.Unspecified) {
                        Content(ClimbDisplayState.PREVIEW, config)
                    }
                    emitter.updateView(result.remoteViews)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "[$dataTypeId] Preview error: ${e.message}", e)
                }
            }
            emitter.setCancellable { }
            return
        }

        // Live mode
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // Initial render
        scope.launch {
            try {
                val displayState = collectDisplayState()
                val result = glance.compose(context, DpSize.Unspecified) {
                    Content(displayState, config)
                }
                emitter.updateView(result.remoteViews)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[$dataTypeId] Initial render failed: ${e.message}", e)
            }
        }

        // Fixed-rate 1Hz updates
        scope.launch {
            var nextUpdateTime = System.currentTimeMillis() + VIEW_UPDATE_INTERVAL_MS
            while (isActive) {
                val now = System.currentTimeMillis()
                val delayMs = nextUpdateTime - now
                if (delayMs > 0) delay(delayMs)

                nextUpdateTime += VIEW_UPDATE_INTERVAL_MS
                val currentTime = System.currentTimeMillis()
                if (nextUpdateTime < currentTime) {
                    nextUpdateTime = currentTime + VIEW_UPDATE_INTERVAL_MS
                }

                try {
                    val displayState = collectDisplayState()
                    val result = glance.compose(context, DpSize.Unspecified) {
                        Content(displayState, config)
                    }
                    emitter.updateView(result.remoteViews)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.w(TAG, "[$dataTypeId] Update error: ${t.message}")
                }
            }
        }

        emitter.setCancellable {
            scope.cancel()
        }
    }

    private fun collectDisplayState(): ClimbDisplayState {
        val climb = climbExtension.climbDataService.activeClimb.value
            ?: climbExtension.climbDetector.detectedClimb.value

        val insight = if (climb != null && climb.isActive && climb.segments.isNotEmpty()) {
            try {
                climbExtension.tacticalAnalyzer.getPrimaryInsight(climb, climb.progress)
            } catch (_: Exception) {
                null
            }
        } else null

        return ClimbDisplayState(
            live = climbExtension.climbDataService.liveState.value,
            wPrime = climbExtension.wPrimeEngine.state.value,
            pacing = climbExtension.pacingCalculator.target.value,
            climb = climb,
            prComparison = climbExtension.prComparisonEngine.comparison.value,
            tacticalInsight = insight
        )
    }
}
