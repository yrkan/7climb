package io.github.climbintelligence.engine

import io.github.climbintelligence.data.PreferencesRepository
import io.github.climbintelligence.data.model.LiveClimbState
import io.github.climbintelligence.data.model.Match
import io.github.climbintelligence.data.model.MatchBurnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchBurnEngine(preferencesRepository: PreferencesRepository) {

    private val _state = MutableStateFlow(MatchBurnState())
    val state: StateFlow<MatchBurnState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cp = 0

    // Match state machine
    private var inMatch = false
    private var matchStartSeconds = 0L
    private var matchPeakPower = 0
    private var matchKjAboveCp = 0.0
    private var belowCpCounter = 0
    private var matchEndElapsed = 0L
    private var totalMatches = 0
    private var totalKjAboveCp = 0.0
    private val recentMatches = mutableListOf<Match>()
    private var hasReceivedData = false
    private var elapsedSeconds = 0L

    init {
        scope.launch {
            preferencesRepository.athleteProfileFlow.collect { p ->
                cp = p.effectiveCp
            }
        }
    }

    fun update(state: LiveClimbState) {
        if (!state.hasData || cp <= 0) return

        hasReceivedData = true
        elapsedSeconds++
        val power = state.power.coerceAtLeast(0)

        if (power > cp) {
            if (!inMatch) {
                // Start new match
                inMatch = true
                matchStartSeconds = elapsedSeconds
                matchPeakPower = power
                matchKjAboveCp = 0.0
                belowCpCounter = 0
            }
            // Accumulate kJ above CP (1 sample = 1 second)
            val excessWatts = power - cp
            matchKjAboveCp += excessWatts / 1000.0
            matchPeakPower = maxOf(matchPeakPower, power)
            belowCpCounter = 0
        } else if (inMatch) {
            belowCpCounter++
            if (belowCpCounter >= 5) {
                // Finalize match (full effort window including grace period)
                val duration = (elapsedSeconds - matchStartSeconds).toInt().coerceAtLeast(1)
                val match = Match(
                    durationSeconds = duration,
                    peakPower = matchPeakPower,
                    kjAboveCp = matchKjAboveCp
                )
                recentMatches.add(match)
                if (recentMatches.size > 10) recentMatches.removeAt(0)
                totalMatches++
                totalKjAboveCp += matchKjAboveCp
                matchEndElapsed = elapsedSeconds
                inMatch = false
            }
        }

        val recoverySec = if (!inMatch && matchEndElapsed > 0) {
            (elapsedSeconds - matchEndElapsed).toInt()
        } else 0

        _state.value = MatchBurnState(
            totalMatches = totalMatches,
            activeMatch = inMatch,
            currentMatchDurationSeconds = if (inMatch) (elapsedSeconds - matchStartSeconds).toInt() else 0,
            currentMatchPeak = if (inMatch) matchPeakPower else 0,
            totalKjAboveCp = totalKjAboveCp + if (inMatch) matchKjAboveCp else 0.0,
            lastMatchRecoverySeconds = recoverySec,
            recentMatches = recentMatches.toList(),
            hasData = hasReceivedData
        )
    }

    fun reset() {
        inMatch = false
        matchStartSeconds = 0L
        matchPeakPower = 0
        matchKjAboveCp = 0.0
        belowCpCounter = 0
        matchEndElapsed = 0L
        totalMatches = 0
        totalKjAboveCp = 0.0
        recentMatches.clear()
        hasReceivedData = false
        elapsedSeconds = 0L
        _state.value = MatchBurnState()
    }
}
