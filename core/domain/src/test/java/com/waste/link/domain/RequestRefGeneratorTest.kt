package com.waste.link.domain

import com.waste.link.domain.model.RequestRefGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RequestRefGeneratorTest {

    // UT-DOM-11: Collision-Safe Reference Tag generation and regex check
    @Test
    fun referenceTag_matchesExpectedPattern() {
        val testUuid = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479")
        // November 2026 UTC timestamp
        val nov2026EpochMs = 1793869200000L

        val ref = RequestRefGenerator.generate(testUuid, nov2026EpochMs)
        assertTrue(
            "Reference '$ref' must match ^WL-[0-9]{4}-[0-9A-Z]{6}$",
            RequestRefGenerator.isValid(ref)
        )
        assertTrue(ref.startsWith("WL-2611-"))
        assertEquals(14, ref.length) // WL- (3) + 2611 (4) + - (1) + 6 chars = 14 chars
    }

    @Test
    fun referenceTag_deterministicForSameInputs() {
        val testUuid = UUID.randomUUID()
        val timestamp = 1793869200000L
        val ref1 = RequestRefGenerator.generate(testUuid, timestamp)
        val ref2 = RequestRefGenerator.generate(testUuid, timestamp)
        assertEquals(ref1, ref2)
    }

    @Test
    fun referenceTag_excludesConfusingCrockfordCharacters() {
        // Crockford alphabet specifically excludes I, L, O, U to prevent visual confusion
        val alphabet = RequestRefGenerator.CROCKFORD_ALPHABET
        assertFalse(alphabet.contains('I'))
        assertFalse(alphabet.contains('L'))
        assertFalse(alphabet.contains('O'))
        assertFalse(alphabet.contains('U'))
        assertEquals(32, alphabet.length)
    }

    @Test
    fun referenceTag_generatesUniqueCodesForDifferentUuids() {
        val set = mutableSetOf<String>()
        val timestamp = 1793869200000L
        for (i in 1..1000) {
            val ref = RequestRefGenerator.generate(UUID.randomUUID(), timestamp)
            assertTrue(RequestRefGenerator.isValid(ref))
            set.add(ref)
        }
        assertEquals(1000, set.size)
    }

    @Test
    fun isValid_rejectsMalformedReferences() {
        assertFalse(RequestRefGenerator.isValid(null))
        assertFalse(RequestRefGenerator.isValid(""))
        assertFalse(RequestRefGenerator.isValid("WL-26-XXXXXX")) // 2-digit year/month
        assertFalse(RequestRefGenerator.isValid("REQ-1234")) // Legacy format
        assertFalse(RequestRefGenerator.isValid("WL-2611-12345")) // 5 chars
        assertFalse(RequestRefGenerator.isValid("WL-2611-1234567")) // 7 chars
    }
}
