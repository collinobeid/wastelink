package com.waste.link.ui.request


enum class WasteCategory {
    HOUSEHOLD, CONSTRUCTION, GARDEN, ELECTRONIC, HAZARDOUS, BULK_ITEMS
}

enum class VolumeTier {
    SMALL_BAG, STANDARD_BIN, LARGE_LOAD, TRUCK_LOAD
}

data class LocationCoords(val lat: Double, val lng: Double)

data class RequestUiState(
    val selectedCategory: WasteCategory? = null,
    val selectedVolume: VolumeTier? = null,
    val location: LocationCoords? = null,
    val address: String = "Detecting location...",
    val notes: String = "",
    val reference: String? = null,
    val isLoading: Boolean = false
)
