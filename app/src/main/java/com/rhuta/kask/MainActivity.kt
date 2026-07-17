/*
Copyright (c) rhuta 2026
 */
package com.rhuta.kask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rhuta.kask.ui.navigation.KaskNavHost
import com.rhuta.kask.ui.navigation.BOTTOM_NAV_ITEMS
import com.rhuta.kask.ui.navigation.Screen
import com.rhuta.kask.ui.settings.SettingsViewModel
import com.rhuta.kask.ui.theme.KaskTheme
import com.rhuta.kask.domain.util.SharedChatContext
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sharedChatContext: SharedChatContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()

            // Key on appLanguage ensures the entire UI is recreated when language changes
            key(settingsState.appLanguage) {
                val context = LocalContext.current
                val locale = remember(settingsState.appLanguage) { 
                    Locale.forLanguageTag(settingsState.appLanguage) 
                }
                
                // Update global configuration
                LaunchedEffect(locale) {
                    val resources = context.resources
                    val config = resources.configuration
                    config.setLocale(locale)
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(config, resources.displayMetrics)
                }

                KaskTheme(darkTheme = settingsState.darkMode) {
                    val navController = rememberNavController()
                    val backStack by navController.currentBackStackEntryAsState()
                    val currentRoute = backStack?.destination?.route
                    val isFirstRun = !settingsState.allModelsDownloaded

                    val configuration = LocalConfiguration.current
                    val useNavDrawer = configuration.screenWidthDp >= 840
                    
                    val isKeyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                    val showBars = currentRoute != Screen.Onboarding.route && !isKeyboardVisible

                    // Main Shell
                    if (useNavDrawer) {
                        PermanentNavigationDrawer(
                            drawerContent = {
                                if (showBars) {
                                    PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                                        Spacer(Modifier.height(16.dp))
                                        BOTTOM_NAV_ITEMS.forEach { item ->
                                            val selected = currentRoute == item.screen.route
                                            val label = when (item.label) {
                                                "Home" -> stringResource(R.string.home)
                                                "History" -> stringResource(R.string.history)
                                                "Library" -> stringResource(R.string.library)
                                                "Settings" -> stringResource(R.string.settings)
                                                else -> item.label
                                            }
                                            NavigationDrawerItem(
                                                selected = selected,
                                                label = { Text(label) },
                                                icon = { Icon(item.icon, contentDescription = item.label) },
                                                onClick = {
                                                    if (!selected) {
                                                        navController.navigate(item.screen.route) {
                                                            popUpTo(Screen.Home.route) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize()
                            ) { innerPadding ->
                                KaskNavHost(
                                    navController = navController,
                                    isFirstRun = isFirstRun,
                                    sharedChatContext = sharedChatContext,
                                    modifier = Modifier.padding(
                                        start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                        end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                        bottom = innerPadding.calculateBottomPadding()
                                    )
                                )
                            }
                        }
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (showBars) {
                                    NavigationBar {
                                        BOTTOM_NAV_ITEMS.forEach { item ->
                                            val selected = currentRoute == item.screen.route
                                            val label = when (item.label) {
                                                "Home" -> stringResource(R.string.home)
                                                "History" -> stringResource(R.string.history)
                                                "Library" -> stringResource(R.string.library)
                                                "Settings" -> stringResource(R.string.settings)
                                                else -> item.label
                                            }
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    if (!selected) {
                                                        navController.navigate(item.screen.route) {
                                                            popUpTo(Screen.Home.route) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label
                                                    )
                                                },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            KaskNavHost(
                                navController = navController,
                                isFirstRun = isFirstRun,
                                sharedChatContext = sharedChatContext,
                                modifier = Modifier.padding(
                                    start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                    end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
