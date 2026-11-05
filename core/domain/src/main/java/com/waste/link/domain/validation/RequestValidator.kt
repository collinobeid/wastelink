package com.waste.link.domain.validation

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestValidationException
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.VolumeTier

/**
 * Pure domain validator for the multi-step resident pickup request wizard.
 *
 * Enforces the approved canonical wizard progression:
 * Step 1: Category -> Step 2: Location -> Step 3: Volume -> Step 4: Confirm.
 */
object RequestValidator {

    const val MAX_ADDRESS_LENGTH = 300
    const val MIN_ADDRESS_LENGTH = 2
    const val MAX_NOTES_LENGTH = 500
    const val MAX_GATE_PHOTO_BASE64_LENGTH = 1_048_576 // 1 MB raw base64 guard

    /**
     * Validates Step 1: Waste Category Selection.
     *
     * @return List of validation error messages, empty if valid.
     */
    fun validateCategory(category: DynamicWasteCategory?): List<String> {
        val errors = mutableListOf<String>()
        if (category == null) {
            errors.add("Please select a waste category.")
        } else {
            if (category.id.isBlank()) {
                errors.add("Category ID cannot be blank.")
            }
            if (!category.isActive) {
                errors.add("Selected category '${category.displayName}' is inactive and cannot be chosen.")
            }
        }
        return errors
    }

    /**
     * Validates Step 2: Location and Service Zone.
     *
     * @return List of validation error messages, empty if valid.
     */
    fun validateLocation(
        coordinates: GeoCoordinates?,
        pickupAddress: String,
        activeZones: List<ServiceZone>
    ): List<String> {
        val errors = mutableListOf<String>()

        if (coordinates == null) {
            errors.add("Please select a pickup location on the map or enable GPS.")
        } else {
            val isInZone = activeZones.any { it.contains(coordinates) }
            if (!isInZone) {
                errors.add("Location is outside active municipal collection zones.")
            }
        }

        val trimmedAddress = pickupAddress.trim()
        if (trimmedAddress.length < MIN_ADDRESS_LENGTH) {
            errors.add("Pickup address or landmark is required (minimum $MIN_ADDRESS_LENGTH characters).")
        } else if (trimmedAddress.length > MAX_ADDRESS_LENGTH) {
            errors.add("Pickup address exceeds maximum length of $MAX_ADDRESS_LENGTH characters.")
        }

        return errors
    }

    /**
     * Validates Step 3: Volume Tier Selection.
     *
     * @return List of validation error messages, empty if valid.
     */
    fun validateVolume(volumeTier: VolumeTier?): List<String> {
        val errors = mutableListOf<String>()
        if (volumeTier == null) {
            errors.add("Please select an estimated waste volume tier.")
        }
        return errors
    }

    /**
     * Validates optional notes and gate photo inputs.
     */
    fun validateOptionalDetails(notes: String?, gatePhotoBase64: String?): List<String> {
        val errors = mutableListOf<String>()
        if (notes != null && notes.length > MAX_NOTES_LENGTH) {
            errors.add("Special instructions exceed maximum length of $MAX_NOTES_LENGTH characters.")
        }
        if (gatePhotoBase64 != null && gatePhotoBase64.length > MAX_GATE_PHOTO_BASE64_LENGTH) {
            errors.add("Gate reference photo exceeds maximum allowed size.")
        }
        return errors
    }

    /**
     * Validates the complete draft before final submission.
     * Throws [RequestValidationException] if any check fails.
     */
    fun assertValidDraft(
        category: DynamicWasteCategory?,
        coordinates: GeoCoordinates?,
        pickupAddress: String,
        volumeTier: VolumeTier?,
        activeZones: List<ServiceZone>,
        notes: String? = null,
        gatePhotoBase64: String? = null
    ) {
        val allErrors = mutableListOf<String>()
        allErrors.addAll(validateCategory(category))
        allErrors.addAll(validateLocation(coordinates, pickupAddress, activeZones))
        allErrors.addAll(validateVolume(volumeTier))
        allErrors.addAll(validateOptionalDetails(notes, gatePhotoBase64))

        if (allErrors.isNotEmpty()) {
            throw RequestValidationException(allErrors)
        }
    }
}
