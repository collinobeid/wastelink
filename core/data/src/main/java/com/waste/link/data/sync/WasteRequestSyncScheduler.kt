package com.waste.link.data.sync

/**
 * Contract for scheduling immediate and delayed background synchronization
 * via WorkManager with strict per-user isolation and bounded backoff.
 */
interface WasteRequestSyncScheduler {
    /**
     * Enqueues an immediate synchronization attempt for [authenticatedUid] when network is CONNECTED.
     */
    fun scheduleImmediateSync(authenticatedUid: String)

    /**
     * Enqueues a delayed synchronization retry for [authenticatedUid] with [delayMs] initial backoff.
     */
    fun scheduleDelayedSync(authenticatedUid: String, delayMs: Long, retryAttempt: Int)

    /**
     * Cancels any pending or active synchronization work for [authenticatedUid].
     */
    fun cancelSync(authenticatedUid: String)
}
