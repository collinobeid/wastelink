package com.waste.link.domain

import com.waste.link.domain.model.CancellationLockedException
import com.waste.link.domain.model.DisputeWindowExpiredException
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.InvalidStateTransitionException
import com.waste.link.domain.model.MissingFailureEvidenceException
import com.waste.link.domain.model.MissingProofOfCollectionException
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.UnauthorizedTransitionException
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import com.waste.link.domain.validation.RequestStateTransitionValidator
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RequestStateTransitionValidatorTest {

    private val residentUid = "resident_123"
    private val otherResidentUid = "resident_456"
    private val collectorUid = "collector_789"
    private val otherCollectorUid = "collector_999"
    private val adminUid = "admin_001"
    private val nowEpochMs = 1793869200000L

    private fun sampleRequest(
        status: RequestLifecycleStatus = RequestLifecycleStatus.PENDING_DISPATCH,
        assignedCollector: String? = null,
        assignedVehicle: String? = null,
        updatedAt: Long? = nowEpochMs
    ): WasteRequest {
        val id = UUID.randomUUID().toString()
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            clientUid = residentUid,
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni_01",
            pickupAddress = "Test Address",
            categoryId = "household",
            volumeTier = VolumeTier.STANDARD_BIN,
            status = status,
            assignedCollectorUid = assignedCollector,
            assignedVehicleId = assignedVehicle,
            updatedAtEpochMs = updatedAt
        )
    }

    // UT-DOM-13 / TR-02: Resident cancels request while PENDING_DISPATCH
    @Test
    fun residentCancelsPendingRequest_succeeds() {
        val req = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        RequestStateTransitionValidator.validateTransition(
            request = req,
            targetStatus = RequestLifecycleStatus.CANCELLED,
            actorUid = residentUid,
            actorRole = UserRole.RESIDENT,
            currentTimeEpochMs = nowEpochMs
        )
    }

    @Test
    fun otherResidentCannotCancel_throwsUnauthorized() {
        val req = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        assertThrows(UnauthorizedTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = req,
                targetStatus = RequestLifecycleStatus.CANCELLED,
                actorUid = otherResidentUid,
                actorRole = UserRole.RESIDENT,
                currentTimeEpochMs = nowEpochMs
            )
        }
    }

    // UT-DOM-15 / TR-02 Lock: Resident attempts to cancel after ASSIGNED_TO_FLEET
    @Test
    fun residentCancelsAssignedRequest_throwsCancellationLockedException() {
        val req = sampleRequest(
            status = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        val ex = assertThrows(CancellationLockedException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = req,
                targetStatus = RequestLifecycleStatus.CANCELLED,
                actorUid = residentUid,
                actorRole = UserRole.RESIDENT,
                currentTimeEpochMs = nowEpochMs
            )
        }
        assertTrue(ex.message!!.contains("cancellation locks upon assignment"))
    }

    // UT-DOM-14: Resident attempts forbidden direct transition to COMPLETED
    @Test
    fun residentAttemptsToCompleteRequest_throwsUnauthorized() {
        val req = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        assertThrows(UnauthorizedTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = req,
                targetStatus = RequestLifecycleStatus.COMPLETED,
                actorUid = residentUid,
                actorRole = UserRole.RESIDENT,
                currentTimeEpochMs = nowEpochMs
            )
        }
    }

    // TR-03 & TR-04: Admin flags and unflags request
    @Test
    fun adminQuarantineAndClearance_succeeds() {
        val pendingReq = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        RequestStateTransitionValidator.validateTransition(
            request = pendingReq,
            targetStatus = RequestLifecycleStatus.FLAGGED_FOR_REVIEW,
            actorUid = adminUid,
            actorRole = UserRole.ADMIN,
            currentTimeEpochMs = nowEpochMs
        )

        val flaggedReq = sampleRequest(status = RequestLifecycleStatus.FLAGGED_FOR_REVIEW)
        RequestStateTransitionValidator.validateTransition(
            request = flaggedReq,
            targetStatus = RequestLifecycleStatus.PENDING_DISPATCH,
            actorUid = adminUid,
            actorRole = UserRole.ADMIN,
            currentTimeEpochMs = nowEpochMs
        )
    }

    // TR-05: Fleet allocation by admin
    @Test
    fun adminAssignsFleet_succeedsWithValidCollectorAndVehicle() {
        val req = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        RequestStateTransitionValidator.validateTransition(
            request = req,
            targetStatus = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
            actorUid = adminUid,
            actorRole = UserRole.ADMIN,
            currentTimeEpochMs = nowEpochMs,
            assignedCollectorUid = collectorUid,
            assignedVehicleId = "TZ-TRUCK-01"
        )
    }

    @Test
    fun adminAssignsFleet_withoutVehicleOrCollector_throwsException() {
        val req = sampleRequest(status = RequestLifecycleStatus.PENDING_DISPATCH)
        assertThrows(InvalidStateTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = req,
                targetStatus = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
                actorUid = adminUid,
                actorRole = UserRole.ADMIN,
                currentTimeEpochMs = nowEpochMs,
                assignedCollectorUid = null,
                assignedVehicleId = null
            )
        }
    }

    // TR-06: Driver drops assignment back to PENDING_DISPATCH
    @Test
    fun assignedDriverDropsTask_succeeds() {
        val req = sampleRequest(
            status = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = req,
            targetStatus = RequestLifecycleStatus.PENDING_DISPATCH,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs
        )
    }

    // TR-07 & TR-08: En Route and Arrived transitions by assigned collector
    @Test
    fun collectorOperationalFlow_enRouteAndArrived_succeeds() {
        val assignedReq = sampleRequest(
            status = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = assignedReq,
            targetStatus = RequestLifecycleStatus.EN_ROUTE,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs
        )

        val enRouteReq = sampleRequest(
            status = RequestLifecycleStatus.EN_ROUTE,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = enRouteReq,
            targetStatus = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs
        )
    }

    @Test
    fun unassignedCollectorCannotOperateRequest_throwsUnauthorized() {
        val assignedReq = sampleRequest(
            status = RequestLifecycleStatus.ASSIGNED_TO_FLEET,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        assertThrows(UnauthorizedTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = assignedReq,
                targetStatus = RequestLifecycleStatus.EN_ROUTE,
                actorUid = otherCollectorUid,
                actorRole = UserRole.COLLECTOR,
                currentTimeEpochMs = nowEpochMs
            )
        }
    }

    // UT-DOM-16 / TR-10: Collection Verified requires mandatory Proof of Collection photo
    @Test
    fun collectionVerified_withoutProofOfCollection_throwsMissingProofOfCollectionException() {
        val arrivedReq = sampleRequest(
            status = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        val ex = assertThrows(MissingProofOfCollectionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = arrivedReq,
                targetStatus = RequestLifecycleStatus.COLLECTION_VERIFIED,
                actorUid = collectorUid,
                actorRole = UserRole.COLLECTOR,
                currentTimeEpochMs = nowEpochMs,
                hasProofOfCollection = false
            )
        }
        assertTrue(ex.message!!.contains("Proof of Collection (PoC) must be attached"))
    }

    @Test
    fun collectionVerified_withProofOfCollection_succeeds() {
        val arrivedReq = sampleRequest(
            status = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = arrivedReq,
            targetStatus = RequestLifecycleStatus.COLLECTION_VERIFIED,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs,
            hasProofOfCollection = true
        )
    }

    // UT-DOM-17 / TR-09 & TR-11: Pickup failed requires failure photo evidence and reason
    @Test
    fun pickupFailed_withoutEvidenceOrReason_throwsMissingFailureEvidenceException() {
        val arrivedReq = sampleRequest(
            status = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        // Missing photo
        assertThrows(MissingFailureEvidenceException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = arrivedReq,
                targetStatus = RequestLifecycleStatus.FAILED,
                actorUid = collectorUid,
                actorRole = UserRole.COLLECTOR,
                currentTimeEpochMs = nowEpochMs,
                hasFailureEvidence = false,
                failureReason = "Gate locked"
            )
        }
        // Missing reason
        assertThrows(MissingFailureEvidenceException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = arrivedReq,
                targetStatus = RequestLifecycleStatus.FAILED,
                actorUid = collectorUid,
                actorRole = UserRole.COLLECTOR,
                currentTimeEpochMs = nowEpochMs,
                hasFailureEvidence = true,
                failureReason = ""
            )
        }
    }

    @Test
    fun pickupFailed_withEvidenceAndReason_succeeds() {
        val arrivedReq = sampleRequest(
            status = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = arrivedReq,
            targetStatus = RequestLifecycleStatus.FAILED,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs,
            hasFailureEvidence = true,
            failureReason = "Gate locked and resident unresponsive"
        )
    }

    // TR-12 & TR-13: In Transit to Dump and Sequential Completion
    @Test
    fun transitToDumpAndCompletion_succeedsSequentially() {
        val verifiedReq = sampleRequest(
            status = RequestLifecycleStatus.COLLECTION_VERIFIED,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = verifiedReq,
            targetStatus = RequestLifecycleStatus.IN_TRANSIT_TO_DUMP,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs
        )

        val inTransitReq = sampleRequest(
            status = RequestLifecycleStatus.IN_TRANSIT_TO_DUMP,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        RequestStateTransitionValidator.validateTransition(
            request = inTransitReq,
            targetStatus = RequestLifecycleStatus.COMPLETED,
            actorUid = collectorUid,
            actorRole = UserRole.COLLECTOR,
            currentTimeEpochMs = nowEpochMs
        )
    }

    @Test
    fun stepSkipping_directlyToCompleted_throwsInvalidStateTransitionException() {
        val arrivedReq = sampleRequest(
            status = RequestLifecycleStatus.ARRIVED_AT_LOCATION,
            assignedCollector = collectorUid,
            assignedVehicle = "T123ABC"
        )
        assertThrows(InvalidStateTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = arrivedReq,
                targetStatus = RequestLifecycleStatus.COMPLETED,
                actorUid = collectorUid,
                actorRole = UserRole.COLLECTOR,
                currentTimeEpochMs = nowEpochMs
            )
        }
    }

    // TR-14: Dispute within 48h vs after 48h
    @Test
    fun residentDisputesCompletedRequest_within48h_succeeds() {
        val completedReq = sampleRequest(
            status = RequestLifecycleStatus.COMPLETED,
            updatedAt = nowEpochMs - (24 * 3600 * 1000L) // 24 hours ago
        )
        RequestStateTransitionValidator.validateTransition(
            request = completedReq,
            targetStatus = RequestLifecycleStatus.DISPUTED,
            actorUid = residentUid,
            actorRole = UserRole.RESIDENT,
            currentTimeEpochMs = nowEpochMs,
            disputeReason = "Only half the waste bags were collected"
        )
    }

    @Test
    fun residentDisputesCompletedRequest_after48h_throwsDisputeWindowExpiredException() {
        val completedReq = sampleRequest(
            status = RequestLifecycleStatus.COMPLETED,
            updatedAt = nowEpochMs - (50 * 3600 * 1000L) // 50 hours ago (> 48h)
        )
        val ex = assertThrows(DisputeWindowExpiredException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = completedReq,
                targetStatus = RequestLifecycleStatus.DISPUTED,
                actorUid = residentUid,
                actorRole = UserRole.RESIDENT,
                currentTimeEpochMs = nowEpochMs,
                disputeReason = "Grievance filed too late"
            )
        }
        assertTrue(ex.message!!.contains("48-hour dispute window has expired"))
    }

    // TR-15 & TR-16: Admin resolves or dismisses dispute
    @Test
    fun adminArbitration_resolveAndDismiss_succeedsWithNote() {
        val disputedReq = sampleRequest(status = RequestLifecycleStatus.DISPUTED)

        RequestStateTransitionValidator.validateTransition(
            request = disputedReq,
            targetStatus = RequestLifecycleStatus.RESOLVED,
            actorUid = adminUid,
            actorRole = UserRole.ADMIN,
            currentTimeEpochMs = nowEpochMs,
            adminNote = "Refund 5000 TZS issued to resident"
        )

        RequestStateTransitionValidator.validateTransition(
            request = disputedReq,
            targetStatus = RequestLifecycleStatus.DISMISSED,
            actorUid = adminUid,
            actorRole = UserRole.ADMIN,
            currentTimeEpochMs = nowEpochMs,
            adminNote = "Driver GPS and photos confirm full collection"
        )
    }

    @Test
    fun adminArbitration_withoutNote_throwsException() {
        val disputedReq = sampleRequest(status = RequestLifecycleStatus.DISPUTED)
        assertThrows(InvalidStateTransitionException::class.java) {
            RequestStateTransitionValidator.validateTransition(
                request = disputedReq,
                targetStatus = RequestLifecycleStatus.RESOLVED,
                actorUid = adminUid,
                actorRole = UserRole.ADMIN,
                currentTimeEpochMs = nowEpochMs,
                adminNote = null
            )
        }
    }
}
