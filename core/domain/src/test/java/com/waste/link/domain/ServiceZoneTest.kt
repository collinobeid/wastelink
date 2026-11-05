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

    // Test zone fixtures with isolated non-hardcoded test boundaries
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

    private val kinondoniCoords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
    private val outsideCoords = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)

    // UT-DOM-18: Coordinate bounds check against active ServiceZone
    @Test
    fun coordinatesInsideActiveZone_returnsTrue() {
        assertTrue(activeZoneKinondoni.contains(kinondoniCoords))
        assertTrue(activeZoneKinondoni.containsGeographically(kinondoniCoords))
    }

    @Test
    fun coordinatesOutsideActiveZone_returnsFalse() {
        assertFalse(activeZoneKinondoni.contains(outsideCoords))
        assertFalse(activeZoneKinondoni.containsGeographically(outsideCoords))
    }

    @Test
    fun coordinatesInsideZoneA_areNotInsideZoneB() {
        // Point is strictly inside Kinondoni, must NOT be inside Ilala
        assertTrue(activeZoneKinondoni.contains(kinondoniCoords))
        assertFalse(activeZoneIlala.contains(kinondoniCoords))
    }

    @Test
    fun coordinatesInsideInactiveZone_containsReturnsFalse() {
        val temekeCoords = GeoCoordinates(latitude = -6.9200, longitude = 39.2500)
        // Geographically contained, but active check returns false
        assertTrue(inactiveZoneTemeke.containsGeographically(temekeCoords))
        assertFalse(inactiveZoneTemeke.contains(temekeCoords))
    }

    @Test
    fun findActiveZone_succeedsForCoveredCoordinates() {
        val zone = findActiveZoneForCoordinates(kinondoniCoords, listOf(activeZoneKinondoni, activeZoneIlala))
        assertEquals("zone_kinondoni_01", zone.zoneId)
    }

    @Test
    fun findActiveZone_throwsOutsideServiceZoneExceptionWhenUncoveredOrEmpty() {
        // Uncovered coordinates
        assertThrows(OutsideServiceZoneException::class.java) {
            findActiveZoneForCoordinates(outsideCoords, listOf(activeZoneKinondoni, activeZoneIlala))
        }

        // Empty active zones catalog
        assertThrows(OutsideServiceZoneException::class.java) {
            findActiveZoneForCoordinates(kinondoniCoords, emptyList())
        }
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
