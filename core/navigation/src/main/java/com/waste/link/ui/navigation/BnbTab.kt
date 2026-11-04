package com.waste.link.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.waste.link.core.navigation.R

data class BnbTab(
    val route: String,
    @StringRes val labelResId: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
)

val BnbTabs = listOf(
    BnbTab(
        route = Screen.Home.route,
        labelResId = R.string.nav_home,
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home
    ),
    BnbTab(
        route = Screen.History.route,
        labelResId = R.string.nav_history,
        outlinedIcon = Icons.Outlined.History,
        filledIcon = Icons.Filled.History
    ),
    BnbTab(
        route = Screen.Map.route,
        labelResId = R.string.nav_map,
        outlinedIcon = Icons.Outlined.LocationOn,
        filledIcon = Icons.Filled.LocationOn
    ),
    BnbTab(
        route = Screen.Alerts.route,
        labelResId = R.string.nav_alerts,
        outlinedIcon = Icons.Outlined.Notifications,
        filledIcon = Icons.Filled.Notifications
    ),
    BnbTab(
        route = Screen.Profile.route,
        labelResId = R.string.nav_profile,
        outlinedIcon = Icons.Outlined.Person,
        filledIcon = Icons.Filled.Person
    )
)
