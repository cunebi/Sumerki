package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.TwilightCheckEntity
import com.example.data.model.AppSettings
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudUserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val onboardingCompleted: Boolean = false,
    val dailyMidnightEnabled: Boolean = true,
    val maxRetryAttempts: Long = 10L,
    val retryDelaySeconds: Long = 3L,
    val twilightPreference: String = TwilightPreference.EARLIEST_AUTO.name,
    val alarmOffsetMinutes: Long = 0L,
    val alarmLabel: String = "Самые ранние сумерки",
    val locationMode: String = LocationMode.AUTO.name,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toAppSettings(): AppSettings {
        val pref = enumValues<TwilightPreference>().firstOrNull { it.name == twilightPreference }
            ?: TwilightPreference.EARLIEST_AUTO
        val locMode = enumValues<LocationMode>().firstOrNull { it.name == locationMode }
            ?: LocationMode.AUTO
        return AppSettings(
            onboardingCompleted = onboardingCompleted,
            dailyMidnightEnabled = dailyMidnightEnabled,
            maxRetryAttempts = maxRetryAttempts.toInt().coerceIn(1, 30),
            retryDelaySeconds = retryDelaySeconds.toInt().coerceIn(1, 30),
            twilightPreference = pref,
            alarmOffsetMinutes = alarmOffsetMinutes.toInt().coerceIn(-30, 30),
            alarmLabel = alarmLabel.ifBlank { "Самые ранние сумерки" },
            locationMode = locMode,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CloudTwilightCheck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateLabel: String = "",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val attemptsUsed: Long = 0L,
    val maxAttempts: Long = 10L,
    val succeeded: Boolean = false,
    val alarmSet: Boolean = false,
    val scheduledMidnight: Boolean = false,
    val twilightTime: String? = null,
    val twilightStage: String? = null,
    val astronomicalTime: String? = null,
    val nauticalTime: String? = null,
    val civilTime: String? = null,
    val sunriseTime: String? = null,
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toLocalEntity(): TwilightCheckEntity {
        val ts = (updatedAt ?: createdAt ?: Timestamp(0, 0)).toDate().time
        return TwilightCheckEntity(
            id = id.hashCode().toLong(),
            timestampMillis = ts,
            dateLabel = dateLabel,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude,
            attemptsUsed = attemptsUsed.toInt(),
            maxAttempts = maxAttempts.toInt(),
            success = succeeded,
            alarmSet = alarmSet,
            twilightTime = twilightTime,
            twilightStage = twilightStage,
            astronomicalTime = astronomicalTime,
            nauticalTime = nauticalTime,
            civilTime = civilTime,
            sunriseTime = sunriseTime,
            message = message,
            isScheduledMidnight = scheduledMidnight
        )
    }
}
