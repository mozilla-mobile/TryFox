package org.mozilla.tryfox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.mozilla.tryfox.ui.screens.HomeScreen
import org.mozilla.tryfox.ui.screens.ProfileScreen
import org.mozilla.tryfox.ui.screens.TryFoxMainScreen
import org.mozilla.tryfox.ui.theme.TryFoxTheme

/**
 * Sealed class representing the navigation screens in the application.
 * Each object corresponds to a specific route in the navigation graph.
 */
sealed class NavScreen(val route: String) {
    /**
     * Represents the Home screen.
     */
    data object Home : NavScreen("home")

    /**
     * Represents the Treeherder search screen without arguments.
     */
    data object TreeherderSearch : NavScreen("treeherder_search")

    /**
     * Represents the Treeherder search screen with project and revision arguments.
     */
    data object TreeherderSearchWithArgs : NavScreen("treeherder_search/{project}/{revision}") {
        /**
         * Creates a route for the Treeherder search screen with the given project and revision.
         * @param project The project name.
         * @param revision The revision hash.
         * @return The formatted route string.
         */
        fun createRoute(project: String, revision: String) = "treeherder_search/$project/$revision"
    }

    /**
     * Represents the Profile screen.
     */
    data object Profile : NavScreen("profile")

    /**
     * Represents the Profile screen filtered by email.
     */
    data object ProfileByEmail : NavScreen("profile_by_email?email={email}") {
        fun createRoute(email: String) = "profile_by_email?email=${Uri.encode(email)}"
    }
}

/**
 * The main activity of the TryFox application.
 * This activity sets up the navigation host and handles deep links.
 */
class MainActivity : ComponentActivity() {

    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called. Intent: $intent, Data: ${intent?.data}")
        enableEdgeToEdge()
        setContent {
            TryFoxTheme {
                // Pass the Koin-injected ViewModel
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called. Intent: $intent, Data: ${intent.data}")
        setIntent(intent)
        if (::navController.isInitialized) {
            routeDeepLink(intent)
        }
    }

    /**
     * Composable function that sets up the application's navigation.
     * It defines the navigation graph and handles different routes and deep links.
     */
    @Composable
    fun AppNavigation() {
        val localNavController = rememberNavController()
        this@MainActivity.navController = localNavController
        Log.d("MainActivity", "AppNavigation: NavController instance assigned: $localNavController")

        LaunchedEffect(localNavController) {
            routeDeepLink(intent)
        }

        NavHost(navController = localNavController, startDestination = NavScreen.Home.route) {
            composable(NavScreen.Home.route) {
                // Inject HomeViewModel using Koin in Composable
                HomeScreen(
                    onNavigateToTreeherder = { localNavController.navigate(NavScreen.TreeherderSearch.route) },
                    onNavigateToProfile = { localNavController.navigate(NavScreen.Profile.route) },
                    homeViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.TreeherderSearch.route) {
                // mainActivityViewModel is already injected and passed as a parameter
                TryFoxMainScreen(
                    tryFoxViewModel = koinViewModel(),
                    onNavigateUp = { localNavController.popBackStack() },
                )
            }
            composable(
                route = NavScreen.TreeherderSearchWithArgs.route,
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("revision") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val project = backStackEntry.arguments?.getString("project")
                val revision = backStackEntry.arguments?.getString("revision")
                Log.d(
                    "MainActivity",
                    "TreeherderSearchWithArgs composable: project='$project', revision='$revision' from NavBackStackEntry. ID: ${backStackEntry.id}",
                )
                TryFoxMainScreen(
                    tryFoxViewModel = koinViewModel { parametersOf(project, revision) },
                    onNavigateUp = { localNavController.popBackStack() },
                )
            }
            composable(NavScreen.Profile.route) {
                ProfileScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    profileViewModel = koinViewModel(),
                )
            }
            composable(
                route = NavScreen.ProfileByEmail.route,
                arguments = listOf(navArgument("email") { type = NavType.StringType }),
            ) { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email")?.let(Uri::decode)

                ProfileScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    profileViewModel = koinViewModel { parametersOf(email) },
                )
            }
        }
    }

    private fun routeDeepLink(intent: Intent?) {
        val destination = AppDeepLinkParser.parse(intent?.data) ?: return
        val route = when (destination) {
            is AppDeepLinkDestination.TreeherderSearch -> {
                NavScreen.TreeherderSearchWithArgs.createRoute(
                    project = destination.project,
                    revision = destination.revision,
                )
            }

            is AppDeepLinkDestination.Profile -> {
                NavScreen.ProfileByEmail.createRoute(destination.email)
            }
        }

        navController.navigate(route) {
            launchSingleTop = true
        }
    }
}
