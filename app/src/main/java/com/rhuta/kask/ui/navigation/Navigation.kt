package com.rhuta.kask.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object History : Screen("history")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Result : Screen("result/{historyId}") {
        fun withId(id: String) = "result/$id"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Home,     "Home",     Icons.Outlined.Home),
    BottomNavItem(Screen.History,  "History",  Icons.Outlined.History),
    BottomNavItem(Screen.Library,  "Library",  Icons.Outlined.BookmarkBorder),
    BottomNavItem(Screen.Settings, "Settings", Icons.Outlined.Settings)
)
