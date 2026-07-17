package com.rhuta.kask.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rhuta.kask.domain.util.SharedChatContext
import com.rhuta.kask.ui.history.HistoryScreen
import com.rhuta.kask.ui.home.HomeScreen
import com.rhuta.kask.ui.library.LibraryScreen
import com.rhuta.kask.ui.onboarding.OnboardingScreen
import com.rhuta.kask.ui.settings.SettingsScreen

@Composable
fun KaskNavHost(
    navController: NavHostController,
    isFirstRun: Boolean,
    sharedChatContext: SharedChatContext,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (isFirstRun) Screen.Onboarding.route else Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen()
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onContinueChat = { historyId ->
                    sharedChatContext.setPendingChat(historyId)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
