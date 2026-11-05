package com.waste.link.domain

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestValidationException
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.validation.RequestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RequestValidatorTest {

    private val activeZoneKinondoni = ServiceZone(
        zoneId = "zone_kinondoni_01",
        name = "Kinondoni Municipal",
        minLat = -6.8200,
        maxLat = -6.7500,
        minLng = 39.1800,
        maxLng = 39.2600,
        isActive = true
    )

    private val activeZoneIlala = ServiceZone(
        zoneId = "zone_ilala_02",
        name = "Ilala Municipal",
        minLat = -6.8800,
        maxLat = -6.8250,
        minLng = 39.2200,
        maxLng = 39.3000,
        isActive = true
    )

    private val inactiveZoneTemeke = ServiceZone(
        zoneId = "zone_temeke_inactive",
        name = "Temeke Municipal (Decommissioned)",
        minLat = -6.9500,
        maxLat = -6.8900,
        minLng = 39.2000,
        maxLng = 39.3000,
        isActive = false
    )

    private val availableZones = listOf(activeZoneKinondoni, activeZoneIlala, inactiveZoneTemeke)

    private val validCategory = DynamicWasteCategory(
        id = "household",
        displayName = "Household Waste",
        isActive = true
    )

    private val inactiveCategory = DynamicWasteCategory(
        id = "old_cat",
        displayName = "Old Category",
        isActive = false
    )

    // Kinondoni coordinates: (-6.7924, 39.2083)
    private val kinondoniCoords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
    // Ilala coordinates: (-6.8500, 39.2600)
    private val ilalaCoords = GeoCoordinates(latitude = -6.8500, longitude = 39.2600)
    // Outside all zones (Dodoma): (-6.1630, 35.7516)
    private val outsideCoords = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)

    private fun createMockWebP(totalBytes: Int): String {
        require(totalBytes >= 12)
        val arr = ByteArray(totalBytes)
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val size = totalBytes - 8
        arr[4] = (size and 0xFF).toByte()
        arr[5] = ((size shr 8) and 0xFF).toByte()
        arr[6] = ((size shr 16) and 0xFF).toByte()
        arr[7] = ((size shr 24) and 0xFF).toByte()
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    // Step 1: Category Tests
    @Test
    fun step1CategoryValidation() {
        val err1 = RequestValidator.validateCategory(null)
        assertEquals(1, err1.size)
        assertTrue(err1[0].contains("select a waste category"))

        val err2 = RequestValidator.validateCategory(inactiveCategory)
        assertEquals(1, err2.size)
        assertTrue(err2[0].contains("is inactive"))

        val ok = RequestValidator.validateCategory(validCategory)
        assertTrue(ok.isEmpty())
    }

    // Step 2: Location and Service Zone Tests
    @Test
    fun step2Location_correctZoneAndCoordinates_succeeds() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun step2Location_correctZoneButOutsideBoundary_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = outsideCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertTrue(errors.any { it.contains("fall outside selected service zone") })
    }

    @Test
    fun step2Location_coordinatesInsideZoneAWhileZoneBSelected_returnsError() {
        // kinondoniCoords are inside Kinondoni, but Ilala zone is selected
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_ilala_02",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertTrue(errors.any { it.contains("fall outside selected service zone") })
    }

    @Test
    fun step2Location_unknownZoneId_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "non_existent_zone_999",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertTrue(errors.any { it.contains("does not exist in the municipal catalog") })
    }

    @Test
    fun step2Location_inactiveZone_returnsError() {
        val temekeCoords = GeoCoordinates(latitude = -6.9200, longitude = 39.2500)
        val errors = RequestValidator.validateLocation(
            coordinates = temekeCoords,
            selectedZoneId = "zone_temeke_inactive",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertTrue(errors.any { it.contains("is currently inactive") })
    }

    @Test
    fun step2Location_noActiveZones_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = emptyList() // No zones loaded
        )
        assertTrue(errors.any { it.contains("does not exist in the municipal catalog") })
    }

    @Test
    fun step2Location_missingCoordinatesOrBlankAddress_returnsErrors() {
        val errCoords = RequestValidator.validateLocation(
            coordinates = null,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Valid Address",
            availableZones = availableZones
        )
        assertTrue(errCoords.any { it.contains("select a pickup location") })

        val errAddress = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "A", // too short (< 2)
            availableZones = availableZones
        )
        assertTrue(errAddress.any { it.contains("minimum 2 characters") })
    }

    // Step 3: Volume Tier Tests
    @Test
    fun step3VolumeValidation() {
        val err = RequestValidator.validateVolume(null)
        assertEquals(1, err.size)
        assertTrue(err[0].contains("select an estimated waste volume tier"))

        val ok = RequestValidator.validateVolume(VolumeTier.STANDARD_BIN)
        assertTrue(ok.isEmpty())
    }

    // Gate Photo Validation Tests
    @Test
    fun gatePhoto_validWebpBelowLimit_succeeds() {
        val webpUnder50Kb = createMockWebP(1024) // 1 KB
        val errors = RequestValidator.validateGatePhoto(webpUnder50Kb)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_validWebpExactlyAt50KbLimit_succeeds() {
        val webpExact50Kb = createMockWebP(50 * 1024) // 51,200 bytes
        val errors = RequestValidator.validateGatePhoto(webpExact50Kb)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_validWebpAbove50KbLimit_returnsError() {
        val webpOversized = createMockWebP(50 * 1024 + 1) // 51,201 bytes
        val errors = RequestValidator.validateGatePhoto(webpOversized)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("exceeds maximum allowed size of 50 KB"))
    }

    @Test
    fun gatePhoto_invalidOrUnsupportedFormat_returnsError() {
        // JPEG magic header: FF D8 FF E0
        val fakeJpeg = ByteArray(500) { 0.toByte() }
        fakeJpeg[0] = 0xFF.toByte()
        fakeJpeg[1] = 0xD8.toByte()
        fakeJpeg[2] = 0xFF.toByte()
        fakeJpeg[3] = 0xE0.toByte()
        val jpegBase64 = Base64.getEncoder().encodeToString(fakeJpeg)

        val errors = RequestValidator.validateGatePhoto(jpegBase64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be in compressed WebP format"))
    }

    @Test
    fun gatePhoto_missingOptionalPhoto_succeeds() {
        assertTrue(RequestValidator.validateGatePhoto(null).isEmpty())
        assertTrue(RequestValidator.validateGatePhoto("").isEmpty())
        assertTrue(RequestValidator.validateGatePhoto("   ").isEmpty())
    }

    @Test
    fun gatePhoto_corruptedBase64_returnsError() {
        val errors = RequestValidator.validateGatePhoto("Not-Valid-Base-64!!!")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("invalid Base64 data"))
    }

    // Complete Draft Assertion Tests
    @Test
    fun completeDraftValidation() {
        RequestValidator.assertValidDraft(
            category = validCategory,
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            volumeTier = VolumeTier.STANDARD_BIN,
            availableZones = availableZones,
            notes = "Ring bell",
            gatePhotoBase64 = createMockWebP(2048)
        )

        assertThrows(RequestValidationException::class.java) {
            RequestValidator.assertValidDraft(
                category = null,
                coordinates = outsideCoords,
                selectedZoneId = "zone_kinondoni_01",
                pickupAddress = "",
                volumeTier = null,
                availableZones = availableZones
            )
        }
    }
}
