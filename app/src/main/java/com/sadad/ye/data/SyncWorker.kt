package com.sadad.ye.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DataRepository(applicationContext)
        return try {
            repository.syncWithCloud()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
