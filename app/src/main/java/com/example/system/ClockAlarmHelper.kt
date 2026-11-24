package com.example.system

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

data class ClockSetResult(
    val intentDispatched: Boolean,
    val message: String
)

object ClockAlarmHelper {

    fun buildSetAlarmIntent(
        hour: Int,
        minute: Int,
        label: String,
        skipUi: Boolean = true
    ): Intent {
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Самые ранние сумерки" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildDismissAlarmIntent(
        hour: Int,
        minute: Int,
        label: String? = null
    ): Intent {
        val normalizedHour = hour.coerceIn(0, 23)
        val normalizedMinute = minute.coerceIn(0, 59)
        return Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            putExtra(AlarmClock.EXTRA_HOUR, normalizedHour)
            putExtra(AlarmClock.EXTRA_MINUTES, normalizedMinute)
            putExtra(AlarmClock.EXTRA_IS_PM, normalizedHour >= 12)
            if (!label.isNullOrBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun dismissAlarmInClockApp(
        context: Context,
        hour: Int,
        minute: Int,
        label: String? = null
    ): Boolean {
        val intent = buildDismissAlarmIntent(hour, minute, label)
        if (intent.resolveActivity(context.packageManager) == null) {
            return false
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun setAlarmInClockApp(
        context: Context,
        hour: Int,
        minute: Int,
        label: String
    ): ClockSetResult {
        val intent = buildSetAlarmIntent(hour, minute, label, skipUi = true)
        val resolved = intent.resolveActivity(context.packageManager)
        if (resolved == null) {
            return ClockSetResult(
                intentDispatched = false,
                message = "На устройстве не найдено приложение «Часы» для установки будильника"
            )
        }
        return try {
            context.startActivity(intent)
            val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
            ClockSetResult(
                intentDispatched = true,
                message = "Будильник на $timeStr передан в приложение «Часы»"
            )
        } catch (e: SecurityException) {
            ClockSetResult(
                intentDispatched = false,
                message = "Система ограничила запуск «Часов» из фона: используйте уведомление для подтверждения"
            )
        } catch (e: Exception) {
            ClockSetResult(
                intentDispatched = false,
                message = "Не удалось открыть «Часы»: ${e.localizedMessage ?: "ошибка системы"}"
            )
        }
    }

    fun openClockApp(context: Context): Boolean {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (showAlarms.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(showAlarms)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }
}
