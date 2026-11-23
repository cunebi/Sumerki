package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

class MidnightCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Immediately schedule tomorrow's 00:00 trigger
        MidnightScheduler.scheduleDailyMidnightCheck(context)

        val workRequest = OneTimeWorkRequestBuilder<TwilightCheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(TwilightCheckWorker.KEY_IS_MIDNIGHT to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "twilight_midnight_immediate_run",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
