package com.waste.link.data.mapper

import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.models.ResidentDraft
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying bidirectional mapping between domain models and persistence records.
 *
 * Verifies Acceptance Criterion:
 * - P1B-18: Supported domain fields round-trip without silent loss.
 * - P1B-13: Local sync state does not alter domain lifecycle status.
 */
class WasteRequestLocalMapperTest {

    @Test
    fun p1b18_wasteRequest_fullRoundTrip_preservesAllFieldsWithoutLoss() {
        val original = WasteRequest(
            id = "b7e41517-5e92-4217-bf27-d4013d526e83",
            clientGeneratedId = "b7e41517-5e92-4217-bf27-d4013d526e83",
            requestRef = "WL-2611-7X9F2B",
            clientUid = "resident_uid_123",
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Ali Hassan Mwinyi Rd, Dar es Salaam",
            categoryId = "cat_household",
            volumeTier = VolumeTier.STANDARD_BIN,
            notes = "Ring gate bell 3 times",
            gatePhotoUrl = "https://storage.googleapis.com/test/gate.webp",
            gatePhotoBase64 = null,
            pocPhotoUrl = "https://storage.googleapis.com/test/poc.webp",
            pocPhotoBase64 = null,
            status = RequestLifecycleStatus.EN_ROUTE,
            assignedCollectorUid = "collector_uid_456",
            assignedVehicleId = "T123ABC",
            priceTzs = 15000L,
            currency = "TZS",
            residentPhoneNumber = "+255712345678",
            collectorPhoneNumber = "+255798765432",
            failureReason = null,
            failurePhotoUrl = null,
            failurePhotoBase64 = null,
            disputeReason = null,
            adminResolutionNote = null,
            createdAtEpochMs = 1730800000000L,
            updatedAtEpochMs = 1730800100000L,
            assignedAtEpochMs = 1730800050000L,
            enRouteAtEpochMs = 1730800080000L,
            arrivedAtEpochMs = null,
            collectedAtEpochMs = null,
            completedAtEpochMs = null,
            failedAtEpochMs = null
        )

        val localRecord = WasteRequestLocalMapper.toLocalRecord(
            domain = original,
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE,
            gatePhotoLocalPath = "/data/user/0/com.waste.link/cache/gate_photo_1.webp",
            lastSyncAttemptEpochMs = 1730800090000L,
            syncErrorMessage = "Network timeout",
            retryCount = 2
        )

        // Verify persistence-specific metadata
        assertEquals(LocalSyncStatus.QUEUED_OFFLINE, localRecord.syncStatus)
        assertEquals("/data/user/0/com.waste.link/cache/gate_photo_1.webp", localRecord.gatePhotoLocalPath)
        assertEquals(1730800090000L, localRecord.lastSyncAttemptEpochMs)
        assertEquals("Network timeout", localRecord.syncErrorMessage)
        assertEquals(2, localRecord.retryCount)

        // Verify domain round-trip
        val reconstructed = WasteRequestLocalMapper.toDomain(localRecord)
        assertEquals(original, reconstructed)
    }

    @Test
    fun p1b18_wasteRequest_withMinimalNullableFields_roundTripsWithoutLoss() {
        val original = WasteRequest(
            id = "c1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
            clientGeneratedId = "c1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
            requestRef = null, // Server has not allocated ref yet
            clientUid = "resident_minimal",
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_ilala",
            pickupAddress = "Samora Avenue, Dar es Salaam",
            categoryId = "cat_recyclables",
            volumeTier = VolumeTier.SMALL_BAG,
            notes = "",
            gatePhotoUrl = null,
            gatePhotoBase64 = null,
            pocPhotoUrl = null,
            pocPhotoBase64 = null,
            status = RequestLifecycleStatus.PENDING_DISPATCH,
            assignedCollectorUid = null,
            assignedVehicleId = null,
            priceTzs = null,
            currency = "TZS",
            residentPhoneNumber = null,
            collectorPhoneNumber = null,
            failureReason = null,
            failurePhotoUrl = null,
            failurePhotoBase64 = null,
            disputeReason = null,
            adminResolutionNote = null,
            createdAtEpochMs = null,
            updatedAtEpochMs = null,
            assignedAtEpochMs = null,
            enRouteAtEpochMs = null,
            arrivedAtEpochMs = null,
            collectedAtEpochMs = null,
            completedAtEpochMs = null,
            failedAtEpochMs = null
        )

        val localRecord = WasteRequestLocalMapper.toLocalRecord(
            domain = original,
            syncStatus = LocalSyncStatus.LOCAL_DRAFT
        )

        val reconstructed = WasteRequestLocalMapper.toDomain(localRecord)
        assertEquals(original, reconstructed)
        assertNull(reconstructed.requestRef)
        assertEquals(RequestLifecycleStatus.PENDING_DISPATCH, reconstructed.status)
    }

    @Test
    fun p1b13_syncStatus_doesNotAlterDomainLifecycleStatus() {
        val original = WasteRequest(
            id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
            clientGeneratedId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
            requestRef = null,
            clientUid = "resident_123",
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Morogoro Rd",
            categoryId = "cat_household",
            volumeTier = VolumeTier.LARGE_LOAD,
            status = RequestLifecycleStatus.PENDING_DISPATCH
        )

        // Iterate through all 5 local sync statuses
        LocalSyncStatus.entries.forEach { syncStatus ->
            val record = WasteRequestLocalMapper.toLocalRecord(original, syncStatus = syncStatus)
            val domain = WasteRequestLocalMapper.toDomain(record)
            // Lifecycle status must remain PENDING_DISPATCH regardless of local sync status
            assertEquals(
                "LocalSyncStatus $syncStatus must not alter domain RequestLifecycleStatus",
                RequestLifecycleStatus.PENDING_DISPATCH,
                domain.status
            )
        }
    }

    @Test
    fun residentDraft_bidirectionalMapping_preservesAllFields() {
        val draft = ResidentDraft(
            clientUid = "user_draft_1",
            clientGeneratedId = "d1e2f3a4-b5c6-4d7e-8f9a-0b1c2d3e4f5a",
            currentStep = "LOCATION",
            categoryId = "cat_electronic",
            latitude = -6.7924,
            longitude = 39.2083,
            pickupAddress = "Bagamoyo Rd",
            serviceZoneId = "zone_kinondoni",
            notes = "Blue gate",
            gatePhotoLocalPath = "/local/path/gate.webp",
            volumeTierId = "large_load",
            createdAtEpochMs = 1730800000000L,
            updatedAtEpochMs = 1730800050000L
        )

        val record = ResidentDraftMapper.toRecord(draft)
        val reconstructed = ResidentDraftMapper.toDomain(record)

        assertEquals(draft, reconstructed)
    }
}
