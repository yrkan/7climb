package io.github.climbintelligence.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.BuildConfig
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.model.PacingMode
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.launch

// Position → CdA mapping
private enum class RidingPosition(val cda: Double) {
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

// Surface → Crr mapping
private enum class RoadSurface(val crr: Double) {
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

// Bike type → default weight mapping
private enum class BikeType(val defaultWeight: Double) {
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
fun SettingsScreen(
    onNavigateToHistory: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }

    val profile by (prefs?.athleteProfileFlow ?: kotlinx.coroutines.flow.flowOf(
        io.github.climbintelligence.data.model.AthleteProfile()
    )).collectAsState(initial = io.github.climbintelligence.data.model.AthleteProfile())

    val pacingMode by (prefs?.pacingModeFlow ?: kotlinx.coroutines.flow.flowOf(PacingMode.STEADY))
        .collectAsState(initial = PacingMode.STEADY)

    val alertsEnabled by (prefs?.alertsEnabledFlow ?: kotlinx.coroutines.flow.flowOf(true))
        .collectAsState(initial = true)
    val alertSound by (prefs?.alertSoundFlow ?: kotlinx.coroutines.flow.flowOf(false))
        .collectAsState(initial = false)
    val alertCooldown by (prefs?.alertCooldownFlow ?: kotlinx.coroutines.flow.flowOf(30))
        .collectAsState(initial = 30)

    var advancedExpanded by remember { mutableStateOf(false) }

    val currentPosition = RidingPosition.fromCda(profile.cda)
    val currentSurface = RoadSurface.fromCrr(profile.crr)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = Theme.colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Divider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Athlete Profile ──────────────────────────────
            SectionHeader(stringResource(R.string.settings_athlete))

            NumericRow(
                label = stringResource(R.string.settings_ftp),
                value = profile.ftp,
                unit = "W",
                onValueChange = { scope.launch { prefs?.updateFtp(it) } }
            )
            HintText(stringResource(R.string.settings_ftp_hint))

            DecimalRow(
                label = stringResource(R.string.settings_weight),
                value = profile.weight,
                unit = "kg",
                onValueChange = { scope.launch { prefs?.updateWeight(it) } }
            )

            // ── Bike Setup ───────────────────────────────────
            SectionHeader(stringResource(R.string.settings_bike))

            val currentBikeType = BikeType.fromWeight(profile.bikeWeight)

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
                onSelect = { pos ->
                    scope.launch { prefs?.updateCda(pos.cda) }
                }
            )
            HintText(stringResource(R.string.settings_position_hint))

            Spacer(modifier = Modifier.height(8.dp))

            PresetLabel(stringResource(R.string.settings_surface))
            SurfaceSelector(
                selected = currentSurface,
                onSelect = { surface ->
                    scope.launch { prefs?.updateCrr(surface.crr) }
                }
            )
            HintText(stringResource(R.string.settings_surface_hint))

            // ── Pacing ───────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_pacing))
            PacingModeSelector(
                selected = pacingMode,
                onSelect = { scope.launch { prefs?.updatePacingMode(it) } }
            )

            // ── Alerts ───────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_alerts))
            ToggleRow(
                label = stringResource(R.string.settings_alerts_enabled),
                enabled = alertsEnabled,
                onToggle = { scope.launch { prefs?.updateAlertsEnabled(it) } }
            )
            if (alertsEnabled) {
                ToggleRow(
                    label = stringResource(R.string.settings_alert_sound),
                    enabled = alertSound,
                    onToggle = { scope.launch { prefs?.updateAlertSound(it) } }
                )
                CooldownSelector(
                    selectedSeconds = alertCooldown,
                    onSelect = { scope.launch { prefs?.updateAlertCooldown(it) } }
                )
            }

            // ── Advanced (collapsed) ─────────────────────────
            SectionHeader(stringResource(R.string.settings_advanced))
            ExpandableRow(
                label = if (advancedExpanded) stringResource(R.string.settings_hide_advanced) else stringResource(R.string.settings_show_advanced),
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded }
            )

