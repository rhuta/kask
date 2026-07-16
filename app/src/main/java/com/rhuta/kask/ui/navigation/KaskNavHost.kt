package com.rhuta.kask.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rhuta.kask.ui.history.HistoryScreen
import com.rhuta.kask.ui.home.HomeScreen
import com.rhuta.kask.ui.library.LibraryScreen
import com.rhuta.kask.ui.onboarding.OnboardingScreen
import com.rhuta.kask.ui.settings.SettingsScreen

@Composable
fun KaskNavHost(
    navController: NavHostController,
    isFirstRun: Boolean,
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

        composable(
            route = Screen.Home.route,
            arguments = listOf(navArgument("continueId") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStack ->
            val continueId = backStack.arguments?.getString("continueId")
            HomeScreen(continueId = continueId)
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onContinueChat = { historyId ->
                    navController.navigate(Screen.Home.withContinueId(historyId)) {
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
