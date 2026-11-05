package com.waste.link.domain.validation

import com.waste.link.domain.model.CancellationLockedException
import com.waste.link.domain.model.DisputeWindowExpiredException
import com.waste.link.domain.model.InvalidStateTransitionException
import com.waste.link.domain.model.MissingFailureEvidenceException
import com.waste.link.domain.model.MissingProofOfCollectionException
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.UnauthorizedTransitionException
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.model.WasteRequest

/**
 * Client-side actor-aware state machine validator for WasteLink waste collection requests.
 *
 * Implements client-side checks for transitions TR-01 through TR-16 from `docs/MODULE_2_RESIDENT_REQUEST_ARCHITECTURE.md`.
 * Strictly checks actor identity, roles, preconditions, photo proofs, and dispute windows.
 *
 * NOTE ON TRUSTED BACKEND ENFORCEMENT:
 * This validator enforces business-domain preconditions on the client before operations are transmitted.
 * It is NOT server-authoritative. Conditions that require trusted server-side proof (such as genuine GPS
 * proximity, physical geofence arrival at dumpsite polygon, image hash authenticity, and server-authoritative
 * timestamps via request.time) are enforced independently on the backend by Firestore Security Rules
 * and Cloud Functions.
 */
object RequestStateTransitionValidator {

    /** Maximum allowed grievance window for residents post-completion/failure (48 hours). */
    const val DISPUTE_WINDOW_MS: Long = 48L * 60L * 60L * 1000L

