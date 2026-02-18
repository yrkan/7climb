package io.github.climbintelligence.engine

import io.github.climbintelligence.data.model.ClimbInfo
import io.github.climbintelligence.data.model.ClimbSegment

/**
 * Analyzes climb segments and generates tactical insights:
 * steep sections ahead, recovery zones, attack points, final kick, etc.
 */
class TacticalAnalyzer {

    data class TacticalInsight(
        val type: InsightType,
        val distanceAhead: Double,   // meters to this section
        val description: String,
        val recommendation: String,
        val priority: Priority
    )

    enum class InsightType {
        STEEP_SECTION,
        EASY_SECTION,
        GRADIENT_CHANGE,
        ATTACK_POINT,
        RECOVERY_ZONE,
        FINAL_KICK,
        DANGEROUS_SECTION
    }

    enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

    /**
     * Analyze the climb from the rider's current position and return prioritized insights.
     *
     * @param climb active climb info with segments
     * @param currentProgress 0.0-1.0 progress along the climb
     * @return sorted list of tactical insights
     */
    fun analyzeClimb(climb: ClimbInfo, currentProgress: Double): List<TacticalInsight> {
        if (climb.segments.isEmpty()) return emptyList()

        val insights = mutableListOf<TacticalInsight>()
        val currentDistance = currentProgress * climb.length

        val upcomingSegments = climb.segments.filter { it.startDistance > currentDistance }

        // 1. Steep sections (grade >12%)
        upcomingSegments
            .filter { it.grade > 12.0 }
            .take(2)
            .forEach { seg ->
                val distAhead = seg.startDistance - currentDistance
                insights.add(
                    TacticalInsight(
                        type = InsightType.STEEP_SECTION,
                        distanceAhead = distAhead,
                        description = "${seg.grade.toInt()}% for ${seg.length.toInt()}m",
                        recommendation = when {
                            distAhead < 100 -> "Steep NOW — shift down, stay seated"
                            distAhead < 300 -> "Steep in ${distAhead.toInt()}m — ease off 10W now"
                            else -> "Steep section at ${distAhead.toInt()}m — plan your effort"
                        },
                        priority = if (distAhead < 200) Priority.HIGH else Priority.MEDIUM
                    )
                )
            }

        // 2. Recovery zones (gentle section after a hard one)
        for (i in 0 until upcomingSegments.lastIndex) {
            val current = upcomingSegments[i]
            val next = upcomingSegments[i + 1]

            if (current.grade > 8.0 && next.grade < 5.0) {
                val distAhead = next.startDistance - currentDistance
                insights.add(
                    TacticalInsight(
                        type = InsightType.RECOVERY_ZONE,
                        distanceAhead = distAhead,
                        description = "${next.grade.toInt()}% for ${next.length.toInt()}m",
                        recommendation = "Recovery zone at ${distAhead.toInt()}m — use to rebuild W'",
                        priority = Priority.MEDIUM
                    )
                )
            }
        }

        // 3. Attack points (moderate section before a steep one)
        for (i in 0 until upcomingSegments.lastIndex) {
            val seg = upcomingSegments[i]
            val nextSeg = upcomingSegments[i + 1]
            if (seg.grade < 6.0 && nextSeg.grade > 10.0) {
                val distAhead = seg.startDistance - currentDistance
                insights.add(
                    TacticalInsight(
                        type = InsightType.ATTACK_POINT,
                        distanceAhead = distAhead,
                        description = "Flat before steep — good attack point",
                        recommendation = "Surge here to gap rivals before the wall",
                        priority = Priority.LOW
                    )
                )
                break // take first only
            }
        }

        // 4. Final kick (last 500m)
        val remaining = climb.length - currentDistance
        if (remaining in 100.0..500.0) {
            val finalSegments = climb.segments.filter { it.endDistance >= climb.length - 500 }
            val avgFinalGrade = if (finalSegments.isNotEmpty()) {
                finalSegments.map { it.grade }.average()
            } else climb.avgGrade

            insights.add(
                TacticalInsight(
                    type = InsightType.FINAL_KICK,
                    distanceAhead = remaining,
                    description = "Final ${remaining.toInt()}m at ${avgFinalGrade.toInt()}%",
                    recommendation = when {
                        avgFinalGrade < climb.avgGrade ->
                            "Finish is easier — go all out!"
                        avgFinalGrade > climb.avgGrade + 2 ->
                            "Finish is steep — save something for the end"
                        else ->
                            "Consistent finish — maintain effort"
                    },
                    priority = Priority.HIGH
                )
            )
        }

        // 5. Dangerous sections (grade >18%)
        upcomingSegments
            .filter { it.grade > 18.0 }
            .take(1)
            .forEach { seg ->
                val distAhead = seg.startDistance - currentDistance
                insights.add(
                    TacticalInsight(
                        type = InsightType.DANGEROUS_SECTION,
                        distanceAhead = distAhead,
                        description = "Max ${seg.grade.toInt()}% gradient!",
                        recommendation = "Extreme gradient — consider standing, lowest gear",
                        priority = Priority.CRITICAL
                    )
                )
            }

        // 6. Gradient changes (>4% jump between adjacent segments)
        for (i in 0 until upcomingSegments.lastIndex) {
            val seg = upcomingSegments[i]
            val nextSeg = upcomingSegments[i + 1]
            val gradeDelta = nextSeg.grade - seg.grade

            if (gradeDelta > 4.0) {
                val distAhead = nextSeg.startDistance - currentDistance
                if (distAhead < 500) {
                    insights.add(
                        TacticalInsight(
                            type = InsightType.GRADIENT_CHANGE,
                            distanceAhead = distAhead,
                            description = "${seg.grade.toInt()}% → ${nextSeg.grade.toInt()}%",
                            recommendation = "Grade ramps up in ${distAhead.toInt()}m — prepare to shift",
                            priority = if (distAhead < 150) Priority.HIGH else Priority.MEDIUM
                        )
                    )
                }
            }
        }

        // 7. Easy sections (grade <4% within a hard climb)
        if (climb.avgGrade > 6.0) {
            upcomingSegments
                .filter { it.grade < 4.0 && it.length > 50 }
                .take(1)
                .forEach { seg ->
                    val distAhead = seg.startDistance - currentDistance
                    if (distAhead < 800) {
                        insights.add(
                            TacticalInsight(
                                type = InsightType.EASY_SECTION,
                                distanceAhead = distAhead,
                                description = "${seg.grade.toInt()}% for ${seg.length.toInt()}m",
                                recommendation = "Easier section at ${distAhead.toInt()}m — recover here",
                                priority = Priority.LOW
                            )
                        )
                    }
                }
        }

        return insights.sortedWith(
            compareByDescending<TacticalInsight> { it.priority.ordinal }
                .thenBy { it.distanceAhead }
        )
    }

    /**
     * Return the single most important insight for the current moment.
     * Prioritizes: CRITICAL > close HIGH > rest.
     */
    fun getPrimaryInsight(climb: ClimbInfo, currentProgress: Double): TacticalInsight? {
        val all = analyzeClimb(climb, currentProgress)

        return all.firstOrNull { it.priority == Priority.CRITICAL }
            ?: all.firstOrNull { it.priority == Priority.HIGH && it.distanceAhead < 300 }
            ?: all.firstOrNull()
    }
}
