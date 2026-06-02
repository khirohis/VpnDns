package net.hogelab.android.vpndns.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.History)
    object Blacklist : Screen("blacklist", "Blacklist", Icons.Default.Block)
    object Whitelist : Screen("whitelist", "Whitelist", Icons.Default.CheckCircle)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.History,
    Screen.Blacklist,
    Screen.Whitelist,
    Screen.Settings
)
