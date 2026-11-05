package com.waste.link.data.sync

/**
 * Observable events emitted by [WasteRequestSyncOrchestrator] across the request synchronization lifecycle.
 */
sealed interface SyncEvent {
    val timestampEpochMs: Long

    data class UploadStarted(
        val requestId: String,
        val clientUid: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class UploadSucceeded(
        val requestId: String,
        val requestRef: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class UploadFailed(
        val requestId: String,
        val errorMessage: String,
        val isRetryable: Boolean,
        val retryAttempt: Int,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class RetryScheduled(
        val requestId: String,
        val retryAttempt: Int,
        val nextAttemptDelayMs: Long,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class AuthUnavailable(
        val reason: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class RequestRejected(
        val requestId: String,
        val reason: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class DuplicateReconciled(
        val requestId: String,
        val requestRef: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent

    data class LegacyRecordQuarantined(
        val requestId: String,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : SyncEvent
}
