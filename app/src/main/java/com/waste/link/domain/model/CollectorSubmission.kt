package com.waste.link.domain.model

/**
 * User-supplied input required for Collector KYC fleet onboarding.
 *
 * All fields map directly to the approved Stage 2.1 schema and are validated against
 * the string length constraints enforced in `firestore.rules` (`/collector_submissions/{userId}`).
 */
data class CollectorSubmissionInput(
    val vehicleType: String,
    val vehiclePlateNumber: String,
    val licenseNumber: String,
    val serviceZoneId: String
)

/**
 * Canonical domain entity representing a Collector's fleet verification submission.
 *
 * Stored at `/collector_submissions/{userId}`.
 *
 * Security & Audit Invariants:
 * - Ordinary collectors can create this document only once (`submissionAttempt == 1`).
 * - While `status == PENDING_REVIEW`, collectors cannot edit or approve themselves.
 * - Collectors can update this document only when `status == REJECTED`, incrementing
 *   `submissionAttempt` and preserving previous review metadata.
 * - Administrative moderation fields ([reviewedByUid], [reviewedAtEpochMs], [rejectionReason], [adminNotes])
 *   are writable exclusively by administrators (`request.auth.token.admin == true`).
 */
data class CollectorSubmission(
    val userId: String,
    val vehicleType: String,
    val vehiclePlateNumber: String,
    val licenseNumber: String,
    val serviceZoneId: String,
    val status: CollectorVerificationStatus,
    val submissionAttempt: Int = 1,
    val submittedAtEpochMs: Long = 0L,
    val reviewedByUid: String? = null,
    val reviewedAtEpochMs: Long? = null,
    val rejectionReason: String? = null,
    val adminNotes: String? = null,
    val lastRejectionReason: String? = null,
    val lastReviewedByUid: String? = null,
    val lastReviewedAtEpochMs: Long? = null
) {
    init {
        require(userId.isNotBlank()) { "CollectorSubmission.userId must not be blank" }
        require(vehicleType.isNotBlank()) { "CollectorSubmission.vehicleType must not be blank" }
        require(vehiclePlateNumber.isNotBlank()) { "CollectorSubmission.vehiclePlateNumber must not be blank" }
        require(licenseNumber.isNotBlank()) { "CollectorSubmission.licenseNumber must not be blank" }
        require(serviceZoneId.isNotBlank()) { "CollectorSubmission.serviceZoneId must not be blank" }
        require(submissionAttempt >= 1) { "submissionAttempt must be >= 1" }
    }

    val isPendingReview: Boolean
        get() = status == CollectorVerificationStatus.PENDING_REVIEW

    val isApproved: Boolean
        get() = status == CollectorVerificationStatus.APPROVED

    val isRejected: Boolean
        get() = status == CollectorVerificationStatus.REJECTED
}

/**
 * Immutable audit trail record stored in the subcollection
 * `/collector_submissions/{userId}/history/{attemptId}`.
 *
 * Security Invariant:
 * - Strictly append-only: updates and deletes are permanently denied for all actors.
 */
data class CollectorSubmissionHistoryEntry(
    val attemptId: String,
    val userId: String,
    val eventType: String, // SUBMITTED, RESUBMITTED, APPROVED, REJECTED
    val vehicleType: String,
    val vehiclePlateNumber: String,
    val licenseNumber: String,
    val serviceZoneId: String,
    val submissionAttempt: Int,
    val timestampEpochMs: Long,
    val actorUid: String,
    val rejectionReason: String? = null
)

/**
 * Structured outcome of a Collector KYC onboarding submission or resubmission operation.
 */
sealed interface CollectorSubmissionOutcome {
    data class Success(
        val submission: CollectorSubmission,
        val updatedUser: User
    ) : CollectorSubmissionOutcome

    data class Failure(
        val error: AuthError
    ) : CollectorSubmissionOutcome
}

/**
 * Mapper for converting `/collector_submissions/{userId}` Firestore data maps
 * to canonical [CollectorSubmission] domain entities.
 */
object CollectorSubmissionMapper {

    fun fromFirestoreMap(data: Map<String, Any?>?): CollectorSubmission? {
        if (data == null) return null
        val userId = data["userId"] as? String ?: return null
        val vehicleType = (data["vehicleType"] as? String)?.trim().orEmpty()
        val vehiclePlateNumber = (data["vehiclePlateNumber"] as? String)?.trim().orEmpty()
        val licenseNumber = (data["licenseNumber"] as? String)?.trim().orEmpty()
        val serviceZoneId = (data["serviceZoneId"] as? String)?.trim().orEmpty()

        if (vehicleType.isEmpty() || vehiclePlateNumber.isEmpty() || licenseNumber.isEmpty() || serviceZoneId.isEmpty()) {
            return null
        }

        val statusStr = data["status"] as? String
        val status = CollectorVerificationStatus.fromFirestoreValue(statusStr, UserRole.COLLECTOR)
        val attempt = (data["submissionAttempt"] as? Number)?.toInt() ?: 1
        val submittedAt = (data["submittedAtEpochMs"] as? Number)?.toLong() ?: 0L

        return CollectorSubmission(
            userId = userId,
            vehicleType = vehicleType,
            vehiclePlateNumber = vehiclePlateNumber,
            licenseNumber = licenseNumber,
            serviceZoneId = serviceZoneId,
            status = status,
            submissionAttempt = attempt,
            submittedAtEpochMs = submittedAt,
            reviewedByUid = data["reviewedByUid"] as? String,
            reviewedAtEpochMs = (data["reviewedAtEpochMs"] as? Number)?.toLong(),
            rejectionReason = data["rejectionReason"] as? String,
            adminNotes = data["adminNotes"] as? String,
            lastRejectionReason = data["lastRejectionReason"] as? String,
            lastReviewedByUid = data["lastReviewedByUid"] as? String,
            lastReviewedAtEpochMs = (data["lastReviewedAtEpochMs"] as? Number)?.toLong()
        )
    }
}
