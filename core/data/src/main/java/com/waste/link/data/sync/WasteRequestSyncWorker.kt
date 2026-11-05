package com.waste.link.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.domain.model.SessionState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background synchronization worker scheduled by WorkManager.
 *
 * Guarantees:
 * 1. Executes strictly on NetworkType.CONNECTED.
 * 2. Derives identity strictly from trusted session ([SessionStateProvider]).
 * 3. Never uploads requests when unauthenticated or cross-account.
 * 4. Retries automatically on transient network drop-off.
 */
@HiltWorker
class WasteRequestSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncOrchestrator: WasteRequestSyncOrchestrator,
    private val sessionStateProvider: SessionStateProvider
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "WasteRequestSyncWorker"
        const val WORK_NAME_PREFIX = "sync_waste_requests_"

        fun uniqueWorkName(authenticatedUid: String): String =
            "$WORK_NAME_PREFIX$authenticatedUid"

        fun enqueueImmediateSync(context: Context, authenticatedUid: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WasteRequestSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(authenticatedUid),
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueuePeriodicSync(context: Context, authenticatedUid: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WasteRequestSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName(authenticatedUid),
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelAllSyncWork(context: Context, authenticatedUid: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(authenticatedUid))
        }
    }

    override suspend fun doWork(): Result {
        val session = sessionStateProvider.sessionState.value
        if (session !is SessionState.Authenticated || !session.isServerVerified) {
            // Cannot upload while unauthenticated or session not server-verified
            return Result.success()
        }

        val authUid = session.user.id
        return try {
            val results = syncOrchestrator.synchronizeQueuedRequests(authUid)
            val hasTransientFailures = results.any { it is SyncResult.QueuedOffline }
            if (hasTransientFailures) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
