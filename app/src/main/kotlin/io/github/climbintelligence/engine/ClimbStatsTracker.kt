package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.ClimbStats
import io.github.climbintelligence.data.model.LiveClimbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class ClimbStatsTracker(preferencesRepository: PreferencesRepository) {

    companion object {
        private const val VAM_WINDOW_MS = 60_000L
    }

    private val _state = MutableStateFlow(ClimbStats())
    val state: StateFlow<ClimbStats> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var weight: Double = 0.0
    private var profileLoaded = false

    // Tracking state
    private var currentClimbId: String? = null
    private var climbStartTime: Long = 0L
    private var climbStartAltitude: Double = 0.0
    private var lastUpdateTime: Long = 0L

    // Rolling VAM window
    private val altitudeWindow = ArrayDeque<Pair<Long, Double>>()

    // Accumulators
    private var totalEnergyJoules: Double = 0.0
    private var powerSum: Long = 0
    private var powerCount: Int = 0
    private var maxPower: Int = 0
    private var hrSum: Long = 0
    private var hrCount: Int = 0
    private var maxHR: Int = 0
    private var cadenceSum: Long = 0
    private var cadenceCount: Int = 0
    private var wKgSum: Double = 0.0
    private var wKgCount: Int = 0

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { profile ->
                if (profile.isConfigured) {
                    weight = profile.weight
                    profileLoaded = true
                }
            }
        }
    }

    fun update(state: LiveClimbState, climb: ClimbInfo?) {
        if (!profileLoaded || weight <= 0) return
        if (!state.hasData) return

        val now = state.timestamp
        if (now == 0L) return

        // Instant W/kg (always computed, even without active climb)
        val instantWKg = if (state.power > 0) state.power.toDouble() / weight else 0.0

        // Handle climb tracking
        val activeClimbId = if (climb != null && climb.isActive) climb.id else null

        // Reset on climb change
        if (activeClimbId != currentClimbId) {
            resetAccumulators()
            currentClimbId = activeClimbId
            if (activeClimbId != null) {
                climbStartTime = now
                climbStartAltitude = state.altitude
                altitudeWindow.addLast(now to state.altitude)
            }
            lastUpdateTime = now
        }

        val isTracking = activeClimbId != null

        if (isTracking) {
            val dt = if (lastUpdateTime > 0) ((now - lastUpdateTime) / 1000.0).coerceIn(0.0, 5.0) else 0.0

            // Energy accumulation
            if (dt > 0 && state.power > 0) {
                totalEnergyJoules += state.power.toDouble() * dt
            }

            // Power stats
            if (state.power > 0) {
                powerSum += state.power
                powerCount++
                if (state.power > maxPower) maxPower = state.power
            }

            // HR stats
            if (state.heartRate > 0) {
                hrSum += state.heartRate
                hrCount++
                if (state.heartRate > maxHR) maxHR = state.heartRate
            }

            // Cadence stats
            if (state.cadence > 0) {
                cadenceSum += state.cadence
                cadenceCount++
            }

            // W/kg stats
            if (instantWKg > 0) {
                wKgSum += instantWKg
                wKgCount++
            }

            // Rolling VAM window
            altitudeWindow.addLast(now to state.altitude)
            while (altitudeWindow.size > 1 && now - altitudeWindow.peekFirst()!!.first > VAM_WINDOW_MS) {
                altitudeWindow.pollFirst()
            }

            // Rolling VAM
            val rollingVam = if (altitudeWindow.size >= 2) {
                val first = altitudeWindow.peekFirst()!!
                val last = altitudeWindow.peekLast()!!
                val timeDelta = (last.first - first.first) / 1000.0
                if (timeDelta > 5.0) {
                    val altGain = (last.second - first.second).coerceAtLeast(0.0)
                    (altGain / timeDelta * 3600.0).toInt()
                } else 0
            } else 0

            // Overall VAM
            val elapsedSec = (now - climbStartTime) / 1000.0
            val overallVam = if (elapsedSec > 10.0) {
                val totalGain = (state.altitude - climbStartAltitude).coerceAtLeast(0.0)
                (totalGain / elapsedSec * 3600.0).toInt()
            } else 0

            val elapsed = ((now - climbStartTime) / 1000L).coerceAtLeast(0)

            _state.value = ClimbStats(
                vamRolling = rollingVam,
                vamOverall = overallVam,
                energyKj = totalEnergyJoules / 1000.0,
                elapsedSeconds = elapsed,
                avgPower = if (powerCount > 0) (powerSum / powerCount).toInt() else 0,
                maxPower = maxPower,
                avgHR = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
                maxHR = maxHR,
                avgCadence = if (cadenceCount > 0) (cadenceSum / cadenceCount).toInt() else 0,
                wKg = instantWKg,
                avgWKg = if (wKgCount > 0) wKgSum / wKgCount else 0.0,
                isTracking = true
            )
        } else {
            _state.value = ClimbStats(
                wKg = instantWKg,
                isTracking = false
            )
        }

        lastUpdateTime = now
    }

    private fun resetAccumulators() {
        altitudeWindow.clear()
        totalEnergyJoules = 0.0
        powerSum = 0
        powerCount = 0
        maxPower = 0
        hrSum = 0
        hrCount = 0
        maxHR = 0
        cadenceSum = 0
        cadenceCount = 0
        wKgSum = 0.0
        wKgCount = 0
        climbStartTime = 0L
        climbStartAltitude = 0.0
        lastUpdateTime = 0L
    }
}
