package com.watercantracker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Dashboard   : Screen("dashboard")
    object Payments    : Screen("payments")
    object AddPayment  : Screen("add_payment")
    object EditPayment : Screen("edit_payment/{paymentId}") {
        fun createRoute(id: Long) = "edit_payment/$id"
    }
    object Members    : Screen("members")
    object AddMember  : Screen("add_member")
    object EditMember : Screen("edit_member/{memberId}") {
        fun createRoute(id: Long) = "edit_member/$id"
    }
    object Reports    : Screen("reports")
    object Settings   : Screen("settings")
    object Settlement : Screen("settlement")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Rounded.Dashboard,   Icons.Outlined.Dashboard),
    BottomNavItem(Screen.Payments,  "Payments",  Icons.Rounded.History,     Icons.Outlined.History),
    BottomNavItem(Screen.Members,   "Members",   Icons.Rounded.Group,       Icons.Outlined.Group),
    BottomNavItem(Screen.Reports,   "Reports",   Icons.Rounded.Analytics,   Icons.Outlined.Analytics),
    BottomNavItem(Screen.Settings,  "Settings",  Icons.Rounded.Settings,    Icons.Outlined.Settings)
)
