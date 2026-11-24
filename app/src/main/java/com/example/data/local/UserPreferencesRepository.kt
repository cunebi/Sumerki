package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "twilight_alarm_prefs"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DAILY_MIDNIGHT_ENABLED = booleanPreferencesKey("daily_midnight_enabled")
        val MAX_RETRY_ATTEMPTS = intPreferencesKey("max_retry_attempts")
        val RETRY_DELAY_SECONDS = intPreferencesKey("retry_delay_seconds")
        val TWILIGHT_PREFERENCE = stringPreferencesKey("twilight_preference")
        val ALARM_OFFSET_MINUTES = intPreferencesKey("alarm_offset_minutes")
        val ALARM_LABEL = stringPreferencesKey("alarm_label")
        val REPLACE_OLD_ALARM = booleanPreferencesKey("replace_old_alarm")
        val LAST_ALARM_HOUR = intPreferencesKey("last_alarm_hour")
        val LAST_ALARM_MINUTE = intPreferencesKey("last_alarm_minute")
        val LAST_ALARM_LABEL = stringPreferencesKey("last_alarm_label")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val LOCATION_TIMEZONE_ID = stringPreferencesKey("location_timezone_id")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            dailyMidnightEnabled = prefs[Keys.DAILY_MIDNIGHT_ENABLED] ?: true,
            maxRetryAttempts = (prefs[Keys.MAX_RETRY_ATTEMPTS] ?: 10).coerceIn(1, 30),
            retryDelaySeconds = (prefs[Keys.RETRY_DELAY_SECONDS] ?: 3).coerceIn(1, 30),
            twilightPreference = prefs[Keys.TWILIGHT_PREFERENCE]?.let { name ->
                TwilightPreference.entries.find { it.name == name }
            } ?: TwilightPreference.EARLIEST_AUTO,
            alarmOffsetMinutes = (prefs[Keys.ALARM_OFFSET_MINUTES] ?: 0).coerceIn(-30, 30),
            alarmLabel = prefs[Keys.ALARM_LABEL]?.takeIf { it.isNotBlank() }
                ?: "Самые ранние сумерки",
            replaceOldAlarm = prefs[Keys.REPLACE_OLD_ALARM] ?: true,
            lastAlarmHour = prefs[Keys.LAST_ALARM_HOUR],
            lastAlarmMinute = prefs[Keys.LAST_ALARM_MINUTE],
            lastAlarmLabel = prefs[Keys.LAST_ALARM_LABEL],
            locationMode = prefs[Keys.LOCATION_MODE]?.let { name ->
                LocationMode.entries.find { it.name == name }
            } ?: LocationMode.AUTO,
            latitude = prefs[Keys.LATITUDE],
            longitude = prefs[Keys.LONGITUDE],
            locationName = prefs[Keys.LOCATION_NAME] ?: "",
            locationTimezoneId = prefs[Keys.LOCATION_TIMEZONE_ID]
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDailyMidnightEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DAILY_MIDNIGHT_ENABLED] = enabled }
    }

    suspend fun setMaxRetryAttempts(attempts: Int) {
        context.settingsDataStore.edit {
            it[Keys.MAX_RETRY_ATTEMPTS] = attempts.coerceIn(1, 30)
        }
    }

    suspend fun setRetryDelaySeconds(seconds: Int) {
        context.settingsDataStore.edit {
            it[Keys.RETRY_DELAY_SECONDS] = seconds.coerceIn(1, 30)
        }
    }

    suspend fun setTwilightPreference(preference: TwilightPreference) {
        context.settingsDataStore.edit { it[Keys.TWILIGHT_PREFERENCE] = preference.name }
    }

    suspend fun setAlarmOffsetMinutes(minutes: Int) {
        context.settingsDataStore.edit {
            it[Keys.ALARM_OFFSET_MINUTES] = minutes.coerceIn(-30, 30)
        }
    }

    suspend fun setAlarmLabel(label: String) {
        context.settingsDataStore.edit {
            it[Keys.ALARM_LABEL] = label.trim().ifEmpty { "Самые ранние сумерки" }
        }
    }

    suspend fun setReplaceOldAlarm(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.REPLACE_OLD_ALARM] = enabled
        }
    }

    suspend fun saveLastCreatedAlarm(hour: Int, minute: Int, label: String) {
        context.settingsDataStore.edit {
            it[Keys.LAST_ALARM_HOUR] = hour.coerceIn(0, 23)
            it[Keys.LAST_ALARM_MINUTE] = minute.coerceIn(0, 59)
            it[Keys.LAST_ALARM_LABEL] = label.trim().ifEmpty { "Самые ранние сумерки" }
        }
    }

    suspend fun setLocationMode(mode: LocationMode) {
        context.settingsDataStore.edit { it[Keys.LOCATION_MODE] = mode.name }
    }

    suspend fun saveLocation(
        latitude: Double,
        longitude: Double,
        name: String,
        mode: LocationMode,
        timezoneId: String? = null
    ) {
        context.settingsDataStore.edit {
            it[Keys.LATITUDE] = latitude
            it[Keys.LONGITUDE] = longitude
            it[Keys.LOCATION_NAME] = name
            it[Keys.LOCATION_MODE] = mode.name
            if (!timezoneId.isNullOrBlank()) {
                it[Keys.LOCATION_TIMEZONE_ID] = timezoneId
            } else {
                it.remove(Keys.LOCATION_TIMEZONE_ID)
            }
        }
    }

    suspend fun setLocationTimezoneId(timezoneId: String) {
        if (timezoneId.isBlank()) return
        context.settingsDataStore.edit {
            it[Keys.LOCATION_TIMEZONE_ID] = timezoneId
        }
    }
}
