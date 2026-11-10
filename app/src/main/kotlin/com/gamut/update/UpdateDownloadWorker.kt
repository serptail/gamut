package com.gamut.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val info = UpdateInfo(
            versionCode = inputData.getInt(K_CODE, 0),
            versionName = "",
            apkUrl = inputData.getString(K_URL) ?: return Result.failure(),
            sha256 = inputData.getString(K_SHA) ?: return Result.failure(),
            sizeBytes = inputData.getLong(K_SIZE, 0L),
        )
        return try {
            UpdateInstaller(applicationContext).download(info) { p -> setProgressAsync(workDataOf(K_PROGRESS to p)) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf(K_ERROR to (e.message ?: "The download failed.")))
            }
        }
    }

    companion object {
        const val K_PROGRESS = "progress"
        const val K_ERROR = "error"
        private const val K_CODE = "code"
        private const val K_URL = "url"
        private const val K_SHA = "sha"
        private const val K_SIZE = "size"
        private const val MAX_ATTEMPTS = 5

        fun nameFor(info: UpdateInfo) = "app-update-download-${info.versionCode}"

        fun enqueue(context: Context, info: UpdateInfo): UUID {
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    workDataOf(K_CODE to info.versionCode, K_URL to info.apkUrl, K_SHA to info.sha256, K_SIZE to info.sizeBytes),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(nameFor(info), ExistingWorkPolicy.REPLACE, request)
            return request.id
        }
    }
}
