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

class RequestValidatorTest {

    private val activeZone = ServiceZone(
        zoneId = "zone_kinondoni_01",
        name = "Kinondoni Municipal",
        minLat = -6.8200,
        maxLat = -6.7500,
        minLng = 39.1800,
        maxLng = 39.2600,
        isActive = true
    )

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

    private val validCoords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
    private val outsideCoords = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)

    // Step 1: Category
    @Test
    fun step1CategoryValidation() {
        // Null category
        val err1 = RequestValidator.validateCategory(null)
        assertEquals(1, err1.size)
        assertTrue(err1[0].contains("select a waste category"))

        // Inactive category
        val err2 = RequestValidator.validateCategory(inactiveCategory)
        assertEquals(1, err2.size)
        assertTrue(err2[0].contains("is inactive"))

        // Valid category
        val ok = RequestValidator.validateCategory(validCategory)
        assertTrue(ok.isEmpty())
    }

    // Step 2: Location
    @Test
    fun step2LocationValidation() {
        // Null coordinates
        val err1 = RequestValidator.validateLocation(null, "Plot 42, Ali Hassan Rd", listOf(activeZone))
        assertTrue(err1.any { it.contains("select a pickup location") })

        // Outside active zones
        val err2 = RequestValidator.validateLocation(outsideCoords, "Plot 42, Ali Hassan Rd", listOf(activeZone))
        assertTrue(err2.any { it.contains("outside active municipal collection zones") })

        // Blank address
        val err3 = RequestValidator.validateLocation(validCoords, " ", listOf(activeZone))
        assertTrue(err3.any { it.contains("address or landmark is required") })

        // Valid coordinates and address
        val ok = RequestValidator.validateLocation(validCoords, "Plot 42, Ali Hassan Rd", listOf(activeZone))
        assertTrue(ok.isEmpty())
    }

    // Step 3: Volume
    @Test
    fun step3VolumeValidation() {
        val err = RequestValidator.validateVolume(null)
        assertEquals(1, err.size)
        assertTrue(err[0].contains("select an estimated waste volume tier"))

        val ok = RequestValidator.validateVolume(VolumeTier.STANDARD_BIN)
        assertTrue(ok.isEmpty())
    }

    // Optional inputs
    @Test
    fun optionalDetailsValidation() {
        val longNotes = "a".repeat(501)
        val errNotes = RequestValidator.validateOptionalDetails(longNotes, null)
        assertEquals(1, errNotes.size)
        assertTrue(errNotes[0].contains("exceed maximum length"))

        val oversizedPhoto = "a".repeat(1_048_577)
        val errPhoto = RequestValidator.validateOptionalDetails(null, oversizedPhoto)
        assertEquals(1, errPhoto.size)
        assertTrue(errPhoto[0].contains("exceeds maximum allowed size"))
    }

    // Complete Draft
    @Test
    fun completeDraftValidation() {
        // Valid draft succeeds
        RequestValidator.assertValidDraft(
            category = validCategory,
            coordinates = validCoords,
            pickupAddress = "Plot 42, Ali Hassan Rd",
            volumeTier = VolumeTier.STANDARD_BIN,
            activeZones = listOf(activeZone),
            notes = "Ring bell"
        )

        // Invalid draft throws RequestValidationException
        assertThrows(RequestValidationException::class.java) {
            RequestValidator.assertValidDraft(
                category = null,
                coordinates = outsideCoords,
                pickupAddress = "",
                volumeTier = null,
                activeZones = listOf(activeZone)
            )
        }
    }
}
