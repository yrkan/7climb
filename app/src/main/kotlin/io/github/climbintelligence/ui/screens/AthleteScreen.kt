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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
