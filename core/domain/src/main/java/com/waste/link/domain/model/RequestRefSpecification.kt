package com.waste.link.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Specification and format validator for WasteLink request reference identifiers.
 *
 * Format Specification:
 * - Canonical pattern: `WL-YYMM-XXXXXX`
 * - Example: `WL-2611-7X9F2B`
 * - Prefix: `WL-` (WasteLink namespace)
 * - Period: 4 digits `YYMM` (e.g., `2611` for November 2026)
 * - Suffix: 6 characters from Crockford Base32 alphabet (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`)
 *
 * ARCHITECTURAL INTEGRITY & GOVERNANCE GUARANTEE:
 * 1. The canonical reference is allocated and guaranteed unique strictly by the trusted backend authority.
 * 2. On client-created drafts, [WasteRequest.requestRef] MUST remain null (unallocated) until the backend
 *    returns the confirmed document.
 * 3. The client must NEVER claim a locally formatted string is guaranteed unique.
 * 4. Any client-side preview generated is strictly non-canonical and must NEVER be persisted as the official reference.
 */
object RequestRefSpecification {
    const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    val REFERENCE_REGEX = Regex("^WL-[0-9]{4}-[0-9A-Z]{6}$")

    /**
     * Validates whether a reference string matches the authoritative server format.
     */
    fun isValid(ref: String?): Boolean {
        if (ref.isNullOrBlank()) return false
        return ref.matches(REFERENCE_REGEX)
    }

    /**
     * Generates a NON-CANONICAL, client-advisory display preview for UI scaffolding.
     *
     * WARNING:
     * This preview is NOT guaranteed unique, is NOT canonical, and must NEVER be stored
     * or submitted as [WasteRequest.requestRef].
     *
     * @param entropy30Bits A 30-bit integer used for advisory UI preview rendering.
     * @param timestampEpochMs Timestamp used to compute the YYMM period (in UTC).
     */
    fun formatNonCanonicalPreview(entropy30Bits: Int, timestampEpochMs: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampEpochMs
        }
        val year2Digits = calendar.get(Calendar.YEAR) % 100
        val month1Based = calendar.get(Calendar.MONTH) + 1
        val yymm = String.format("%02d%02d", year2Digits, month1Based)

        var val30Bits = entropy30Bits and 0x3FFFFFFF
        val chars = CharArray(6)
        for (i in 5 downTo 0) {
            val index = val30Bits and 0x1F
            chars[i] = CROCKFORD_ALPHABET[index]
            val30Bits = val30Bits ushr 5
        }

        return "WL-$yymm-${String(chars)}"
    }
}
