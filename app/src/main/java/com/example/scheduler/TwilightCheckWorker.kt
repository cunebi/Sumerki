package com.example.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.TwilightApp
import com.example.domain.CheckOutcome

class TwilightCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TwilightApp ?: return Result.failure()
        val settings = app.container.preferencesRepository.getSnapshot()
        if (!settings.dailyMidnightEnabled && inputData.getBoolean(KEY_IS_MIDNIGHT, false)) {
            return Result.success()
        }

        val isMidnight = inputData.getBoolean(KEY_IS_MIDNIGHT, true)
        return when (app.container.twilightCheckEngine.executeTwilightCheck(isScheduledMidnight = isMidnight)) {
            is CheckOutcome.Success -> Result.success()
            // We intentionally already ran all configured attempts inside TwilightCheckEngine;
            // do not endlessly re-trigger WorkManager beyond user's configured maxAttempts.
            is CheckOutcome.Failure -> Result.success()
        }
    }

    companion object {
        const val KEY_IS_MIDNIGHT = "is_midnight"
    }
}
