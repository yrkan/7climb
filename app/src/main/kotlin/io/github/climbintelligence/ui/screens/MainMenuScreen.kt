package io.github.climbintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.climbintelligence.BuildConfig
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.DetectionSensitivity
import io.github.climbintelligence.data.model.DetectionSettings
import io.github.climbintelligence.data.model.PacingMode
import io.github.climbintelligence.data.model.PacingTolerance
import io.github.climbintelligence.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf

@Composable
fun MainMenuScreen(
    onNavigate: (String) -> Unit
) {
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }

    val profile by (prefs?.athleteProfileFlow ?: flowOf(AthleteProfile()))
        .collectAsState(initial = AthleteProfile())
    val pacingMode by (prefs?.pacingModeFlow ?: flowOf(PacingMode.STEADY))
        .collectAsState(initial = PacingMode.STEADY)
    val pacingTolerance by (prefs?.pacingToleranceFlow ?: flowOf(PacingTolerance.NORMAL))
        .collectAsState(initial = PacingTolerance.NORMAL)
    val alertsEnabled by (prefs?.alertsEnabledFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val detectionSettings by (prefs?.detectionSettingsFlow ?: flowOf(DetectionSettings()))
        .collectAsState(initial = DetectionSettings())
    val alertWPrime by (prefs?.alertWPrimeFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertSteep by (prefs?.alertSteepFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertSummit by (prefs?.alertSummitFlow ?: flowOf(true))
        .collectAsState(initial = true)
    val alertClimbStart by (prefs?.alertClimbStartFlow ?: flowOf(true))
        .collectAsState(initial = true)

    // Subtitles
    val athleteSubtitle = if (profile.isConfigured) {
        "FTP: ${profile.ftp}W \u00B7 ${"%.1f".format(profile.weight)}kg"
    } else {
        stringResource(R.string.settings_not_configured)
    }

    val bikeType = BikeType.fromWeight(profile.bikeWeight)
    val position = RidingPosition.fromCda(profile.cda)
    val surface = RoadSurface.fromCrr(profile.crr)
    val bikeSubtitle = buildString {
        append(bikeType.name.lowercase().replaceFirstChar { it.uppercase() })
        position?.let { append(" \u00B7 ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}") }
        surface?.let { append(" \u00B7 ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}") }
    }

    val detectionSubtitle = when {
        detectionSettings.isCustom -> "Custom"
        else -> when (detectionSettings.sensitivity) {
            DetectionSensitivity.SENSITIVE -> "Sensitive"
            DetectionSensitivity.BALANCED -> "Balanced"
            DetectionSensitivity.CONSERVATIVE -> "Conservative"
        }
    }

    val pacingSubtitle = buildString {
        append(when (pacingMode) {
            PacingMode.STEADY -> "Steady"
            PacingMode.RACE -> "Race"
            PacingMode.SURVIVAL -> "Survival"
        })
        pacingTolerance?.let { append(" \u00B7 \u00B1${it.watts}W") }
    }

    val activeAlerts = listOf(alertWPrime, alertSteep, alertSummit, alertClimbStart).count { it }
    val alertsSubtitle = if (alertsEnabled) {
        "Enabled \u00B7 $activeAlerts/4 active"
    } else {
        "Disabled"
    }

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

        SettingsDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            MenuRow(
                label = stringResource(R.string.settings_athlete),
                subtitle = athleteSubtitle,
                onClick = { onNavigate("settings/athlete") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.settings_bike),
                subtitle = bikeSubtitle,
                onClick = { onNavigate("settings/bike") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.settings_detection),
                subtitle = detectionSubtitle,
                onClick = { onNavigate("settings/detection") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.settings_pacing),
                subtitle = pacingSubtitle,
                onClick = { onNavigate("settings/pacing") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.settings_alerts),
                subtitle = alertsSubtitle,
                onClick = { onNavigate("settings/alerts") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.settings_view_history),
                onClick = { onNavigate("history") }
            )
            SettingsDivider()

            MenuRow(
                label = stringResource(R.string.about_title),
                subtitle = "v${BuildConfig.VERSION_NAME}",
                onClick = { onNavigate("settings/about") }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
