package com.waste.link.data.repository

import androidx.annotation.VisibleForTesting
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionHistoryEntry
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionOutcome

/**
 * Dedicated repository for WasteLink Collector Know-Your-Customer (KYC) vetting operations (Stage 5.1).
 *
 * Responsibilities:
 * - Mutex-protected initial KYC verification submission.
 * - Mutex-protected KYC resubmission after administrative rejection.
 * - Idempotent network retry handling to prevent duplicate submissions.
 * - Authoritative inspection of active application status and historical audit trails.
 * - Administrative approval and rejection helpers for integration testing.
 */
interface CollectorVerificationRepository {

    /**
     * Executes atomic WriteBatch for initial Collector KYC onboarding.
     * Requires server-verified session, role == COLLECTOR, and status == NOT_SUBMITTED.
     * Enforces concurrency protection and idempotent network retry handling.
     */
    suspend fun submitInitialVerification(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome

    /**
     * Executes atomic WriteBatch for Collector KYC resubmission after rejection.
     * Requires server-verified session, role == COLLECTOR, and status == REJECTED.
     * Enforces concurrency protection and idempotent network retry handling.
     */
    suspend fun resubmitVerification(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome

    /**
     * Fetches the active [CollectorSubmission] for [uid] from `/collector_submissions/{uid}`.
     */
    suspend fun getSubmission(
        uid: String
    ): Result<CollectorSubmission?>

    /**
     * Fetches the complete immutable history trail for [uid] from `/collector_submissions/{uid}/history`.
     */
    suspend fun getSubmissionHistory(
        uid: String
    ): Result<List<CollectorSubmissionHistoryEntry>>

    /**
     * Administrative helper to approve a collector's submission atomically.
     * Requires administrator authorization (`request.auth.token.admin == true`).
     */
    @VisibleForTesting
    suspend fun adminApproveCollectorSubmission(
        collectorUid: String,
        adminUid: String
    ): Result<Unit>

    /**
     * Administrative helper to reject a collector's submission atomically.
     * Requires administrator authorization (`request.auth.token.admin == true`).
     */
    @VisibleForTesting
    suspend fun adminRejectCollectorSubmission(
        collectorUid: String,
        adminUid: String,
        rejectionReason: String,
        adminNotes: String? = null
    ): Result<Unit>
}
