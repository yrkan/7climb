package io.github.climbintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// Position -> CdA mapping
internal enum class RidingPosition(val cda: Double) {
    TOPS(0.370),
    HOODS(0.320),
    DROPS(0.300),
    AERO_DROPS(0.270),
    AERO(0.240),
    TT(0.220);

    companion object {
        fun fromCda(cda: Double): RidingPosition? =
            entries.firstOrNull { kotlin.math.abs(it.cda - cda) <= 0.005 }
    }
}

// Surface -> Crr mapping
internal enum class RoadSurface(val crr: Double) {
    SMOOTH(0.004),
    ROAD(0.005),
    ROUGH(0.007),
    COBBLES(0.010),
    GRAVEL(0.012);

    companion object {
        fun fromCrr(crr: Double): RoadSurface? =
            entries.firstOrNull { kotlin.math.abs(it.crr - crr) <= 0.0005 }
    }
}

// Bike type -> default weight mapping
internal enum class BikeType(val defaultWeight: Double) {
    RACE(7.5),
    AERO(8.0),
    TT(9.0),
    ENDURANCE(9.5),
    CUSTOM(0.0);

    companion object {
        fun fromWeight(weight: Double): BikeType =
            entries.firstOrNull { it != CUSTOM && kotlin.math.abs(it.defaultWeight - weight) < 0.05 }
                ?: CUSTOM
    }
}

@Composable
fun BikeScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }
    val profile by (prefs?.athleteProfileFlow ?: flowOf(AthleteProfile()))
        .collectAsState(initial = AthleteProfile())

    var advancedExpanded by remember { mutableStateOf(false) }
    val currentBikeType = BikeType.fromWeight(profile.bikeWeight)
    val currentPosition = RidingPosition.fromCda(profile.cda)
    val currentSurface = RoadSurface.fromCrr(profile.crr)

    SubScreenScaffold(
        title = stringResource(R.string.settings_bike),
        onNavigateBack = onNavigateBack
    ) {
        PresetLabel(stringResource(R.string.settings_bike_type))
        BikeTypeSelector(
            selected = currentBikeType,
            onSelect = { type ->
                if (type != BikeType.CUSTOM) {
                    scope.launch { prefs?.updateBikeWeight(type.defaultWeight) }
                }
            }
        )

        if (currentBikeType == BikeType.CUSTOM) {
            DecimalRow(
                label = stringResource(R.string.settings_bike_weight),
                value = profile.bikeWeight,
                unit = "kg",
                onValueChange = { scope.launch { prefs?.updateBikeWeight(it) } }
            )
            HintText(stringResource(R.string.settings_bike_weight_hint))
        }

        Spacer(modifier = Modifier.height(8.dp))

        PresetLabel(stringResource(R.string.settings_position))
        PositionSelector(
            selected = currentPosition,
            onSelect = { pos -> scope.launch { prefs?.updateCda(pos.cda) } }
        )
        HintText(stringResource(R.string.settings_position_hint))

        Spacer(modifier = Modifier.height(8.dp))

        PresetLabel(stringResource(R.string.settings_surface))
        SurfaceSelector(
            selected = currentSurface,
            onSelect = { surface -> scope.launch { prefs?.updateCrr(surface.crr) } }
        )
        HintText(stringResource(R.string.settings_surface_hint))

        // Advanced (CdA, Crr)
        SectionHeader(stringResource(R.string.settings_advanced))
        ExpandableRow(
            label = if (advancedExpanded) stringResource(R.string.settings_hide_advanced) else stringResource(R.string.settings_show_advanced),
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded }
        )

        androidx.compose.animation.AnimatedVisibility(visible = advancedExpanded) {
            Column {
                HintText(stringResource(R.string.settings_advanced_hint))

                DecimalRow(
                    label = stringResource(R.string.settings_cda),
                    value = profile.cda,
                    unit = "m\u00B2",
                    onValueChange = { scope.launch { prefs?.updateCda(it) } }
                )
                HintText(stringResource(R.string.settings_cda_hint))

                DecimalRow(
                    label = stringResource(R.string.settings_crr),
                    value = profile.crr,
                    unit = "",
                    onValueChange = { scope.launch { prefs?.updateCrr(it) } }
                )
                HintText(stringResource(R.string.settings_crr_hint))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun BikeTypeSelector(
    selected: BikeType,
    onSelect: (BikeType) -> Unit
) {
    val topRow = listOf(BikeType.RACE, BikeType.AERO, BikeType.TT)
    val bottomRow = listOf(BikeType.ENDURANCE, BikeType.CUSTOM)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(topRow, bottomRow).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowTypes.forEach { type ->
                    val isSelected = type == selected
                    val label = when (type) {
                        BikeType.RACE -> stringResource(R.string.bike_race)
                        BikeType.AERO -> stringResource(R.string.bike_aero)
                        BikeType.TT -> stringResource(R.string.bike_tt)
                        BikeType.ENDURANCE -> stringResource(R.string.bike_endurance)
                        BikeType.CUSTOM -> stringResource(R.string.bike_custom)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                            .clickable { onSelect(type) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Theme.colors.background else Theme.colors.dim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PositionSelector(
    selected: RidingPosition?,
    onSelect: (RidingPosition) -> Unit
) {
    val topRow = listOf(RidingPosition.TOPS, RidingPosition.HOODS, RidingPosition.DROPS)
    val bottomRow = listOf(RidingPosition.AERO_DROPS, RidingPosition.AERO, RidingPosition.TT)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(topRow, bottomRow).forEach { rowPositions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowPositions.forEach { pos ->
                    val isSelected = pos == selected
                    val label = when (pos) {
                        RidingPosition.TOPS -> stringResource(R.string.position_tops)
                        RidingPosition.HOODS -> stringResource(R.string.position_hoods)
                        RidingPosition.DROPS -> stringResource(R.string.position_drops)
                        RidingPosition.AERO_DROPS -> stringResource(R.string.position_aero_drops)
                        RidingPosition.AERO -> stringResource(R.string.position_aero)
                        RidingPosition.TT -> stringResource(R.string.position_tt)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                            .clickable { onSelect(pos) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Theme.colors.background else Theme.colors.dim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SurfaceSelector(
    selected: RoadSurface?,
    onSelect: (RoadSurface) -> Unit
) {
    val topRow = listOf(RoadSurface.SMOOTH, RoadSurface.ROAD, RoadSurface.ROUGH)
    val bottomRow = listOf(RoadSurface.COBBLES, RoadSurface.GRAVEL)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(topRow, bottomRow).forEach { rowSurfaces ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowSurfaces.forEach { surface ->
                    val isSelected = surface == selected
                    val label = when (surface) {
                        RoadSurface.SMOOTH -> stringResource(R.string.surface_smooth)
                        RoadSurface.ROAD -> stringResource(R.string.surface_road)
                        RoadSurface.ROUGH -> stringResource(R.string.surface_rough)
                        RoadSurface.COBBLES -> stringResource(R.string.surface_cobbles)
                        RoadSurface.GRAVEL -> stringResource(R.string.surface_gravel)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                            .clickable { onSelect(surface) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Theme.colors.background else Theme.colors.dim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
