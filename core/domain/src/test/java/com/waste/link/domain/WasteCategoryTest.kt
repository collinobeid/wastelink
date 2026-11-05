package com.waste.link.domain

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.InactiveCategoryException
import com.waste.link.domain.model.StandardCategoryCode
import com.waste.link.domain.model.UnknownWasteCategoryException
import com.waste.link.domain.model.resolveCategoryStrict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteCategoryTest {

    private val catalog = listOf(
        DynamicWasteCategory(
            id = "household",
            displayName = "Household Waste",
            isHazardous = false,
            requiresSpecialHandling = false,
            baseEstimateTzs = 5000L,
            isActive = true
        ),
        DynamicWasteCategory(
            id = "recyclable",
            displayName = "Recyclable Plastics & Paper",
            isHazardous = false,
            requiresSpecialHandling = false,
            baseEstimateTzs = 3000L,
            isActive = true
        ),
        DynamicWasteCategory(
            id = "hazardous",
            displayName = "Hazardous Chemical / Industrial Waste",
            isHazardous = true,
            requiresSpecialHandling = true,
            baseEstimateTzs = null,
            isActive = true
        ),
        DynamicWasteCategory(
            id = "decommissioned_category",
            displayName = "Old E-Waste Scheme",
            isHazardous = false,
            requiresSpecialHandling = true,
            baseEstimateTzs = 10000L,
            isActive = false
        )
    )

    // UT-DOM-05: Validate DynamicWasteCategory parsing from valid ID string
    @Test
    fun validCategoryId_resolvesExpectedDomainEntity() {
        val category = resolveCategoryStrict("household", catalog)
        assertNotNull(category)
        assertEquals("household", category.id)
        assertEquals("Household Waste", category.displayName)
        assertFalse(category.isHazardous)
        assertFalse(category.requiresSpecialHandling)
        assertEquals(5000L, category.baseEstimateTzs)
        assertTrue(category.isActive)
        assertFalse(category.requiresCustomQuotation)
    }

    // UT-DOM-06: No Silent Fallback Test - unknown category must fail explicitly
    @Test
    fun unknownCategoryId_throwsUnknownWasteCategoryException() {
        val ex = assertThrows(UnknownWasteCategoryException::class.java) {
            resolveCategoryStrict("unknown_waste_type", catalog)
        }
        assertTrue(ex.message!!.contains("Unknown waste category: 'unknown_waste_type'"))
        assertTrue(ex.message!!.contains("Silent fallback to a default category is strictly prohibited"))
    }

    // UT-DOM-07: Inactive Category Test - deactivated category must fail explicitly
    @Test
    fun inactiveCategory_throwsInactiveCategoryException() {
        val ex = assertThrows(InactiveCategoryException::class.java) {
            resolveCategoryStrict("decommissioned_category", catalog)
        }
        assertTrue(ex.message!!.contains("Old E-Waste Scheme"))
        assertTrue(ex.message!!.contains("is inactive"))
    }

    @Test
    fun hazardousCategory_flagsSpecialHandlingAndQuotationRequirement() {
        val hazardous = resolveCategoryStrict("hazardous", catalog)
        assertTrue(hazardous.isHazardous)
        assertTrue(hazardous.requiresSpecialHandling)
        assertTrue(hazardous.requiresCustomQuotation)
    }

    @Test
    fun dynamicCategory_withNegativeEstimate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            DynamicWasteCategory(
                id = "test",
                displayName = "Test",
                baseEstimateTzs = -100L
            )
        }
    }

    @Test
    fun hazardousCategory_withoutSpecialHandling_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            DynamicWasteCategory(
                id = "test_haz",
                displayName = "Test Haz",
                isHazardous = true,
                requiresSpecialHandling = false
            )
        }
    }

    @Test
    fun standardCategoryCode_convertsToDynamicCategory() {
        val household = StandardCategoryCode.HOUSEHOLD.toDynamicCategory(baseEstimateTzs = 5000L)
        assertEquals("household", household.id)
        assertEquals("Household Waste", household.displayName)
        assertEquals(5000L, household.baseEstimateTzs)
    }
}
