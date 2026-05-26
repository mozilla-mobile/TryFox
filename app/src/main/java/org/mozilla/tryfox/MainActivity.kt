package org.mozilla.tryfox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import org.mozilla.tryfox.ui.screens.QrCodeScannerScreen
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
    data object Home : NavScreen(AppRoutes.HOME)

    /**
     * Represents the QR code scanner screen.
     */
    data object QrScanner : NavScreen(AppRoutes.QR_SCANNER)

    /**
     * Represents the Treeherder search screen without arguments.
     */
    data object TreeherderSearch : NavScreen(AppRoutes.TREEHERDER_SEARCH)

    /**
     * Represents the Treeherder search screen with project and revision arguments.
     */
    data object TreeherderSearchWithArgs : NavScreen(AppRoutes.TREEHERDER_SEARCH_WITH_ARGS) {
        /**
         * Creates a route for the Treeherder search screen with the given project and revision.
         * @param project The project name.
         * @param revision The revision hash.
         * @return The formatted route string.
         */
        fun createRoute(project: String, revision: String) = AppRoutes.createTreeherderSearchRoute(
            project = project,
            revision = revision,
        )
    }

    /**
     * Represents the Profile screen.
     */
    data object Profile : NavScreen(AppRoutes.PROFILE)

    /**
     * Represents the Profile screen filtered by email.
     */
    data object ProfileByEmail : NavScreen(AppRoutes.PROFILE_BY_EMAIL) {
        fun createRoute(email: String) = AppRoutes.createProfileByEmailRoute(email)
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
        setIntent(intent)
        if (::navController.isInitialized) {
            routeDeepLink(intent)
        }
    }

    /**
     * Composable function that sets up the application's navigation.
     * It defines the navigation graph and handles different routes and deep links.
     */
    @Suppress("LongMethod")
    @Composable
    fun AppNavigation() {
        val localNavController = rememberNavController()
        this@MainActivity.navController = localNavController

        LaunchedEffect(localNavController) {
            routeDeepLink(intent)
        }

        NavHost(navController = localNavController, startDestination = NavScreen.Home.route) {
            composable(NavScreen.Home.route) {
                // Inject HomeViewModel using Koin in Composable
                HomeScreen(
                    onNavigateToTreeherder = { localNavController.navigate(NavScreen.TreeherderSearch.route) },
                    onNavigateToProfile = { localNavController.navigate(NavScreen.Profile.route) },
                    onNavigateToQrScanner = { localNavController.navigate(NavScreen.QrScanner.route) },
                    homeViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.QrScanner.route) {
                QrCodeScannerScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    onQrCodeScanned = { rawValue ->
                        routeDeepLink(rawValue, popQrScanner = true)
                    },
                )
            }
            composable(NavScreen.TreeherderSearch.route) {
                // mainActivityViewModel is already injected and passed as a parameter
                TryFoxMainScreen(
                    tryFoxViewModel = koinViewModel(),
                    deepLinkProject = null,
                    deepLinkRevision = null,
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
                TryFoxMainScreen(
                    tryFoxViewModel = koinViewModel { parametersOf(project, revision) },
                    deepLinkProject = project,
                    deepLinkRevision = revision,
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
        routeDeepLink(intent?.data?.toString(), popQrScanner = false)
    }

    private fun routeDeepLink(rawValue: String?, popQrScanner: Boolean): Boolean {
        val route = AppDeepLinkRouteMapper.routeFor(rawValue) ?: return false

        navController.navigate(route) {
            launchSingleTop = true
            if (popQrScanner) {
                popUpTo(NavScreen.QrScanner.route) {
                    inclusive = true
                }
            }
        }
        return true
    }
}
