package com.waste.link.data.remote.mapper

import com.google.firebase.Timestamp
import com.waste.link.data.remote.dto.WasteRequestDto
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest

/**
 * Bi-directional mapping between domain [WasteRequest] and Firestore [WasteRequestDto].
 */
object WasteRequestRemoteMapper {

    fun toDto(
        request: WasteRequest,
        allocatedRef: String? = null
    ): WasteRequestDto {
        return WasteRequestDto(
            id = request.id,
            requestRef = request.requestRef ?: allocatedRef.orEmpty(),
            clientUid = request.clientUid,
            lat = request.coordinates.latitude,
            lng = request.coordinates.longitude,
            geohash = request.geohash,
            pickupAddress = request.pickupAddress,
            category = request.categoryId,
            volumeTier = request.volumeTier.name,
            notes = request.notes,
            gatePhotoBase64 = request.gatePhotoBase64,
            gatePhotoUrl = request.gatePhotoUrl,
            pocPhotoBase64 = request.pocPhotoBase64,
            pocPhotoUrl = request.pocPhotoUrl,
            status = request.status.name,
            assignedTruckId = request.assignedCollectorUid,
            residentPhoneNumber = request.residentPhoneNumber,
            collectorPhoneNumber = request.collectorPhoneNumber,
            failureReason = request.failureReason,
            createdAt = request.createdAtEpochMs?.let { toTimestamp(it) },
            updatedAt = request.updatedAtEpochMs?.let { toTimestamp(it) },
            assignedAt = request.assignedAtEpochMs?.let { toTimestamp(it) },
            enRouteAt = request.enRouteAtEpochMs?.let { toTimestamp(it) },
            arrivedAt = request.arrivedAtEpochMs?.let { toTimestamp(it) },
            collectedAt = request.collectedAtEpochMs?.let { toTimestamp(it) },
            completedAt = request.completedAtEpochMs?.let { toTimestamp(it) },
            failedAt = request.failedAtEpochMs?.let { toTimestamp(it) }
        )
    }

    fun toDomain(
        dto: WasteRequestDto,
        fallbackServiceZoneId: String
    ): WasteRequest {
        val requestId = dto.id.ifBlank { error("WasteRequestDto.id must not be blank") }
        val coords = GeoCoordinates(dto.lat, dto.lng)
        val derivedGeohash = if (dto.geohash.length in 5..12 && coords.toGeohash(dto.geohash.length) == dto.geohash) {
            dto.geohash
        } else {
            coords.toGeohash()
        }

        val volumeTierEnum = VolumeTier.fromIdOrNull(dto.volumeTier)
            ?: VolumeTier.entries.firstOrNull { it.name.equals(dto.volumeTier, ignoreCase = true) }
            ?: VolumeTier.STANDARD_BIN

        val statusEnum = RequestLifecycleStatus.entries.firstOrNull {
            it.name.equals(dto.status, ignoreCase = true)
        } ?: RequestLifecycleStatus.PENDING_DISPATCH

        return WasteRequest(
            id = requestId,
            clientGeneratedId = requestId,
            requestRef = dto.requestRef.takeIf { it.isNotBlank() },
            clientUid = dto.clientUid,
            coordinates = coords,
            geohash = derivedGeohash,
            serviceZoneId = fallbackServiceZoneId,
            pickupAddress = dto.pickupAddress,
            categoryId = dto.category,
            volumeTier = volumeTierEnum,
            notes = dto.notes,
            gatePhotoUrl = dto.gatePhotoUrl,
            gatePhotoBase64 = dto.gatePhotoBase64,
            pocPhotoUrl = dto.pocPhotoUrl,
            pocPhotoBase64 = dto.pocPhotoBase64,
            status = statusEnum,
            assignedCollectorUid = dto.assignedTruckId,
            assignedVehicleId = null,
            priceTzs = dto.agreedPrice,
            currency = "TZS",
            residentPhoneNumber = dto.residentPhoneNumber,
            collectorPhoneNumber = dto.collectorPhoneNumber,
            failureReason = dto.failureReason,
            createdAtEpochMs = dto.createdAt?.let { toEpochMs(it) },
            updatedAtEpochMs = dto.updatedAt?.let { toEpochMs(it) },
            assignedAtEpochMs = dto.assignedAt?.let { toEpochMs(it) },
            enRouteAtEpochMs = dto.enRouteAt?.let { toEpochMs(it) },
            arrivedAtEpochMs = dto.arrivedAt?.let { toEpochMs(it) },
            collectedAtEpochMs = dto.collectedAt?.let { toEpochMs(it) },
            completedAtEpochMs = dto.completedAt?.let { toEpochMs(it) },
            failedAtEpochMs = dto.failedAt?.let { toEpochMs(it) }
        )
    }

    private fun toTimestamp(epochMs: Long): Timestamp {
        val seconds = epochMs / 1000
        val nanoseconds = ((epochMs % 1000) * 1_000_000).toInt()
        return Timestamp(seconds, nanoseconds)
    }

    private fun toEpochMs(timestamp: Timestamp): Long {
        return timestamp.seconds * 1000 + timestamp.nanoseconds / 1_000_000
    }
}
