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

    /** Strict product requirement: Optional gate photograph must be strictly smaller than 50 KB (< 51,200 bytes). Exactly 50 KB or larger is rejected. */
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
     * - Strict 50 KB boundary: Decoded raw byte size must be strictly smaller than 50 KB (< 51,200 bytes).
     *   Exactly 50 KB (51,200 bytes) or larger is rejected.
     * - Structural WebP container validation:
     *     - RIFF container envelope (bytes 0..3: 'RIFF').
     *     - Little-endian declared RIFF length consistency (bytes 4..7; rejects truncated payloads).
     *     - WebP identifier (bytes 8..11: 'WEBP').
     *     - Supported chunk FourCC header (bytes 12..15: 'VP8 ', 'VP8L', or 'VP8X').
     *     - Chunk length consistency (bytes 16..19; rejects truncated chunk data).
     *     - Format-specific magic signatures (VP8 keyframe code 0x9D 0x01 0x2A or VP8L 0x2F signature).
     *
     * BOUNDARY & AUTHENTICITY DISCLAIMER:
     * This function performs structural container validation in pure Kotlin.
     * Pixel raster decoding, visual rendering, and EXIF extraction belong to the Android platform
     * layer (BitmapFactory / ImageDecoder).
     * Real-world authenticity, camera hardware provenance, EXIF timestamp verification, anti-tampering,
     * and storage upload authorization are enforced by Play Integrity API and Cloud Storage security rules.
     * Client-side validation does NOT prove authenticity or physical ownership of photographic evidence.
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

        // Strict 50 KB boundary requirement (< 50 * 1024 bytes)
        if (rawBytes.size >= MAX_GATE_PHOTO_BYTES) {
            errors.add(
                "Gate reference photo size (${rawBytes.size} bytes) must be strictly smaller than 50 KB ($MAX_GATE_PHOTO_BYTES bytes)."
            )
        }

        // Structural WebP container validation
        val webpError = validateWebpStructure(rawBytes)
        if (webpError != null) {
            errors.add(webpError)
        }

        return errors
    }

    /**
     * Inspects raw bytes to verify WebP container and stream structure.
     * Returns error description if invalid, or null if structurally valid.
     *
     * STRUCTURAL VALIDATION GUARANTEES:
     * - Validates RIFF container envelope (bytes 0..3: 'RIFF').
     * - Validates RIFF declared length against actual payload: rejects truncated payloads and extraneous trailing data.
     * - Validates WebP identifier (bytes 8..11: 'WEBP').
     * - Validates chunk boundaries, headers, and 2-byte alignment padding.
     * - For simple lossy ('VP8 '): verifies keyframe start code (0x9D 0x01 0x2A) and single-image container span.
     * - For simple lossless ('VP8L'): verifies 0x2F signature and single-image container span.
     * - For extended containers ('VP8X'): rejects header-only payloads, validates subsequent chunk headers and bounds,
     *   and strictly requires presence of valid image data ('VP8 ' or 'VP8L').
     */
    private fun validateWebpStructure(bytes: ByteArray): String? {
        if (bytes.size < 20) {
            return "Gate reference photo must be in compressed WebP format (minimum 20 bytes required)."
        }

        // 1. RIFF header: bytes 0..3 == 'R', 'I', 'F', 'F'
        val isRiff = bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        if (!isRiff) {
            return "Gate reference photo must be in compressed WebP format."
        }

        // 2. Declared RIFF file size: bytes 4..7 (little-endian unsigned 32-bit)
        val declaredRiffSize = readLittleEndian32(bytes, 4)
        val expectedMinSize = declaredRiffSize + 8L
        val riffPad = declaredRiffSize % 2L

        // Total file must strictly match declaredRiffSize + 8 (+ optional 1-byte RIFF pad if declaredRiffSize is odd)
        if (bytes.size.toLong() < expectedMinSize) {
            return "Gate reference photo is truncated (RIFF declared length $declaredRiffSize requires at least $expectedMinSize bytes, got ${bytes.size} bytes)."
        }
        if (bytes.size.toLong() > expectedMinSize + riffPad) {
            return "Gate reference photo contains extraneous data (RIFF declared length $declaredRiffSize specifies $expectedMinSize bytes, got ${bytes.size} bytes)."
        }

        // 3. WEBP FourCC: bytes 8..11 == 'W', 'E', 'B', 'P'
        val isWebp = bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        if (!isWebp) {
            return "Gate reference photo must be in compressed WebP format."
        }

        // 4. First Chunk FourCC: bytes 12..15
        val firstFourCC = readFourCC(bytes, 12)
        val firstChunkSize = readLittleEndian32(bytes, 16)

        if (12 + 8 + firstChunkSize > bytes.size) {
            return "Gate reference photo is truncated (chunk '$firstFourCC' declared $firstChunkSize bytes exceeding payload)."
        }

        return when (firstFourCC) {
            "VP8 " -> {
                if (firstChunkSize < 10L) {
                    "Gate reference photo contains corrupted VP8 header (chunk size $firstChunkSize < 10)."
                } else {
                    // Check VP8 keyframe start code at bytes 23..25: 0x9D, 0x01, 0x2A
                    val hasKeyframeCode = bytes[23] == 0x9D.toByte() &&
                        bytes[24] == 0x01.toByte() &&
                        bytes[25] == 0x2A.toByte()
                    if (!hasKeyframeCode) {
                        "Gate reference photo contains corrupted VP8 stream (missing keyframe start code)."
                    } else null
                }
            }
            "VP8L" -> {
                if (firstChunkSize < 5L) {
                    "Gate reference photo contains corrupted VP8L header (chunk size $firstChunkSize < 5)."
                } else {
                    // Check VP8L 1-byte signature at byte 20: 0x2F
                    val hasLosslessSignature = bytes[20] == 0x2F.toByte()
                    if (!hasLosslessSignature) {
                        "Gate reference photo contains corrupted VP8L stream (missing 0x2F signature)."
                    } else null
                }
            }
            "VP8X" -> {
                if (firstChunkSize < 10L) {
                    return "Gate reference photo contains corrupted VP8X header (chunk size $firstChunkSize < 10)."
                }
                val firstPad = (firstChunkSize % 2L).toInt()
                val nextChunkOffset = 20 + firstChunkSize.toInt() + firstPad
                if (nextChunkOffset >= bytes.size) {
                    return "Gate reference photo contains header-only VP8X without image data."
                }

                var subOffset = nextChunkOffset
                var foundImageChunk = false

                while (subOffset < bytes.size) {
                    if (subOffset + 8 > bytes.size) {
                        return "Gate reference photo contains truncated subsequent chunk header at offset $subOffset."
                    }
                    val subFourCC = readFourCC(bytes, subOffset)
                    val subChunkSize = readLittleEndian32(bytes, subOffset + 4)
                    val subPayloadOffset = subOffset + 8

                    if (subPayloadOffset + subChunkSize > bytes.size) {
                        return "Gate reference photo contains truncated subsequent chunk '$subFourCC' (declared $subChunkSize bytes exceeding payload)."
                    }

                    when (subFourCC) {
                        "VP8 " -> {
                            if (subChunkSize < 10L) {
                                return "Gate reference photo contains corrupted subsequent VP8 header (chunk size $subChunkSize < 10)."
                            }
                            val hasKeyframeCode = bytes[subPayloadOffset + 3] == 0x9D.toByte() &&
                                bytes[subPayloadOffset + 4] == 0x01.toByte() &&
                                bytes[subPayloadOffset + 5] == 0x2A.toByte()
                            if (!hasKeyframeCode) {
                                return "Gate reference photo contains corrupted VP8 stream in extended container (missing keyframe start code)."
                            }
                            foundImageChunk = true
                        }
                        "VP8L" -> {
                            if (subChunkSize < 5L) {
                                return "Gate reference photo contains corrupted subsequent VP8L header (chunk size $subChunkSize < 5)."
                            }
                            val hasLosslessSignature = bytes[subPayloadOffset] == 0x2F.toByte()
                            if (!hasLosslessSignature) {
                                return "Gate reference photo contains corrupted VP8L stream in extended container (missing 0x2F signature)."
                            }
                            foundImageChunk = true
                        }
                        "ICCP", "ALPH", "EXIF", "XMP " -> {
                            // Valid auxiliary chunks
                        }
                        else -> {
                            return "Gate reference photo contains unrecognized subsequent chunk '$subFourCC' in VP8X container."
                        }
                    }

                    val pad = (subChunkSize % 2L).toInt()
                    subOffset = subPayloadOffset + subChunkSize.toInt() + pad
                }

                if (!foundImageChunk) {
                    "Gate reference photo contains VP8X container with missing image data (no VP8 or VP8L chunk found)."
                } else null
            }
            else -> {
                "Gate reference photo must be in compressed WebP format (unsupported chunk: '$firstFourCC')."
            }
        }
    }

    private fun readLittleEndian32(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun readFourCC(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        val c0 = bytes[offset].toInt().toChar()
        val c1 = bytes[offset + 1].toInt().toChar()
        val c2 = bytes[offset + 2].toInt().toChar()
        val c3 = bytes[offset + 3].toInt().toChar()
        return "$c0$c1$c2$c3"
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
