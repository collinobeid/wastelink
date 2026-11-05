package com.waste.link.domain

import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.GeohashUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCoordinatesTest {

    // UT-DOM-01: Instantiate GeoCoordinates with valid coordinates
    @Test
    fun validCoordinates_instantiatedSuccessfully() {
        val coords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
        assertNotNull(coords)
        assertEquals(-6.7924, coords.latitude, 0.00001)
        assertEquals(39.2083, coords.longitude, 0.00001)
    }

    // UT-DOM-02: Latitude bounds check
    @Test
    fun invalidLatitude_throwsIllegalArgumentException() {
        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            GeoCoordinates(latitude = -91.0, longitude = 39.2083)
        }
        assertTrue(ex1.message!!.contains("Latitude out of bounds"))

        val ex2 = assertThrows(IllegalArgumentException::class.java) {
            GeoCoordinates(latitude = 95.0, longitude = 39.2083)
        }
        assertTrue(ex2.message!!.contains("Latitude out of bounds"))
    }

    // UT-DOM-03: Longitude bounds check
    @Test
    fun invalidLongitude_throwsIllegalArgumentException() {
        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            GeoCoordinates(latitude = -6.7924, longitude = -185.0)
        }
        assertTrue(ex1.message!!.contains("Longitude out of bounds"))

        val ex2 = assertThrows(IllegalArgumentException::class.java) {
            GeoCoordinates(latitude = -6.7924, longitude = 181.0)
        }
        assertTrue(ex2.message!!.contains("Longitude out of bounds"))
    }

    // UT-DOM-04: Verified Geohash computation for Dar es Salaam test coordinates
    @Test
    fun darEsSalaamCoordinates_computesExactGeohash() {
        // Exact verified geohash for (-6.7924, 39.2083) at 7-character precision is "kygck5q"
        val geohash = GeohashUtils.encode(latitude = -6.7924, longitude = 39.2083, precision = 7)
        assertEquals("kygck5q", geohash)

        val coords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
        assertEquals("kygck5q", coords.toGeohash(precision = 7))
    }

    @Test
    fun haversineDistance_computesAccurateProximity() {
        val pointA = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
        // Point B ~ 50 meters away
        val pointB = GeoCoordinates(latitude = -6.7924, longitude = 39.20875)
        val distance = pointA.distanceMetersTo(pointB)
        assertTrue(distance in 45.0..55.0)
    }
}
