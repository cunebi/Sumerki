package com.example.domain

import android.content.Context
import com.example.data.local.TwilightCheckDao
import com.example.data.local.TwilightCheckEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import com.example.data.model.TwilightTimes
import com.example.data.remote.CuratedCities
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.SunriseSunsetResults
import com.example.data.remote.TimezoneApiService
import com.example.data.remote.TwilightApiService
import com.example.system.ClockAlarmHelper
import com.example.system.LocationHelper
import com.example.system.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface CheckOutcome {
    data class Success(
        val times: TwilightTimes,
        val attemptsUsed: Int,
        val maxAttempts: Int,
        val clockDispatched: Boolean,
        val message: String
    ) : CheckOutcome

    data class Failure(
        val attemptsUsed: Int,
        val maxAttempts: Int,
        val reason: String
    ) : CheckOutcome
}

class TwilightCheckEngine(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val checkDao: TwilightCheckDao,
    private val twilightApi: TwilightApiService,
    private val locationHelper: LocationHelper,
    private val firestoreSyncRepository: FirestoreSyncRepository? = null,
    private val timezoneApi: TimezoneApiService? = null
) {

    private suspend fun persistCheck(entity: TwilightCheckEntity) {
        checkDao.insertCheck(entity)
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            firestoreSyncRepository?.addCheckLog(uid, entity)
        }
    }

    private suspend fun resolveTimezoneId(
        latitude: Double,
        longitude: Double,
        cachedTimezoneId: String?
    ): String? {
        if (!cachedTimezoneId.isNullOrBlank() && isValidTimeZoneId(cachedTimezoneId)) {
            return cachedTimezoneId
        }
        CuratedCities.findTimezoneForCoordinates(latitude, longitude)?.let { presetTz ->
            if (isValidTimeZoneId(presetTz)) return presetTz
        }
        return try {
            timezoneApi?.getTimezoneForCoordinates(latitude, longitude)
                ?.timezone
                ?.takeIf { isValidTimeZoneId(it) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidTimeZoneId(id: String): Boolean {
        if (id.isBlank()) return false
        val tz = TimeZone.getTimeZone(id)
        return tz.id != "GMT" || id.equals("GMT", ignoreCase = true) || id.equals("UTC", ignoreCase = true)
    }

    private fun resolveTargetTimeZone(timezoneId: String?): TimeZone {
        return if (!timezoneId.isNullOrBlank() && isValidTimeZoneId(timezoneId)) {
            TimeZone.getTimeZone(timezoneId)
        } else {
            TimeZone.getDefault()
        }
    }

    suspend fun executeTwilightCheck(
        isScheduledMidnight: Boolean,
        onProgress: (attempt: Int, maxAttempts: Int, statusMessage: String) -> Unit = { _, _, _ -> }
    ): CheckOutcome {
        val settings = preferencesRepository.getSnapshot()
        val maxAttempts = settings.maxRetryAttempts.coerceIn(1, 30)
        val delayMs = (settings.retryDelaySeconds.coerceIn(1, 30)) * 1000L

        // Step 1: Resolve location
        var lat = settings.latitude
        var lon = settings.longitude
        var locName = settings.locationName
        var locationTimezoneId = settings.locationTimezoneId

        if (settings.locationMode == LocationMode.AUTO) {
            onProgress(0, maxAttempts, "Определяем местоположение…")
            val detected = locationHelper.detectCurrentLocation()
            if (detected != null) {
                lat = detected.latitude
                lon = detected.longitude
                locName = detected.displayName
                locationTimezoneId = resolveTimezoneId(lat, lon, cachedTimezoneId = null)
                preferencesRepository.saveLocation(
                    latitude = lat,
                    longitude = lon,
                    name = locName,
                    mode = LocationMode.AUTO,
                    timezoneId = locationTimezoneId
                )
            }
        }

        if (lat == null || lon == null) {
            val todayDisplay = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            val reason = if (settings.locationMode == LocationMode.AUTO) {
                "Не удалось определить геопозицию автоматически. Выберите город вручную."
            } else {
                "Местоположение не задано. Выберите город вручную."
            }
            persistCheck(
                TwilightCheckEntity(
                    timestampMillis = System.currentTimeMillis(),
                    dateLabel = todayDisplay,
                    locationName = "Не определено",
                    latitude = null,
                    longitude = null,
                    attemptsUsed = 0,
                    maxAttempts = maxAttempts,
                    success = false,
                    alarmSet = false,
                    twilightTime = null,
                    twilightStage = null,
                    message = reason,
                    isScheduledMidnight = isScheduledMidnight
                )
            )
            if (isScheduledMidnight) {
                NotificationHelper.showCheckFailedNotification(context, 0, "Не задано", reason)
            }
            return CheckOutcome.Failure(0, maxAttempts, reason)
        }

        val resolvedTimezoneId = resolveTimezoneId(lat, lon, locationTimezoneId)
        if (!resolvedTimezoneId.isNullOrBlank() && resolvedTimezoneId != settings.locationTimezoneId) {
            preferencesRepository.setLocationTimezoneId(resolvedTimezoneId)
        }
        val targetTz = resolveTargetTimeZone(resolvedTimezoneId)

        val todayCal = Calendar.getInstance(targetTz)
        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = targetTz
        }
        val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
            timeZone = targetTz
        }
        val todayIso = apiDateFormat.format(todayCal.time)
        val todayDisplay = displayDateFormat.format(todayCal.time)

        val resolvedLocName = locName.ifBlank {
            String.format(Locale.US, "%.4f°, %.4f°", lat, lon)
        }

        // Step 2: Retry up to maxAttempts to fetch earliest twilight
        var lastError = "Ответ о сумерках не найден"

        for (attempt in 1..maxAttempts) {
            onProgress(
                attempt,
                maxAttempts,
                "Запрос $attempt из $maxAttempts к службе сумерек…"
            )

            try {
                var response = twilightApi.getTwilightForDate(
                    latitude = lat,
                    longitude = lon,
                    dateIso = todayIso,
                    formatted = 0,
                    tzid = resolvedTimezoneId
                )
                if (!response.status.equals("OK", ignoreCase = true) && !resolvedTimezoneId.isNullOrBlank()) {
                    response = twilightApi.getTwilightForDate(
                        latitude = lat,
                        longitude = lon,
                        dateIso = todayIso,
                        formatted = 0,
                        tzid = null
                    )
                }

                if (response.status.equals("OK", ignoreCase = true) && response.results != null) {
                    val parsed = parseTwilightResults(
                        results = response.results,
                        dateLabel = todayDisplay,
                        preference = settings.twilightPreference,
                        offsetMinutes = settings.alarmOffsetMinutes,
                        targetTimeZone = targetTz
                    )
                    if (parsed != null) {
                        val previousAlarm = resolvePreviousAppAlarm(
                            settingsHour = settings.lastAlarmHour,
                            settingsMinute = settings.lastAlarmMinute,
                            settingsLabel = settings.lastAlarmLabel,
                            defaultLabel = settings.alarmLabel
                        )
                        var dismissedOldAlarmTime: String? = null
                        if (settings.replaceOldAlarm && previousAlarm != null) {
                            val (oldHour, oldMinute, oldLabel) = previousAlarm
                            val differs = oldHour != parsed.selectedHour ||
                                oldMinute != parsed.selectedMinute ||
                                oldLabel != settings.alarmLabel
                            if (differs) {
                                val dismissed = ClockAlarmHelper.dismissAlarmInClockApp(
                                    context = context,
                                    hour = oldHour,
                                    minute = oldMinute,
                                    label = oldLabel
                                )
                                if (dismissed) {
                                    dismissedOldAlarmTime = String.format(
                                        Locale.getDefault(),
                                        "%02d:%02d",
                                        oldHour,
                                        oldMinute
                                    )
                                    delay(300L)
                                }
                            }
                        }

                        val clockResult = ClockAlarmHelper.setAlarmInClockApp(
                            context = context,
                            hour = parsed.selectedHour,
                            minute = parsed.selectedMinute,
                            label = settings.alarmLabel
                        )

                        if (clockResult.intentDispatched) {
                            preferencesRepository.saveLastCreatedAlarm(
                                hour = parsed.selectedHour,
                                minute = parsed.selectedMinute,
                                label = settings.alarmLabel
                            )
                        }

                        val summaryMsg = buildString {
                            append("Найдены ${parsed.selectedStageLabel.lowercase()} в ${parsed.selectedTimeLocal}. ")
                            if (dismissedOldAlarmTime != null) {
                                append("Предыдущий будильник ($dismissedOldAlarmTime) отключён. ")
                            }
                            append(clockResult.message)
                        }

                        persistCheck(
                            TwilightCheckEntity(
                                timestampMillis = System.currentTimeMillis(),
                                dateLabel = todayDisplay,
                                locationName = resolvedLocName,
                                latitude = lat,
                                longitude = lon,
                                attemptsUsed = attempt,
                                maxAttempts = maxAttempts,
                                success = true,
                                alarmSet = clockResult.intentDispatched,
                                twilightTime = parsed.selectedTimeLocal,
                                twilightStage = parsed.selectedStageLabel,
                                astronomicalTime = parsed.astronomicalBeginLocal,
                                nauticalTime = parsed.nauticalBeginLocal,
                                civilTime = parsed.civilBeginLocal,
                                sunriseTime = parsed.sunriseLocal,
                                message = summaryMsg,
                                isScheduledMidnight = isScheduledMidnight
                            )
                        )

                        NotificationHelper.showAlarmSetNotification(
                            context = context,
                            timeText = parsed.selectedTimeLocal,
                            stageLabel = parsed.selectedStageLabel,
                            locationName = resolvedLocName,
                            attemptsUsed = attempt,
                            maxAttempts = maxAttempts,
                            hour = parsed.selectedHour,
                            minute = parsed.selectedMinute,
                            label = settings.alarmLabel,
                            clockDispatched = clockResult.intentDispatched
                        )

                        return CheckOutcome.Success(
                            times = parsed,
                            attemptsUsed = attempt,
                            maxAttempts = maxAttempts,
                            clockDispatched = clockResult.intentDispatched,
                            message = summaryMsg
                        )
                    } else {
                        lastError = "На выбранную дату сумерки не наступают или данные пусты"
                    }
                } else {
                    lastError = "Сервер вернул статус: ${response.status}"
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Сетевая ошибка при запросе"
            }

            if (attempt < maxAttempts) {
                onProgress(
                    attempt,
                    maxAttempts,
                    "Попытка $attempt/$maxAttempts не дала ответа. Повтор через ${settings.retryDelaySeconds} с…"
                )
                delay(delayMs)
            }
        }

        // Step 3: All attempts exhausted — DO NOT set alarm
        val failMessage = "После $maxAttempts из $maxAttempts попыток ответ не получен ($lastError). Будильник не поставлен."
        persistCheck(
            TwilightCheckEntity(
                timestampMillis = System.currentTimeMillis(),
                dateLabel = todayDisplay,
                locationName = resolvedLocName,
                latitude = lat,
                longitude = lon,
                attemptsUsed = maxAttempts,
                maxAttempts = maxAttempts,
                success = false,
                alarmSet = false,
                twilightTime = null,
                twilightStage = null,
                message = failMessage,
                isScheduledMidnight = isScheduledMidnight
            )
        )

        NotificationHelper.showCheckFailedNotification(
            context = context,
            attemptsUsed = maxAttempts,
            locationName = resolvedLocName,
            reason = lastError
        )

        return CheckOutcome.Failure(
            attemptsUsed = maxAttempts,
            maxAttempts = maxAttempts,
            reason = failMessage
        )
    }

    internal fun parseTwilightResults(
        results: SunriseSunsetResults,
        dateLabel: String,
        preference: TwilightPreference,
        offsetMinutes: Int,
        targetTimeZone: TimeZone = TimeZone.getDefault()
    ): TwilightTimes? {
        val astroDate = parseIsoUtc(results.astronomicalTwilightBegin)
        val nautDate = parseIsoUtc(results.nauticalTwilightBegin)
        val civilDate = parseIsoUtc(results.civilTwilightBegin)
        val sunriseDate = parseIsoUtc(results.sunrise)

        val chosenPair = when (preference) {
            TwilightPreference.EARLIEST_AUTO -> when {
                astroDate != null -> astroDate to "Астрономические сумерки"
                nautDate != null -> nautDate to "Навигационные сумерки"
                civilDate != null -> civilDate to "Гражданские сумерки"
                else -> null
            }
            TwilightPreference.ASTRONOMICAL -> astroDate?.let { it to "Астрономические сумерки" }
            TwilightPreference.NAUTICAL -> nautDate?.let { it to "Навигационные сумерки" }
            TwilightPreference.CIVIL -> civilDate?.let { it to "Гражданские сумерки" }
        } ?: return null

        val alarmCal = Calendar.getInstance(targetTimeZone).apply {
            time = chosenPair.first
            if (offsetMinutes != 0) {
                add(Calendar.MINUTE, offsetMinutes)
            }
        }

        val hour = alarmCal.get(Calendar.HOUR_OF_DAY)
        val minute = alarmCal.get(Calendar.MINUTE)
        val selectedFormatted = formatLocalTime(alarmCal.time, targetTimeZone)

        return TwilightTimes(
            dateLabel = dateLabel,
            astronomicalBeginLocal = astroDate?.let { formatLocalTime(it, targetTimeZone) },
            nauticalBeginLocal = nautDate?.let { formatLocalTime(it, targetTimeZone) },
            civilBeginLocal = civilDate?.let { formatLocalTime(it, targetTimeZone) },
            sunriseLocal = sunriseDate?.let { formatLocalTime(it, targetTimeZone) },
            selectedTimeLocal = selectedFormatted,
            selectedHour = hour,
            selectedMinute = minute,
            selectedStageLabel = chosenPair.second
        )
    }

    private fun parseIsoUtc(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        return try {
            val normalized = when {
                iso.endsWith("Z") -> iso.dropLast(1) + "+0000"
                iso.length >= 6 && (iso[iso.length - 3] == ':') &&
                    (iso[iso.length - 6] == '+' || iso[iso.length - 6] == '-') -> {
                    iso.substring(0, iso.length - 3) + iso.substring(iso.length - 2)
                }
                else -> iso
            }
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = parser.parse(normalized) ?: return null
            val checkCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = parsed
            }
            // sunrise-sunset.org returns 1970-01-01 when twilight doesn't occur (e.g., polar white nights)
            if (checkCal.get(Calendar.YEAR) < 2000) null else parsed
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLocalTime(date: Date, targetTimeZone: TimeZone): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = targetTimeZone
        }
        return formatter.format(date)
    }

    private suspend fun resolvePreviousAppAlarm(
        settingsHour: Int?,
        settingsMinute: Int?,
        settingsLabel: String?,
        defaultLabel: String
    ): Triple<Int, Int, String>? {
        if (settingsHour != null && settingsMinute != null) {
            return Triple(
                settingsHour.coerceIn(0, 23),
                settingsMinute.coerceIn(0, 59),
                settingsLabel?.takeIf { it.isNotBlank() } ?: defaultLabel
            )
        }
        val latestSet = checkDao.getLatestSetAlarmCheck() ?: return null
        val (h, m) = parseHourMinute(latestSet.twilightTime) ?: return null
        return Triple(h, m, defaultLabel)
    }

    internal fun parseHourMinute(hhmm: String?): Pair<Int, Int>? {
        if (hhmm.isNullOrBlank()) return null
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }
}
