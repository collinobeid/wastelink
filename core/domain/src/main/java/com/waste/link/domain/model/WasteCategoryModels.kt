package com.waste.link.domain.model

/**
 * Pure domain representation of a configurable waste category loaded dynamically
 * from remote Firestore configuration (`/waste_categories/{categoryId}`).
 *
 * Design Invariants:
 * - Dynamic remote categories can be introduced without requiring an application release.
 * - Silent fallback to a default category (e.g. HOUSEHOLD) is strictly prohibited.
 * - Hazardous categories require special handling and municipal inspection quotation.
 */
data class DynamicWasteCategory(
    val id: String,
    val displayName: String,
    val isHazardous: Boolean = false,
    val requiresSpecialHandling: Boolean = false,
    val baseEstimateTzs: Long? = null,
    val isActive: Boolean = true,
    val version: Int = 1
) {
    init {
        require(id.isNotBlank()) { "DynamicWasteCategory.id must not be blank" }
        require(displayName.isNotBlank()) { "DynamicWasteCategory.displayName must not be blank" }
        if (baseEstimateTzs != null) {
            require(baseEstimateTzs >= 0L) { "baseEstimateTzs must not be negative, received $baseEstimateTzs" }
        }
        if (isHazardous) {
            require(requiresSpecialHandling) {
                "Hazardous category '$id' must have requiresSpecialHandling set to true."
            }
        }
    }

    /**
     * Resolves whether this category requires an administrative / dispatcher quote
     * rather than displaying a fixed instant price.
     */
    val requiresCustomQuotation: Boolean
        get() = isHazardous || requiresSpecialHandling || baseEstimateTzs == null
}

/**
 * Well-known canonical category codes used for standard seed fixtures and type matching.
 * The system is not limited to these codes; any [DynamicWasteCategory] loaded from
 * `/waste_categories` is valid as long as [DynamicWasteCategory.isActive] is true.
 */
enum class StandardCategoryCode(
    val id: String,
    val defaultDisplayName: String,
    val isHazardous: Boolean = false,
    val requiresSpecialHandling: Boolean = false
) {
    HOUSEHOLD("household", "Household Waste", isHazardous = false, requiresSpecialHandling = false),
    RECYCLABLE("recyclable", "Recyclable Materials", isHazardous = false, requiresSpecialHandling = false),
    ORGANIC("organic", "Organic / Food Waste", isHazardous = false, requiresSpecialHandling = false),
    HAZARDOUS("hazardous", "Hazardous Waste", isHazardous = true, requiresSpecialHandling = true),
    ELECTRONIC("electronic", "E-Waste / Electronics", isHazardous = false, requiresSpecialHandling = true),
    CONSTRUCTION("construction", "Construction Debris", isHazardous = false, requiresSpecialHandling = true),
    BULKY("bulky", "Bulky Items / Furniture", isHazardous = false, requiresSpecialHandling = false);

    fun toDynamicCategory(baseEstimateTzs: Long? = null, isActive: Boolean = true, version: Int = 1): DynamicWasteCategory {
        return DynamicWasteCategory(
            id = id,
            displayName = defaultDisplayName,
            isHazardous = isHazardous,
            requiresSpecialHandling = requiresSpecialHandling,
            baseEstimateTzs = baseEstimateTzs,
            isActive = isActive,
            version = version
        )
    }

    companion object {
        fun fromIdOrNull(id: String?): StandardCategoryCode? =
            entries.find { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/**
 * Resolves a category ID against an available catalog with strict failure semantics.
 *
 * @throws UnknownWasteCategoryException when [categoryId] is not found in [catalog]. Silent fallback is forbidden.
 * @throws InactiveCategoryException when the category exists but is flagged as [DynamicWasteCategory.isActive] == false.
 */
fun resolveCategoryStrict(
    categoryId: String,
    catalog: Collection<DynamicWasteCategory>
): DynamicWasteCategory {
    val category = catalog.firstOrNull { it.id.equals(categoryId.trim(), ignoreCase = true) }
        ?: throw UnknownWasteCategoryException(
            "Unknown waste category: '$categoryId'. Silent fallback to a default category is strictly prohibited."
        )

    if (!category.isActive) {
        throw InactiveCategoryException(
            "Waste category '${category.displayName}' ($categoryId) is inactive and cannot be selected for pickup."
        )
    }

    return category
}
