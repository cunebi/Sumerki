package com.example.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    private const val CHANNEL_ID = "twilight_alarm_channel"
    private const val NOTIF_ID_RESULT = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_alarm_name)
            val desc = context.getString(R.string.notification_channel_alarm_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showAlarmSetNotification(
        context: Context,
        timeText: String,
        stageLabel: String,
        locationName: String,
        attemptsUsed: Int,
        maxAttempts: Int,
        hour: Int,
        minute: Int,
        label: String,
        clockDispatched: Boolean
    ) {
        if (!PermissionStatusHelper.hasNotificationPermission(context)) return
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val setClockIntent = ClockAlarmHelper.buildSetAlarmIntent(hour, minute, label, skipUi = false)
        val setClockPendingIntent = PendingIntent.getActivity(
            context,
            11,
            setClockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val showClockPendingIntent = PendingIntent.getActivity(
            context,
            12,
            showClockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (clockDispatched) {
            "Будильник поставлен на $timeText"
        } else {
            "Сумерки в $timeText — подтвердите будильник"
        }
        val body = buildString {
            append("$stageLabel · $locationName. ")
            append("Найдено с попытки $attemptsUsed/$maxAttempts.")
            if (!clockDispatched) {
                append(" Нажмите «Поставить в Часах».")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(if (clockDispatched) contentPendingIntent else setClockPendingIntent)

        if (setClockIntent.resolveActivity(context.packageManager) != null) {
            if (clockDispatched && showClockIntent.resolveActivity(context.packageManager) != null) {
                builder.addAction(
                    android.R.drawable.ic_menu_recent_history,
                    "Открыть Часы",
                    showClockPendingIntent
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_lock_idle_alarm,
                    "Поставить в Часах",
                    setClockPendingIntent
                )
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_RESULT, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun showCheckFailedNotification(
        context: Context,
        attemptsUsed: Int,
        locationName: String,
        reason: String
    ) {
        if (!PermissionStatusHelper.hasNotificationPermission(context)) return
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            20,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "После $attemptsUsed попыток не удалось определить сумерки ($locationName). " +
            "Будильник не поставлен. Причина: $reason"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Будильник не поставлен")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_RESULT, notification)
        } catch (_: SecurityException) {
            // Ignore
        }
    }
}
