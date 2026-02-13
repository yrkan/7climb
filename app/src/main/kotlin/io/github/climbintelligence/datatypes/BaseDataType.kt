package io.github.climbintelligence.datatypes

import io.hammerhead.karooext.models.ViewConfig

object BaseDataType {

    const val NO_DATA = "-"
    const val SENSOR_DISCONNECTED = "--"

    enum class LayoutSize {
        SMALL,
        SMALL_WIDE,
        MEDIUM_WIDE,
        MEDIUM,
        LARGE,
        NARROW
    }

    fun getLayoutSize(config: ViewConfig): LayoutSize {
        val isFullWidth = config.gridSize.first >= 50
        val height = config.viewSize.second

        return if (isFullWidth) {
            when {
                height >= 250 -> LayoutSize.LARGE
                height >= 160 -> LayoutSize.MEDIUM_WIDE
                else -> LayoutSize.SMALL_WIDE
            }
        } else {
            when {
                height >= 600 -> LayoutSize.NARROW
                height >= 200 -> LayoutSize.MEDIUM
                else -> LayoutSize.SMALL
            }
        }
    }
}
