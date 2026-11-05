package com.waste.link.data.sync

/**
 * Result returned from request synchronization operations.
 */
sealed interface SyncResult {
    data class Success(val requestId: String, val requestRef: String) : SyncResult
    data class QueuedOffline(val requestId: String, val reason: String) : SyncResult
    data class Rejected(val requestId: String, val errorCode: String, val message: String) : SyncResult
    data class AuthUnavailable(val reason: String) : SyncResult
    data class Failure(val requestId: String, val error: Throwable) : SyncResult
}
