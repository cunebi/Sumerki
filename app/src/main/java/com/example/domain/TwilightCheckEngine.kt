package com.example.domain

import android.content.Context
import com.example.data.local.TwilightCheckDao
import com.example.data.local.TwilightCheckEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import com.example.data.model.TwilightTimes
import com.example.data.remote.SunriseSunsetResults
import com.example.data.remote.TwilightApiService
import com.example.system.ClockAlarmHelper
import com.example.system.LocationHelper
import com.example.system.NotificationHelper
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
    private val locationHelper: LocationHelper
) {

    suspend fun executeTwilightCheck(
        isScheduledMidnight: Boolean,
        onProgress: (attempt: Int, maxAttempts: Int, statusMessage: String) -> Unit = { _, _, _ -> }
    ): CheckOutcome {
        val settings = preferencesRepository.getSnapshot()
        val maxAttempts = settings.maxRetryAttempts.coerceIn(1, 30)
        val delayMs = (settings.retryDelaySeconds.coerceIn(1, 30)) * 1000L

        val todayCal = Calendar.getInstance()
        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val todayIso = apiDateFormat.format(todayCal.time)
        val todayDisplay = displayDateFormat.format(todayCal.time)

        // Step 1: Resolve location
        var lat = settings.latitude
        var lon = settings.longitude
        var locName = settings.locationName

        if (settings.locationMode == LocationMode.AUTO) {
            onProgress(0, maxAttempts, "Определяем местоположение…")
            val detected = locationHelper.detectCurrentLocation()
            if (detected != null) {
                lat = detected.latitude
                lon = detected.longitude
                locName = detected.displayName
                preferencesRepository.saveLocation(lat, lon, locName, LocationMode.AUTO)
            }
        }

        if (lat == null || lon == null) {
            val reason = if (settings.locationMode == LocationMode.AUTO) {
                "Не удалось определить геопозицию автоматически. Выберите город вручную."
            } else {
                "Местоположение не задано. Выберите город вручную."
            }
            checkDao.insertCheck(
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
                val response = twilightApi.getTwilightForDate(
                    latitude = lat,
                    longitude = lon,
                    dateIso = todayIso,
                    formatted = 0
                )

                if (response.status.equals("OK", ignoreCase = true) && response.results != null) {
                    val parsed = parseTwilightResults(
                        results = response.results,
                        dateLabel = todayDisplay,
                        preference = settings.twilightPreference,
                        offsetMinutes = settings.alarmOffsetMinutes
                    )
                    if (parsed != null) {
                        val clockResult = ClockAlarmHelper.setAlarmInClockApp(
                            context = context,
                            hour = parsed.selectedHour,
                            minute = parsed.selectedMinute,
                            label = settings.alarmLabel
                        )

                        val summaryMsg = buildString {
                            append("Найдены ${parsed.selectedStageLabel.lowercase()} в ${parsed.selectedTimeLocal}. ")
                            append(clockResult.message)
                        }

                        checkDao.insertCheck(
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
        checkDao.insertCheck(
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
        offsetMinutes: Int
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

        val alarmCal = Calendar.getInstance().apply {
            time = chosenPair.first
            if (offsetMinutes != 0) {
                add(Calendar.MINUTE, offsetMinutes)
            }
        }

        val hour = alarmCal.get(Calendar.HOUR_OF_DAY)
        val minute = alarmCal.get(Calendar.MINUTE)
        val selectedFormatted = formatLocalTime(alarmCal.time)

        return TwilightTimes(
            dateLabel = dateLabel,
            astronomicalBeginLocal = astroDate?.let(::formatLocalTime),
            nauticalBeginLocal = nautDate?.let(::formatLocalTime),
            civilBeginLocal = civilDate?.let(::formatLocalTime),
            sunriseLocal = sunriseDate?.let(::formatLocalTime),
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

    private fun formatLocalTime(date: Date): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return formatter.format(date)
    }
}
