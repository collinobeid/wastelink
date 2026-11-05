package com.waste.link.domain

import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.OutsideServiceZoneException
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.findActiveZoneForCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceZoneTest {

    // Test zone fixture (Kinondoni test boundary)
    private val activeKinondoniZone = ServiceZone(
        zoneId = "zone_kinondoni_01",
        name = "Kinondoni Municipal",
        minLat = -6.8200,
        maxLat = -6.7500,
        minLng = 39.1800,
        maxLng = 39.2600,
        isActive = true
    )

    private val inactiveTemekeZone = ServiceZone(
        zoneId = "zone_temeke_inactive",
        name = "Temeke Municipal (Decommissioned)",
        minLat = -6.9000,
        maxLat = -6.8300,
        minLng = 39.2000,
        maxLng = 39.3000,
        isActive = false
    )

    // UT-DOM-18: Coordinate bounds check against active ServiceZone
    @Test
    fun coordinatesInsideActiveZone_returnsTrue() {
        // Point inside Kinondoni test bounds
        val insidePoint = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
        assertTrue(activeKinondoniZone.contains(insidePoint))
        assertTrue(activeKinondoniZone.containsGeographically(insidePoint))
    }

    @Test
    fun coordinatesOutsideActiveZone_returnsFalse() {
        // Point far outside (e.g. Dodoma coordinates)
        val outsidePoint = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)
        assertFalse(activeKinondoniZone.contains(outsidePoint))
    }

    @Test
    fun coordinatesInsideInactiveZone_containsReturnsFalse() {
        val pointInTemeke = GeoCoordinates(latitude = -6.8500, longitude = 39.2500)
        // Geographically contained, but active check returns false
        assertTrue(inactiveTemekeZone.containsGeographically(pointInTemeke))
        assertFalse(inactiveTemekeZone.contains(pointInTemeke))
    }

    @Test
    fun findActiveZone_succeedsForCoveredCoordinates() {
        val coords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
        val zone = findActiveZoneForCoordinates(coords, listOf(activeKinondoniZone, inactiveTemekeZone))
        assertEquals("zone_kinondoni_01", zone.zoneId)
    }

    @Test
    fun findActiveZone_throwsOutsideServiceZoneExceptionWhenUncovered() {
        val outsideCoords = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)
        val ex = assertThrows(OutsideServiceZoneException::class.java) {
            findActiveZoneForCoordinates(outsideCoords, listOf(activeKinondoniZone, inactiveTemekeZone))
        }
        assertTrue(ex.message!!.contains("outside all active municipal collection zones"))
    }

    @Test
    fun serviceZone_invalidLatitudeInversion_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceZone(
                zoneId = "invalid_lat",
                name = "Invalid Lat",
                minLat = -6.5000,
                maxLat = -6.8000, // min > max
                minLng = 39.0,
                maxLng = 40.0
            )
        }
    }
}
