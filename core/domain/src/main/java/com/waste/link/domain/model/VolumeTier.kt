package com.waste.link.domain.model

/**
 * Authoritative volume tier classification for waste pickup requests.
 *
 * Mapped server-side to vehicle allocation and logistics capacity:
 * - [SMALL_BAG]: 1 – 3 bags, approximately 50L (bag equivalent: 2)
 * - [STANDARD_BIN]: 240L municipal wheelie bin (bag equivalent: 5)
 * - [LARGE_LOAD]: Half pickup truck bed, ~1.5 m³ (bag equivalent: 15)
 * - [TRUCK_LOAD]: Full tipper truck load, ~5.0 m³ (bag equivalent: 40)
 */
enum class VolumeTier(
    val id: String,
    val displayName: String,
    val volumeDescription: String,
    val bagEquivalent: Int
) {
    SMALL_BAG(
        id = "small_bag",
        displayName = "Small Bag",
        volumeDescription = "1 – 3 standard bags (~50L)",
        bagEquivalent = 2
    ),
    STANDARD_BIN(
        id = "standard_bin",
        displayName = "Standard Bin",
        volumeDescription = "240L municipal wheelie bin",
        bagEquivalent = 5
    ),
    LARGE_LOAD(
        id = "large_load",
        displayName = "Large Load",
        volumeDescription = "Half pickup bed (~1.5 m³)",
        bagEquivalent = 15
    ),
    TRUCK_LOAD(
        id = "truck_load",
        displayName = "Truck Load",
        volumeDescription = "Full tipper truck (~5.0 m³)",
        bagEquivalent = 40
    );

    companion object {
        /**
         * Strictly parses a [VolumeTier] from a string identifier.
         *
         * Strict domain invariant:
         * - Unknown or malformed IDs throw [UnknownVolumeTierException].
         * - Silent fallback to [STANDARD_BIN] or any default tier is strictly prohibited.
         */
        fun fromId(id: String): VolumeTier {
            val normalized = id.trim()
            return entries.find { it.id.equals(normalized, ignoreCase = true) }
                ?: throw UnknownVolumeTierException(
                    "Unknown volume tier: '$id'. Silent fallback to a default volume tier is strictly prohibited."
                )
        }

        /**
         * Safe parser returning null for non-matching strings, useful for advisory lookups.
         */
        fun fromIdOrNull(id: String?): VolumeTier? {
            if (id.isNullOrBlank()) return null
            val normalized = id.trim()
            return entries.find { it.id.equals(normalized, ignoreCase = true) }
        }
    }
}
