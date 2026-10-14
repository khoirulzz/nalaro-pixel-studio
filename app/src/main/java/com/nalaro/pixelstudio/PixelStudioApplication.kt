package com.nalaro.pixelstudio

import android.app.Application
import android.util.Log
import androidx.work.*
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.data.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PixelStudioApplication : Application() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("PixelStudioApp", "Unhandled background coroutine exception", throwable)
    }

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler
    )

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                val repository = ModelRepository.getInstance(this@PixelStudioApplication)
                repository.initialize()
                // Check for updates on app launch with throttling
                repository.checkForUpdates(force = false)
            } catch (t: Throwable) {
                Log.e("PixelStudioApp", "Startup repository initialization failed", t)
            }
        }

        schedulePeriodicUpdateChecks()
    }

    private fun schedulePeriodicUpdateChecks() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                12, TimeUnit.HOURS,
                2, TimeUnit.HOURS
            )
                .setInitialDelay(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UpdateCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        } catch (t: Throwable) {
            Log.e("PixelStudioApp", "Failed to schedule periodic update checks", t)
        }
    }
}
