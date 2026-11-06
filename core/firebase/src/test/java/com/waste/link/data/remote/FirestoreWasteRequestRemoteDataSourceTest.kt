package com.waste.link.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeoutException

class FirestoreWasteRequestRemoteDataSourceTest {

    private lateinit var dataSource: FirestoreWasteRequestRemoteDataSource

    @Before
    fun setUp() {
        dataSource = FirestoreWasteRequestRemoteDataSource(
            firestoreProvider = { error("Live Firestore must not be invoked during unit test") }
        )
    }

    private fun sampleRequest(
        id: String = "test-uuid-1",
        clientUid: String = "resident_1",
        serviceZoneId: String = "zone_kinondoni",
        requestRef: String? = "WL-2611-7X9F2B"
    ): WasteRequest {
        val coords = GeoCoordinates(-6.7924, 39.2083)
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            requestRef = requestRef,
            clientUid = clientUid,
            coordinates = coords,
            serviceZoneId = serviceZoneId,
            pickupAddress = "Masaki, Dar es Salaam",
            categoryId = "cat_general",
            volumeTier = VolumeTier.STANDARD_BIN
        )
    }

    @Test
    fun createRequest_nullRequestRef_returnsRejectedReferenceAllocationRequired() = runBlocking {
        val outcome = dataSource.createRequest(
            authenticatedUid = "resident_1",
            request = sampleRequest(requestRef = null)
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("REFERENCE_ALLOCATION_REQUIRED", rejected.errorCode)
        org.junit.Assert.assertFalse(rejected.requiresUserIntervention)
    }


    @Test
    fun createRequest_blankAuthUid_returnsRejectedUnauthenticated() = runBlocking {
        val outcome = dataSource.createRequest(
            authenticatedUid = "",
            request = sampleRequest()
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("UNAUTHENTICATED", rejected.errorCode)
        assertTrue(rejected.requiresUserIntervention)
    }

    @Test
    fun createRequest_uidMismatch_returnsRejectedUidMismatch() = runBlocking {
        val outcome = dataSource.createRequest(
            authenticatedUid = "attacker_uid",
            request = sampleRequest(clientUid = "victim_uid")
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("UID_MISMATCH", rejected.errorCode)
        assertTrue(rejected.requiresUserIntervention)
    }

    @Test
    fun createRequest_legacyUnmappedZone_returnsRejectedQuarantined() = runBlocking {
        val outcome = dataSource.createRequest(
            authenticatedUid = "resident_1",
            request = sampleRequest(serviceZoneId = "legacy_unmapped_zone")
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("LEGACY_UNMAPPED_ZONE", rejected.errorCode)
        assertTrue(rejected.requiresUserIntervention)
    }

    @Test
    fun createRequest_permissionDenied_mapsToRejectedPermissionDenied() = runBlocking {
        val pDenied = FirebaseFirestoreException(
            "Missing or insufficient permissions",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        dataSource.setSimulatedNextFailure(pDenied)

        val outcome = dataSource.createRequest(
            authenticatedUid = "resident_1",
            request = sampleRequest()
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("PERMISSION_DENIED", rejected.errorCode)
        assertTrue(rejected.requiresUserIntervention)
    }

    @Test
    fun createRequest_networkUnavailable_mapsToTransientFailure() = runBlocking {
        val unavail = FirebaseFirestoreException(
            "Service unavailable",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )
        dataSource.setSimulatedNextFailure(unavail)

        val outcome = dataSource.createRequest(
            authenticatedUid = "resident_1",
            request = sampleRequest()
        )

        assertTrue(outcome is RemoteRequestOutcome.TransientFailure)
        val transient = outcome as RemoteRequestOutcome.TransientFailure
        assertTrue(transient.cause is FirebaseFirestoreException)
    }

    @Test
    fun createRequest_ioException_mapsToTransientFailure() = runBlocking {
        val ioEx = IOException("Connection reset by peer")
        dataSource.setSimulatedNextFailure(ioEx)

        val outcome = dataSource.createRequest(
            authenticatedUid = "resident_1",
            request = sampleRequest()
        )

        assertTrue(outcome is RemoteRequestOutcome.TransientFailure)
        val transient = outcome as RemoteRequestOutcome.TransientFailure
        assertEquals("Connection reset by peer", transient.message)
    }

    @Test
    fun getRequestById_blankAuthUid_returnsRejectedUnauthenticated() = runBlocking {
        val outcome = dataSource.getRequestById(
            authenticatedUid = "",
            requestId = "req-1"
        )

        assertTrue(outcome is RemoteRequestOutcome.Rejected)
        val rejected = outcome as RemoteRequestOutcome.Rejected
        assertEquals("UNAUTHENTICATED", rejected.errorCode)
    }

    @Test
    fun getRequestById_timeoutException_mapsToTransientFailure() = runBlocking {
        val timeout = TimeoutException("Read timed out")
        dataSource.setSimulatedNextFailure(timeout)

        val outcome = dataSource.getRequestById(
            authenticatedUid = "resident_1",
            requestId = "req-1"
        )

        assertTrue(outcome is RemoteRequestOutcome.TransientFailure)
    }

    // =========================================================================
    // Finding 4: Fail-closed service-zone geometry validation tests
    // =========================================================================

    @Test
    fun validateServiceZoneGeometry_missingBounds_returnsMalformed() {
        val missingMinLat = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = null,
            maxLat = -6.7,
            minLng = 39.1,
            maxLng = 39.3,
            rawVertices = null
        )
        assertTrue(missingMinLat is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)

        val missingMaxLng = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -6.8,
            maxLat = -6.7,
            minLng = 39.1,
            maxLng = null,
            rawVertices = null
        )
        assertTrue(missingMaxLng is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)
    }

    @Test
    fun validateServiceZoneGeometry_invalidBounds_returnsMalformed() {
        // minLat > maxLat
        val invertedLat = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -6.7,
            maxLat = -6.8,
            minLng = 39.1,
            maxLng = 39.3,
            rawVertices = null
        )
        assertTrue(invertedLat is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)

        // minLng > maxLng
        val invertedLng = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -6.8,
            maxLat = -6.7,
            minLng = 39.5,
            maxLng = 39.3,
            rawVertices = null
        )
        assertTrue(invertedLng is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)

        // Out of WGS-84 coordinate range
        val outOfRange = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -95.0,
            maxLat = -6.7,
            minLng = 39.1,
            maxLng = 39.3,
            rawVertices = null
        )
        assertTrue(outOfRange is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)
    }

    @Test
    fun validateServiceZoneGeometry_malformedPolygonVertices_returnsMalformed() {
        // Less than 3 vertices
        val twoVertices = listOf(
            mapOf("lat" to -6.75, "lng" to 39.2),
            mapOf("lat" to -6.76, "lng" to 39.25)
        )
        val resultFew = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -6.8,
            maxLat = -6.7,
            minLng = 39.1,
            maxLng = 39.3,
            rawVertices = twoVertices
        )
        assertTrue(resultFew is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)

        // Vertex outside bounding box
        val vertexOutside = listOf(
            mapOf("lat" to -6.75, "lng" to 39.2),
            mapOf("lat" to -6.76, "lng" to 39.25),
            mapOf("lat" to -6.90, "lng" to 39.2) // -6.90 is < minLat (-6.8)
        )
        val resultOutside = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_1",
            zoneName = "Zone 1",
            minLat = -6.8,
            maxLat = -6.7,
            minLng = 39.1,
            maxLng = 39.3,
            rawVertices = vertexOutside
        )
        assertTrue(resultOutside is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Malformed)
    }

    @Test
    fun validateServiceZoneGeometry_validBoundsAndPolygon_returnsValid() {
        val validVertices = listOf(
            mapOf("latitude" to -6.75, "longitude" to 39.2),
            mapOf("latitude" to -6.75, "longitude" to 39.25),
            mapOf("latitude" to -6.78, "longitude" to 39.22)
        )
        val result = dataSource.validateServiceZoneGeometry(
            zoneId = "zone_kinondoni",
            zoneName = "Kinondoni Municipal",
            minLat = -6.82,
            maxLat = -6.70,
            minLng = 39.18,
            maxLng = 39.26,
            rawVertices = validVertices
        )
        assertTrue(result is FirestoreWasteRequestRemoteDataSource.GeometryValidation.Valid)
        val valid = result as FirestoreWasteRequestRemoteDataSource.GeometryValidation.Valid
        assertEquals("zone_kinondoni", valid.zone.zoneId)
        assertEquals("Kinondoni Municipal", valid.zone.name)
        assertEquals(3, valid.zone.polygonVertices.size)
    }

    // =========================================================================
    // Additional Review 1: Payload conflict detection tests
    // =========================================================================

    @Test
    fun checkPayloadConflict_matchingPayload_returnsFalse() {
        val request = sampleRequest()
        val dto = com.waste.link.data.remote.dto.WasteRequestDto(
            id = request.id,
            requestRef = request.requestRef ?: "",
            clientUid = request.clientUid,
            category = request.categoryId,
            volumeTier = request.volumeTier.name,
            pickupAddress = request.pickupAddress,
            lat = request.coordinates.latitude,
            lng = request.coordinates.longitude,
            notes = request.notes ?: "",
            gatePhotoUrl = request.gatePhotoUrl,
            residentPhoneNumber = request.residentPhoneNumber
        )

        org.junit.Assert.assertFalse(dataSource.checkPayloadConflict(dto, request))
    }

    @Test
    fun checkPayloadConflict_conflictingNotes_returnsTrue() {
        val request = sampleRequest(id = "req-1").copy(notes = "Different note")
        val dto = com.waste.link.data.remote.dto.WasteRequestDto(
            id = "req-1",
            category = request.categoryId,
            volumeTier = request.volumeTier.name,
            pickupAddress = request.pickupAddress,
            lat = request.coordinates.latitude,
            lng = request.coordinates.longitude,
            notes = "Original note"
        )

        assertTrue(dataSource.checkPayloadConflict(dto, request))
    }

    @Test
    fun checkPayloadConflict_conflictingCoordinates_returnsTrue() {
        val request = sampleRequest(id = "req-1")
        val dto = com.waste.link.data.remote.dto.WasteRequestDto(
            id = "req-1",
            category = request.categoryId,
            volumeTier = request.volumeTier.name,
            pickupAddress = request.pickupAddress,
            lat = request.coordinates.latitude + 0.05, // Different lat
            lng = request.coordinates.longitude,
            notes = request.notes ?: ""
        )

        assertTrue(dataSource.checkPayloadConflict(dto, request))
    }

    @Test
    fun checkPayloadConflict_conflictingCategory_returnsTrue() {
        val request = sampleRequest(id = "req-1").copy(categoryId = "cat_plastic")
        val dto = com.waste.link.data.remote.dto.WasteRequestDto(
            id = "req-1",
            category = "cat_organic",
            volumeTier = request.volumeTier.name,
            pickupAddress = request.pickupAddress,
            lat = request.coordinates.latitude,
            lng = request.coordinates.longitude,
            notes = request.notes ?: ""
        )

        assertTrue(dataSource.checkPayloadConflict(dto, request))
    }
}
