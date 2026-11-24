package com.example.data.model

enum class LocationMode {
    AUTO,
    MANUAL
}

enum class TwilightPreference(
    val titleRu: String,
    val shortTitleRu: String,
    val descriptionRu: String
) {
    EARLIEST_AUTO(
        titleRu = "Самые ранние (авто)",
        shortTitleRu = "Самые ранние",
        descriptionRu = "Астрономические, либо навигационные / гражданские при белых ночах"
    ),
    ASTRONOMICAL(
        titleRu = "Астрономические сумерки",
        shortTitleRu = "Астрономические",
        descriptionRu = "Солнце на 18° ниже горизонта — самое начало рассвета"
    ),
    NAUTICAL(
        titleRu = "Навигационные сумерки",
        shortTitleRu = "Навигационные",
        descriptionRu = "Солнце на 12° ниже горизонта — виден горизонт"
    ),
    CIVIL(
        titleRu = "Гражданские сумерки",
        shortTitleRu = "Гражданские",
        descriptionRu = "Солнце на 6° ниже горизонта — светло без фонарей"
    )
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val dailyMidnightEnabled: Boolean = true,
    val maxRetryAttempts: Int = 10,
    val retryDelaySeconds: Int = 3,
    val twilightPreference: TwilightPreference = TwilightPreference.EARLIEST_AUTO,
    val alarmOffsetMinutes: Int = 0,
    val alarmLabel: String = "Самые ранние сумерки",
    val replaceOldAlarm: Boolean = true,
    val lastAlarmHour: Int? = null,
    val lastAlarmMinute: Int? = null,
    val lastAlarmLabel: String? = null,
    val locationMode: LocationMode = LocationMode.AUTO,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    val locationTimezoneId: String? = null
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null
}

data class TwilightTimes(
    val dateLabel: String,
    val astronomicalBeginLocal: String?,
    val nauticalBeginLocal: String?,
    val civilBeginLocal: String?,
    val sunriseLocal: String?,
    val selectedTimeLocal: String,
    val selectedHour: Int,
    val selectedMinute: Int,
    val selectedStageLabel: String
)

data class CityOption(
    val name: String,
    val country: String,
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String? = null
) {
    val displayTitle: String
        get() = buildString {
            append(name)
            if (!region.isNullOrBlank() && region != name) {
                append(", ")
                append(region)
            }
            if (country.isNotBlank()) {
                append(" · ")
                append(country)
            }
        }
}

data class PermissionStatus(
    val locationGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val clockAppAvailable: Boolean = true
) {
    val coreGrantedCount: Int
        get() = listOf(
            locationGranted,
            notificationsGranted,
            batteryOptimizationIgnored,
            exactAlarmGranted
        ).count { it }
}

sealed interface CheckLiveState {
    data object Idle : CheckLiveState
    data class Running(
        val attempt: Int,
        val maxAttempts: Int,
        val statusMessage: String
    ) : CheckLiveState

    data class Succeeded(
        val times: TwilightTimes,
        val attemptsUsed: Int,
        val maxAttempts: Int,
        val clockSetSuccess: Boolean,
        val message: String
    ) : CheckLiveState

    data class Failed(
        val attemptsUsed: Int,
        val maxAttempts: Int,
        val reason: String
    ) : CheckLiveState
}
