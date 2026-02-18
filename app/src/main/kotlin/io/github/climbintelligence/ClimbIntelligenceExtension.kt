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
import io.github.climbintelligence.datatypes.glance.ClimbStatsGlanceDataType
import io.github.climbintelligence.datatypes.fit.ClimbFitRecording
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.engine.ClimbDataService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.github.climbintelligence.engine.ClimbStatsTracker
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
import kotlinx.coroutines.Job
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

    private var _climbStatsTracker: ClimbStatsTracker? = null
    val climbStatsTracker: ClimbStatsTracker
        get() = _climbStatsTracker ?: throw IllegalStateException("ClimbStatsTracker not initialized")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Jobs launched on Karoo connection — cancelled on disconnect to prevent duplicates */
    private val connectionJobs = mutableListOf<Job>()

    /** Climb IDs already saved this ride — prevents double-saves between climb-end and ride-end */
    private val savedClimbIds = mutableSetOf<String>()

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
        _checkpointManager = CheckpointManager(preferencesRepository, this)
        _tacticalAnalyzer = TacticalAnalyzer()
        _climbStatsTracker = ClimbStatsTracker(preferencesRepository)
        _alertManager = AlertManager(this, preferencesRepository)
        _rideStateMonitor = RideStateMonitor(
            extension = this,
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

                // Cancel any leftover jobs from a previous connection cycle
                connectionJobs.forEach { it.cancel() }
                connectionJobs.clear()

                _climbDataService?.startStreaming()
                _rideStateMonitor?.startMonitoring()

                // Wire detection settings to ClimbDetector
                connectionJobs += serviceScope.launch {
                    preferencesRepository.detectionSettingsFlow.collect { settings ->
                        _climbDetector?.updateSettings(settings)
                    }
                }

                // Wire data flow: ClimbDataService -> WPrimeEngine, PacingCalculator, ClimbDetector
                connectionJobs += serviceScope.launch {
                    climbDataService.liveState.collect { state ->
                        _wPrimeEngine?.update(state)
                        _pacingCalculator?.update(state, _climbDataService?.activeClimb?.value)
                        _climbDetector?.update(state)
                        _climbStatsTracker?.update(state, _climbDataService?.activeClimb?.value)
                    }
                }

                // Wire ClimbDetector → "Climb Started" alert
                connectionJobs += serviceScope.launch {
                    var wasConfirmed = false
                    climbDetector.detectionState.collect { state ->
                        if (state == ClimbDetector.DetectionState.CONFIRMED_CLIMB && !wasConfirmed) {
                            wasConfirmed = true
                            val climb = _climbDetector?.detectedClimb?.value ?: return@collect
                            _alertManager?.dispatchClimbStarted(
                                name = climb.name,
                                lengthKm = climb.distanceClimbedKm,
                                avgGrade = climb.avgGrade,
                                elevationM = climb.elevation
                            )
                        } else if (state == ClimbDetector.DetectionState.NOT_CLIMBING) {
                            wasConfirmed = false
                        }
                    }
                }

                // Wire active climb → "Climb Started" + "Summit Approaching" alerts (route climbs)
                connectionJobs += serviceScope.launch {
                    var climbStartFired = false
                    var summitAlertFired = false
                    var lastClimbId = ""
                    climbDataService.activeClimb.collect { climb ->
                        if (climb == null || !climb.isActive || !climb.hasRouteMetrics) {
                            if (climb?.id != lastClimbId) {
                                climbStartFired = false
                                summitAlertFired = false
                                lastClimbId = climb?.id ?: ""
                            }
                            return@collect
                        }
                        if (climb.id != lastClimbId) {
                            climbStartFired = false
                            summitAlertFired = false
                            lastClimbId = climb.id
                        }
                        // Fire "Climb Started" when rider enters a route climb
                        if (!climbStartFired) {
                            climbStartFired = true
                            _alertManager?.dispatchClimbStarted(
                                name = climb.name,
                                lengthKm = climb.length / 1000.0,
                                avgGrade = climb.avgGrade,
                                elevationM = climb.elevation
                            )
                        }
                        if (!summitAlertFired && climb.distanceToTop in 50.0..500.0) {
                            summitAlertFired = true
                            _alertManager?.dispatchSummitApproaching(climb.distanceToTop.toInt())
                        }
                    }
                }

                // Wire detected climbs → ClimbDataService.activeClimb (when no route loaded)
                connectionJobs += serviceScope.launch {
                    climbDetector.detectedClimb.collect { detected ->
                        if (!climbDataService.hasRoute.value) {
                            climbDataService.updateActiveClimb(detected)
                        }
                    }
                }

                // Wire climb end → save attempt + PR alert
                connectionJobs += serviceScope.launch(Dispatchers.IO) {
                    var lastActiveClimbId: String? = null
                    var lastActiveClimb: ClimbInfo? = null
                    climbDataService.activeClimb.collect { climb ->
                        val currentId = if (climb?.isActive == true) climb.id else null
                        if (lastActiveClimbId != null && currentId != lastActiveClimbId) {
                            lastActiveClimb?.let { saveClimbAndCheckPR(it) }
                        }
                        lastActiveClimbId = currentId
                        if (climb?.isActive == true) lastActiveClimb = climb
                    }
                }

                // Wire live PR comparison for route climbs (throttled, every 5s)
                connectionJobs += serviceScope.launch(Dispatchers.IO) {
                    var lastPRClimbId = ""
                    var lastPRUpdateTime = 0L
                    climbDataService.activeClimb.collect { climb ->
                        if (climb != null && climb.isActive && climb.isFromRoute) {
                            if (climb.id != lastPRClimbId) {
                                lastPRClimbId = climb.id
                                _prComparisonEngine?.reset()
                                lastPRUpdateTime = 0
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastPRUpdateTime >= 5000) {
                                lastPRUpdateTime = now
                                val elapsed = _climbStatsTracker?.state?.value?.elapsedSeconds ?: 0
                                if (elapsed > 0) {
                                    _prComparisonEngine?.updateComparison(climb.id, elapsed * 1000L)
                                }
                            }
                        } else if (lastPRClimbId.isNotEmpty()) {
                            lastPRClimbId = ""
                            _prComparisonEngine?.reset()
                        }
                    }
                }
            } else {
                android.util.Log.w(TAG, "KarooSystemService disconnected")

                // Cancel all connection-scoped Flow collectors to prevent duplicates on reconnect
                connectionJobs.forEach { it.cancel() }
                connectionJobs.clear()

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
        _climbStatsTracker = null
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
            CompactClimbGlanceDataType(this),
            ClimbStatsGlanceDataType(this)
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

    internal suspend fun saveClimbAndCheckPR(climb: ClimbInfo) {
        synchronized(savedClimbIds) {
            if (climb.id in savedClimbIds) return
            savedClimbIds.add(climb.id)
        }

        try {
            val stats = _climbStatsTracker?.state?.value ?: return
            if (stats.elapsedSeconds < 30) return

            val result = _climbRepository?.saveAttempt(
                climbId = climb.id,
                climbName = climb.name,
                latitude = climb.startLatitude,
                longitude = climb.startLongitude,
                length = climb.length,
                elevation = climb.elevation,
                avgGrade = climb.avgGrade,
                timeMs = stats.elapsedSeconds * 1000L,
                avgPower = stats.avgPower,
                avgHR = stats.avgHR
            ) ?: return

            android.util.Log.i(TAG, "Saved climb: ${climb.name}, PR=${result.isPR}")

            if (result.isPR && result.improvedByMs > 0) {
                val delta = formatTimeDelta(result.improvedByMs)
                _alertManager?.dispatchPR(climb.name, delta)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save climb: ${e.message}")
        }
    }

    internal fun clearSavedClimbIds() {
        synchronized(savedClimbIds) { savedClimbIds.clear() }
    }

    private fun formatTimeDelta(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "${min}m${sec}s" else "${sec}s"
    }
}
