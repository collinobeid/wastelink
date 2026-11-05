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

    // Triangular polygon zone inside bounding box [-6.80, -6.70] x [39.20, 39.30]
    // Vertices form a triangle: (-6.70, 39.20), (-6.70, 39.30), (-6.80, 39.20)
    private val polygonVertices = listOf(
        GeoCoordinates(latitude = -6.70, longitude = 39.20),
        GeoCoordinates(latitude = -6.70, longitude = 39.30),
        GeoCoordinates(latitude = -6.80, longitude = 39.20)
    )
    private val activePolygonZone = ServiceZone(
        zoneId = "zone_polygon_01",
        name = "Precision Triangular Ward",
        minLat = -6.80,
        maxLat = -6.70,
        minLng = 39.20,
        maxLng = 39.30,
        polygonVertices = polygonVertices,
        isActive = true
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
    fun boundaryEdgePoints_areContainedInBoundingBox() {
        // Inclusive bounds check: minLat, maxLat, minLng, maxLng edges must be contained
        val minEdge = GeoCoordinates(latitude = -6.8200, longitude = 39.1800)
        val maxEdge = GeoCoordinates(latitude = -6.7500, longitude = 39.2600)
        assertTrue(activeZoneKinondoni.contains(minEdge))
        assertTrue(activeZoneKinondoni.contains(maxEdge))
    }

    @Test
    fun polygonZone_pointInsidePolygon_returnsTrue() {
        // Point inside the triangle: (-6.72, 39.22)
        val insidePoint = GeoCoordinates(latitude = -6.72, longitude = 39.22)
        assertTrue(activePolygonZone.contains(insidePoint))
        assertTrue(activePolygonZone.containsGeographically(insidePoint))
    }

    @Test
    fun polygonZone_pointInsideBoundingBoxButOutsidePolygon_returnsFalse() {
        // Point in upper-right half of bounding box: (-6.72, 39.29)
        // Inside [-6.80, -6.70] x [39.20, 39.30], but outside the triangle!
        val outsidePoint = GeoCoordinates(latitude = -6.72, longitude = 39.29)
        assertFalse(activePolygonZone.contains(outsidePoint))
        assertFalse(activePolygonZone.containsGeographically(outsidePoint))
    }

    @Test
    fun polygonVertices_lessThanThree_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceZone(
                zoneId = "zone_invalid_poly",
                name = "Invalid Poly",
                minLat = -6.80,
                maxLat = -6.70,
                minLng = 39.20,
                maxLng = 39.30,
                polygonVertices = listOf(
                    GeoCoordinates(latitude = -6.70, longitude = 39.20),
                    GeoCoordinates(latitude = -6.70, longitude = 39.30)
                ) // only 2 vertices
            )
        }
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
                minLat = -6.7000,
                maxLat = -6.8000, // minLat > maxLat
                minLng = 39.2000,
                maxLng = 39.3000
            )
        }
    }
}
