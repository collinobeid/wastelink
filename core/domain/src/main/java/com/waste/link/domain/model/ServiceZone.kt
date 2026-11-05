package com.waste.link.domain.model

/**
 * Domain entity representing a municipal service zone boundary contract.
 *
 * Operational Rules:
 * - Real municipal service zone boundaries are NOT hardcoded in production; they are configured
 *   dynamically by the municipal administration and synced from `/service_zones/{zoneId}`.
 * - Pickup requests can only be placed within an active service zone boundary.
 * - Attempting to place a request outside all active zones throws [OutsideServiceZoneException].
 *
 * GEOMETRIC SPECIFICATION & BOUNDARY ACCURACY:
 * - [minLat], [maxLat], [minLng], [maxLng] define an axis-aligned bounding box (AABB).
 *   This rectangular bounding box serves as an intentional initial approximation and high-efficiency
 *   O(1) spatial pre-filter.
 * - When [polygonVertices] is empty, containment evaluates strictly against this bounding box approximation.
 *   This MUST NOT be claimed as exact municipal ward/polygon boundary validation in reports or documentation.
 * - When [polygonVertices] is supplied (at least 3 WGS 84 vertices forming a sequential boundary),
 *   containment is evaluated using pure Kotlin ray-casting (even-odd rule) point-in-polygon verification.
 *   This provides exact non-convex municipal boundary verification without external GIS, Android SDK,
 *   or Firebase dependencies.
 */
data class ServiceZone(
    val zoneId: String,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val polygonVertices: List<GeoCoordinates> = emptyList(),
    val isActive: Boolean = true
) {
    init {
        require(zoneId.isNotBlank()) { "ServiceZone.zoneId must not be blank" }
        require(name.isNotBlank()) { "ServiceZone.name must not be blank" }
        require(minLat in -90.0..90.0) { "minLat out of bounds: $minLat" }
        require(maxLat in -90.0..90.0) { "maxLat out of bounds: $maxLat" }
        require(minLat <= maxLat) { "minLat ($minLat) must be <= maxLat ($maxLat)" }
        require(minLng in -180.0..180.0) { "minLng out of bounds: $minLng" }
        require(maxLng in -180.0..180.0) { "maxLng out of bounds: $maxLng" }
        require(minLng <= maxLng) { "minLng ($minLng) must be <= maxLng ($maxLng)" }
        if (polygonVertices.isNotEmpty()) {
            require(polygonVertices.size >= 3) {
                "ServiceZone polygonVertices must contain at least 3 vertices to form a valid polygon, but had ${polygonVertices.size}"
            }
        }
    }

    /**
     * Checks if coordinates fall within this active service zone.
     * Evaluates active state, then bounding-box pre-filter, then exact polygon if configured.
     * Returns false if the zone is deactivated.
     */
    fun contains(coordinates: GeoCoordinates): Boolean {
        if (!isActive) return false
        return containsGeographically(coordinates)
    }

    /**
     * Checks geometric containment regardless of active status.
     * 1. Evaluates bounding box in O(1). If outside, returns false immediately.
     * 2. If [polygonVertices] is configured, evaluates ray-casting point-in-polygon containment.
     * 3. Otherwise, returns true (bounding-box approximation).
     */
    fun containsGeographically(coordinates: GeoCoordinates): Boolean {
        val inBoundingBox = coordinates.latitude in minLat..maxLat &&
            coordinates.longitude in minLng..maxLng
        if (!inBoundingBox) return false

        if (polygonVertices.isEmpty()) {
            return true
        }

        return isPointInPolygon(coordinates, polygonVertices)
    }

    companion object {
        /**
         * Pure Kotlin Ray-Casting Algorithm (Even-Odd Rule).
         * Determines if a point [pt] is inside a 2D polygon defined by [vertices].
         * Works for arbitrary convex and non-convex polygons without platform dependencies.
         */
        fun isPointInPolygon(pt: GeoCoordinates, vertices: List<GeoCoordinates>): Boolean {
            if (vertices.size < 3) return false
            var inside = false
            var j = vertices.size - 1
            for (i in vertices.indices) {
                val vi = vertices[i]
                val vj = vertices[j]
                val xi = vi.longitude
                val yi = vi.latitude
                val xj = vj.longitude
                val yj = vj.latitude

                val intersect = ((yi > pt.latitude) != (yj > pt.latitude)) &&
                    (pt.longitude < (xj - xi) * (pt.latitude - yi) / (yj - yi) + xi)
                if (intersect) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}

/**
 * Finds the first active [ServiceZone] that contains the given [coordinates].
 *
 * @throws OutsideServiceZoneException if [coordinates] do not fall within any active zone.
 */
fun findActiveZoneForCoordinates(
    coordinates: GeoCoordinates,
    zones: Collection<ServiceZone>
): ServiceZone {
    return zones.firstOrNull { it.contains(coordinates) }
        ?: throw OutsideServiceZoneException(
            "Location (${coordinates.latitude}, ${coordinates.longitude}) is outside all active municipal collection zones."
        )
}
