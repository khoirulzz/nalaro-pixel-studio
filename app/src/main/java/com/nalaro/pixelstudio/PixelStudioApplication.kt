package com.nalaro.pixelstudio

import android.app.Application
import androidx.work.*
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.data.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PixelStudioApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            val repository = ModelRepository.getInstance(this@PixelStudioApplication)
            repository.initialize()
            // Check for updates on app launch with throttling
            repository.checkForUpdates(force = false)
        }

        schedulePeriodicUpdateChecks()
    }

    private fun schedulePeriodicUpdateChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            12, TimeUnit.HOURS,
            2, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}
