package com.waste.link.data.remote

import com.google.firebase.Timestamp
import com.waste.link.data.remote.dto.WasteRequestDto
import com.waste.link.data.remote.mapper.WasteRequestRemoteMapper
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteRequestRemoteMapperTest {

    private val allowedFirestoreKeys = setOf(
        "id", "requestRef", "clientUid", "geohash", "lat", "lng",
        "pickupAddress", "category", "volumeTier", "notes",
        "gatePhotoBase64", "pocPhotoBase64", "gatePhotoUrl", "pocPhotoUrl",
        "photos", "status", "assignedTruckId", "assignedAt", "enRouteAt",
        "arrivedAt", "collectedAt", "completedAt", "failedAt", "failureReason",
        "agreedPrice", "residentPhoneNumber", "collectorPhoneNumber",
        "createdAt", "updatedAt"
    )

    @Test
    fun toDto_mapsAllDomainFieldsCorrectly() {
        val coords = GeoCoordinates(-6.7924, 39.2083)
        val domain = WasteRequest(
            id = "req-1234-uuid",
            clientGeneratedId = "req-1234-uuid",
            requestRef = "WL-2611-7X9F2B",
            clientUid = "resident_uid_1",
            coordinates = coords,
            serviceZoneId = "zone_kinondoni",
            pickupAddress = "Plot 42, Haile Selassie Rd, Masaki",
            categoryId = "cat_organic",
            volumeTier = VolumeTier.LARGE_LOAD,
            notes = "Ring gate buzzer",
            gatePhotoBase64 = "base64_gate_data",
            gatePhotoUrl = "https://storage.googleapis.com/gate.jpg",
            residentPhoneNumber = "+255712345678",
            createdAtEpochMs = 1793869200000L,
            updatedAtEpochMs = 1793869205000L
        )

        val dto = WasteRequestRemoteMapper.toDto(domain)

        assertEquals("req-1234-uuid", dto.id)
        assertEquals("WL-2611-7X9F2B", dto.requestRef)
        assertEquals("resident_uid_1", dto.clientUid)
        assertEquals(-6.7924, dto.lat, 0.0001)
        assertEquals(39.2083, dto.lng, 0.0001)
        assertEquals("Plot 42, Haile Selassie Rd, Masaki", dto.pickupAddress)
        assertEquals("cat_organic", dto.category)
        assertEquals("LARGE_LOAD", dto.volumeTier)
        assertEquals("Ring gate buzzer", dto.notes)
        assertEquals("base64_gate_data", dto.gatePhotoBase64)
        assertEquals("https://storage.googleapis.com/gate.jpg", dto.gatePhotoUrl)
        assertEquals("+255712345678", dto.residentPhoneNumber)
        assertEquals(1793869200L, dto.createdAt?.seconds)
        assertEquals(1793869205L, dto.updatedAt?.seconds)
    }

    @Test
    fun toFirestoreCreationMap_strictlyConformsToFirestoreRulesKeyWhitelist() {
        val dto = WasteRequestDto(
            id = "req-1234-uuid",
            requestRef = "WL-2611-7X9F2B",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            geohash = "kygck5q",
            pickupAddress = "Masaki, Dar es Salaam",
            category = "cat_general",
            volumeTier = "SMALL_BAG",
            notes = "Near main gate",
            gatePhotoBase64 = "photo_b64",
            gatePhotoUrl = "https://url.com/photo.jpg",
            photos = listOf("photo1", "photo2"),
            residentPhoneNumber = "+255712345678",
            status = "PENDING_DISPATCH"
        )

        val map = dto.toFirestoreCreationMap(createdAtValue = 1793869200000L, updatedAtValue = 1793869200000L)

        // Every single key must exist in allowedFirestoreKeys
        for (key in map.keys) {
            assertTrue("Key '$key' is not in allowedFirestoreKeys whitelist!", allowedFirestoreKeys.contains(key))
        }

        // Must NEVER contain serviceZoneId, clientGeneratedId, priceTzs, or currency
        assertFalse(map.containsKey("serviceZoneId"))
        assertFalse(map.containsKey("clientGeneratedId"))
        assertFalse(map.containsKey("priceTzs"))
        assertFalse(map.containsKey("currency"))

        // Status must be PENDING_DISPATCH
        assertEquals("PENDING_DISPATCH", map["status"])
    }

    @Test
    fun toDomain_mapsDtoToDomain_withValidServiceZoneId() {
        val dto = WasteRequestDto(
            id = "req-uuid-999",
            requestRef = "WL-2611-7X9F2B",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            geohash = "kygck5q",
            pickupAddress = "Masaki, Dar es Salaam",
            category = "cat_recyclable",
            volumeTier = "TRUCK_LOAD",
            notes = "Glass bottles sorted",
            status = "PENDING_DISPATCH",
            createdAt = Timestamp(1793869200L, 0),
            updatedAt = Timestamp(1793869205L, 0)
        )

        val domain = WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = "zone_kinondoni")

        assertEquals("req-uuid-999", domain.id)
        assertEquals("req-uuid-999", domain.clientGeneratedId)
        assertEquals("WL-2611-7X9F2B", domain.requestRef)
        assertEquals("resident_uid_1", domain.clientUid)
        assertEquals("zone_kinondoni", domain.serviceZoneId)
        assertEquals("cat_recyclable", domain.categoryId)
        assertEquals(VolumeTier.TRUCK_LOAD, domain.volumeTier)
        assertEquals(RequestLifecycleStatus.PENDING_DISPATCH, domain.status)
        assertEquals(1793869200000L, domain.createdAtEpochMs)
        assertEquals(1793869205000L, domain.updatedAtEpochMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomain_unknownVolumeTier_throwsIllegalArgumentException() {
        val dto = WasteRequestDto(
            id = "req-uuid-bad-tier",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            pickupAddress = "Masaki",
            category = "cat_general",
            volumeTier = "MEGA_MONSTER_BIN", // Unknown volume tier!
            status = "PENDING_DISPATCH"
        )
        WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = "zone_kinondoni")
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomain_unknownStatus_throwsIllegalArgumentException() {
        val dto = WasteRequestDto(
            id = "req-uuid-bad-status",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            pickupAddress = "Masaki",
            category = "cat_general",
            volumeTier = "STANDARD_BIN",
            status = "MAGIC_TELEPORTED" // Unknown status!
        )
        WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = "zone_kinondoni")
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomain_blankServiceZoneId_throwsIllegalArgumentException() {
        val dto = WasteRequestDto(
            id = "req-uuid-blank-zone",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            pickupAddress = "Masaki",
            category = "cat_general",
            volumeTier = "STANDARD_BIN",
            status = "PENDING_DISPATCH"
        )
        WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomain_fabricatedRemoteZone_throwsIllegalArgumentException() {
        val dto = WasteRequestDto(
            id = "req-uuid-fake-zone",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            pickupAddress = "Masaki",
            category = "cat_general",
            volumeTier = "STANDARD_BIN",
            status = "PENDING_DISPATCH"
        )
        WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = "remote_zone")
    }

    @Test
    fun toDomainOrNull_malformedDto_returnsNull() {
        val badTierDto = WasteRequestDto(
            id = "req-bad",
            clientUid = "resident_uid_1",
            lat = -6.7924,
            lng = 39.2083,
            pickupAddress = "Masaki",
            category = "cat_general",
            volumeTier = "INVALID_TIER",
            status = "PENDING_DISPATCH"
        )
        assertNull(WasteRequestRemoteMapper.toDomainOrNull(badTierDto, serviceZoneId = "zone_kinondoni"))
    }
}
