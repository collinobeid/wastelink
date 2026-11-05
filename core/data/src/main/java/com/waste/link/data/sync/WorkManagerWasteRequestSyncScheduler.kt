package com.waste.link.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [WasteRequestSyncScheduler] backed by Android [WorkManager].
 *
 * Guarantees:
 * 1. Executes strictly on NetworkType.CONNECTED.
 * 2. Uniquely keyed by authenticated UID to preserve account isolation.
 * 3. Enforces bounded exponential backoff.
 * 4. Prevents duplicate workers using [ExistingWorkPolicy.KEEP] and [ExistingWorkPolicy.REPLACE].
 */
@Singleton
class WorkManagerWasteRequestSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : WasteRequestSyncScheduler {

    override fun scheduleImmediateSync(authenticatedUid: String) {
        if (authenticatedUid.isBlank()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WasteRequestSyncWorker>()
            .setConstraints(constraints)
            .addTag(WasteRequestSyncWorker.TAG)
            .addTag("user_$authenticatedUid")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WasteRequestSyncWorker.uniqueWorkName(authenticatedUid),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun scheduleDelayedSync(authenticatedUid: String, delayMs: Long, retryAttempt: Int) {
        if (authenticatedUid.isBlank()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WasteRequestSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(WasteRequestSyncWorker.TAG)
            .addTag("user_$authenticatedUid")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WasteRequestSyncWorker.uniqueWorkName(authenticatedUid),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelSync(authenticatedUid: String) {
        if (authenticatedUid.isBlank()) return
        WorkManager.getInstance(context).cancelUniqueWork(
            WasteRequestSyncWorker.uniqueWorkName(authenticatedUid)
        )
    }
}
