package com.waste.link.domain

import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WasteRequestDomainTest {

    private fun createValidRequest(
        id: String = UUID.randomUUID().toString(),
        requestRef: String? = null,
        clientUid: String = "resident_uid_123",
        status: RequestLifecycleStatus = RequestLifecycleStatus.PENDING_DISPATCH,
        priceTzs: Long? = null,
        currency: String = "TZS"
    ): WasteRequest {
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            requestRef = requestRef,
            clientUid = clientUid,
            coordinates = GeoCoordinates(latitude = -6.7924, longitude = 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Mwinyi Rd, Dar es Salaam",
            categoryId = "household",
            volumeTier = VolumeTier.STANDARD_BIN,
            notes = "Ring bell at blue gate",
            status = status,
            priceTzs = priceTzs,
            currency = currency
        )
    }

    @Test
    fun validRequest_instantiatedWithUnallocatedRef() {
        val req = createValidRequest(requestRef = null)
        assertNotNull(req)
        assertNull(req.requestRef)
        assertTrue(req.isUnallocatedRef)
        assertFalse(req.isAssigned)
        assertEquals("TZS", req.currency)
    }

    @Test
    fun validRequest_withServerAllocatedRef_succeeds() {
        val req = createValidRequest(requestRef = "WL-2611-7X9F2B")
        assertEquals("WL-2611-7X9F2B", req.requestRef)
        assertFalse(req.isUnallocatedRef)
    }

    @Test
    fun idMismatch_throwsIllegalArgumentException() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            WasteRequest(
                id = "id_1",
                clientGeneratedId = "id_2", // Mismatch
                clientUid = "user_1",
                coordinates = GeoCoordinates(-6.7924, 39.2083),
                geohash = "kygck5q",
                serviceZoneId = "zone_1",
                pickupAddress = "Address",
                categoryId = "household",
                volumeTier = VolumeTier.STANDARD_BIN
            )
        }
        assertTrue(ex.message!!.contains("must match clientGeneratedId"))
    }

    @Test
    fun blankAddress_throwsIllegalArgumentException() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            createValidRequest().copy(pickupAddress = "   ")
        }
        assertTrue(ex.message!!.contains("pickupAddress must not be blank"))
    }

    @Test
    fun nonTzsCurrency_throwsIllegalArgumentException() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            createValidRequest(currency = "USD")
        }
        assertTrue(ex.message!!.contains("Currency must be 'TZS'"))
    }

    @Test
    fun negativePrice_throwsIllegalArgumentException() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            createValidRequest(priceTzs = -500L)
        }
        assertTrue(ex.message!!.contains("priceTzs must be strictly positive"))
    }

    @Test
    fun malformedRequestRef_throwsIllegalArgumentException() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            createValidRequest(requestRef = "INVALID_REF")
        }
        assertTrue(ex.message!!.contains("Invalid requestRef format"))
    }

    // Architecture & Layering Verification: Zero framework/sync leakage
    @Test
    fun pureDomainIsolation_verifiesZeroSyncMetadataLeakage() {
        val fields = WasteRequest::class.java.declaredFields.map { it.name }

        // Must not contain Room or synchronization fields
        assertFalse("syncStatus leaked into WasteRequest", fields.contains("syncStatus"))
        assertFalse("syncErrorMessage leaked into WasteRequest", fields.contains("syncErrorMessage"))
        assertFalse("retryCount leaked into WasteRequest", fields.contains("retryCount"))
        assertFalse("lastSyncAttemptEpochMs leaked into WasteRequest", fields.contains("lastSyncAttemptEpochMs"))

        // Must not contain any Android or Room/Firebase annotations
        val annotations = WasteRequest::class.java.annotations.map { it.annotationClass.java.name }
        for (annotationName in annotations) {
            assertFalse(annotationName.startsWith("androidx.room"))
            assertFalse(annotationName.startsWith("com.google.firebase"))
            assertFalse(annotationName.startsWith("android."))
        }
    }
}
