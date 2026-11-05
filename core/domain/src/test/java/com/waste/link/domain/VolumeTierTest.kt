package com.waste.link.domain

import com.waste.link.domain.model.UnknownVolumeTierException
import com.waste.link.domain.model.VolumeTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeTierTest {

    // UT-DOM-08: Validate VolumeTier.fromId() with valid IDs
    @Test
    fun validVolumeTierIds_parseCorrectly() {
        assertEquals(VolumeTier.SMALL_BAG, VolumeTier.fromId("small_bag"))
        assertEquals(VolumeTier.STANDARD_BIN, VolumeTier.fromId("standard_bin"))
        assertEquals(VolumeTier.LARGE_LOAD, VolumeTier.fromId("large_load"))
        assertEquals(VolumeTier.TRUCK_LOAD, VolumeTier.fromId("truck_load"))

        // Case-insensitivity check
        assertEquals(VolumeTier.SMALL_BAG, VolumeTier.fromId("SMALL_BAG"))
        assertEquals(VolumeTier.STANDARD_BIN, VolumeTier.fromId("Standard_Bin"))
    }

    // UT-DOM-09: No Silent Fallback Test - unknown tier must throw UnknownVolumeTierException
    @Test
    fun unknownVolumeTierId_throwsUnknownVolumeTierException() {
        val ex1 = assertThrows(UnknownVolumeTierException::class.java) {
            VolumeTier.fromId("huge_tank")
        }
        assertTrue(ex1.message!!.contains("Unknown volume tier: 'huge_tank'"))
        assertTrue(ex1.message!!.contains("Silent fallback to a default volume tier is strictly prohibited"))

        val ex2 = assertThrows(UnknownVolumeTierException::class.java) {
            VolumeTier.fromId("random_string")
        }
        assertTrue(ex2.message!!.contains("Unknown volume tier: 'random_string'"))
    }

    // UT-DOM-10: Bag equivalent calculation for logistics heuristics
    @Test
    fun volumeTier_verifiesBagEquivalents() {
        assertEquals(2, VolumeTier.SMALL_BAG.bagEquivalent)
        assertEquals(5, VolumeTier.STANDARD_BIN.bagEquivalent)
        assertEquals(15, VolumeTier.LARGE_LOAD.bagEquivalent)
        assertEquals(40, VolumeTier.TRUCK_LOAD.bagEquivalent)
    }

    @Test
    fun fromIdOrNull_returnsNullForInvalidTier() {
        assertNull(VolumeTier.fromIdOrNull("invalid"))
        assertNull(VolumeTier.fromIdOrNull(null))
        assertEquals(VolumeTier.SMALL_BAG, VolumeTier.fromIdOrNull("small_bag"))
    }
}
