package io.github.climbintelligence.engine

import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.ClimbSegment
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.util.ElevationPolylineDecoder
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Volatile
    private var hasReceivedData = false

    // Cached route elevation profile points
    @Volatile
    private var routeElevationPoints: List<ElevationPolylineDecoder.ElevationPoint> = emptyList()

    fun startStreaming() {
        android.util.Log.i(TAG, "Starting data stream subscriptions")

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
                            emitState()
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
                            emitState()
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
                            emitState()
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
                            emitState()
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
                            emitState()
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
                            emitState()
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
                            updateActiveClimbFromRoute()
                            emitState()
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
                        values["lat"]?.let { currentLat.set(it) }
                        values["lng"]?.let { currentLon.set(it) }
                        emitState()
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start streaming: ${e.message}")
        }
    }

    // ── Navigation handling ──────────────────────────────────────────────

    private fun handleNavigationState(state: OnNavigationState.NavigationState) {
        when (state) {
            is OnNavigationState.NavigationState.NavigatingRoute -> {
                android.util.Log.i(TAG, "Route loaded: ${state.name}, ${state.climbs.size} climbs")
                _hasRoute.value = true

                // Decode elevation polyline for segment analysis
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

                // Convert Karoo climbs to our ClimbInfo model
                val climbs = state.climbs.mapIndexed { index, karooClimb ->
                    buildClimbInfo(index, karooClimb)
                }
                _routeClimbs.value = climbs

                // Set first upcoming climb as active if none is set
                if (_activeClimb.value == null && climbs.isNotEmpty()) {
                    val dist = currentDistance.get()
                    val upcoming = climbs.firstOrNull { dist < it.startDistance + it.length }
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
     * and updates activeClimb with live progress metrics.
     */
    private fun updateActiveClimbFromRoute() {
        val climbs = _routeClimbs.value
        if (climbs.isEmpty()) return

        val dist = currentDistance.get()

        // Find the climb we're currently on
        val onClimb = climbs.firstOrNull { climb ->
            dist >= climb.startDistance && dist < (climb.startDistance + climb.length)
        }

        if (onClimb != null) {
            val distOnClimb = dist - onClimb.startDistance
            val distToTop = onClimb.length - distOnClimb
            val progress = (distOnClimb / onClimb.length).coerceIn(0.0, 1.0)
            val elevToTop = onClimb.elevation * (distToTop / onClimb.length)

            _activeClimb.value = onClimb.copy(
                distanceToTop = distToTop,
                elevationToTop = elevToTop,
                progress = progress,
                isActive = true
            )
        } else {
            // Not on a climb — show next upcoming climb (if any)
            val next = climbs.firstOrNull { it.startDistance > dist }
            if (next != null) {
                _activeClimb.value = next.copy(
                    distanceToTop = next.length,
                    elevationToTop = next.elevation,
                    progress = 0.0,
                    isActive = false
                )
            } else if (_activeClimb.value?.isFromRoute == true) {
                // Past all route climbs
                _activeClimb.value = null
            }
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
        removeConsumer(powerConsumerId)
        removeConsumer(hrConsumerId)
        removeConsumer(cadenceConsumerId)
        removeConsumer(speedConsumerId)
        removeConsumer(elevationGainConsumerId)
        removeConsumer(gradeConsumerId)
        removeConsumer(distanceConsumerId)
        removeConsumer(locationConsumerId)
        removeConsumer(navigationConsumerId)
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
