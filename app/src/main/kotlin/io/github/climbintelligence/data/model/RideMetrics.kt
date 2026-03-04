package io.github.climbintelligence.data.model

data class RideMetrics(
    val normalizedPower: Int = 0,
    val intensityFactor: Double = 0.0,
    val trainingStressScore: Double = 0.0,
    val variabilityIndex: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val avgPower: Int = 0,
    val totalKj: Double = 0.0,
    val powerZones: IntArray = IntArray(7),
    val currentZone: Int = -1,
    val hasData: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RideMetrics) return false
        return normalizedPower == other.normalizedPower &&
                intensityFactor == other.intensityFactor &&
                trainingStressScore == other.trainingStressScore &&
                variabilityIndex == other.variabilityIndex &&
                elapsedSeconds == other.elapsedSeconds &&
                avgPower == other.avgPower &&
                totalKj == other.totalKj &&
                powerZones.contentEquals(other.powerZones) &&
                currentZone == other.currentZone &&
                hasData == other.hasData
    }

    override fun hashCode(): Int {
        var result = normalizedPower
        result = 31 * result + intensityFactor.hashCode()
        result = 31 * result + trainingStressScore.hashCode()
        result = 31 * result + variabilityIndex.hashCode()
        result = 31 * result + elapsedSeconds.hashCode()
        result = 31 * result + avgPower
        result = 31 * result + totalKj.hashCode()
        result = 31 * result + powerZones.contentHashCode()
        result = 31 * result + currentZone
        result = 31 * result + hasData.hashCode()
        return result
    }
}
