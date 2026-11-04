package com.waste.link.ui.navigation

sealed class Screen(val route: String) {
    object AuthGate : Screen("auth_gate")
    object PhoneVerification : Screen("phone_verification")
    object Main : Screen("main")
    
    // Bottom Navigation Screens
    object Home : Screen("home")
    object History : Screen("history")
    object Map : Screen("map")
    object Alerts : Screen("alerts")
    object Profile : Screen("profile")
    
    // Modal/Detail Screens
    object RequestCreation : Screen("request_creation")
    object PinDropMap : Screen("pin_drop_map")
    object WRDetail : Screen("wr_detail/{rid}") {
        fun createRoute(rid: String) = "wr_detail/$rid"
    }
}
