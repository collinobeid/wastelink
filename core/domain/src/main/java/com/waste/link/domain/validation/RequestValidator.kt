package com.waste.link.domain.validation

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestValidationException
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.VolumeTier
import java.util.Base64

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

    /** Strict limit: One optional compressed WebP gate photograph smaller than or equal to 50 KB (51,200 bytes). */
    const val MAX_GATE_PHOTO_BYTES = 50 * 1024

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
     * Validates that:
     * 1. Coordinates are present and valid.
     * 2. The selected service zone exists in the supplied municipal catalog.
     * 3. The selected zone is active.
     * 4. The coordinates fall inside that exact selected zone.
     * 5. A location inside another zone must not validate against the selected zone.
     *
     * @return List of validation error messages, empty if valid.
     */
    fun validateLocation(
        coordinates: GeoCoordinates?,
        selectedZoneId: String?,
        pickupAddress: String,
        availableZones: List<ServiceZone>
    ): List<String> {
        val errors = mutableListOf<String>()

        if (coordinates == null) {
            errors.add("Please select a pickup location on the map or enable GPS.")
        }

        if (selectedZoneId.isNullOrBlank()) {
            errors.add("A municipal service zone must be selected.")
        } else {
            val zone = availableZones.find { it.zoneId.equals(selectedZoneId.trim(), ignoreCase = true) }
            if (zone == null) {
                errors.add("Selected service zone '$selectedZoneId' does not exist in the municipal catalog.")
            } else if (!zone.isActive) {
                errors.add("Selected service zone '${zone.name}' ($selectedZoneId) is currently inactive.")
            } else if (coordinates != null && !zone.contains(coordinates)) {
                errors.add(
                    "Coordinates (${coordinates.latitude}, ${coordinates.longitude}) fall outside selected service zone '${zone.name}' ($selectedZoneId)."
                )
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
     * Validates optional gate photograph:
     * - Must be strictly optional (null or blank allowed).
     * - Must be valid Base64 data.
     * - Decoded raw byte size must not exceed 50 KB (51,200 bytes).
     * - Must be in compressed WebP format (RIFF container with WEBP chunk).
     */
    fun validateGatePhoto(gatePhotoBase64: String?): List<String> {
        val errors = mutableListOf<String>()
        if (gatePhotoBase64.isNullOrBlank()) {
            return errors
        }

        val rawBytes: ByteArray = try {
            Base64.getDecoder().decode(gatePhotoBase64.trim())
        } catch (e: IllegalArgumentException) {
            errors.add("Gate reference photo contains invalid Base64 data.")
            return errors
        }

        if (rawBytes.size > MAX_GATE_PHOTO_BYTES) {
            errors.add(
                "Gate reference photo size (${rawBytes.size} bytes) exceeds maximum allowed size of 50 KB ($MAX_GATE_PHOTO_BYTES bytes)."
            )
        }

        // WebP RIFF format validation:
        // Bytes 0..3: 'R', 'I', 'F', 'F'
        // Bytes 8..11: 'W', 'E', 'B', 'P'
        val isWebP = rawBytes.size >= 12 &&
            rawBytes[0] == 0x52.toByte() && rawBytes[1] == 0x49.toByte() &&
            rawBytes[2] == 0x46.toByte() && rawBytes[3] == 0x46.toByte() &&
            rawBytes[8] == 0x57.toByte() && rawBytes[9] == 0x45.toByte() &&
            rawBytes[10] == 0x42.toByte() && rawBytes[11] == 0x50.toByte()

        if (!isWebP) {
            errors.add("Gate reference photo must be in compressed WebP format.")
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
        errors.addAll(validateGatePhoto(gatePhotoBase64))
        return errors
    }

    /**
     * Validates the complete draft before final submission.
     * Throws [RequestValidationException] if any check fails.
     */
    fun assertValidDraft(
        category: DynamicWasteCategory?,
        coordinates: GeoCoordinates?,
        selectedZoneId: String?,
        pickupAddress: String,
        volumeTier: VolumeTier?,
        availableZones: List<ServiceZone>,
        notes: String? = null,
        gatePhotoBase64: String? = null
    ) {
        val allErrors = mutableListOf<String>()
        allErrors.addAll(validateCategory(category))
        allErrors.addAll(validateLocation(coordinates, selectedZoneId, pickupAddress, availableZones))
        allErrors.addAll(validateVolume(volumeTier))
        allErrors.addAll(validateOptionalDetails(notes, gatePhotoBase64))

        if (allErrors.isNotEmpty()) {
            throw RequestValidationException(allErrors)
        }
    }
}
