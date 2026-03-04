package io.github.climbintelligence.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.datatypes.BaseDataType
import io.github.climbintelligence.datatypes.ClimbDisplayState
import io.github.climbintelligence.datatypes.GlanceDataType
import io.hammerhead.karooext.models.ViewConfig

class PowerZonesGlanceDataType(extension: ClimbIntelligenceExtension) :
    GlanceDataType(extension, "power-zones") {

    @Composable
    override fun Content(state: ClimbDisplayState, config: ViewConfig) {
        val layoutSize = BaseDataType.getLayoutSize(config)
        DataFieldContainer {
            when (layoutSize) {
                BaseDataType.LayoutSize.SMALL -> ZonesSmall(state)
                BaseDataType.LayoutSize.SMALL_WIDE -> ZonesSmallWide(state)
                BaseDataType.LayoutSize.MEDIUM -> ZonesMedium(state)
                BaseDataType.LayoutSize.MEDIUM_WIDE -> ZonesMediumWide(state)
                BaseDataType.LayoutSize.LARGE -> ZonesLarge(state)
                BaseDataType.LayoutSize.NARROW -> ZonesNarrow(state)
            }
        }
    }
}

private val ZONE_NAMES = arrayOf("Z1", "Z2", "Z3", "Z4", "Z5", "Z6", "Z7")

private fun zoneLabel(zone: Int): String = if (zone in 0..6) ZONE_NAMES[zone] else "-"

private fun formatZoneTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m${s}s" else "${s}s"
}

@Composable
private fun ZonesSmall(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val zone = m.currentZone
    val value = if (m.hasData && zone >= 0) zoneLabel(zone) else BaseDataType.NO_DATA
    val color = if (m.hasData && zone >= 0) GlanceColors.zoneColor(zone) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ValueText(value, color, 24)
    }
}

@Composable
private fun ZonesSmallWide(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val zone = m.currentZone
    if (!m.hasData || zone !in 0..6) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 28)
        }
        return
    }
    val color = GlanceColors.zoneColor(zone)
    val time = formatZoneTime(m.powerZones[zone])

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("ZONE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueText(zoneLabel(zone), color, 28)
                ValueText(" $time", GlanceColors.Label, 14, GlanceModifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun ZonesMedium(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val zone = m.currentZone
    val value = if (m.hasData && zone >= 0) zoneLabel(zone) else BaseDataType.NO_DATA
    val color = if (m.hasData && zone >= 0) GlanceColors.zoneColor(zone) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("ZONE", fontSize = 11)
            ValueText(value, color, 24)
            if (m.hasData) {
                ZoneBar(m.powerZones, 6)
            }
        }
    }
}

@Composable
private fun ZonesMediumWide(state: ClimbDisplayState) {
    val m = state.rideMetrics
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 32)
        }
        return
    }

    val totalSeconds = m.powerZones.sum().coerceAtLeast(1)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("POWER ZONES")
            ZoneBar(m.powerZones, 10)
            // Show percentage for each zone
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                for (i in 0..6) {
                    val pct = (m.powerZones[i] * 100.0 / totalSeconds).toInt()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        LabelText("${pct}%", fontSize = 9)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZonesLarge(state: ClimbDisplayState) {
    val m = state.rideMetrics
    if (!m.hasData) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ValueText(BaseDataType.NO_DATA, GlanceColors.Label, 42)
        }
        return
    }

    val zone = m.currentZone
    val color = if (zone >= 0) GlanceColors.zoneColor(zone) else GlanceColors.Label
    val totalSeconds = m.powerZones.sum().coerceAtLeast(1)

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("POWER ZONES", fontSize = 12)
            if (zone >= 0) {
                ValueText(zoneLabel(zone), color, 32)
            }
            GlanceDivider()
            // Zone bars with time
            for (i in 0..6) {
                val pct = m.powerZones[i].toDouble() / totalSeconds
                val time = formatZoneTime(m.powerZones[i])
                val zColor = GlanceColors.zoneColor(i)
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ValueText(ZONE_NAMES[i], zColor, 11, GlanceModifier.padding(end = 2.dp))
                    Box(modifier = GlanceModifier.defaultWeight().height(6.dp).background(GlanceColors.Frame)) {
                        Row(modifier = GlanceModifier.fillMaxSize()) {
                            val filled = (pct * 10).toInt().coerceIn(0, 10)
                            for (j in 0 until 10) {
                                Box(
                                    modifier = GlanceModifier.fillMaxHeight().defaultWeight()
                                        .background(if (j < filled) zColor else GlanceColors.Background)
                                ) {}
                            }
                        }
                    }
                    LabelText(time, GlanceModifier.padding(start = 2.dp), fontSize = 9)
                }
            }
        }
    }
}

@Composable
private fun ZonesNarrow(state: ClimbDisplayState) {
    val m = state.rideMetrics
    val zone = m.currentZone
    val value = if (m.hasData && zone >= 0) zoneLabel(zone) else BaseDataType.NO_DATA
    val color = if (m.hasData && zone >= 0) GlanceColors.zoneColor(zone) else GlanceColors.Label

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText("ZONE", fontSize = 11)
            ValueText(value, color, 28)
            if (m.hasData) {
                // Show top 3 zones by time
                val totalSeconds = m.powerZones.sum().coerceAtLeast(1)
                val sorted = m.powerZones.indices.sortedByDescending { m.powerZones[it] }.take(3)
                for (idx in sorted) {
                    if (m.powerZones[idx] > 0) {
                        val pct = (m.powerZones[idx] * 100.0 / totalSeconds).toInt()
                        LabelText("${ZONE_NAMES[idx]} ${pct}%", fontSize = 11)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneBar(zones: IntArray, height: Int) {
    val total = zones.sum().coerceAtLeast(1)
    Row(modifier = GlanceModifier.fillMaxWidth().height(height.dp).padding(vertical = 1.dp)) {
        for (i in 0..6) {
            val segments = (zones[i].toDouble() / total * 10).toInt().coerceIn(0, 10)
            if (segments > 0) {
                for (j in 0 until segments) {
                    Box(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight()
                            .background(GlanceColors.zoneColor(i))
                    ) {}
                }
            }
        }
        // Fill remaining with background
        val filled = (0..6).sumOf { (zones[it].toDouble() / total * 10).toInt().coerceIn(0, 10) }
        for (j in filled until 10) {
            Box(
                modifier = GlanceModifier.fillMaxHeight().defaultWeight()
                    .background(GlanceColors.Background)
            ) {}
        }
    }
}
