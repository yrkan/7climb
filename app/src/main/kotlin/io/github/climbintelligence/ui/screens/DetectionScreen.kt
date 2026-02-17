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
import io.github.climbintelligence.data.model.DetectionSensitivity
import io.github.climbintelligence.data.model.DetectionSettings
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun DetectionScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }
    val detectionSettings by (prefs?.detectionSettingsFlow ?: flowOf(DetectionSettings()))
        .collectAsState(initial = DetectionSettings())

    var fineTuneExpanded by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = stringResource(R.string.settings_detection),
        onNavigateBack = onNavigateBack
    ) {
        DetectionSensitivitySelector(
            settings = detectionSettings,
            onSelectPreset = { sensitivity ->
                scope.launch { prefs?.updateDetectionSensitivity(sensitivity) }
                fineTuneExpanded = false
            }
        )

        val sensitivityDesc = when {
            detectionSettings.isCustom -> stringResource(R.string.detection_custom_desc)
            else -> when (detectionSettings.sensitivity) {
                DetectionSensitivity.SENSITIVE -> stringResource(R.string.detection_sensitive_desc)
                DetectionSensitivity.BALANCED -> stringResource(R.string.detection_balanced_desc)
                DetectionSensitivity.CONSERVATIVE -> stringResource(R.string.detection_conservative_desc)
            }
        }
        HintText(sensitivityDesc)

        ExpandableRow(
            label = if (fineTuneExpanded) stringResource(R.string.detection_hide_finetune)
                else stringResource(R.string.detection_show_finetune),
            expanded = fineTuneExpanded,
            onToggle = { fineTuneExpanded = !fineTuneExpanded }
        )

        androidx.compose.animation.AnimatedVisibility(visible = fineTuneExpanded) {
            Column {
                DecimalRow(
                    label = stringResource(R.string.detection_min_grade),
                    value = detectionSettings.minGrade,
                    unit = "%",
                    onValueChange = { scope.launch { prefs?.updateDetectionMinGrade(it) } }
                )
                HintText(stringResource(R.string.detection_min_grade_hint))

                NumericRow(
                    label = stringResource(R.string.detection_min_elevation),
                    value = detectionSettings.minElevation,
                    unit = "m",
                    onValueChange = { scope.launch { prefs?.updateDetectionMinElevation(it) } }
                )
                HintText(stringResource(R.string.detection_min_elevation_hint))

                NumericRow(
                    label = stringResource(R.string.detection_confirm_distance),
                    value = detectionSettings.confirmDistance,
                    unit = "m",
                    onValueChange = { scope.launch { prefs?.updateDetectionConfirmDistance(it) } }
                )
                HintText(stringResource(R.string.detection_confirm_distance_hint))

                NumericRow(
                    label = stringResource(R.string.detection_end_distance),
                    value = detectionSettings.endDistance,
                    unit = "m",
                    onValueChange = { scope.launch { prefs?.updateDetectionEndDistance(it) } }
                )
                HintText(stringResource(R.string.detection_end_distance_hint))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun DetectionSensitivitySelector(
    settings: DetectionSettings,
    onSelectPreset: (DetectionSensitivity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DetectionSensitivity.entries.forEach { sensitivity ->
            val isSelected = !settings.isCustom && sensitivity == settings.sensitivity
            val label = when (sensitivity) {
                DetectionSensitivity.SENSITIVE -> stringResource(R.string.detection_sensitive)
                DetectionSensitivity.BALANCED -> stringResource(R.string.detection_balanced)
                DetectionSensitivity.CONSERVATIVE -> stringResource(R.string.detection_conservative)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Theme.colors.optimal else Theme.colors.surface)
                    .clickable { onSelectPreset(sensitivity) }
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
