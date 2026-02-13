package io.github.climbintelligence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.ClimbRepository
import io.github.climbintelligence.data.database.ClimbDatabase
import io.github.climbintelligence.datatypes.glance.GradeGlanceDataType
import io.github.climbintelligence.datatypes.glance.WPrimeGlanceDataType
import io.github.climbintelligence.datatypes.glance.PacingGlanceDataType
import io.github.climbintelligence.datatypes.glance.ClimbOverviewGlanceDataType
import io.github.climbintelligence.datatypes.glance.ETAGlanceDataType
import io.github.climbintelligence.datatypes.glance.ClimbProgressGlanceDataType
import io.github.climbintelligence.datatypes.glance.ClimbProfileGlanceDataType
import io.github.climbintelligence.datatypes.glance.NextSegmentGlanceDataType
import io.github.climbintelligence.datatypes.glance.CompactClimbGlanceDataType
import io.github.climbintelligence.datatypes.fit.ClimbFitRecording
import io.github.climbintelligence.engine.ClimbDataService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.github.climbintelligence.engine.WPrimeEngine
import io.github.climbintelligence.engine.PacingCalculator
import io.github.climbintelligence.engine.ClimbDetector
import io.github.climbintelligence.engine.AlertManager
import io.github.climbintelligence.engine.RideStateMonitor
import io.github.climbintelligence.engine.PRComparisonEngine
import io.github.climbintelligence.engine.CheckpointManager
import io.github.climbintelligence.engine.TacticalAnalyzer
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClimbIntelligenceExtension : KarooExtension("climbintelligence", BuildConfig.VERSION_NAME) {

    companion object {
        private const val TAG = "ClimbIntExtension"
        private const val NOTIFICATION_CHANNEL_ID = "climb_intelligence_bg"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var instance: ClimbIntelligenceExtension? = null
            private set
    }

    lateinit var karooSystem: KarooSystemService
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var _preferencesRepository: PreferencesRepository? = null
    val preferencesRepository: PreferencesRepository
        get() = _preferencesRepository ?: throw IllegalStateException("PreferencesRepository not initialized")

    private var _climbDataService: ClimbDataService? = null
    val climbDataService: ClimbDataService
        get() = _climbDataService ?: throw IllegalStateException("ClimbDataService not initialized")

    private var _wPrimeEngine: WPrimeEngine? = null
    val wPrimeEngine: WPrimeEngine
        get() = _wPrimeEngine ?: throw IllegalStateException("WPrimeEngine not initialized")

    private var _pacingCalculator: PacingCalculator? = null
    val pacingCalculator: PacingCalculator
        get() = _pacingCalculator ?: throw IllegalStateException("PacingCalculator not initialized")

    private var _climbDetector: ClimbDetector? = null
    val climbDetector: ClimbDetector
        get() = _climbDetector ?: throw IllegalStateException("ClimbDetector not initialized")

    private var _alertManager: AlertManager? = null
    val alertManager: AlertManager
        get() = _alertManager ?: throw IllegalStateException("AlertManager not initialized")

    private var _rideStateMonitor: RideStateMonitor? = null
    val rideStateMonitor: RideStateMonitor
        get() = _rideStateMonitor ?: throw IllegalStateException("RideStateMonitor not initialized")

    private var _climbRepository: ClimbRepository? = null
    val climbRepository: ClimbRepository
        get() = _climbRepository ?: throw IllegalStateException("ClimbRepository not initialized")

    private var _prComparisonEngine: PRComparisonEngine? = null
    val prComparisonEngine: PRComparisonEngine
        get() = _prComparisonEngine ?: throw IllegalStateException("PRComparisonEngine not initialized")

    private var _checkpointManager: CheckpointManager? = null

    private var _tacticalAnalyzer: TacticalAnalyzer? = null
    val tacticalAnalyzer: TacticalAnalyzer
        get() = _tacticalAnalyzer ?: throw IllegalStateException("TacticalAnalyzer not initialized")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        startForegroundService()

        karooSystem = KarooSystemService(this)
        _preferencesRepository = PreferencesRepository(this)

        val database = ClimbDatabase.getInstance(this)

        _climbDataService = ClimbDataService(this)
        _wPrimeEngine = WPrimeEngine(preferencesRepository)
        _pacingCalculator = PacingCalculator(preferencesRepository)
        _climbDetector = ClimbDetector()
        _climbRepository = ClimbRepository(database.climbDao(), database.attemptDao())
        _prComparisonEngine = PRComparisonEngine(climbRepository)
        _checkpointManager = CheckpointManager(preferencesRepository)
        _tacticalAnalyzer = TacticalAnalyzer()
        _alertManager = AlertManager(this, preferencesRepository)
        _rideStateMonitor = RideStateMonitor(
            extension = this,
            climbRepository = climbRepository,
            checkpointManager = _checkpointManager
        )

        // Restore checkpoint if available
        serviceScope.launch(Dispatchers.IO) {
            try {
                _checkpointManager?.tryRestore(_wPrimeEngine, _climbDataService)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to restore checkpoint: ${e.message}")
            }
        }

        karooSystem.connect { connected ->
            _isConnected.value = connected
            if (connected) {
                android.util.Log.i(TAG, "KarooSystemService connected")
                _climbDataService?.startStreaming()
                _rideStateMonitor?.startMonitoring()

                // Wire data flow: ClimbDataService -> WPrimeEngine, PacingCalculator, ClimbDetector
                serviceScope.launch {
                    climbDataService.liveState.collect { state ->
                        _wPrimeEngine?.update(state)
                        _pacingCalculator?.update(state, _climbDataService?.activeClimb?.value)
                        _climbDetector?.update(state)
                    }
                }
            } else {
                android.util.Log.w(TAG, "KarooSystemService disconnected")
                _climbDataService?.stopStreaming()
                _rideStateMonitor?.stopMonitoring()
                _alertManager?.stopMonitoring()
            }
        }
    }

    private fun startForegroundService() {
        try {
            createNotificationChannel()
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_climb)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy called")

        // Emergency checkpoint save
        if (_rideStateMonitor?.isRecording() == true) {
            _checkpointManager?.emergencySave(_wPrimeEngine, _climbDataService)
        }

        instance = null
        _isConnected.value = false

        _alertManager?.destroy()
        _alertManager = null

        _rideStateMonitor?.destroy()
        _rideStateMonitor = null

        _checkpointManager = null
        _tacticalAnalyzer = null
        _prComparisonEngine = null
        _climbRepository = null
        _climbDetector = null
        _pacingCalculator = null
        _wPrimeEngine = null

        _climbDataService?.destroy()
        _climbDataService = null

        _preferencesRepository = null

        serviceScope.cancel()

        try {
            karooSystem.disconnect()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error disconnecting: ${e.message}")
        }

        super.onDestroy()
    }

    override val types by lazy {
        listOf(
            WPrimeGlanceDataType(this),
            PacingGlanceDataType(this),
            GradeGlanceDataType(this),
            ClimbOverviewGlanceDataType(this),
            ETAGlanceDataType(this),
            ClimbProgressGlanceDataType(this),
            ClimbProfileGlanceDataType(this),
            NextSegmentGlanceDataType(this),
            CompactClimbGlanceDataType(this)
        )
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        val fitConsumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.ELAPSED_TIME)
        ) { event: OnStreamState ->
            if (event.state is StreamState.Streaming) {
                try {
                    val wPrimeState = _wPrimeEngine?.state?.value ?: return@addConsumer
                    val pacingTarget = _pacingCalculator?.target?.value ?: return@addConsumer
                    val prComparison = _prComparisonEngine?.comparison?.value

                    emitter.onNext(
                        ClimbFitRecording.buildRecordValues(
                            wPrimeBalance = wPrimeState.balance,
                            wPrimePercent = wPrimeState.percentage,
                            pacingAdviceOrdinal = pacingTarget.advice.ordinal,
                            targetPower = pacingTarget.targetPower,
                            prDeltaMs = prComparison?.deltaMs,
                            hasPR = prComparison?.hasPR == true
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "FIT record error: ${e.message}")
                }
            }
        }

        emitter.setCancellable {
            karooSystem.removeConsumer(fitConsumerId)
        }
    }

    override fun onBonusAction(actionId: String) {
        android.util.Log.i(TAG, "Bonus action: $actionId")

        when (actionId) {
            "toggle-view" -> {
                // Feedback beep
                playBeep(listOf(PlayBeepPattern.Tone(frequency = 1000, durationMs = 50)))
            }

            "cycle-pacing" -> {
                serviceScope.launch {
                    try {
                        val currentMode = _pacingCalculator?.target?.value?.mode
                            ?: io.github.climbintelligence.data.model.PacingMode.STEADY
                        val modes = io.github.climbintelligence.data.model.PacingMode.entries
                        val nextIndex = (modes.indexOf(currentMode) + 1) % modes.size
                        val nextMode = modes[nextIndex]

                        _preferencesRepository?.updatePacingMode(nextMode)

                        karooSystem.dispatch(
                            InRideAlert(
                                id = "pacing-mode",
                                icon = R.drawable.ic_climb,
                                title = getString(R.string.action_pacing_mode_changed),
                                detail = nextMode.name,
                                autoDismissMs = 2000L,
                                backgroundColor = R.color.alert_bg,
                                textColor = R.color.alert_text
                            )
                        )

                        playBeep(listOf(
                            PlayBeepPattern.Tone(frequency = 1000, durationMs = 50),
                            PlayBeepPattern.Tone(frequency = null, durationMs = 50),
                            PlayBeepPattern.Tone(frequency = 1200, durationMs = 50)
                        ))
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to cycle pacing: ${e.message}")
                    }
                }
            }

            "reset-wprime" -> {
                _wPrimeEngine?.reset()

                try {
                    karooSystem.dispatch(
                        InRideAlert(
                            id = "wprime-reset",
                            icon = R.drawable.ic_climb,
                            title = getString(R.string.action_wprime_reset),
                            detail = getString(R.string.action_wprime_reset_detail),
                            autoDismissMs = 2000L,
                            backgroundColor = R.color.alert_bg,
                            textColor = R.color.alert_text
                        )
                    )

                    playBeep(listOf(
                        PlayBeepPattern.Tone(frequency = 800, durationMs = 80),
                        PlayBeepPattern.Tone(frequency = null, durationMs = 40),
                        PlayBeepPattern.Tone(frequency = 1000, durationMs = 80),
                        PlayBeepPattern.Tone(frequency = null, durationMs = 40),
                        PlayBeepPattern.Tone(frequency = 1200, durationMs = 120)
                    ))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to dispatch reset alert: ${e.message}")
                }
            }
        }
    }

    private fun playBeep(tones: List<PlayBeepPattern.Tone>) {
        try {
            karooSystem.dispatch(PlayBeepPattern(tones))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to play beep: ${e.message}")
        }
    }
}
