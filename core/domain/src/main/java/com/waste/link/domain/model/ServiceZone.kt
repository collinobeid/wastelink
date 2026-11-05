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
 * - When [polygonVertices] is supplied (at least 3 WGS 84 vertices forming a sequential boundary):
 *     1. Invariant: Every polygon vertex must fall strictly within the declared bounding box [minLat..maxLat] and [minLng..maxLng].
 *     2. Boundary-Inclusive Policy: Containment is strictly boundary-inclusive. Points lying directly on a polygon
 *        vertex or along a polygon boundary edge are considered INSIDE for service-zone eligibility.
 *     3. Interior Containment: Points within the polygon interior are verified using the pure Kotlin Ray-Casting
 *        algorithm (Jordan curve theorem, even-odd rule).
 *   This provides exact municipal boundary verification without external GIS, Android SDK, or Firebase dependencies.
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
            polygonVertices.forEachIndexed { index, vertex ->
                require(vertex.latitude in minLat..maxLat && vertex.longitude in minLng..maxLng) {
                    "Polygon vertex #$index (${vertex.latitude}, ${vertex.longitude}) falls outside declared bounding box (lat in $minLat..$maxLat, lng in $minLng..$maxLng)"
                }
            }
        }
    }

    /**
     * Checks if coordinates fall within this active service zone.
     * Evaluates active state, then bounding-box pre-filter, then exact boundary-inclusive polygon if configured.
     * Returns false if the zone is deactivated.
     */
    fun contains(coordinates: GeoCoordinates): Boolean {
        if (!isActive) return false
        return containsGeographically(coordinates)
    }

    /**
     * Checks geometric containment regardless of active status.
     * 1. Evaluates bounding box in O(1). If outside, returns false immediately.
     * 2. If [polygonVertices] is configured, evaluates boundary-inclusive ray-casting point-in-polygon containment.
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
         * Boundary-Inclusive 2D Polygon Containment Verification.
         *
         * Boundary Policy:
         * 1. A point lying directly on any polygon vertex is considered INSIDE.
         * 2. A point lying directly on any boundary edge segment is considered INSIDE.
         * 3. An interior point is verified using the Ray-Casting algorithm (even-odd rule).
         *
         * @param pt Coordinate point to test.
         * @param vertices Ordered list of polygon vertices (minimum 3).
         * @param epsilon Epsilon tolerance for collinearity and floating point boundary detection (default 1e-9 degrees).
         */
        fun isPointInPolygon(
            pt: GeoCoordinates,
            vertices: List<GeoCoordinates>,
            epsilon: Double = 1e-9
        ): Boolean {
            if (vertices.size < 3) return false

            // Step 1: Boundary check — point on vertex or edge segment is considered INSIDE
            var j = vertices.size - 1
            for (i in vertices.indices) {
                val vi = vertices[i]
                val vj = vertices[j]
                if (isPointOnSegment(pt, vj, vi, epsilon)) {
                    return true
                }
                j = i
            }

            // Step 2: Interior check — Ray-Casting Algorithm (Jordan curve theorem)
            var inside = false
            j = vertices.size - 1
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

        /**
         * Checks whether point [pt] lies directly on the segment between [v1] and [v2].
         */
        fun isPointOnSegment(
            pt: GeoCoordinates,
            v1: GeoCoordinates,
            v2: GeoCoordinates,
            epsilon: Double = 1e-9
        ): Boolean {
            val minLat = minOf(v1.latitude, v2.latitude) - epsilon
            val maxLat = maxOf(v1.latitude, v2.latitude) + epsilon
            val minLng = minOf(v1.longitude, v2.longitude) - epsilon
            val maxLng = maxOf(v1.longitude, v2.longitude) + epsilon

            if (pt.latitude !in minLat..maxLat || pt.longitude !in minLng..maxLng) {
                return false
            }

            // Cross product determines collinearity between (v1 -> pt) and (v1 -> v2)
            val crossProduct = (pt.longitude - v1.longitude) * (v2.latitude - v1.latitude) -
                (pt.latitude - v1.latitude) * (v2.longitude - v1.longitude)
            return kotlin.math.abs(crossProduct) <= epsilon
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
