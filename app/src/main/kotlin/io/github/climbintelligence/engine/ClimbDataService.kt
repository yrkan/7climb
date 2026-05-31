package io.github.climbintelligence.engine

import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.ClimbSegment
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.NextClimbInfo
import io.github.climbintelligence.util.ElevationPolylineDecoder
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class ClimbDataService(private val climbExtension: ClimbIntelligenceExtension) {

    companion object {
        private const val TAG = "ClimbDataService"
    }

    private val _liveState = MutableStateFlow(LiveClimbState())
    val liveState: StateFlow<LiveClimbState> = _liveState.asStateFlow()

    private val _activeClimb = MutableStateFlow<ClimbInfo?>(null)
    val activeClimb: StateFlow<ClimbInfo?> = _activeClimb.asStateFlow()

    /** All climbs on the current route, parsed from NavigationState */
    private val _routeClimbs = MutableStateFlow<List<ClimbInfo>>(emptyList())
    val routeClimbs: StateFlow<List<ClimbInfo>> = _routeClimbs.asStateFlow()

    /** Whether a route with climbs is currently loaded */
    private val _hasRoute = MutableStateFlow(false)
    val hasRoute: StateFlow<Boolean> = _hasRoute.asStateFlow()

    /** Next upcoming climb on the route */
    private val _nextClimb = MutableStateFlow(NextClimbInfo())
    val nextClimb: StateFlow<NextClimbInfo> = _nextClimb.asStateFlow()

    // Consumer IDs for cleanup
    private val powerConsumerId = AtomicReference<String?>(null)
    private val hrConsumerId = AtomicReference<String?>(null)
    private val cadenceConsumerId = AtomicReference<String?>(null)
    private val speedConsumerId = AtomicReference<String?>(null)
    private val elevationGainConsumerId = AtomicReference<String?>(null)
    private val gradeConsumerId = AtomicReference<String?>(null)
    private val distanceConsumerId = AtomicReference<String?>(null)
    private val locationConsumerId = AtomicReference<String?>(null)
    private val navigationConsumerId = AtomicReference<String?>(null)
    // Karoo CLIMB stream (karoo-ext 1.1.8+) — native Climber detection on routes + freestyle
    private val climbStreamConsumerId = AtomicReference<String?>(null)
    private val climbNumberConsumerId = AtomicReference<String?>(null)

    // Current values (thread-safe via AtomicReference)
    private val currentPower = AtomicReference(0)
    private val currentHR = AtomicReference(0)
    private val currentCadence = AtomicReference(0)
    private val currentSpeed = AtomicReference(0.0)
    private val currentAltitude = AtomicReference(0.0)
    private val currentGrade = AtomicReference(0.0)
    private val currentDistance = AtomicReference(0.0)
    private val currentLat = AtomicReference(0.0)
    private val currentLon = AtomicReference(0.0)
    private val lastLocationMs = AtomicReference(0L)  // wall-clock of the last GPS fix

    // CLIMB stream cached fields (last emitted values)
    private val currentClimbDistanceToTop = AtomicReference(0.0)
    private val currentClimbElevationToTop = AtomicReference(0.0)
    private val currentClimbDistanceFromBottom = AtomicReference(0.0)
    private val currentClimbElevationFromBottom = AtomicReference(0.0)
    private val currentClimbElevationTotal = AtomicReference(0.0)
    private val currentClimbNumber = AtomicReference(0)
    private val lastClimbStreamEmitMs = AtomicReference(0L)
    private val climbStreamStartTimestamp = AtomicReference(0L)
    // Last tick while the CLIMB stream was active — lets us tell a brief stream blip on
    // the SAME climb from a genuinely new climb when the stream re-activates.
    private val lastClimbActiveMs = AtomicReference(0L)
    private val lastClimbDistFromBottom = AtomicReference(0.0)
    // Previous CLIMB emit's identity/geometry — to detect the stale carry-over tick at a
    // climb switch (Karoo bumps climbNum before refreshing distance/elevation fields).
    private val lastEmitClimbNum = AtomicReference(-1)
    private val lastEmitDistToTop = AtomicReference(-1.0)
    private val lastEmitDistFromBottom = AtomicReference(-1.0)

    /** True while the Karoo CLIMB stream is emitting on an active climb. */
    private val _climbStreamActive = MutableStateFlow(false)
    val climbStreamActive: StateFlow<Boolean> = _climbStreamActive.asStateFlow()

    @Volatile
    private var hasReceivedData = false

    /** Set by sensor callbacks, cleared by 1Hz timer after emission.
     *  Prevents stale emissions when sensors are paused (e.g. ride pause). */
    @Volatile
    private var sensorUpdatedSinceLastEmit = false

    private var emitJob: Job? = null

    // Cached route elevation profile points
    @Volatile
    private var routeElevationPoints: List<ElevationPolylineDecoder.ElevationPoint> = emptyList()

    // Cached route coordinate geometry (lat/lng + cumulative distance) for mapping
    // a live GPS fix to a distance-along-route, independent of ride distance.
    @Volatile
    private var routeGeometry: List<ElevationPolylineDecoder.RoutePoint> = emptyList()

    // Last good distance-along-route (m). Held when the GPS fix is stale so we never
    // blend route-frame with ride-frame distance, and seeds the continuity window.
    private val lastRouteDistance = AtomicReference(0.0)

    // A GPS fix older than this is treated as "no fix" (hold last route distance).
    private val gpsFixMaxAgeMs = 5_000L
    // A CLIMB-stream re-activation with distanceFromBottom still continuing (not reset toward
    // a new base) is the SAME climb session — no re-alert. distanceFromBottom continuity is
    // the real discriminator; the gap is just a generous staleness bound.
    private val climbSessionContinuationGapMs = 300_000L
    private val climbSessionContinuationTolM = 200.0

    // routePolyline hash of the currently-latched route. Karoo re-emits NavigatingRoute
    // for the same route while progressively pruning the climb you're on and re-basing the
    // rest to your position; the polyline stays identical. We latch the full climb list on
    // the first populated emit and only rebuild when this signature changes (a real route
    // change) or on Idle. 0 = nothing latched.
    @Volatile
    private var loadedRouteSignature: Int = 0

    fun startStreaming() {
        android.util.Log.i(TAG, "Starting data stream subscriptions")
        emitJob?.cancel() // Defensive: prevent duplicate timers on reconnect

        try {
            // --- Navigation state subscription ---
            navigationConsumerId.set(
                climbExtension.karooSystem.addConsumer<OnNavigationState> { event ->
                    handleNavigationState(event.state)
                }
            )

            // --- Sensor data subscriptions ---
            powerConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.POWER)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.toInt()?.let { value ->
                            currentPower.set(value)
                            hasReceivedData = true
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            hrConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.HEART_RATE)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.toInt()?.let { value ->
                            currentHR.set(value)
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            cadenceConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.CADENCE)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.toInt()?.let { value ->
                            currentCadence.set(value)
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            speedConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.SPEED)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.let { value ->
                            currentSpeed.set(value)
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            elevationGainConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.ELEVATION_GAIN)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.let { value ->
                            currentAltitude.set(value)
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            gradeConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.ELEVATION_GRADE)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.let { value ->
                            currentGrade.set(value)
                            sensorUpdatedSinceLastEmit = true
                        }
                    }
                }
            )

            distanceConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.DISTANCE)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.singleValue?.let { value ->
                            currentDistance.set(value)
                            sensorUpdatedSinceLastEmit = true
                            updateActiveClimbFromRoute()
                        }
                    }
                }
            )

            locationConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.LOCATION)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        val values = state.dataPoint.values
                        values[DataType.Field.LOC_LATITUDE]?.let { currentLat.set(it) }
                        values[DataType.Field.LOC_LONGITUDE]?.let { currentLon.set(it) }
                        lastLocationMs.set(System.currentTimeMillis())
                        sensorUpdatedSinceLastEmit = true
                    }
                }
            )

            // --- Karoo CLIMB stream (1.1.8+) ---
            // Compound DataType carrying DISTANCE_FROM_BOTTOM, DISTANCE_TO_TOP,
            // ELEVATION_FROM_BOTTOM, ELEVATION_TO_TOP, CLIMB_ELEVATION — driven by
            // Karoo's native Climber detection on routes AND freestyle rides.
            climbStreamConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.CLIMB)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        val values = state.dataPoint.values
                        values[DataType.Field.DISTANCE_TO_TOP]?.let { currentClimbDistanceToTop.set(it) }
                        values[DataType.Field.DISTANCE_FROM_BOTTOM]?.let { currentClimbDistanceFromBottom.set(it) }
                        values[DataType.Field.ELEVATION_TO_TOP]?.let { currentClimbElevationToTop.set(it) }
                        values[DataType.Field.ELEVATION_FROM_BOTTOM]?.let { currentClimbElevationFromBottom.set(it) }
                        values[DataType.Field.CLIMB_ELEVATION]?.let { currentClimbElevationTotal.set(it) }
                        lastClimbStreamEmitMs.set(System.currentTimeMillis())

                        // Stale carry-over guard: at a climb switch Karoo flips climbNum
                        // BEFORE refreshing distanceToTop/FromBottom, so the first tick of the
                        // new climb still carries the previous climb's geometry. Acting on it
                        // alerts the old climb's tail (and fools the continuation check). Skip
                        // that tick — act only once the geometry refreshes for the new climb.
                        val emitClimbNum = currentClimbNumber.get()
                        val emitDToTop = currentClimbDistanceToTop.get()
                        val emitDFromBot = currentClimbDistanceFromBottom.get()
                        val staleTransition = emitClimbNum != lastEmitClimbNum.get() &&
                            emitDToTop == lastEmitDistToTop.get() &&
                            emitDFromBot == lastEmitDistFromBottom.get()
                        if (staleTransition) {
                            return@addConsumer
                        }

                        val onClimb = currentClimbDistanceToTop.get() > 0.0 ||
                                      currentClimbDistanceFromBottom.get() > 0.0
                        val wasActive = _climbStreamActive.value
                        _climbStreamActive.value = onClimb

                        if (onClimb && !wasActive) {
                            // Only start a NEW session (→ new climb-started alert) for a
                            // genuinely new climb. If the stream merely blipped and we're
                            // still up the SAME climb (distanceFromBottom continues, short
                            // gap), keep the existing session id so we don't re-alert.
                            val now = System.currentTimeMillis()
                            val gapMs = now - lastClimbActiveMs.get()
                            val dFromBot = currentClimbDistanceFromBottom.get()
                            val continuation = lastClimbActiveMs.get() > 0L &&
                                gapMs < climbSessionContinuationGapMs &&
                                dFromBot >= lastClimbDistFromBottom.get() - climbSessionContinuationTolM
                            if (continuation) {
                                android.util.Log.i(
                                    TAG,
                                    "CLIMB stream re-activated mid-climb (gap ${gapMs}ms," +
                                        " dFromBot ${dFromBot.toInt()}m) — continuation, keeping session"
                                )
                            } else {
                                climbStreamStartTimestamp.set(now)
                                android.util.Log.i(
                                    TAG,
                                    "CLIMB stream activated — top in ${currentClimbDistanceToTop.get().toInt()}m" +
                                        " (${currentClimbElevationToTop.get().toInt()}m elevation)"
                                )
                            }
                        } else if (!onClimb && wasActive) {
                            android.util.Log.i(TAG, "CLIMB stream ended")
                        }

                        if (onClimb) {
                            lastClimbActiveMs.set(System.currentTimeMillis())
                            lastClimbDistFromBottom.set(currentClimbDistanceFromBottom.get())
                        }

                        // Record this processed emit so the next switch can detect a stale tick.
                        lastEmitClimbNum.set(emitClimbNum)
                        lastEmitDistToTop.set(emitDToTop)
                        lastEmitDistFromBottom.set(emitDFromBot)

                        updateActiveClimbFromStream()
                        sensorUpdatedSinceLastEmit = true
                    }
                }
            )

            climbNumberConsumerId.set(
                climbExtension.karooSystem.addConsumer(
                    OnStreamState.StartStreaming(DataType.Type.CLIMB_NUMBER)
                ) { event: OnStreamState ->
                    val state = event.state
                    if (state is StreamState.Streaming) {
                        state.dataPoint.values[DataType.Field.CLIMB_NUMBER]?.toInt()?.let {
                            currentClimbNumber.set(it)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start streaming: ${e.message}")
        }

        // Single 1Hz emission timer — replaces per-callback emitState() calls.
        // Only emits when at least one sensor has fired since the last emission,
        // preventing stale data accumulation during ride pause.
        emitJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            while (isActive) {
                delay(1000)
                // Watchdog: deactivate CLIMB stream if no emit in the last 5s
                // (Karoo sometimes stops emitting without an explicit zero-out
                // when a climb ends; without this the activeClimb would stick).
                if (_climbStreamActive.value &&
                    (System.currentTimeMillis() - lastClimbStreamEmitMs.get()) > 5000L
                ) {
                    android.util.Log.i(TAG, "CLIMB stream watchdog: no emit in 5s — deactivating")
                    _climbStreamActive.value = false
                    updateActiveClimbFromStream()
                }
                if (hasReceivedData && sensorUpdatedSinceLastEmit) {
                    sensorUpdatedSinceLastEmit = false
                    emitState()
                }
            }
        }
    }

    // ── Navigation handling ──────────────────────────────────────────────

    private fun handleNavigationState(state: OnNavigationState.NavigationState) {
        when (state) {
            is OnNavigationState.NavigationState.NavigatingRoute -> {
                android.util.Log.i(TAG, "Route loaded: ${state.name}, ${state.climbs.size} climbs")
                _hasRoute.value = true

                // Only (re)decode geometry + reset the latched climb list when the route
                // genuinely changes — keyed on the routePolyline. Karoo re-emits the same
                // route repeatedly while pruning the active climb; the polyline is stable.
                val signature = state.routePolyline.hashCode()
                val isNewRoute = signature != loadedRouteSignature
                if (isNewRoute) {
                    loadedRouteSignature = signature
                    routeElevationPoints = state.routeElevationPolyline?.let { polyline ->
                        when (val result = ElevationPolylineDecoder.decodeSafe(polyline)) {
                            is ElevationPolylineDecoder.DecodeResult.Success -> {
                                android.util.Log.i(TAG, "Decoded ${result.points.size} elevation points")
                                ElevationPolylineDecoder.smooth(result.points)
                            }
                            is ElevationPolylineDecoder.DecodeResult.Error -> {
                                android.util.Log.w(TAG, "Elevation decode failed: ${result.message}")
                                emptyList()
                            }
                        }
                    } ?: emptyList()
                    routeGeometry = ElevationPolylineDecoder.buildRouteGeometry(state.routePolyline)
                    _routeClimbs.value = emptyList()
                }

                // Latch the FULL climb list from the first populated emit for this route, and
                // keep it: Karoo later drops the climb you're on and re-bases the rest to your
                // position, which would otherwise make the active climb un-matchable mid-ascent.
                if (_routeClimbs.value.isEmpty() && state.climbs.isNotEmpty()) {
                    _routeClimbs.value = state.climbs.mapIndexed { index, karooClimb ->
                        buildClimbInfo(index, karooClimb)
                    }
                }


                // Set first upcoming climb as active if none is set
                if (_activeClimb.value == null && _routeClimbs.value.isNotEmpty()) {
                    val dist = currentRouteDistance()
                    val upcoming = _routeClimbs.value.firstOrNull { dist < it.startDistance + it.length }
                    if (upcoming != null) {
                        _activeClimb.value = upcoming.copy(isActive = false)
                    }
                }
            }

            is OnNavigationState.NavigationState.NavigatingToDestination -> {
                _hasRoute.value = true
                routeElevationPoints = state.elevationPolyline?.let { polyline ->
                    when (val result = ElevationPolylineDecoder.decodeSafe(polyline)) {
                        is ElevationPolylineDecoder.DecodeResult.Success ->
                            ElevationPolylineDecoder.smooth(result.points)
                        is ElevationPolylineDecoder.DecodeResult.Error -> emptyList()
                    }
                } ?: emptyList()

                routeGeometry = ElevationPolylineDecoder.buildRouteGeometry(state.polyline)

                val climbs = state.climbs.mapIndexed { index, karooClimb ->
                    buildClimbInfo(index, karooClimb)
                }
                _routeClimbs.value = climbs
            }

            is OnNavigationState.NavigationState.Idle -> {
                android.util.Log.i(TAG, "Navigation idle — no route")
                _hasRoute.value = false
                _routeClimbs.value = emptyList()
                routeElevationPoints = emptyList()
                routeGeometry = emptyList()
                loadedRouteSignature = 0  // route unloaded — next load rebuilds + re-latches
                // Don't clear activeClimb — ClimbDetector may provide detected climbs
            }
        }
    }

    /**
     * Convert a Karoo NavigationState.Climb into our ClimbInfo model,
     * including segment breakdown from elevation polyline.
     */
    private fun buildClimbInfo(index: Int, karooClimb: OnNavigationState.NavigationState.Climb): ClimbInfo {
        val climbId = "route_${index}_${karooClimb.startDistance.toInt()}"

        // Extract segments from elevation profile if available
        val segments = if (routeElevationPoints.isNotEmpty()) {
            val climbProfile = ElevationPolylineDecoder.extractClimbProfile(
                routeElevationPoints,
                karooClimb.startDistance,
                karooClimb.length
            )
            ElevationPolylineDecoder.buildSegments(climbProfile, karooClimb.length)
                .map { seg ->
                    ClimbSegment(
                        startDistance = seg.startDistance,
                        endDistance = seg.endDistance,
                        grade = seg.grade,
                        length = seg.length,
                        elevation = seg.elevation
                    )
                }
        } else {
            // No polyline — create single segment from average data
            listOf(
                ClimbSegment(
                    startDistance = 0.0,
                    endDistance = karooClimb.length,
                    grade = karooClimb.grade,
                    length = karooClimb.length,
                    elevation = karooClimb.totalElevation
                )
            )
        }

        val maxGrade = segments.maxOfOrNull { it.grade } ?: karooClimb.grade

        val category = categorizeClimb(
            karooClimb.length,
            karooClimb.totalElevation,
            karooClimb.grade
        )

        // Generate descriptive name since Karoo doesn't provide climb names
        val catLabel = when (category) {
            1 -> "HC"; 2 -> "Cat 1"; 3 -> "Cat 2"; 4 -> "Cat 3"; else -> "Cat 4"
        }
        val lengthKm = "%.1fkm".format(karooClimb.length / 1000.0)
        val climbName = "$catLabel $lengthKm"

        return ClimbInfo(
            id = climbId,
            name = climbName,
            category = category,
            length = karooClimb.length,
            elevation = karooClimb.totalElevation,
            avgGrade = karooClimb.grade,
            maxGrade = maxGrade,
            segments = segments,
            distanceToTop = karooClimb.length,
            elevationToTop = karooClimb.totalElevation,
            progress = 0.0,
            isActive = false,
            isFromRoute = true,
            startDistance = karooClimb.startDistance
        )
    }

    /**
     * Called on every distance update — checks if rider is on a route climb
     * and updates activeClimb with live progress metrics. When the Karoo CLIMB
     * stream is firing (karoo-ext 1.1.8+), it owns activeClimb's progress fields
     * and this method skips that write; the nextClimb countdown stays computed
     * here from route position regardless of stream state.
     */
    /**
     * Rider's current distance along the loaded route (m), derived from the GPS
     * fix so it is independent of ride/recording distance — which desyncs when a
     * route is added mid-ride or the rider joins partway.
     *
     * Frame safety: with a route loaded we stay in route-distance space. If the GPS
     * fix is stale we HOLD the last good route-distance rather than blend in ride
     * distance (a different origin), which would otherwise jump the climb state by
     * kilometres. With no route loaded there are no route climbs to match, so ride
     * distance is the harmless legacy default.
     */
    private fun currentRouteDistance(): Double {
        if (routeGeometry.isEmpty()) return currentDistance.get()

        val lat = currentLat.get()
        val lon = currentLon.get()
        val fixAgeMs = System.currentTimeMillis() - lastLocationMs.get()
        // No usable fix — stale, or the (0,0) "null island" sentinel Karoo emits before a
        // GPS lock. Hold the last good route-distance rather than map a bogus point, which
        // otherwise snaps to an arbitrary far vertex and matches a wrong/phantom climb.
        if (fixAgeMs > gpsFixMaxAgeMs || (lat == 0.0 && lon == 0.0)) return lastRouteDistance.get()

        val prior = lastRouteDistance.get().takeIf { it > 0.0 }  // continuity hint after first match
        val matched = ElevationPolylineDecoder.nearestDistanceAlong(routeGeometry, lat, lon, prior)
        val offRoute = matched == null
        // Off-route (GPS farther than maxSnapM from the route) → hold the last good
        // route-distance, don't corrupt it with a far snap. We re-acquire (global nearest)
        // automatically once back within range.
        if (matched != null) lastRouteDistance.set(matched)
        val routeDist = lastRouteDistance.get()
        return routeDist
    }

    private fun updateActiveClimbFromRoute() {
        val climbs = _routeClimbs.value
        if (climbs.isEmpty()) return

        val dist = currentRouteDistance()
        val streamOwnsActiveClimb = _climbStreamActive.value

        // Find the climb we're currently on
        val onClimb = climbs.firstOrNull { climb ->
            dist >= climb.startDistance && dist < (climb.startDistance + climb.length)
        }

        if (onClimb != null) {
            // CLIMBER-led detection: the route path no longer marks a climb active on
            // its own. CLIMBER (the CLIMB stream) is the sole detector; GPS is used only
            // to identify WHICH route climb we're on — which updateActiveClimbFromStream
            // does (matching this same GPS route-distance to a routeClimb) so it can
            // attach the route's polyline/segments + metadata once CLIMBER has fired.
            // Activating here would fire the climb-started alert before CLIMBER detects,
            // then again when it does (the double-alert observed on a mid-climb join).

            // While on a climb, look for the next one after this climb
            val nextAfterCurrent = climbs.firstOrNull { it.startDistance > onClimb.startDistance + onClimb.length }
            if (nextAfterCurrent != null) {
                val distToNext = (nextAfterCurrent.startDistance - dist).coerceAtLeast(0.0)
                val speed = currentSpeed.get()
                val eta = if (speed > 0.5) (distToNext / speed).toLong() else 0L
                _nextClimb.value = NextClimbInfo(
                    distanceToStart = distToNext,
                    etaSeconds = eta,
                    climbName = nextAfterCurrent.name,
                    climbCategory = nextAfterCurrent.category,
                    climbLength = nextAfterCurrent.length,
                    climbElevation = nextAfterCurrent.elevation,
                    climbAvgGrade = nextAfterCurrent.avgGrade,
                    hasNext = true
                )
            } else {
                _nextClimb.value = NextClimbInfo()
            }
        } else {
            // Not on a climb — show next upcoming climb (if any)
            val next = climbs.firstOrNull { it.startDistance > dist }
            if (next != null) {
                if (!streamOwnsActiveClimb) {
                    _activeClimb.value = next.copy(
                        distanceToTop = next.length,
                        elevationToTop = next.elevation,
                        progress = 0.0,
                        isActive = false
                    )
                }

                // Update next climb countdown
                val distToNext = (next.startDistance - dist).coerceAtLeast(0.0)
                val speed = currentSpeed.get()
                val eta = if (speed > 0.5) (distToNext / speed).toLong() else 0L
                _nextClimb.value = NextClimbInfo(
                    distanceToStart = distToNext,
                    etaSeconds = eta,
                    climbName = next.name,
                    climbCategory = next.category,
                    climbLength = next.length,
                    climbElevation = next.elevation,
                    climbAvgGrade = next.avgGrade,
                    hasNext = true
                )
            } else {
                if (!streamOwnsActiveClimb && _activeClimb.value?.isFromRoute == true) {
                    // Past all route climbs
                    _activeClimb.value = null
                }
                _nextClimb.value = NextClimbInfo()
            }
        }
    }

    /**
     * Build _activeClimb from the Karoo CLIMB stream cached fields. Called from
     * the CLIMB consumer callback (karoo-ext 1.1.8+). When a route climb is
     * underfoot, route metadata (name, category, segments) is overlaid with the
     * stream's authoritative distanceToTop / elevationToTop / progress.
     * Otherwise a stream-only ClimbInfo is synthesized — no segments means
     * Layer 2 (route strategy) and Layer 3 (tactical analyzer) won't fire,
     * but Layer 1 pacing target still works off the median grade.
     */
    private fun updateActiveClimbFromStream() {
        if (!_climbStreamActive.value) {
            // Stream just deactivated — clear stream-driven climb (route-only
            // climbs are managed by updateActiveClimbFromRoute).
            val current = _activeClimb.value
            if (current != null && !current.isFromRoute) {
                _activeClimb.value = null
            }
            return
        }

        val distToTop = currentClimbDistanceToTop.get()
        val distFromBottom = currentClimbDistanceFromBottom.get()
        val elevToTop = currentClimbElevationToTop.get()
        val elevFromBottom = currentClimbElevationFromBottom.get()
        val climbElevationTotal = currentClimbElevationTotal.get()
        val totalLength = distToTop + distFromBottom
        // Total ascent = remaining-to-top + done-from-bottom (conserved across the
        // climb). CLIMB_ELEVATION is NOT total ascent — on-device trace showed it
        // reads ~237 m and grows with elevFromBottom, while eToTop+eFromBot = ~407 m
        // matched Karoo's own Climber drawer. So we do not use CLIMB_ELEVATION here.
        val totalElevation = elevToTop + elevFromBottom
        val progress = if (totalLength > 0.0)
            (distFromBottom / totalLength).coerceIn(0.0, 1.0) else 0.0
        val avgGrade = if (totalLength > 0.0)
            (totalElevation / totalLength) * 100.0 else 0.0

        val dist = currentRouteDistance()
        val routeClimb = _routeClimbs.value.firstOrNull { rc ->
            dist >= rc.startDistance && dist < (rc.startDistance + rc.length)
        }

        _activeClimb.value = if (routeClimb != null) {
            // Route climb metadata + stream's authoritative progress AND totals.
            // The CLIMB_ELEVATION-derived totalElevation is ground truth; the route
            // summary's NavigationState.Climb.totalElevation can be badly understated
            // (e.g. 300 m on a ~1200 m climb), which previously leaked a wrong
            // elevation into the climb-started alert. Override length/elevation too.
            routeClimb.copy(
                length = totalLength,
                elevation = totalElevation,
                distanceToTop = distToTop,
                elevationToTop = elevToTop,
                progress = progress,
                isActive = true
            )
        } else {
            // No route — synthesize from stream alone (no segments / category)
            val startTs = climbStreamStartTimestamp.get()
                .takeIf { it > 0 } ?: System.currentTimeMillis()
            val lengthKm = "%.1f km".format(totalLength / 1000.0)
            ClimbInfo(
                // Id keyed on the stream-session start (startTs), NOT climbNum: Karoo flips
                // climbNum at onset and re-detects within one continuous CLIMBER session
                // (e.g. a 1.1 km climb growing into an 8 km one). The climb-started alert
                // gate keys on id, so a session-stable id => one alert per session. A real
                // new session (stream re-activates) gets a fresh startTs and re-alerts.
                id = "karoo_climb_${startTs}",
                name = "Climb ($lengthKm)",
                category = 0,
                length = totalLength,
                elevation = totalElevation,
                avgGrade = avgGrade,
                maxGrade = avgGrade,
                segments = emptyList(),
                distanceToTop = distToTop,
                elevationToTop = elevToTop,
                progress = progress,
                isActive = true,
                isFromRoute = false,
                startTimestamp = startTs
            )
        }
    }

    /**
     * Categorize a climb by difficulty: 1=HC, 2=Cat1, 3=Cat2, 4=Cat3, 5=Cat4
     */
    private fun categorizeClimb(length: Double, elevation: Double, grade: Double): Int {
        val score = elevation * grade // Simple climb score
        return when {
            score > 8000 || (elevation > 1000 && grade > 7) -> 1   // HC
            score > 4000 || (elevation > 600 && grade > 6)  -> 2   // Cat 1
            score > 2000 || (elevation > 400 && grade > 5)  -> 3   // Cat 2
            score > 1000 || (elevation > 200 && grade > 4)  -> 4   // Cat 3
            else                                             -> 5   // Cat 4
        }
    }

    // ── State emission ───────────────────────────────────────────────────

    private fun emitState() {
        _liveState.value = LiveClimbState(
            power = currentPower.get(),
            heartRate = currentHR.get(),
            cadence = currentCadence.get(),
            speed = currentSpeed.get(),
            altitude = currentAltitude.get(),
            grade = currentGrade.get(),
            distance = currentDistance.get(),
            latitude = currentLat.get(),
            longitude = currentLon.get(),
            timestamp = System.currentTimeMillis(),
            hasData = hasReceivedData
        )
    }

    fun updateActiveClimb(climb: ClimbInfo?) {
        _activeClimb.value = climb
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    fun stopStreaming() {
        android.util.Log.i(TAG, "Stopping data stream subscriptions")
        emitJob?.cancel()
        emitJob = null
        removeConsumer(powerConsumerId)
        removeConsumer(hrConsumerId)
        removeConsumer(cadenceConsumerId)
        removeConsumer(speedConsumerId)
        removeConsumer(elevationGainConsumerId)
        removeConsumer(gradeConsumerId)
        removeConsumer(distanceConsumerId)
        removeConsumer(locationConsumerId)
        removeConsumer(navigationConsumerId)
        removeConsumer(climbStreamConsumerId)
        removeConsumer(climbNumberConsumerId)
        _climbStreamActive.value = false
    }

    private fun removeConsumer(ref: AtomicReference<String?>) {
        ref.getAndSet(null)?.let { id ->
            try {
                climbExtension.karooSystem.removeConsumer(id)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to remove consumer: ${e.message}")
            }
        }
    }

    fun destroy() {
        stopStreaming()
    }
}
