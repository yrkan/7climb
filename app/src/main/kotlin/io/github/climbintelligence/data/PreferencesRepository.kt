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
import io.github.climbintelligence.data.model.DetectionSensitivity
import io.github.climbintelligence.data.model.DetectionSettings
import io.github.climbintelligence.data.model.PacingMode
import io.github.climbintelligence.data.model.PacingTolerance
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
        private val KEY_BIKE_WEIGHT = doublePreferencesKey("bike_weight")
        private val KEY_CP = intPreferencesKey("cp")
        private val KEY_MAX_POWER = intPreferencesKey("max_power")
        private val KEY_USE_KAROO_PROFILE = booleanPreferencesKey("use_karoo_profile")
        private val KEY_KAROO_FTP = intPreferencesKey("karoo_ftp")
        private val KEY_KAROO_WEIGHT = doublePreferencesKey("karoo_weight")
        private val KEY_USE_THREE_PARAM_MODEL = booleanPreferencesKey("use_three_param_model")
        private val KEY_PACING_MODE = stringPreferencesKey("pacing_mode")

        // Alert settings
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_ALERT_WPRIME = booleanPreferencesKey("alert_wprime")
        private val KEY_ALERT_STEEP = booleanPreferencesKey("alert_steep")
        private val KEY_ALERT_SUMMIT = booleanPreferencesKey("alert_summit")
        private val KEY_ALERT_CLIMB_START = booleanPreferencesKey("alert_climb_start")
        private val KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound")
        private val KEY_ALERT_COOLDOWN = intPreferencesKey("alert_cooldown")
        private val KEY_WPRIME_ALERT_THRESHOLD = intPreferencesKey("wprime_alert_threshold")
        private val KEY_ALERT_WPRIME_DEFICIT = booleanPreferencesKey("alert_wprime_deficit")

        // W' history chart settings (W'-history-chart feature)
        private val KEY_WPRIME_CHART_WINDOW_MIN = intPreferencesKey("wprime_chart_window_min")
        private val KEY_WPRIME_CHART_REDRAW_SEC = intPreferencesKey("wprime_chart_redraw_sec")

        // Detection settings
        private val KEY_DETECTION_SENSITIVITY = stringPreferencesKey("detection_sensitivity")
        private val KEY_DETECTION_MIN_GRADE = doublePreferencesKey("detection_min_grade")
        private val KEY_DETECTION_MIN_ELEVATION = intPreferencesKey("detection_min_elevation")
        private val KEY_DETECTION_CONFIRM_DISTANCE = intPreferencesKey("detection_confirm_distance")
        private val KEY_DETECTION_END_DISTANCE = intPreferencesKey("detection_end_distance")

        // Pacing tolerance
        private val KEY_PACING_TOLERANCE = stringPreferencesKey("pacing_tolerance")
        private val KEY_PACING_TOLERANCE_WATTS = intPreferencesKey("pacing_tolerance_watts")

        // Fatigue/durability settings
        private val KEY_FATIGUE_ENABLED = booleanPreferencesKey("fatigue_enabled")
        private val KEY_FATIGUE_DECAY_RATE = doublePreferencesKey("fatigue_decay_rate")
        private val KEY_FATIGUE_THRESHOLD_HOURS = doublePreferencesKey("fatigue_threshold_hours")
    }

    val athleteProfileFlow: Flow<AthleteProfile> = context.dataStore.data
        .map { prefs ->
            AthleteProfile(
                ftp = prefs[KEY_FTP] ?: 0,
                weight = prefs[KEY_WEIGHT] ?: 0.0,
                wPrimeMax = prefs[KEY_WPRIME_MAX] ?: 20000,
                cda = prefs[KEY_CDA] ?: 0.321,
                crr = prefs[KEY_CRR] ?: 0.005,
                bikeWeight = prefs[KEY_BIKE_WEIGHT] ?: 8.0,
                cp = prefs[KEY_CP] ?: 0,
                maxPower = prefs[KEY_MAX_POWER] ?: 0,
                useKarooProfile = prefs[KEY_USE_KAROO_PROFILE] ?: true,
                karooFtp = prefs[KEY_KAROO_FTP] ?: 0,
                karooWeight = prefs[KEY_KAROO_WEIGHT] ?: 0.0,
                useThreeParamModel = prefs[KEY_USE_THREE_PARAM_MODEL] ?: false,
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

    val alertClimbStartFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_CLIMB_START] ?: true }
        .distinctUntilChanged()

    val alertSoundFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_SOUND] ?: false }
        .distinctUntilChanged()

    val alertCooldownFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_COOLDOWN] ?: 30 }
        .distinctUntilChanged()

    val wPrimeAlertThresholdFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_WPRIME_ALERT_THRESHOLD] ?: 20 }
        .distinctUntilChanged()

    /** One-shot alert when W' crosses below 0% (DEFICIT). Default ON. */
    val alertWPrimeDeficitFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALERT_WPRIME_DEFICIT] ?: true }
        .distinctUntilChanged()

    /**
     * Visible window for the W' history chart, in minutes.
     * 0 = full ride; 5–240 = rolling window. Default 0.
     */
    val wPrimeChartWindowMinutesFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_WPRIME_CHART_WINDOW_MIN] ?: 0 }
        .distinctUntilChanged()

    /**
     * Minimum interval between chart Bitmap redraws, in seconds. Samples
     * still append at 1Hz; only the chart bitmap regen is throttled.
     * Default 2, range 1–10.
     */
    val wPrimeChartRedrawSecondsFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_WPRIME_CHART_REDRAW_SEC] ?: 2 }
        .distinctUntilChanged()

    // Detection settings
    val detectionSettingsFlow: Flow<DetectionSettings> = context.dataStore.data
        .map { prefs ->
            val sensitivityName = prefs[KEY_DETECTION_SENSITIVITY] ?: "BALANCED"
            val sensitivity = try {
                DetectionSensitivity.valueOf(sensitivityName)
            } catch (e: Exception) {
                DetectionSensitivity.BALANCED
            }

            val minGrade = prefs[KEY_DETECTION_MIN_GRADE]
            val minElevation = prefs[KEY_DETECTION_MIN_ELEVATION]
            val confirmDistance = prefs[KEY_DETECTION_CONFIRM_DISTANCE]
            val endDistance = prefs[KEY_DETECTION_END_DISTANCE]

            // If any fine-tune value differs from the preset, mark as custom
            val isCustom = minGrade != null && minElevation != null &&
                confirmDistance != null && endDistance != null && (
                minGrade != sensitivity.minGrade ||
                minElevation != sensitivity.minElevation ||
                confirmDistance != sensitivity.confirmDistance ||
                endDistance != sensitivity.endDistance
            )

            DetectionSettings(
                sensitivity = sensitivity,
                minGrade = minGrade ?: sensitivity.minGrade,
                minElevation = minElevation ?: sensitivity.minElevation,
                confirmDistance = confirmDistance ?: sensitivity.confirmDistance,
                endDistance = endDistance ?: sensitivity.endDistance,
                isCustom = isCustom
            )
        }
        .distinctUntilChanged()

    // Pacing tolerance
    val pacingToleranceWattsFlow: Flow<Int> = context.dataStore.data
        .map { prefs ->
            val toleranceName = prefs[KEY_PACING_TOLERANCE]
            val customWatts = prefs[KEY_PACING_TOLERANCE_WATTS]

            if (customWatts != null && toleranceName == null) {
                customWatts.coerceIn(3, 30)
            } else {
                val tolerance = try {
                    PacingTolerance.valueOf(toleranceName ?: "NORMAL")
                } catch (e: Exception) {
                    PacingTolerance.NORMAL
                }
                customWatts ?: tolerance.watts
            }
        }
        .distinctUntilChanged()

    // Fatigue/durability flows
    val fatigueEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_FATIGUE_ENABLED] ?: true }
        .distinctUntilChanged()

    val fatigueDecayRateFlow: Flow<Double> = context.dataStore.data
        .map { prefs -> prefs[KEY_FATIGUE_DECAY_RATE] ?: 0.03 }
        .distinctUntilChanged()

    val fatigueThresholdHoursFlow: Flow<Double> = context.dataStore.data
        .map { prefs -> prefs[KEY_FATIGUE_THRESHOLD_HOURS] ?: 2.0 }
        .distinctUntilChanged()

    val pacingToleranceFlow: Flow<PacingTolerance?> = context.dataStore.data
        .map { prefs ->
            val toleranceName = prefs[KEY_PACING_TOLERANCE] ?: "NORMAL"
            try {
                val tolerance = PacingTolerance.valueOf(toleranceName)
                val customWatts = prefs[KEY_PACING_TOLERANCE_WATTS]
                if (customWatts != null && customWatts != tolerance.watts) null else tolerance
            } catch (e: Exception) {
                null
            }
        }
        .distinctUntilChanged()

    suspend fun updateFtp(ftp: Int) {
        context.dataStore.edit { it[KEY_FTP] = ftp.coerceIn(50, 600) }
    }

    suspend fun updateWeight(weight: Double) {
        context.dataStore.edit { it[KEY_WEIGHT] = weight.coerceIn(30.0, 200.0) }
    }

    suspend fun updateWPrimeMax(wPrimeMax: Int) {
        context.dataStore.edit { it[KEY_WPRIME_MAX] = wPrimeMax.coerceIn(5000, 40000) }
    }

    suspend fun updateCda(cda: Double) {
        context.dataStore.edit { it[KEY_CDA] = cda.coerceIn(0.15, 0.60) }
    }

    suspend fun updateCrr(crr: Double) {
        context.dataStore.edit { it[KEY_CRR] = crr.coerceIn(0.002, 0.015) }
    }

    suspend fun updateBikeWeight(weight: Double) {
        context.dataStore.edit { it[KEY_BIKE_WEIGHT] = weight.coerceIn(5.0, 25.0) }
    }

    suspend fun updateCp(cp: Int) {
        context.dataStore.edit { it[KEY_CP] = cp.coerceIn(0, 500) }
    }

    suspend fun updateMaxPower(maxPower: Int) {
        context.dataStore.edit { it[KEY_MAX_POWER] = maxPower.coerceIn(0, 2500) }
    }

    suspend fun updateUseKarooProfile(use: Boolean) {
        context.dataStore.edit { it[KEY_USE_KAROO_PROFILE] = use }
    }

    /** Called from ClimbIntelligenceExtension when the Karoo's UserProfile
     *  stream emits new values. Bypasses the rider-visible "manual" fields,
     *  so flipping the Karoo's profile doesn't overwrite the typed values. */
    suspend fun updateKarooFtp(ftp: Int) {
        context.dataStore.edit { it[KEY_KAROO_FTP] = ftp.coerceIn(0, 600) }
    }

    suspend fun updateKarooWeight(weightKg: Double) {
        context.dataStore.edit { it[KEY_KAROO_WEIGHT] = weightKg.coerceIn(0.0, 200.0) }
    }

    suspend fun updateUseThreeParamModel(use: Boolean) {
        context.dataStore.edit { it[KEY_USE_THREE_PARAM_MODEL] = use }
    }

    suspend fun updatePacingMode(mode: PacingMode) {
        context.dataStore.edit { it[KEY_PACING_MODE] = mode.name }
    }

    suspend fun updateAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    suspend fun updateAlertWPrime(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_WPRIME] = enabled }
    }

    suspend fun updateAlertSteep(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_STEEP] = enabled }
    }

    suspend fun updateAlertSummit(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_SUMMIT] = enabled }
    }

    suspend fun updateAlertClimbStart(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_CLIMB_START] = enabled }
    }

    suspend fun updateAlertSound(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_SOUND] = enabled }
    }

    suspend fun updateAlertCooldown(seconds: Int) {
        context.dataStore.edit { it[KEY_ALERT_COOLDOWN] = seconds.coerceIn(10, 120) }
    }

    suspend fun updateWPrimeAlertThreshold(percent: Int) {
        context.dataStore.edit { it[KEY_WPRIME_ALERT_THRESHOLD] = percent.coerceIn(5, 50) }
    }

    suspend fun updateAlertWPrimeDeficit(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_WPRIME_DEFICIT] = enabled }
    }

    suspend fun updateWPrimeChartWindowMinutes(minutes: Int) {
        // 0 = full ride; 5–240 = rolling window. Anything between 1 and 4 snaps to 5.
        val clamped = when {
            minutes <= 0 -> 0
            minutes < 5  -> 5
            else         -> minutes.coerceAtMost(240)
        }
        context.dataStore.edit { it[KEY_WPRIME_CHART_WINDOW_MIN] = clamped }
    }

    suspend fun updateWPrimeChartRedrawSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_WPRIME_CHART_REDRAW_SEC] = seconds.coerceIn(1, 10) }
    }

    suspend fun updateDetectionSensitivity(sensitivity: DetectionSensitivity) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DETECTION_SENSITIVITY] = sensitivity.name
            prefs[KEY_DETECTION_MIN_GRADE] = sensitivity.minGrade
            prefs[KEY_DETECTION_MIN_ELEVATION] = sensitivity.minElevation
            prefs[KEY_DETECTION_CONFIRM_DISTANCE] = sensitivity.confirmDistance
            prefs[KEY_DETECTION_END_DISTANCE] = sensitivity.endDistance
        }
    }

    suspend fun updateDetectionMinGrade(grade: Double) {
        context.dataStore.edit { it[KEY_DETECTION_MIN_GRADE] = grade.coerceIn(2.0, 8.0) }
    }

    suspend fun updateDetectionMinElevation(meters: Int) {
        context.dataStore.edit { it[KEY_DETECTION_MIN_ELEVATION] = meters.coerceIn(5, 50) }
    }

    suspend fun updateDetectionConfirmDistance(meters: Int) {
        context.dataStore.edit { it[KEY_DETECTION_CONFIRM_DISTANCE] = meters.coerceIn(50, 500) }
    }

    suspend fun updateDetectionEndDistance(meters: Int) {
        context.dataStore.edit { it[KEY_DETECTION_END_DISTANCE] = meters.coerceIn(50, 300) }
    }

    suspend fun updatePacingTolerance(tolerance: PacingTolerance) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PACING_TOLERANCE] = tolerance.name
            prefs[KEY_PACING_TOLERANCE_WATTS] = tolerance.watts
        }
    }

    suspend fun updatePacingToleranceWatts(watts: Int) {
        context.dataStore.edit { it[KEY_PACING_TOLERANCE_WATTS] = watts.coerceIn(3, 30) }
    }

    suspend fun updateFatigueEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FATIGUE_ENABLED] = enabled }
    }

    suspend fun updateFatigueDecayRate(rate: Double) {
        context.dataStore.edit { it[KEY_FATIGUE_DECAY_RATE] = rate.coerceIn(0.01, 0.10) }
    }

    suspend fun updateFatigueThresholdHours(hours: Double) {
        context.dataStore.edit { it[KEY_FATIGUE_THRESHOLD_HOURS] = hours.coerceIn(0.5, 5.0) }
    }
}
