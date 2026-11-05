package com.waste.link.domain

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestValidationException
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.validation.RequestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RequestValidatorTest {

    private val activeZoneKinondoni = ServiceZone(
        zoneId = "zone_kinondoni_01",
        name = "Kinondoni Municipal",
        minLat = -6.8200,
        maxLat = -6.7500,
        minLng = 39.1800,
        maxLng = 39.2600,
        isActive = true
    )

    private val activeZoneIlala = ServiceZone(
        zoneId = "zone_ilala_02",
        name = "Ilala Municipal",
        minLat = -6.8800,
        maxLat = -6.8250,
        minLng = 39.2200,
        maxLng = 39.3000,
        isActive = true
    )

    private val inactiveZoneTemeke = ServiceZone(
        zoneId = "zone_temeke_inactive",
        name = "Temeke Municipal (Decommissioned)",
        minLat = -6.9500,
        maxLat = -6.8900,
        minLng = 39.2000,
        maxLng = 39.3000,
        isActive = false
    )

    private val availableZones = listOf(activeZoneKinondoni, activeZoneIlala, inactiveZoneTemeke)

    private val validCategory = DynamicWasteCategory(
        id = "household",
        displayName = "Household Waste",
        isActive = true
    )

    private val inactiveCategory = DynamicWasteCategory(
        id = "old_cat",
        displayName = "Old Category",
        isActive = false
    )

    // Kinondoni coordinates: (-6.7924, 39.2083)
    private val kinondoniCoords = GeoCoordinates(latitude = -6.7924, longitude = 39.2083)
    // Ilala coordinates: (-6.8500, 39.2600)
    private val ilalaCoords = GeoCoordinates(latitude = -6.8500, longitude = 39.2600)
    // Outside all zones (Dodoma): (-6.1630, 35.7516)
    private val outsideCoords = GeoCoordinates(latitude = -6.1630, longitude = 35.7516)

    // Genuine 1x1 Lossy WebP (68 bytes, libwebp generated)
    private val genuine1x1LossyWebpBase64 =
        "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoBAAEAAUAmJaACdLoB+AADsAD+8ut//NgVzXPv9//S4P0uD9Lg/9KQAAA="

    // Genuine 1x1 Lossless WebP (36 bytes, libwebp generated)
    private val genuine1x1LosslessWebpBase64 =
        "UklGRhwAAABXRUJQVlA4TA8AAAAvAAAAAAdQwIj+ByKi/wEA"

    /**
     * Constructs a structurally valid VP8 WebP with exact byte size [totalBytes].
     */
    private fun createValidStructuralWebP(totalBytes: Int): String {
        require(totalBytes >= 26) { "Valid VP8 WebP requires at least 26 bytes" }
        val arr = ByteArray(totalBytes)
        // RIFF header: 'R', 'I', 'F', 'F'
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP FourCC
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8 Chunk FourCC
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = ' '.code.toByte()
        val chunkSize = totalBytes - 20
        arr[16] = (chunkSize and 0xFF).toByte()
        arr[17] = ((chunkSize shr 8) and 0xFF).toByte()
        arr[18] = ((chunkSize shr 16) and 0xFF).toByte()
        arr[19] = ((chunkSize shr 24) and 0xFF).toByte()
        // VP8 frame tag
        arr[20] = 0xD0.toByte()
        arr[21] = 0x01.toByte()
        arr[22] = 0x00.toByte()
        // VP8 keyframe start code: 0x9D, 0x01, 0x2A
        arr[23] = 0x9D.toByte()
        arr[24] = 0x01.toByte()
        arr[25] = 0x2A.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun createHeaderOnlyVp8x(): String {
        // RIFF (12) + VP8X chunk (18) = 30 bytes
        val totalBytes = 30
        val arr = ByteArray(totalBytes)
        // RIFF
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = 10.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun createVp8xMissingImageData(): String {
        // RIFF (12) + VP8X (18) + ICCP (16) = 46 bytes
        val totalBytes = 46
        val arr = ByteArray(totalBytes)
        // RIFF
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = 10.toByte()
        // Feature flag for ICC Profile in byte 20: 0x20
        arr[20] = 0x20.toByte()
        // ICCP chunk at offset 30
        arr[30] = 'I'.code.toByte()
        arr[31] = 'C'.code.toByte()
        arr[32] = 'C'.code.toByte()
        arr[33] = 'P'.code.toByte()
        arr[34] = 8.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun createVp8xTruncatedSubsequentChunk(): String {
        // RIFF (12) + VP8X (18) + truncated VP8 chunk header (4 bytes) = 34 bytes
        val totalBytes = 34
        val arr = ByteArray(totalBytes)
        // RIFF
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = 10.toByte()
        // Truncated subsequent chunk at offset 30
        arr[30] = 'V'.code.toByte()
        arr[31] = 'P'.code.toByte()
        arr[32] = '8'.code.toByte()
        arr[33] = ' '.code.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun createVp8xTruncatedSubsequentChunkPayload(): String {
        // RIFF (12) + VP8X (18) + VP8 header (8) + partial payload (6 bytes) = 44 bytes
        // But VP8 header declares 48 bytes!
        val totalBytes = 44
        val arr = ByteArray(totalBytes)
        // RIFF
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = 10.toByte()
        // VP8 chunk at offset 30
        arr[30] = 'V'.code.toByte()
        arr[31] = 'P'.code.toByte()
        arr[32] = '8'.code.toByte()
        arr[33] = ' '.code.toByte()
        // Declared chunk size = 48 bytes (exceeds payload which is only 44 - 38 = 6 bytes)
        arr[34] = 48.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun createValidVp8xWithImageData(): String {
        // RIFF (12) + VP8X (18) + VP8 (56: 8 header + 48 payload) = 86 bytes
        val totalBytes = 86
        val arr = ByteArray(totalBytes)
        // RIFF
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = totalBytes - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = 10.toByte()
        // VP8 chunk at offset 30
        arr[30] = 'V'.code.toByte()
        arr[31] = 'P'.code.toByte()
        arr[32] = '8'.code.toByte()
        arr[33] = ' '.code.toByte()
        arr[34] = 48.toByte()
        // VP8 frame tag at offset 38
        arr[38] = 0xD0.toByte()
        arr[39] = 0x01.toByte()
        arr[40] = 0x00.toByte()
        // VP8 keyframe start code 0x9D, 0x01, 0x2A at offset 41..43
        arr[41] = 0x9D.toByte()
        arr[42] = 0x01.toByte()
        arr[43] = 0x2A.toByte()
        return Base64.getEncoder().encodeToString(arr)
    }

    private fun validVp8Payload(): ByteArray {
        val payload = ByteArray(48)
        payload[0] = 0xD0.toByte()
        payload[1] = 0x01.toByte()
        payload[2] = 0x00.toByte()
        payload[3] = 0x9D.toByte()
        payload[4] = 0x01.toByte()
        payload[5] = 0x2A.toByte()
        return payload
    }

    private fun validVp8lPayload(): ByteArray {
        val payload = ByteArray(10)
        payload[0] = 0x2F.toByte()
        return payload
    }

    private fun createVp8xContainer(
        flags: Byte = 0,
        chunks: List<Pair<String, ByteArray>> = emptyList(),
        vp8xPayloadSize: Int = 10,
        reserved24: ByteArray = ByteArray(3)
    ): String {
        val vp8xHeaderAndPayload = 8 + vp8xPayloadSize
        var total = 12 + vp8xHeaderAndPayload
        for ((_, payload) in chunks) {
            val pad = if (payload.size % 2 != 0) 1 else 0
            total += 8 + payload.size + pad
        }
        // If total bytes is odd, add 1 byte padding to container to ensure even RIFF size
        val outerPad = if (total % 2 != 0) 1 else 0
        total += outerPad

        val arr = ByteArray(total)
        // RIFF header
        arr[0] = 'R'.code.toByte()
        arr[1] = 'I'.code.toByte()
        arr[2] = 'F'.code.toByte()
        arr[3] = 'F'.code.toByte()
        val riffSize = total - 8
        arr[4] = (riffSize and 0xFF).toByte()
        arr[5] = ((riffSize shr 8) and 0xFF).toByte()
        arr[6] = ((riffSize shr 16) and 0xFF).toByte()
        arr[7] = ((riffSize shr 24) and 0xFF).toByte()
        // WEBP
        arr[8] = 'W'.code.toByte()
        arr[9] = 'E'.code.toByte()
        arr[10] = 'B'.code.toByte()
        arr[11] = 'P'.code.toByte()
        // VP8X FourCC
        arr[12] = 'V'.code.toByte()
        arr[13] = 'P'.code.toByte()
        arr[14] = '8'.code.toByte()
        arr[15] = 'X'.code.toByte()
        arr[16] = (vp8xPayloadSize and 0xFF).toByte()
        arr[17] = ((vp8xPayloadSize shr 8) and 0xFF).toByte()
        arr[18] = ((vp8xPayloadSize shr 16) and 0xFF).toByte()
        arr[19] = ((vp8xPayloadSize shr 24) and 0xFF).toByte()

        // VP8X payload
        if (vp8xPayloadSize >= 1) arr[20] = flags
        if (vp8xPayloadSize >= 4) {
            arr[21] = reserved24[0]
            arr[22] = reserved24[1]
            arr[23] = reserved24[2]
        }

        var offset = 12 + vp8xHeaderAndPayload
        for ((fourCC, payload) in chunks) {
            arr[offset] = fourCC[0].code.toByte()
            arr[offset + 1] = fourCC[1].code.toByte()
            arr[offset + 2] = fourCC[2].code.toByte()
            arr[offset + 3] = fourCC[3].code.toByte()
            val cSize = payload.size
            arr[offset + 4] = (cSize and 0xFF).toByte()
            arr[offset + 5] = ((cSize shr 8) and 0xFF).toByte()
            arr[offset + 6] = ((cSize shr 16) and 0xFF).toByte()
            arr[offset + 7] = ((cSize shr 24) and 0xFF).toByte()
            System.arraycopy(payload, 0, arr, offset + 8, payload.size)
            val pad = if (payload.size % 2 != 0) 1 else 0
            if (pad == 1 && offset + 8 + payload.size < arr.size) {
                arr[offset + 8 + payload.size] = 0.toByte()
            }
            offset += 8 + payload.size + pad
        }
        return Base64.getEncoder().encodeToString(arr)
    }

    // Step 1: Category Tests
    @Test
    fun step1CategoryValidation() {
        val err1 = RequestValidator.validateCategory(null)
        assertEquals(1, err1.size)
        assertTrue(err1[0].contains("select a waste category"))

        val err2 = RequestValidator.validateCategory(inactiveCategory)
        assertEquals(1, err2.size)
        assertTrue(err2[0].contains("is inactive"))

        val ok = RequestValidator.validateCategory(validCategory)
        assertTrue(ok.isEmpty())
    }

    // Step 2: Location and Service Zone Tests
    @Test
    fun step2Location_validCoordinatesInsideSelectedZone_succeeds() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Mwinyi Rd",
            availableZones = availableZones
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun step2Location_coordinatesInsideZoneAButZoneBSelected_failsValidation() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords, // inside Kinondoni
            selectedZoneId = "zone_ilala_02", // but Ilala is selected!
            pickupAddress = "Plot 42, Ali Hassan Mwinyi Rd",
            availableZones = availableZones
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("fall outside selected service zone 'Ilala Municipal'"))
    }

    @Test
    fun step2Location_coordinatesOutsideAllZones_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = outsideCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Dodoma Central",
            availableZones = availableZones
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("fall outside selected service zone"))
    }

    @Test
    fun step2Location_unknownServiceZone_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_unknown_99",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = availableZones
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("does not exist in the municipal catalog"))
    }

    @Test
    fun step2Location_inactiveServiceZone_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = GeoCoordinates(latitude = -6.9200, longitude = 39.2500),
            selectedZoneId = "zone_temeke_inactive",
            pickupAddress = "Plot 10, Temeke Industrial Area",
            availableZones = availableZones
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("is currently inactive"))
    }

    @Test
    fun step2Location_noActiveZones_returnsError() {
        val errors = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            availableZones = emptyList()
        )
        assertTrue(errors.any { it.contains("does not exist in the municipal catalog") })
    }

    @Test
    fun step2Location_missingCoordinatesOrBlankAddress_returnsErrors() {
        val errCoords = RequestValidator.validateLocation(
            coordinates = null,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Valid Address",
            availableZones = availableZones
        )
        assertTrue(errCoords.any { it.contains("select a pickup location") })

        val errAddress = RequestValidator.validateLocation(
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "A", // too short (< 2)
            availableZones = availableZones
        )
        assertTrue(errAddress.any { it.contains("minimum 2 characters") })
    }

    // Step 3: Volume Tier Tests
    @Test
    fun step3VolumeValidation() {
        val err = RequestValidator.validateVolume(null)
        assertEquals(1, err.size)
        assertTrue(err[0].contains("select an estimated waste volume tier"))

        val ok = RequestValidator.validateVolume(VolumeTier.STANDARD_BIN)
        assertTrue(ok.isEmpty())
    }

    // Gate Photo Validation: Genuine WebP Tests
    @Test
    fun gatePhoto_genuine1x1LossyWebp_succeeds() {
        val errors = RequestValidator.validateGatePhoto(genuine1x1LossyWebpBase64)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_genuine1x1LosslessWebp_succeeds() {
        val errors = RequestValidator.validateGatePhoto(genuine1x1LosslessWebpBase64)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_validVp8xWithImageData_succeeds() {
        val validVp8x = createValidVp8xWithImageData()
        val errors = RequestValidator.validateGatePhoto(validVp8x)
        assertTrue(errors.isEmpty())
    }

    // Gate Photo Validation: Strict 50 KB Boundary Tests
    @Test
    fun gatePhoto_strict50KbBoundary_immediatelyBelowLimit_succeeds() {
        // 50 * 1024 - 2 = 51,198 bytes (< 50 KB limit, even RIFF length)
        val webpUnderLimit = createValidStructuralWebP(50 * 1024 - 2)
        val errors = RequestValidator.validateGatePhoto(webpUnderLimit)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_strict50KbBoundary_exactlyAt50KbLimit_isRejected() {
        // Exactly 50 * 1024 = 51,200 bytes (NOT smaller than 50 KB)
        val webpExactLimit = createValidStructuralWebP(50 * 1024)
        val errors = RequestValidator.validateGatePhoto(webpExactLimit)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be strictly smaller than 50 KB"))
    }

    @Test
    fun gatePhoto_strict50KbBoundary_immediatelyAbove50KbLimit_isRejected() {
        // 50 * 1024 + 2 = 51,202 bytes (> 50 KB limit, even RIFF length)
        val webpOverLimit = createValidStructuralWebP(50 * 1024 + 2)
        val errors = RequestValidator.validateGatePhoto(webpOverLimit)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be strictly smaller than 50 KB"))
    }

    // Gate Photo Validation: Structural Corruption & Truncation Tests
    @Test
    fun gatePhoto_corruptedVp8StartCode_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LossyWebpBase64)
        // Corrupt VP8 keyframe start code at byte 23
        rawBytes[23] = 0x00.toByte()
        val corruptedBase64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(corruptedBase64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("corrupted VP8 stream"))
    }

    @Test
    fun gatePhoto_corruptedVp8lSignature_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        // Corrupt VP8L signature at byte 20 (was 0x2F)
        rawBytes[20] = 0x00.toByte()
        val corruptedBase64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(corruptedBase64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("corrupted VP8L stream"))
    }

    @Test
    fun gatePhoto_truncatedWebpPayload_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LossyWebpBase64)
        // Truncate to 30 bytes when declared RIFF size is 60 (total 68 bytes)
        val truncatedBytes = rawBytes.copyOf(30)
        val truncatedBase64 = Base64.getEncoder().encodeToString(truncatedBytes)

        val errors = RequestValidator.validateGatePhoto(truncatedBase64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("truncated"))
    }

    // WebP VP8X Extended Container Tests
    @Test
    fun gatePhoto_headerOnlyVp8x_returnsError() {
        val headerOnly = createHeaderOnlyVp8x()
        val errors = RequestValidator.validateGatePhoto(headerOnly)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("header-only VP8X without image data"))
    }

    @Test
    fun gatePhoto_vp8xMissingImageData_returnsError() {
        val missingData = createVp8xMissingImageData()
        val errors = RequestValidator.validateGatePhoto(missingData)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("missing image data"))
    }

    @Test
    fun gatePhoto_vp8xIncompleteTrailingChunkHeader_returnsError() {
        val truncatedSub = createVp8xTruncatedSubsequentChunk()
        val errors = RequestValidator.validateGatePhoto(truncatedSub)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("incomplete trailing chunk header"))
    }

    @Test
    fun gatePhoto_vp8xTruncatedSubsequentChunkPayload_returnsError() {
        val truncatedPayload = createVp8xTruncatedSubsequentChunkPayload()
        val errors = RequestValidator.validateGatePhoto(truncatedPayload)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("truncated subsequent chunk 'VP8 '"))
    }

    // VP8X Header Payload Size & Reserved Field Invariant Tests
    @Test
    fun gatePhoto_vp8xHeaderShorterThan10Bytes_returnsError() {
        val b64 = createVp8xContainer(flags = 0, chunks = listOf("VP8 " to validVp8Payload()), vp8xPayloadSize = 9)
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be exactly 10 bytes"))
    }

    @Test
    fun gatePhoto_vp8xHeaderLargerThan10Bytes_returnsError() {
        val b64 = createVp8xContainer(flags = 0, chunks = listOf("VP8 " to validVp8Payload()), vp8xPayloadSize = 12)
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must be exactly 10 bytes"))
    }

    @Test
    fun gatePhoto_vp8xReservedBitsInFlagsByte_returnsError() {
        // Bit 7 set (0x80)
        val b64Bit7 = createVp8xContainer(flags = 0x80.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val err1 = RequestValidator.validateGatePhoto(b64Bit7)
        assertEquals(1, err1.size)
        assertTrue(err1[0].contains("reserved bits in flags byte must be 0"))

        // Bit 0 set (0x01)
        val b64Bit0 = createVp8xContainer(flags = 0x01.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val err2 = RequestValidator.validateGatePhoto(b64Bit0)
        assertEquals(1, err2.size)
        assertTrue(err2[0].contains("reserved bits in flags byte must be 0"))
    }

    @Test
    fun gatePhoto_vp8xReserved24BitFieldNonZero_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0,
            chunks = listOf("VP8 " to validVp8Payload()),
            reserved24 = byteArrayOf(1, 0, 0)
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("reserved 24-bit field must be 0"))
    }

    @Test
    fun gatePhoto_vp8xAnimationFlagRejected_returnsError() {
        val b64 = createVp8xContainer(flags = 0x02.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("animated WebP is not supported"))
    }

    @Test
    fun gatePhoto_vp8xAnimatedChunks_returnsError() {
        val b64Anim = createVp8xContainer(flags = 0, chunks = listOf("ANIM" to ByteArray(6), "VP8 " to validVp8Payload()))
        val err1 = RequestValidator.validateGatePhoto(b64Anim)
        assertEquals(1, err1.size)
        assertTrue(err1[0].contains("animated WebP chunk 'ANIM' is not supported"))

        val b64Anmf = createVp8xContainer(flags = 0, chunks = listOf("ANMF" to ByteArray(6), "VP8 " to validVp8Payload()))
        val err2 = RequestValidator.validateGatePhoto(b64Anmf)
        assertEquals(1, err2.size)
        assertTrue(err2[0].contains("animated WebP chunk 'ANMF' is not supported"))
    }

    // Extended Container: Single Image Stream & Duplicate Chunk Tests
    @Test
    fun gatePhoto_vp8xDuplicateImageChunks_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0,
            chunks = listOf("VP8 " to validVp8Payload(), "VP8 " to validVp8Payload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate or multiple image chunks"))
    }

    @Test
    fun gatePhoto_vp8xMultipleImageStreams_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0,
            chunks = listOf("VP8 " to validVp8Payload(), "VP8L" to validVp8lPayload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate or multiple image chunks"))
    }

    // Extended Container: ALPH Combinations & Permitted Ordering Tests
    @Test
    fun gatePhoto_vp8xAlphPairedWithLosslessVp8l_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x10.toByte(),
            chunks = listOf("ALPH" to ByteArray(6), "VP8L" to validVp8lPayload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("not immediately followed by lossy VP8") || errors[0].contains("invalid ALPH chunk paired with lossless VP8L"))
    }

    @Test
    fun gatePhoto_vp8xAlphMisplacedAfterImage_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x10.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "ALPH" to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("misplaced ALPH chunk") || errors[0].contains("duplicate ALPH chunk"))
    }

    @Test
    fun gatePhoto_vp8xAlphNotFollowedByVp8_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x18.toByte(),
            chunks = listOf("ALPH" to ByteArray(6), "EXIF" to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("not immediately followed by lossy VP8"))
    }

    @Test
    fun gatePhoto_vp8xDuplicateAlphChunk_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x10.toByte(),
            chunks = listOf("ALPH" to ByteArray(6), "VP8 " to validVp8Payload(), "ALPH" to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate ALPH chunk") || errors[0].contains("misplaced ALPH chunk"))
    }

    // Extended Container: Metadata Ordering & Duplicates Tests
    @Test
    fun gatePhoto_vp8xExifBeforeImage_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x08.toByte(),
            chunks = listOf("EXIF" to ByteArray(6), "VP8 " to validVp8Payload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("misplaced EXIF chunk"))
    }

    @Test
    fun gatePhoto_vp8xDuplicateExif_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x08.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "EXIF" to ByteArray(6), "EXIF" to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate EXIF chunk"))
    }

    @Test
    fun gatePhoto_vp8xDuplicateXmp_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x04.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "XMP " to ByteArray(6), "XMP " to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate XMP chunk"))
    }

    @Test
    fun gatePhoto_vp8xExifAfterXmp_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x0C.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "XMP " to ByteArray(6), "EXIF" to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("misplaced EXIF chunk"))
    }

    @Test
    fun gatePhoto_vp8xIccpAfterImage_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x20.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "ICCP" to ByteArray(8))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("misplaced ICCP chunk"))
    }

    @Test
    fun gatePhoto_vp8xDuplicateIccp_returnsError() {
        val b64 = createVp8xContainer(
            flags = 0x20.toByte(),
            chunks = listOf("ICCP" to ByteArray(8), "ICCP" to ByteArray(8), "VP8 " to validVp8Payload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate ICCP chunk"))
    }

    // Extended Container: Feature Flag / Chunk Agreement Tests
    @Test
    fun gatePhoto_vp8xFlagIccWithoutIccpChunk_returnsError() {
        val b64 = createVp8xContainer(flags = 0x20.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("declared ICC profile flag but no ICCP chunk was present"))
    }

    @Test
    fun gatePhoto_vp8xIccpChunkWithoutFlagIcc_returnsError() {
        val b64 = createVp8xContainer(flags = 0x00.toByte(), chunks = listOf("ICCP" to ByteArray(8), "VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("contains ICCP chunk but VP8X header did not set ICC profile flag"))
    }

    @Test
    fun gatePhoto_vp8xFlagAlphaWithoutAlphChunk_returnsError() {
        val b64 = createVp8xContainer(flags = 0x10.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("declared alpha flag but no ALPH chunk was present with lossy VP8"))
    }

    @Test
    fun gatePhoto_vp8xAlphChunkWithoutFlagAlpha_returnsError() {
        val b64 = createVp8xContainer(flags = 0x00.toByte(), chunks = listOf("ALPH" to ByteArray(6), "VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("contains ALPH chunk but VP8X header did not set alpha flag"))
    }

    @Test
    fun gatePhoto_vp8xFlagExifWithoutExifChunk_returnsError() {
        val b64 = createVp8xContainer(flags = 0x08.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("declared EXIF flag but no EXIF chunk was present"))
    }

    @Test
    fun gatePhoto_vp8xExifChunkWithoutFlagExif_returnsError() {
        val b64 = createVp8xContainer(flags = 0x00.toByte(), chunks = listOf("VP8 " to validVp8Payload(), "EXIF" to ByteArray(6)))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("contains EXIF chunk but VP8X header did not set EXIF flag"))
    }

    @Test
    fun gatePhoto_vp8xFlagXmpWithoutXmpChunk_returnsError() {
        val b64 = createVp8xContainer(flags = 0x04.toByte(), chunks = listOf("VP8 " to validVp8Payload()))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("declared XMP flag but no XMP chunk was present"))
    }

    @Test
    fun gatePhoto_vp8xXmpChunkWithoutFlagXmp_returnsError() {
        val b64 = createVp8xContainer(flags = 0x00.toByte(), chunks = listOf("VP8 " to validVp8Payload(), "XMP " to ByteArray(6)))
        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("contains XMP chunk but VP8X header did not set XMP flag"))
    }

    // Valid Extended VP8X Permutations Tests
    @Test
    fun gatePhoto_validVp8xWithIccpAndLosslessVp8l_succeeds() {
        val b64 = createVp8xContainer(
            flags = 0x20.toByte(),
            chunks = listOf("ICCP" to ByteArray(8), "VP8L" to validVp8lPayload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_validVp8xWithAlphAndLossyVp8_succeeds() {
        val b64 = createVp8xContainer(
            flags = 0x10.toByte(),
            chunks = listOf("ALPH" to ByteArray(6), "VP8 " to validVp8Payload())
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_validVp8xWithExifAndXmp_succeeds() {
        val b64 = createVp8xContainer(
            flags = 0x0C.toByte(),
            chunks = listOf("VP8 " to validVp8Payload(), "EXIF" to ByteArray(6), "XMP " to ByteArray(6))
        )
        val errors = RequestValidator.validateGatePhoto(b64)
        assertTrue(errors.isEmpty())
    }

    // RIFF Declared Length Discrepancy Tests
    @Test
    fun gatePhoto_riffDeclaredLengthSmallerThanPayload_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LossyWebpBase64)
        // Deliberately set declared RIFF size to 30 (expects 38 bytes, but payload is 68 bytes)
        rawBytes[4] = 30.toByte()
        rawBytes[5] = 0.toByte()
        rawBytes[6] = 0.toByte()
        rawBytes[7] = 0.toByte()
        val b64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("trailing bytes"))
    }

    @Test
    fun gatePhoto_riffDeclaredLengthLargerThanPayload_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LossyWebpBase64)
        // Deliberately set declared RIFF size to 200 (expects 208 bytes, but payload is 68 bytes)
        rawBytes[4] = 200.toByte()
        rawBytes[5] = 0.toByte()
        rawBytes[6] = 0.toByte()
        rawBytes[7] = 0.toByte()
        val b64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("truncated"))
    }

    // Strict RIFF Envelope Tests
    @Test
    fun gatePhoto_oddRiffDeclaredLength_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        // Alter declared RIFF size at bytes 4..7 to an odd number (27 instead of 28)
        rawBytes[4] = 27.toByte()
        val b64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("invalid odd RIFF declared length"))
    }

    @Test
    fun gatePhoto_riffDeclaredLengthWithExtraTrailingByte_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        // Append 1 extra trailing byte (total 37 bytes when declared RIFF size 28 requires exactly 36 bytes)
        val withTrailing = rawBytes.copyOf(rawBytes.size + 1)
        withTrailing[rawBytes.size] = 0x00.toByte()
        val b64 = Base64.getEncoder().encodeToString(withTrailing)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("trailing bytes"))
    }

    // Simple VP8 & VP8L Container Completion Tests
    @Test
    fun gatePhoto_simpleVp8WithTrailingGarbage_returnsError() {
        // Simple VP8 (68 bytes: 12 RIFF + 8 VP8 header + 48 payload).
        // Expand file by 10 trailing garbage bytes to 78 bytes and update declared RIFF size to 70 (even).
        val rawBytes = Base64.getDecoder().decode(genuine1x1LossyWebpBase64)
        val withGarbage = ByteArray(78)
        System.arraycopy(rawBytes, 0, withGarbage, 0, rawBytes.size)
        withGarbage[4] = 70.toByte()
        val b64 = Base64.getEncoder().encodeToString(withGarbage)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("trailing data or unparsed chunks after simple VP8 chunk"))
    }

    @Test
    fun gatePhoto_simpleVp8lWithTrailingGarbage_returnsError() {
        // Simple VP8L (36 bytes: 12 RIFF + 8 VP8L header + 15 payload + 1 pad).
        // Expand file by 10 trailing garbage bytes to 46 bytes and update declared RIFF size to 38 (even).
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        val withGarbage = ByteArray(46)
        System.arraycopy(rawBytes, 0, withGarbage, 0, rawBytes.size)
        withGarbage[4] = 38.toByte()
        val b64 = Base64.getEncoder().encodeToString(withGarbage)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("trailing data or unparsed chunks after simple VP8L chunk"))
    }

    // Chunk Boundary & Zero-Padding Tests
    @Test
    fun gatePhoto_oddSizedChunkMissingPadding_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        // genuine1x1LosslessWebp is 36 bytes (12 RIFF + 8 VP8L header + 15 payload + 1 zero-padding byte).
        // Drop the 1-byte padding byte at the end (resulting in 35 bytes with 15 bytes odd payload and missing pad).
        val truncatedPad = rawBytes.copyOf(35)
        val b64 = Base64.getEncoder().encodeToString(truncatedPad)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("truncated") || errors[0].contains("missing required zero-padding byte"))
    }

    @Test
    fun gatePhoto_oddSizedChunkWithNonZeroPadding_returnsError() {
        val rawBytes = Base64.getDecoder().decode(genuine1x1LosslessWebpBase64)
        // Byte 35 is the padding byte for the 15-byte odd VP8L chunk. Corrupt it to non-zero (0xFF).
        rawBytes[35] = 0xFF.toByte()
        val b64 = Base64.getEncoder().encodeToString(rawBytes)

        val errors = RequestValidator.validateGatePhoto(b64)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("with non-zero padding byte"))
    }

    @Test
    fun gatePhoto_validOddSizedChunkWithZeroPadding_succeeds() {
        // genuine1x1LosslessWebpBase64 has a 15-byte odd VP8L payload followed by a valid 0x00 padding byte
        val errors = RequestValidator.validateGatePhoto(genuine1x1LosslessWebpBase64)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun gatePhoto_invalidOrUnsupportedFormats_returnsError() {
        // Fake JPEG (magic header FF D8 FF E0)
        val fakeJpeg = ByteArray(100) { 0.toByte() }
        fakeJpeg[0] = 0xFF.toByte()
        fakeJpeg[1] = 0xD8.toByte()
        fakeJpeg[2] = 0xFF.toByte()
        fakeJpeg[3] = 0xE0.toByte()
        val jpegBase64 = Base64.getEncoder().encodeToString(fakeJpeg)
        val errJpeg = RequestValidator.validateGatePhoto(jpegBase64)
        assertEquals(1, errJpeg.size)
        assertTrue(errJpeg[0].contains("must be in compressed WebP format"))

        // Fake PNG (magic header 89 50 4E 47)
        val fakePng = ByteArray(100) { 0.toByte() }
        fakePng[0] = 0x89.toByte()
        fakePng[1] = 0x50.toByte()
        fakePng[2] = 0x4E.toByte()
        fakePng[3] = 0x47.toByte()
        val pngBase64 = Base64.getEncoder().encodeToString(fakePng)
        val errPng = RequestValidator.validateGatePhoto(pngBase64)
        assertEquals(1, errPng.size)
        assertTrue(errPng[0].contains("must be in compressed WebP format"))

        // RIFF container with WAVE instead of WEBP
        val fakeWave = ByteArray(100) { 0.toByte() }
        fakeWave[0] = 'R'.code.toByte()
        fakeWave[1] = 'I'.code.toByte()
        fakeWave[2] = 'F'.code.toByte()
        fakeWave[3] = 'F'.code.toByte()
        fakeWave[4] = 92.toByte() // declared RIFF size = 100 - 8 = 92
        fakeWave[8] = 'W'.code.toByte()
        fakeWave[9] = 'A'.code.toByte()
        fakeWave[10] = 'V'.code.toByte()
        fakeWave[11] = 'E'.code.toByte()
        val waveBase64 = Base64.getEncoder().encodeToString(fakeWave)
        val errWave = RequestValidator.validateGatePhoto(waveBase64)
        assertEquals(1, errWave.size)
        assertTrue(errWave[0].contains("must be in compressed WebP format"))
    }

    @Test
    fun gatePhoto_missingOptionalPhoto_succeeds() {
        assertTrue(RequestValidator.validateGatePhoto(null).isEmpty())
        assertTrue(RequestValidator.validateGatePhoto("").isEmpty())
        assertTrue(RequestValidator.validateGatePhoto("   ").isEmpty())
    }

    @Test
    fun gatePhoto_corruptedBase64_returnsError() {
        val errors = RequestValidator.validateGatePhoto("Not-Valid-Base-64!!!")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("invalid Base64 data"))
    }

    // Complete Draft Assertion Tests
    @Test
    fun completeDraftValidation() {
        RequestValidator.assertValidDraft(
            category = validCategory,
            coordinates = kinondoniCoords,
            selectedZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            volumeTier = VolumeTier.STANDARD_BIN,
            availableZones = availableZones,
            notes = "Ring bell",
            gatePhotoBase64 = genuine1x1LossyWebpBase64
        )

        assertThrows(RequestValidationException::class.java) {
            RequestValidator.assertValidDraft(
                category = null,
                coordinates = outsideCoords,
                selectedZoneId = "zone_kinondoni_01",
                pickupAddress = "",
                volumeTier = null,
                availableZones = availableZones
            )
        }
    }
}
