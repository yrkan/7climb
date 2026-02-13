package io.github.climbintelligence.engine

import io.github.climbintelligence.data.ClimbRepository
import io.github.climbintelligence.data.model.ClimbInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PRComparison(
    val hasPR: Boolean = false,
    val prTimeMs: Long = 0L,
    val currentTimeMs: Long = 0L,
    val deltaMs: Long = 0L,
    val isAhead: Boolean = false
) {
    val deltaFormatted: String
        get() {
            val absDelta = kotlin.math.abs(deltaMs) / 1000
            val min = absDelta / 60
            val sec = absDelta % 60
            val sign = if (isAhead) "-" else "+"
            return if (min > 0) "$sign${min}m${sec}s" else "$sign${sec}s"
        }
}

class PRComparisonEngine(private val climbRepository: ClimbRepository) {

    private val _comparison = MutableStateFlow(PRComparison())
    val comparison: StateFlow<PRComparison> = _comparison.asStateFlow()

    suspend fun updateComparison(climbId: String, currentTimeMs: Long) {
        try {
            val prAttempt = climbRepository.getPR(climbId)
            if (prAttempt != null) {
                val deltaMs = currentTimeMs - prAttempt.timeMs
                _comparison.value = PRComparison(
                    hasPR = true,
                    prTimeMs = prAttempt.timeMs,
                    currentTimeMs = currentTimeMs,
                    deltaMs = deltaMs,
                    isAhead = deltaMs < 0
                )
            } else {
                _comparison.value = PRComparison()
            }
        } catch (e: Exception) {
            android.util.Log.w("PRComparison", "Failed to get PR: ${e.message}")
        }
    }

    fun reset() {
        _comparison.value = PRComparison()
    }
}
