package io.github.climbintelligence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val durationSeconds: Int = 0,
    val peakPower: Int = 0,
    val kjAboveCp: Double = 0.0
)

@Serializable
data class MatchBurnState(
    val totalMatches: Int = 0,
    val activeMatch: Boolean = false,
    val currentMatchDurationSeconds: Int = 0,
    val currentMatchPeak: Int = 0,
    val totalKjAboveCp: Double = 0.0,
    val lastMatchRecoverySeconds: Int = 0,
    val recentMatches: List<Match> = emptyList(),
    val hasData: Boolean = false
)
