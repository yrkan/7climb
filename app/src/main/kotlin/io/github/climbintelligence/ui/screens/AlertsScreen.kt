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
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }

    val alertsEnabled by (prefs?.alertsEnabledFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertSound by (prefs?.alertSoundFlow ?: flowOf(false))
        .collectAsState(initial = false)
    val alertCooldown by (prefs?.alertCooldownFlow ?: flowOf(30))
        .collectAsState(initial = 30)
    val alertWPrime by (prefs?.alertWPrimeFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertSteep by (prefs?.alertSteepFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertSummit by (prefs?.alertSummitFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertClimbStart by (prefs?.alertClimbStartFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val wPrimeAlertThreshold by (prefs?.wPrimeAlertThresholdFlow ?: flowOf(20))
        .collectAsState(initial = 20)

    SubScreenScaffold(
        title = stringResource(R.string.settings_alerts),
        onNavigateBack = onNavigateBack
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_alerts_enabled),
            enabled = alertsEnabled,
            onToggle = { scope.launch { prefs?.updateAlertsEnabled(it) } }
        )

        if (alertsEnabled) {
            ToggleRow(
                label = stringResource(R.string.settings_alert_wprime_toggle),
                enabled = alertWPrime,
                onToggle = { scope.launch { prefs?.updateAlertWPrime(it) } }
            )
            ToggleRow(
                label = stringResource(R.string.settings_alert_steep_toggle),
                enabled = alertSteep,
                onToggle = { scope.launch { prefs?.updateAlertSteep(it) } }
            )
            ToggleRow(
                label = stringResource(R.string.settings_alert_summit_toggle),
                enabled = alertSummit,
                onToggle = { scope.launch { prefs?.updateAlertSummit(it) } }
            )
            ToggleRow(
                label = stringResource(R.string.settings_alert_climb_start_toggle),
                enabled = alertClimbStart,
                onToggle = { scope.launch { prefs?.updateAlertClimbStart(it) } }
            )

            if (alertWPrime) {
                NumericRow(
                    label = stringResource(R.string.settings_wprime_threshold),
                    value = wPrimeAlertThreshold,
                    unit = "%",
                    onValueChange = { scope.launch { prefs?.updateWPrimeAlertThreshold(it) } }
                )
                HintText(stringResource(R.string.settings_wprime_threshold_hint))
            }

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

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun CooldownSelector(
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
                        .padding(vertical = 12.dp),
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