            AnimatedVisibility(visible = advancedExpanded) {
                Column {
                    HintText(stringResource(R.string.settings_advanced_hint))

                    NumericRow(
                        label = stringResource(R.string.settings_wprime),
                        value = profile.wPrimeMax,
                        unit = "J",
                        onValueChange = { scope.launch { prefs?.updateWPrimeMax(it) } }
                    )
                    HintText(stringResource(R.string.settings_wprime_hint))

                    CpRow(
                        cp = profile.cp,
                        effectiveCp = profile.effectiveCp,
                        onValueChange = { scope.launch { prefs?.updateCp(it) } }
                    )
                    HintText(stringResource(R.string.settings_cp_hint))

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

            // ── History ──────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_history))
            NavigationRow(
                label = stringResource(R.string.settings_view_history),
                onClick = onNavigateToHistory
            )

            // ── About ───────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_about))
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── UI Components ────────────────────────────────────────────────

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Theme.colors.divider)
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Theme.colors.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        color = Theme.colors.dim,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun PresetLabel(text: String) {
    Text(
        text = text,
        color = Theme.colors.text,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun NumericRow(
    label: String,
    value: Int,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = if (value > 0) "$value $unit" else "---",
            color = if (value > 0) Theme.colors.optimal else Theme.colors.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = label,
            initialValue = if (value > 0) value.toString() else "",
            unit = unit,
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                text.trim().toIntOrNull()?.let { onValueChange(it) }
                showDialog = false
            }
        )
    }
}

@Composable
private fun DecimalRow(
    label: String,
    value: Double,
    unit: String,
    onValueChange: (Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        val display = if (value > 0) {
            if (value < 1) "%.3f".format(value) else "%.1f".format(value)
        } else "---"
        Text(
            text = if (value > 0) "$display $unit".trim() else "---",
            color = if (value > 0) Theme.colors.optimal else Theme.colors.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = label,
            initialValue = if (value > 0) {
                if (value < 1) "%.3f".format(value) else "%.1f".format(value)
            } else "",
            unit = unit,
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                text.trim().toDoubleOrNull()?.let { onValueChange(it) }
                showDialog = false
            }
        )
    }
}

@Composable
private fun NumericInputDialog(
    title: String,
    initialValue: String,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConfirm(text)
                        }
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Theme.colors.background,
                        unfocusedContainerColor = Theme.colors.background,
                        focusedTextColor = Theme.colors.text,
                        unfocusedTextColor = Theme.colors.text,
                        cursorColor = Theme.colors.optimal
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = unit,
                        color = Theme.colors.dim,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.ok), color = Theme.colors.optimal, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Theme.colors.dim)
            }
        },
        containerColor = Theme.colors.surface,
        titleContentColor = Theme.colors.text,
        textContentColor = Theme.colors.text
    )
}

@Composable
private fun PositionSelector(
    selected: RidingPosition?,
    onSelect: (RidingPosition) -> Unit
) {
    val topRow = listOf(RidingPosition.TOPS, RidingPosition.HOODS, RidingPosition.DROPS)
    val bottomRow = listOf(RidingPosition.AERO_DROPS, RidingPosition.AERO, RidingPosition.TT)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(topRow, bottomRow).forEach { rowPositions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            .padding(vertical = 10.dp),
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
private fun SurfaceSelector(
    selected: RoadSurface?,
    onSelect: (RoadSurface) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RoadSurface.entries.forEach { surface ->
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
                    .padding(vertical = 10.dp),
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

@Composable
private fun BikeTypeSelector(
    selected: BikeType,
    onSelect: (BikeType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BikeType.entries.forEach { type ->
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
                    .padding(vertical = 10.dp),
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

@Composable
private fun CpRow(
    cp: Int,
    effectiveCp: Int,
    onValueChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_cp),
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = if (cp > 0) "$cp W" else "Auto ($effectiveCp W)",
            color = Theme.colors.optimal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        NumericInputDialog(
            title = stringResource(R.string.settings_cp),
            initialValue = if (cp > 0) cp.toString() else "",
            unit = "W",
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                val value = text.trim().toIntOrNull() ?: 0
                onValueChange(value)
                showDialog = false
            }
        )
    }
}

@Composable
private fun PacingModeSelector(
    selected: PacingMode,
    onSelect: (PacingMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        PacingMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = when (mode) {
                PacingMode.STEADY -> stringResource(R.string.pacing_steady)
                PacingMode.RACE -> stringResource(R.string.pacing_race)
                PacingMode.SURVIVAL -> stringResource(R.string.pacing_survival)
            }
            val desc = when (mode) {
                PacingMode.STEADY -> stringResource(R.string.pacing_steady_desc)
                PacingMode.RACE -> stringResource(R.string.pacing_race_desc)
                PacingMode.SURVIVAL -> stringResource(R.string.pacing_survival_desc)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = if (isSelected) Theme.colors.background else Theme.colors.text,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = desc,
                        color = if (isSelected) Theme.colors.background.copy(alpha = 0.7f) else Theme.colors.dim,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CooldownSelector(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit
) {
    val options = listOf(15, 30, 60, 90)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_alert_cooldown),
            color = Theme.colors.text,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { seconds ->
                val isSelected = seconds == selectedSeconds
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                        .clickable { onSelect(seconds) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${seconds}s",
                        color = if (isSelected) Theme.colors.background else Theme.colors.dim,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Theme.colors.background,
                checkedTrackColor = Theme.colors.optimal,
                uncheckedThumbColor = Theme.colors.dim,
                uncheckedTrackColor = Theme.colors.surface
            )
        )
    }
}

@Composable
private fun ExpandableRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.textSecondary,
            fontSize = 12.sp
        )
        Text(
            text = if (expanded) "\u25B2" else "\u25BC",
            color = Theme.colors.dim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun NavigationRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = ">",
            color = Theme.colors.dim,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Theme.colors.text,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Theme.colors.dim,
            fontSize = 12.sp
        )
    }
}