    /**
     * Validates whether a state transition from [request.status] to [targetStatus] is authorized
     * and satisfies all operational preconditions.
     *
     * @param request The current authoritative [WasteRequest].
     * @param targetStatus The desired destination [RequestLifecycleStatus].
     * @param actorUid The UID of the authenticated user attempting the transition.
     * @param actorRole The verified [UserRole] of the actor.
     * @param currentTimeEpochMs Authoritative timestamp of the transition attempt.
     * @param hasProofOfCollection True if valid PoC photo (URL or Base64) is supplied.
     * @param hasFailureEvidence True if valid failure photo (URL or Base64) is supplied.
     * @param failureReason Non-empty explanation required when transitioning to [RequestLifecycleStatus.FAILED].
     * @param disputeReason Non-empty grievance explanation required when transitioning to [RequestLifecycleStatus.DISPUTED].
     * @param adminNote Administrative resolution justification required when settling disputes.
     * @param assignedCollectorUid Required driver UID when allocating to [RequestLifecycleStatus.ASSIGNED_TO_FLEET].
     * @param assignedVehicleId Required vehicle ID when allocating to [RequestLifecycleStatus.ASSIGNED_TO_FLEET].
     *
     * @throws CancellationLockedException if a resident attempts to cancel an already-assigned or executed request.
     * @throws MissingProofOfCollectionException if transitioning to COLLECTION_VERIFIED without PoC photo.
     * @throws MissingFailureEvidenceException if transitioning to FAILED without photo proof or reason.
     * @throws DisputeWindowExpiredException if filing a dispute after 48 hours have elapsed.
     * @throws UnauthorizedTransitionException if the actor is not authorized for this transition.
     * @throws InvalidStateTransitionException if the transition path is invalid in the state machine.
     */
    fun validateTransition(
        request: WasteRequest,
        targetStatus: RequestLifecycleStatus,
        actorUid: String,
        actorRole: UserRole,
        currentTimeEpochMs: Long,
        hasProofOfCollection: Boolean = false,
        hasFailureEvidence: Boolean = false,
        failureReason: String? = null,
        disputeReason: String? = null,
        adminNote: String? = null,
        assignedCollectorUid: String? = null,
        assignedVehicleId: String? = null
    ) {
        val current = request.status

        // Redundant transition check (same state)
        if (current == targetStatus) {
            throw InvalidStateTransitionException(
                "Request is already in state $current; self-transition is not permitted."
            )
        }

        // Terminal states cannot transition to any other state except DISPUTED from COMPLETED/FAILED
        if (current.isTerminal && !(current == RequestLifecycleStatus.COMPLETED && targetStatus == RequestLifecycleStatus.DISPUTED)) {
            throw InvalidStateTransitionException(
                "Cannot transition from terminal state $current to $targetStatus."
            )
        }

        when (targetStatus) {
            // TR-02: PENDING_DISPATCH -> CANCELLED
            RequestLifecycleStatus.CANCELLED -> {
                if (actorRole == UserRole.RESIDENT) {
                    if (request.clientUid != actorUid) {
                        throw UnauthorizedTransitionException(
                            "Residents can only cancel their own requests (owner: ${request.clientUid}, actor: $actorUid)."
                        )
                    }
                    if (current != RequestLifecycleStatus.PENDING_DISPATCH) {
                        throw CancellationLockedException(
                            "Request has already progressed to $current; cancellation locks upon assignment."
                        )
                    }
                } else if (actorRole == UserRole.ADMIN) {
                    // Admin can cancel from PENDING_DISPATCH, FLAGGED_FOR_REVIEW, or ASSIGNED_TO_FLEET
                    if (current !in setOf(
                            RequestLifecycleStatus.PENDING_DISPATCH,
                            RequestLifecycleStatus.FLAGGED_FOR_REVIEW,
                            RequestLifecycleStatus.ASSIGNED_TO_FLEET
                        )
                    ) {
                        throw InvalidStateTransitionException(
                            "Admin cannot cancel request currently in field execution state $current."
                        )
                    }
                    if (adminNote.isNullOrBlank()) {
                        throw InvalidStateTransitionException(
                            "Admin cancellation requires a non-blank administrative justification note."
                        )
                    }
                } else {
                    throw UnauthorizedTransitionException(
                        "Role $actorRole is not authorized to cancel requests."
                    )
                }
            }

            // TR-03: PENDING_DISPATCH -> FLAGGED_FOR_REVIEW
            RequestLifecycleStatus.FLAGGED_FOR_REVIEW -> {
                if (actorRole != UserRole.ADMIN) {
                    throw UnauthorizedTransitionException(
                        "Only administrators or automated security triggers can quarantine requests."
                    )
                }
                if (current != RequestLifecycleStatus.PENDING_DISPATCH) {
                    throw InvalidStateTransitionException(
                        "Only PENDING_DISPATCH requests can be flagged for review, current is $current."
                    )
                }
            }

            // TR-04: FLAGGED_FOR_REVIEW -> PENDING_DISPATCH
            // TR-06: ASSIGNED_TO_FLEET -> PENDING_DISPATCH (driver drops task)
            RequestLifecycleStatus.PENDING_DISPATCH -> {
                when (current) {
                    RequestLifecycleStatus.FLAGGED_FOR_REVIEW -> {
                        if (actorRole != UserRole.ADMIN) {
                            throw UnauthorizedTransitionException(
                                "Only administrators can release quarantined requests back to PENDING_DISPATCH."
                            )
                        }
                    }
                    RequestLifecycleStatus.ASSIGNED_TO_FLEET -> {
                        val isAssignedDriver = actorRole == UserRole.COLLECTOR && request.assignedCollectorUid == actorUid
                        val isAdmin = actorRole == UserRole.ADMIN
                        if (!isAssignedDriver && !isAdmin) {
                            throw UnauthorizedTransitionException(
                                "Only the assigned driver or an administrator can drop an assignment."
                            )
                        }
                    }
                    else -> throw InvalidStateTransitionException(
                        "Cannot transition from $current back to PENDING_DISPATCH."
                    )
                }
            }

            // TR-05: PENDING_DISPATCH or FLAGGED_FOR_REVIEW -> ASSIGNED_TO_FLEET
            RequestLifecycleStatus.ASSIGNED_TO_FLEET -> {
                if (actorRole != UserRole.ADMIN) {
                    throw UnauthorizedTransitionException(
                        "Only administrators or automated dispatch engines can allocate fleet resources."
                    )
                }
                if (current !in setOf(RequestLifecycleStatus.PENDING_DISPATCH, RequestLifecycleStatus.FLAGGED_FOR_REVIEW)) {
                    throw InvalidStateTransitionException(
                        "Cannot assign fleet from current state $current."
                    )
                }
                val collector = assignedCollectorUid ?: request.assignedCollectorUid
                val vehicle = assignedVehicleId ?: request.assignedVehicleId
                if (collector.isNullOrBlank() || vehicle.isNullOrBlank()) {
                    throw InvalidStateTransitionException(
                        "Fleet allocation requires valid assignedCollectorUid and assignedVehicleId."
                    )
                }
            }

            // TR-07: ASSIGNED_TO_FLEET -> EN_ROUTE
            RequestLifecycleStatus.EN_ROUTE -> {
                assertAssignedCollector(request, actorUid, actorRole)
                if (current != RequestLifecycleStatus.ASSIGNED_TO_FLEET) {
                    throw InvalidStateTransitionException(
                        "Cannot start route to pickup location from current state $current (must be ASSIGNED_TO_FLEET)."
                    )
                }
            }

            // TR-08: EN_ROUTE -> ARRIVED_AT_LOCATION
            RequestLifecycleStatus.ARRIVED_AT_LOCATION -> {
                assertAssignedCollector(request, actorUid, actorRole)
                if (current != RequestLifecycleStatus.EN_ROUTE) {
                    throw InvalidStateTransitionException(
                        "Cannot arrive at pickup location from current state $current (must be EN_ROUTE)."
                    )
                }
            }

            // TR-10: ARRIVED_AT_LOCATION -> COLLECTION_VERIFIED
            RequestLifecycleStatus.COLLECTION_VERIFIED -> {
                assertAssignedCollector(request, actorUid, actorRole)
                if (current != RequestLifecycleStatus.ARRIVED_AT_LOCATION) {
                    throw InvalidStateTransitionException(
                        "Cannot verify collection from current state $current (must be ARRIVED_AT_LOCATION)."
                    )
                }
                if (!hasProofOfCollection && !request.hasProofOfCollection) {
                    throw MissingProofOfCollectionException(
                        "Mandatory photographic Proof of Collection (PoC) must be attached to verify collection."
                    )
                }
            }

            // TR-12: COLLECTION_VERIFIED -> IN_TRANSIT_TO_DUMP
            RequestLifecycleStatus.IN_TRANSIT_TO_DUMP -> {
                assertAssignedCollector(request, actorUid, actorRole)
                if (current != RequestLifecycleStatus.COLLECTION_VERIFIED) {
                    throw InvalidStateTransitionException(
                        "Cannot begin transit to dump from current state $current (must be COLLECTION_VERIFIED)."
                    )
                }
            }

            // TR-13: IN_TRANSIT_TO_DUMP -> COMPLETED
            RequestLifecycleStatus.COMPLETED -> {
                val isAssignedDriver = actorRole == UserRole.COLLECTOR && request.assignedCollectorUid == actorUid
                val isAdmin = actorRole == UserRole.ADMIN
                if (!isAssignedDriver && !isAdmin) {
                    throw UnauthorizedTransitionException(
                        "Only the assigned collector or an administrator can mark a request COMPLETED."
                    )
                }
                if (current != RequestLifecycleStatus.IN_TRANSIT_TO_DUMP) {
                    throw InvalidStateTransitionException(
                        "Cannot complete request directly from $current (must progress sequentially via IN_TRANSIT_TO_DUMP)."
                    )
                }
            }

            // TR-09 & TR-11: EN_ROUTE or ARRIVED_AT_LOCATION -> FAILED
            RequestLifecycleStatus.FAILED -> {
                assertAssignedCollector(request, actorUid, actorRole)
                if (current !in setOf(RequestLifecycleStatus.EN_ROUTE, RequestLifecycleStatus.ARRIVED_AT_LOCATION)) {
                    throw InvalidStateTransitionException(
                        "Cannot mark request as FAILED from current state $current."
                    )
                }
                val hasPhoto = hasFailureEvidence || !request.failurePhotoUrl.isNullOrBlank() || !request.failurePhotoBase64.isNullOrBlank()
                val reason = failureReason ?: request.failureReason
                if (!hasPhoto || reason.isNullOrBlank()) {
                    throw MissingFailureEvidenceException(
                        "Marking a pickup as FAILED requires both photographic evidence and a non-empty failureReason."
                    )
                }
            }

            // TR-14: FAILED or COMPLETED -> DISPUTED
            RequestLifecycleStatus.DISPUTED -> {
                if (actorRole != UserRole.RESIDENT || request.clientUid != actorUid) {
                    throw UnauthorizedTransitionException(
                        "Only the resident owner can file a service dispute on their request."
                    )
                }
                if (current !in setOf(RequestLifecycleStatus.COMPLETED, RequestLifecycleStatus.FAILED)) {
                    throw InvalidStateTransitionException(
                        "Disputes can only be filed against COMPLETED or FAILED requests, current is $current."
                    )
                }
                if (disputeReason.isNullOrBlank()) {
                    throw InvalidStateTransitionException(
                        "Filing a dispute requires a non-blank grievance explanation (disputeReason)."
                    )
                }
                // TR-14: Fail-closed dispute eligibility validation
                // Authoritative terminal timestamp must strictly match the current terminal state
                val terminalTimestamp = when (current) {
                    RequestLifecycleStatus.COMPLETED -> request.completedAtEpochMs
                    RequestLifecycleStatus.FAILED -> request.failedAtEpochMs
                    else -> null
                }

                if (terminalTimestamp == null || terminalTimestamp <= 0L) {
                    throw InvalidStateTransitionException(
                        "Cannot file dispute: request is missing authoritative terminal timestamp for state $current."
                    )
                }

                if (terminalTimestamp > currentTimeEpochMs) {
                    throw InvalidStateTransitionException(
                        "Cannot file dispute: authoritative terminal timestamp ($terminalTimestamp) is in the future relative to current time ($currentTimeEpochMs)."
                    )
                }

                val elapsedMs = currentTimeEpochMs - terminalTimestamp
                if (elapsedMs > DISPUTE_WINDOW_MS) {
                    throw DisputeWindowExpiredException(
                        "The 48-hour dispute window has expired ($elapsedMs ms elapsed > $DISPUTE_WINDOW_MS ms). Grievance cannot be accepted."
                    )
                }
            }

            // TR-15: DISPUTED -> RESOLVED
            RequestLifecycleStatus.RESOLVED -> {
                if (actorRole != UserRole.ADMIN) {
                    throw UnauthorizedTransitionException(
                        "Only administrators can arbitrate and resolve disputes."
                    )
                }
                if (current != RequestLifecycleStatus.DISPUTED) {
                    throw InvalidStateTransitionException(
                        "Only DISPUTED requests can be marked RESOLVED, current is $current."
                    )
                }
                if (adminNote.isNullOrBlank()) {
                    throw InvalidStateTransitionException(
                        "Resolving a dispute requires a non-blank administrative resolution note."
                    )
                }
            }

            // TR-16: DISPUTED -> DISMISSED
            RequestLifecycleStatus.DISMISSED -> {
                if (actorRole != UserRole.ADMIN) {
                    throw UnauthorizedTransitionException(
                        "Only administrators can dismiss disputes."
                    )
                }
                if (current != RequestLifecycleStatus.DISPUTED) {
                    throw InvalidStateTransitionException(
                        "Only DISPUTED requests can be DISMISSED, current is $current."
                    )
                }
                if (adminNote.isNullOrBlank()) {
                    throw InvalidStateTransitionException(
                        "Dismissing a dispute requires a non-blank administrative resolution note."
                    )
                }
            }
        }
    }

    private fun assertAssignedCollector(request: WasteRequest, actorUid: String, actorRole: UserRole) {
        if (actorRole != UserRole.COLLECTOR) {
            throw UnauthorizedTransitionException(
                "Role $actorRole is not authorized for field collection transitions."
            )
        }
        if (request.assignedCollectorUid != actorUid) {
            throw UnauthorizedTransitionException(
                "Collector $actorUid is not assigned to this request (assigned: ${request.assignedCollectorUid})."
            )
        }
    }
}
