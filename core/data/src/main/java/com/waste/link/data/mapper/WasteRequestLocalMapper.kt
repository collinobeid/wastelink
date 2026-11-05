package com.waste.link.data.mapper

import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest

/**
 * Bidirectional mappers between pure domain [WasteRequest] and Room persistence [LocalWasteRequestRecord].
 *
 * Guarantees:
 * 1. Zero data loss on round-trip transformation of all supported domain fields.
 * 2. Strict independence between domain operational [RequestLifecycleStatus] and persistence [LocalSyncStatus].
 */
object WasteRequestLocalMapper {

    fun toLocalRecord(
        domain: WasteRequest,
        syncStatus: LocalSyncStatus = LocalSyncStatus.QUEUED_OFFLINE,
        gatePhotoLocalPath: String? = null,
        lastSyncAttemptEpochMs: Long? = null,
        syncErrorMessage: String? = null,
        retryCount: Int = 0
    ): LocalWasteRequestRecord {
        return LocalWasteRequestRecord(
            id = domain.id,
            clientGeneratedId = domain.clientGeneratedId,
            clientUid = domain.clientUid,
            requestRef = domain.requestRef,
            latitude = domain.coordinates.latitude,
            longitude = domain.coordinates.longitude,
            geohash = domain.geohash,
            serviceZoneId = domain.serviceZoneId,
            pickupAddress = domain.pickupAddress,
            categoryId = domain.categoryId,
            volumeTierId = domain.volumeTier.id,
            notes = domain.notes,
            gatePhotoUrl = domain.gatePhotoUrl,
            gatePhotoLocalPath = gatePhotoLocalPath,
            gatePhotoBase64 = domain.gatePhotoBase64,
            pocPhotoUrl = domain.pocPhotoUrl,
            pocPhotoBase64 = domain.pocPhotoBase64,
            status = domain.status.name,
            syncStatus = syncStatus,
            assignedCollectorUid = domain.assignedCollectorUid,
            assignedVehicleId = domain.assignedVehicleId,
            priceTzs = domain.priceTzs,
            currency = domain.currency,
            residentPhoneNumber = domain.residentPhoneNumber,
            collectorPhoneNumber = domain.collectorPhoneNumber,
            failureReason = domain.failureReason,
            failurePhotoUrl = domain.failurePhotoUrl,
            failurePhotoBase64 = domain.failurePhotoBase64,
            disputeReason = domain.disputeReason,
            adminResolutionNote = domain.adminResolutionNote,
            createdAtEpochMs = domain.createdAtEpochMs,
            updatedAtEpochMs = domain.updatedAtEpochMs,
            assignedAtEpochMs = domain.assignedAtEpochMs,
            enRouteAtEpochMs = domain.enRouteAtEpochMs,
            arrivedAtEpochMs = domain.arrivedAtEpochMs,
            collectedAtEpochMs = domain.collectedAtEpochMs,
            completedAtEpochMs = domain.completedAtEpochMs,
            failedAtEpochMs = domain.failedAtEpochMs,
            lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
            syncErrorMessage = syncErrorMessage,
            retryCount = retryCount
        )
    }

    fun toDomain(record: LocalWasteRequestRecord): WasteRequest {
        val resolvedStatus = RequestLifecycleStatus.fromLegacyStatus(record.status)
            ?: throw IllegalStateException(
                "Corrupted or unrecognized lifecycle status in persistence record '${record.id}': '${record.status}'. " +
                    "Silent fallback to PENDING_DISPATCH or any default operational state is strictly prohibited."
            )
        val resolvedVolumeTier = VolumeTier.fromId(record.volumeTierId)

        return WasteRequest(
            id = record.id,
            clientGeneratedId = record.clientGeneratedId,
            requestRef = record.requestRef,
            clientUid = record.clientUid,
            coordinates = GeoCoordinates(record.latitude, record.longitude),
            geohash = record.geohash,
            serviceZoneId = record.serviceZoneId,
            pickupAddress = record.pickupAddress,
            categoryId = record.categoryId,
            volumeTier = resolvedVolumeTier,
            notes = record.notes,
            gatePhotoUrl = record.gatePhotoUrl,
            gatePhotoBase64 = record.gatePhotoBase64,
            pocPhotoUrl = record.pocPhotoUrl,
            pocPhotoBase64 = record.pocPhotoBase64,
            status = resolvedStatus,
            assignedCollectorUid = record.assignedCollectorUid,
            assignedVehicleId = record.assignedVehicleId,
            priceTzs = record.priceTzs,
            currency = record.currency,
            residentPhoneNumber = record.residentPhoneNumber,
            collectorPhoneNumber = record.collectorPhoneNumber,
            failureReason = record.failureReason,
            failurePhotoUrl = record.failurePhotoUrl,
            failurePhotoBase64 = record.failurePhotoBase64,
            disputeReason = record.disputeReason,
            adminResolutionNote = record.adminResolutionNote,
            createdAtEpochMs = record.createdAtEpochMs,
            updatedAtEpochMs = record.updatedAtEpochMs,
            assignedAtEpochMs = record.assignedAtEpochMs,
            enRouteAtEpochMs = record.enRouteAtEpochMs,
            arrivedAtEpochMs = record.arrivedAtEpochMs,
            collectedAtEpochMs = record.collectedAtEpochMs,
            completedAtEpochMs = record.completedAtEpochMs,
            failedAtEpochMs = record.failedAtEpochMs
        )
    }

    /**
     * Safe domain mapping helper returning null if the record contains an unrecognized
     * lifecycle status or unrecognized volume tier.
     */
    fun toDomainOrNull(record: LocalWasteRequestRecord): WasteRequest? {
        return runCatching { toDomain(record) }.getOrNull()
    }
}

fun WasteRequest.toLocalRecord(
    syncStatus: LocalSyncStatus = LocalSyncStatus.QUEUED_OFFLINE,
    gatePhotoLocalPath: String? = null,
    lastSyncAttemptEpochMs: Long? = null,
    syncErrorMessage: String? = null,
    retryCount: Int = 0
): LocalWasteRequestRecord = WasteRequestLocalMapper.toLocalRecord(
    domain = this,
    syncStatus = syncStatus,
    gatePhotoLocalPath = gatePhotoLocalPath,
    lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
    syncErrorMessage = syncErrorMessage,
    retryCount = retryCount
)

fun LocalWasteRequestRecord.toDomain(): WasteRequest =
    WasteRequestLocalMapper.toDomain(this)

fun LocalWasteRequestRecord.toDomainOrNull(): WasteRequest? =
    WasteRequestLocalMapper.toDomainOrNull(this)
