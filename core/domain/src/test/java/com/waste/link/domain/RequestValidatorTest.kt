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
        // 50 * 1024 - 1 = 51,199 bytes (< 50 KB limit)
        val webpUnderLimit = createValidStructuralWebP(50 * 1024 - 1)
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
        // 50 * 1024 + 1 = 51,201 bytes (> 50 KB limit)
        val webpOverLimit = createValidStructuralWebP(50 * 1024 + 1)
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
    fun gatePhoto_vp8xTruncatedSubsequentChunk_returnsError() {
        val truncatedSub = createVp8xTruncatedSubsequentChunk()
        val errors = RequestValidator.validateGatePhoto(truncatedSub)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("truncated subsequent chunk"))
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
        assertTrue(errors[0].contains("extraneous data"))
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
