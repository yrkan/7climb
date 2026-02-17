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
import io.github.climbintelligence.data.model.PacingMode
import io.github.climbintelligence.data.model.PacingTolerance
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun PacingScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }

    val pacingMode by (prefs?.pacingModeFlow ?: flowOf(PacingMode.STEADY))
        .collectAsState(initial = PacingMode.STEADY)
    val pacingTolerance by (prefs?.pacingToleranceFlow ?: flowOf(PacingTolerance.NORMAL))
        .collectAsState(initial = PacingTolerance.NORMAL)

    SubScreenScaffold(
        title = stringResource(R.string.settings_pacing),
        onNavigateBack = onNavigateBack
    ) {
        PacingModeSelector(
            selected = pacingMode,
            onSelect = { scope.launch { prefs?.updatePacingMode(it) } }
        )

        Spacer(modifier = Modifier.height(8.dp))

        PresetLabel(stringResource(R.string.settings_pacing_tolerance))
        PacingToleranceSelector(
            selected = pacingTolerance,
            onSelect = { tolerance ->
                scope.launch { prefs?.updatePacingTolerance(tolerance) }
            }
        )
        val toleranceDesc = when (pacingTolerance) {
            PacingTolerance.TIGHT -> stringResource(R.string.pacing_tolerance_tight_desc)
            PacingTolerance.NORMAL -> stringResource(R.string.pacing_tolerance_normal_desc)
            PacingTolerance.RELAXED -> stringResource(R.string.pacing_tolerance_relaxed_desc)
            null -> stringResource(R.string.pacing_tolerance_custom_desc)
        }
        HintText(toleranceDesc)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun PacingModeSelector(
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
internal fun PacingToleranceSelector(
    selected: PacingTolerance?,
    onSelect: (PacingTolerance) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PacingTolerance.entries.forEach { tolerance ->
            val isSelected = tolerance == selected
            val label = when (tolerance) {
                PacingTolerance.TIGHT -> stringResource(R.string.pacing_tolerance_tight)
                PacingTolerance.NORMAL -> stringResource(R.string.pacing_tolerance_normal)
                PacingTolerance.RELAXED -> stringResource(R.string.pacing_tolerance_relaxed)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                    .clickable { onSelect(tolerance) }
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
