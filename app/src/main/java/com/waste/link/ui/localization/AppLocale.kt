package com.waste.link.ui.localization

sealed class AppLocale(val code: String) {
    object English : AppLocale("en")
    object Swahili : AppLocale("sw")
    
    companion object {
        fun fromCode(code: String): AppLocale {
            return when (code) {
                "sw" -> Swahili
                else -> English
            }
        }
    }
}
