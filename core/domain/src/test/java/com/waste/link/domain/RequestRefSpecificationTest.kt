package com.waste.link.domain

import com.waste.link.domain.model.RequestRefSpecification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestRefSpecificationTest {

    // UT-DOM-11: Canonical reference tag format and regex check
    @Test
    fun referenceTag_matchesCanonicalPattern() {
        val testEntropy = 0x1A2B3C4D
        val nov2026EpochMs = 1793869200000L

        val ref = RequestRefSpecification.formatNonCanonicalPreview(testEntropy, nov2026EpochMs)
        assertTrue(
            "Reference '$ref' must match ^WL-[0-9]{4}-[0-9A-Z]{6}$",
            RequestRefSpecification.isValid(ref)
        )
        assertTrue(ref.startsWith("WL-2611-"))
        assertEquals(14, ref.length) // WL- (3) + 2611 (4) + - (1) + 6 chars = 14 chars
    }

    @Test
    fun referenceTag_deterministicForSameInputs() {
        val entropy = 0x0ABCDEF1
        val timestamp = 1793869200000L
        val ref1 = RequestRefSpecification.formatNonCanonicalPreview(entropy, timestamp)
        val ref2 = RequestRefSpecification.formatNonCanonicalPreview(entropy, timestamp)
        assertEquals(ref1, ref2)
    }

    @Test
    fun referenceTag_excludesConfusingCrockfordCharacters() {
        // Crockford alphabet specifically excludes I, L, O, U to prevent visual ambiguity
        val alphabet = RequestRefSpecification.CROCKFORD_ALPHABET
        assertFalse(alphabet.contains('I'))
        assertFalse(alphabet.contains('L'))
        assertFalse(alphabet.contains('O'))
        assertFalse(alphabet.contains('U'))
        assertEquals(32, alphabet.length)
    }

    @Test
    fun nonCanonicalPreview_isDistinguishableAndAdvisory() {
        // Confirms that non-canonical preview formats correctly without claiming uniqueness
        val preview = RequestRefSpecification.formatNonCanonicalPreview(0x3FFFFFFF, 1793869200000L)
        assertTrue(RequestRefSpecification.isValid(preview))
        assertEquals("WL-2611-ZZZZZZ", preview)
    }

    @Test
    fun isValid_rejectsMalformedReferences() {
        assertFalse(RequestRefSpecification.isValid(null))
        assertFalse(RequestRefSpecification.isValid(""))
        assertFalse(RequestRefSpecification.isValid("WL-26-XXXXXX")) // 2-digit year/month
        assertFalse(RequestRefSpecification.isValid("REQ-1234")) // Legacy format
        assertFalse(RequestRefSpecification.isValid("WL-2611-12345")) // 5 chars
        assertFalse(RequestRefSpecification.isValid("WL-2611-1234567")) // 7 chars
        assertFalse(RequestRefSpecification.isValid("WL-2611-ABC12!")) // Special character
    }
}
