package com.waste.link.data.mapper

import com.waste.link.data.local.entity.ResidentDraftRecord
import com.waste.link.data.models.ResidentDraft

/**
 * Bidirectional mappers between [ResidentDraft] and Room persistence [ResidentDraftRecord].
 */
object ResidentDraftMapper {

    fun toRecord(draft: ResidentDraft): ResidentDraftRecord {
        return ResidentDraftRecord(
            clientUid = draft.clientUid.trim(),
            clientGeneratedId = draft.clientGeneratedId.trim(),
            currentStep = draft.currentStep.trim(),
            categoryId = draft.categoryId?.trim(),
            latitude = draft.latitude,
            longitude = draft.longitude,
            pickupAddress = draft.pickupAddress?.trim(),
            serviceZoneId = draft.serviceZoneId?.trim(),
            notes = draft.notes?.trim(),
            gatePhotoLocalPath = draft.gatePhotoLocalPath?.trim(),
            volumeTierId = draft.volumeTierId?.trim(),
            createdAtEpochMs = draft.createdAtEpochMs,
            updatedAtEpochMs = draft.updatedAtEpochMs
        )
    }

    fun toDomain(record: ResidentDraftRecord): ResidentDraft {
        return ResidentDraft(
            clientUid = record.clientUid,
            clientGeneratedId = record.clientGeneratedId,
            currentStep = record.currentStep,
            categoryId = record.categoryId,
            latitude = record.latitude,
            longitude = record.longitude,
            pickupAddress = record.pickupAddress,
            serviceZoneId = record.serviceZoneId,
            notes = record.notes,
            gatePhotoLocalPath = record.gatePhotoLocalPath,
            volumeTierId = record.volumeTierId,
            createdAtEpochMs = record.createdAtEpochMs,
            updatedAtEpochMs = record.updatedAtEpochMs
        )
    }
}

fun ResidentDraft.toRecord(): ResidentDraftRecord = ResidentDraftMapper.toRecord(this)

fun ResidentDraftRecord.toDomain(): ResidentDraft = ResidentDraftMapper.toDomain(this)
