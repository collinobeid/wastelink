package com.waste.link.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure domain value object representing geographic coordinates on the WGS 84 ellipsoid.
 *
 * Invariants:
 * - Latitude must lie within [-90.0, 90.0].
 * - Longitude must lie within [-180.0, 180.0].
 */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of bounds: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of bounds: $longitude" }
    }

    /**
     * Calculates the great-circle distance in meters to another coordinate using the Haversine formula.
     */
    fun distanceMetersTo(other: GeoCoordinates): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - this.latitude)
        val dLng = Math.toRadians(other.longitude - this.longitude)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(other.latitude)) *
            sin(dLng / 2.0) * sin(dLng / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadiusMeters * c
    }

    /**
     * Computes the geohash representation for these coordinates.
     */
    fun toGeohash(precision: Int = 7): String =
        GeohashUtils.encode(latitude, longitude, precision)
}

/**
 * Pure Kotlin Geohash encoder implementing standard Morton space-filling curve / base-32 interval halving.
 *
 * Reference verification:
 * Coordinates `lat: -6.7924, lng: 39.2083` encode to 7-character geohash `"kygck5q"`.
 */
object GeohashUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    /**
     * Encodes latitude and longitude into a standard base-32 geohash string.
     *
     * @param latitude Latitude in degrees [-90.0, 90.0]
     * @param longitude Longitude in degrees [-180.0, 180.0]
     * @param precision Number of base-32 characters (default: 7, approx. 153m x 153m bounding box)
     */
    fun encode(latitude: Double, longitude: Double, precision: Int = 7): String {
        require(latitude in -90.0..90.0) { "Latitude out of bounds: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of bounds: $longitude" }
        require(precision in 1..12) { "Precision must be between 1 and 12, received $precision" }

        val latInterval = doubleArrayOf(-90.0, 90.0)
        val lngInterval = doubleArrayOf(-180.0, 180.0)
        val geohash = StringBuilder(precision)

        var bit = 0
        var charIndex = 0
        var isEven = true

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lngInterval[0] + lngInterval[1]) / 2.0
                if (longitude > mid) {
                    charIndex = charIndex or BITS[bit]
                    lngInterval[0] = mid
                } else {
                    lngInterval[1] = mid
                }
            } else {
                val mid = (latInterval[0] + latInterval[1]) / 2.0
                if (latitude > mid) {
                    charIndex = charIndex or BITS[bit]
                    latInterval[0] = mid
                } else {
                    latInterval[1] = mid
                }
            }
            isEven = !isEven

            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[charIndex])
                bit = 0
                charIndex = 0
            }
        }

        return geohash.toString()
    }
}
