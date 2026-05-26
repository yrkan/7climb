package io.github.climbintelligence.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.climbintelligence.ClimbIntelligenceExtension
import io.github.climbintelligence.R
import io.github.climbintelligence.data.model.AthleteProfile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun AthleteScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { ClimbIntelligenceExtension.instance?.preferencesRepository }
    val profile by (prefs?.athleteProfileFlow ?: flowOf(AthleteProfile()))
        .collectAsState(initial = AthleteProfile())

    var advancedExpanded by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = stringResource(R.string.settings_athlete),
        onNavigateBack = onNavigateBack
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_use_karoo_profile),
            enabled = profile.useKarooProfile,
            onToggle = { scope.launch { prefs?.updateUseKarooProfile(it) } }
        )
        HintText(stringResource(R.string.settings_use_karoo_profile_hint))

        // FTP row — read-only when toggle is on AND Karoo has a value;
        // editable otherwise. Same pattern for weight below.
        if (profile.useKarooProfile && profile.karooFtp > 0) {
            InfoRow(
                label = stringResource(R.string.settings_karoo_ftp),
                value = "${profile.karooFtp} W"
            )
        } else {
            NumericRow(
                label = stringResource(R.string.settings_ftp),
                value = profile.ftp,
                unit = "W",
                onValueChange = { scope.launch { prefs?.updateFtp(it) } }
            )
            HintText(stringResource(R.string.settings_ftp_hint))
        }

        if (profile.useKarooProfile && profile.karooWeight > 0.0) {
            InfoRow(
                label = stringResource(R.string.settings_karoo_weight),
                value = "%.1f kg".format(profile.karooWeight)
            )
        } else {
            DecimalRow(
                label = stringResource(R.string.settings_weight),
                value = profile.weight,
                unit = "kg",
                onValueChange = { scope.launch { prefs?.updateWeight(it) } }
            )
        }

        // Advanced (W', CP)
        SectionHeader(stringResource(R.string.settings_advanced))
        ExpandableRow(
            label = if (advancedExpanded) stringResource(R.string.settings_hide_advanced) else stringResource(R.string.settings_show_advanced),
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded }
        )

        androidx.compose.animation.AnimatedVisibility(visible = advancedExpanded) {
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

                NumericRow(
                    label = stringResource(R.string.settings_max_power),
                    value = profile.maxPower,
                    unit = "W",
                    onValueChange = { scope.launch { prefs?.updateMaxPower(it) } }
                )
                HintText(stringResource(R.string.settings_max_power_hint))

                ToggleRow(
                    label = stringResource(R.string.settings_use_three_param_model),
                    enabled = profile.useThreeParamModel,
                    onToggle = { scope.launch { prefs?.updateUseThreeParamModel(it) } }
                )
                HintText(stringResource(R.string.settings_use_three_param_model_hint))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
