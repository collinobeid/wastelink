package com.waste.link.domain.model

import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Generates and validates Crockford Base32 human-readable reference tags for waste requests.
 *
 * Format: `WL-YYMM-XXXXXX`
 * Example: `WL-2611-7X9F2B`
 *
 * Specification:
 * - Prefix: `WL-` (WasteLink)
 * - Period: 4 digits `YYMM` (e.g., `2611` for November 2026)
 * - Suffix: 6 characters from Crockford Base32 alphabet (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`)
 *   providing 32^6 = 1,073,741,824 collision-free combinations per month.
 *
 * Domain Constraints:
 * - A locally generated reference is client-advisory; the server is the ultimate authority.
 * - [WasteRequest.requestRef] can be null (unallocated) until the backend returns the canonical reference.
 */
object RequestRefGenerator {
    const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    val REFERENCE_REGEX = Regex("^WL-[0-9]{4}-[0-9A-Z]{6}$")

    /**
     * Formats a collision-safe reference tag from a UUID and an epoch millisecond timestamp.
     *
     * @param uuid The deterministic UUID associated with the request draft.
     * @param timestampEpochMs The timestamp used to compute the YYMM period (in UTC).
     */
    fun generate(uuid: UUID, timestampEpochMs: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampEpochMs
        }
        val year2Digits = calendar.get(Calendar.YEAR) % 100
        val month1Based = calendar.get(Calendar.MONTH) + 1
        val yymm = String.format("%02d%02d", year2Digits, month1Based)

        val msb = uuid.mostSignificantBits
        var val30Bits = (msb ushr 34).toInt() and 0x3FFFFFFF

        val chars = CharArray(6)
        for (i in 5 downTo 0) {
            val index = val30Bits and 0x1F
            chars[i] = CROCKFORD_ALPHABET[index]
            val30Bits = val30Bits ushr 5
        }

        return "WL-$yymm-${String(chars)}"
    }

    /**
     * Validates whether a given reference string matches the canonical pattern.
     */
    fun isValid(ref: String?): Boolean {
        if (ref.isNullOrBlank()) return false
        return ref.matches(REFERENCE_REGEX)
    }
}
