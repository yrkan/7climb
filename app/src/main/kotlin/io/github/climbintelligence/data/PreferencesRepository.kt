package io.github.climbintelligence.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.climbintelligence.data.model.AthleteProfile
import io.github.climbintelligence.data.model.PacingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "climb_settings")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_FTP = intPreferencesKey("ftp")
        private val KEY_WEIGHT = doublePreferencesKey("weight")
        private val KEY_WPRIME_MAX = intPreferencesKey("wprime_max")
        private val KEY_CDA = doublePreferencesKey("cda")
        private val KEY_CRR = doublePreferencesKey("crr")
        private val KEY_PACING_MODE = stringPreferencesKey("pacing_mode")

        // Alert settings
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_ALERT_WPRIME = booleanPreferencesKey("alert_wprime")
        private val KEY_ALERT_STEEP = booleanPreferencesKey("alert_steep")
        private val KEY_ALERT_SUMMIT = booleanPreferencesKey("alert_summit")
        private val KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound")
        private val KEY_ALERT_COOLDOWN = intPreferencesKey("alert_cooldown")
    }

    val athleteProfileFlow: Flow<AthleteProfile> = context.dataStore.data
        .map { prefs ->
            AthleteProfile(
                ftp = prefs[KEY_FTP] ?: 0,
                weight = prefs[KEY_WEIGHT] ?: 0.0,
                wPrimeMax = prefs[KEY_WPRIME_MAX] ?: 20000,
                cda = prefs[KEY_CDA] ?: 0.321,
                crr = prefs[KEY_CRR] ?: 0.005
            )
        }
        .distinctUntilChanged()

    val pacingModeFlow: Flow<PacingMode> = context.dataStore.data
        .map { prefs ->
            try {
                PacingMode.valueOf(prefs[KEY_PACING_MODE] ?: "STEADY")
            } catch (e: Exception) {
                PacingMode.STEADY
            }
        }
        .distinctUntilChanged()

    val alertsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERTS_ENABLED] ?: true }
        .distinctUntilChanged()

    val alertWPrimeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_WPRIME] ?: true }
        .distinctUntilChanged()

    val alertSteepFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_STEEP] ?: true }
        .distinctUntilChanged()

    val alertSummitFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_SUMMIT] ?: true }
        .distinctUntilChanged()

    val alertSoundFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_SOUND] ?: false }
        .distinctUntilChanged()

    val alertCooldownFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_COOLDOWN] ?: 30 }
        .distinctUntilChanged()

    suspend fun updateFtp(ftp: Int) {
        context.dataStore.edit { it[KEY_FTP] = ftp.coerceIn(50, 500) }
    }

    suspend fun updateWeight(weight: Double) {
        context.dataStore.edit { it[KEY_WEIGHT] = weight.coerceIn(30.0, 200.0) }
    }

    suspend fun updateWPrimeMax(wPrimeMax: Int) {
        context.dataStore.edit { it[KEY_WPRIME_MAX] = wPrimeMax.coerceIn(5000, 40000) }
    }

    suspend fun updateCda(cda: Double) {
        context.dataStore.edit { it[KEY_CDA] = cda.coerceIn(0.1, 1.0) }
    }

    suspend fun updateCrr(crr: Double) {
        context.dataStore.edit { it[KEY_CRR] = crr.coerceIn(0.001, 0.02) }
    }

    suspend fun updatePacingMode(mode: PacingMode) {
        context.dataStore.edit { it[KEY_PACING_MODE] = mode.name }
    }

    suspend fun updateAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    suspend fun updateAlertSound(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_SOUND] = enabled }
    }

    suspend fun updateAlertCooldown(seconds: Int) {
        context.dataStore.edit { it[KEY_ALERT_COOLDOWN] = seconds.coerceIn(10, 120) }
    }
}
