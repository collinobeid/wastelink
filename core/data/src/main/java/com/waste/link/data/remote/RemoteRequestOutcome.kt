package com.waste.link.data.remote

import com.waste.link.domain.model.WasteRequest

/**
 * Standardized outcome hierarchy for remote request creation, idempotent verification,
 * and remote query operations.
 *
 * Strict Architecture Invariants:
 * 1. Encapsulates remote communication results cleanly in `:core:data`.
 * 2. Separates permanent rejections (e.g. security rules, account locked, invalid category/zone)
 *    from transient network errors safe for exponential backoff retry.
 * 3. Supports deterministic idempotency reconciliation ([AlreadyExists]) without duplicating writes.
 */
sealed interface RemoteRequestOutcome {

    /**
     * Request successfully created and confirmed by the remote backend authority.
     *
     * @property confirmedRequest Domain request with server-allocated reference and timestamps.
     * @property requestRef Authoritative human-readable reference allocated by backend.
     * @property serverCreatedAtEpochMs Authoritative creation timestamp in epoch milliseconds.
     * @property serverUpdatedAtEpochMs Authoritative update timestamp in epoch milliseconds.
     */
    data class Success(
        val confirmedRequest: WasteRequest,
        val requestRef: String,
        val serverCreatedAtEpochMs: Long,
        val serverUpdatedAtEpochMs: Long
    ) : RemoteRequestOutcome

    /**
     * Document already exists in Firestore under this client-generated UUID for this user.
     * Reconciled idempotently without duplicate creation.
     */
    data class AlreadyExists(
        val existingRequest: WasteRequest,
        val requestRef: String,
        val serverCreatedAtEpochMs: Long,
        val serverUpdatedAtEpochMs: Long
    ) : RemoteRequestOutcome

    /**
     * Non-retryable permanent failure (e.g. security rule rejection PERMISSION_DENIED,
     * account suspended/banned, inactive category, unmapped/quarantined service zone).
     */
    data class Rejected(
        val errorCode: String,
        val message: String,
        val requiresUserIntervention: Boolean = true
    ) : RemoteRequestOutcome

    /**
     * Transient network or infrastructure failure safe for bounded exponential backoff retry
     * (e.g. network disconnect, timeout, Firestore UNAVAILABLE, 503).
     */
    data class TransientFailure(
        val cause: Throwable,
        val message: String = cause.message ?: "Transient network failure during sync."
    ) : RemoteRequestOutcome
}
