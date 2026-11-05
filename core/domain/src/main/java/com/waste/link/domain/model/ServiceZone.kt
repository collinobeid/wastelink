package com.waste.link.domain.model

/**
 * Domain entity representing a municipal service zone boundary contract.
 *
 * Operational Rules:
 * - Real municipal service zone boundaries are NOT hardcoded in production; they are configured
 *   dynamically by the municipal administration and synced from `/service_zones/{zoneId}`.
 * - Pickup requests can only be placed within an active service zone boundary.
 * - Attempting to place a request outside all active zones throws [OutsideServiceZoneException].
 */
data class ServiceZone(
    val zoneId: String,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
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
    }

    /**
     * Checks if coordinates fall within this active service zone boundary.
     * Returns false if the zone is deactivated.
     */
    fun contains(coordinates: GeoCoordinates): Boolean {
        if (!isActive) return false
        return coordinates.latitude in minLat..maxLat &&
            coordinates.longitude in minLng..maxLng
    }

    /**
     * Checks geometric bounding box containment regardless of active status.
     */
    fun containsGeographically(coordinates: GeoCoordinates): Boolean {
        return coordinates.latitude in minLat..maxLat &&
            coordinates.longitude in minLng..maxLng
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
