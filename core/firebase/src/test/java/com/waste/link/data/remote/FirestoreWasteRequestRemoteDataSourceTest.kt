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
}
