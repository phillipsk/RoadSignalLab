package io.roadsignal.sdk

import android.content.Context
import androidx.work.*
import io.roadsignal.sdk.internal.TripStore
import java.util.concurrent.TimeUnit

/**
 * Uses durable WorkManager scheduling for a mock, local-only transport. It does not upload to CMT.
 */
class UploadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            val report = TripStore(applicationContext).use { it.sync() }
            if (report.retryable > 0) Result.retry() else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }

    companion object {
        internal const val UNIQUE_NAME = "io.roadsignal.sdk.outbox"

        @JvmStatic
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .addTag(UNIQUE_NAME)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }

        internal fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
