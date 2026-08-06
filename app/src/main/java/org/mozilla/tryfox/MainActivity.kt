package org.mozilla.tryfox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.mozilla.tryfox.EXTRA_RECEIVE_FROM_DESKTOP_START_REQUESTED
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.ui.screens.HistoryScreen
import org.mozilla.tryfox.ui.screens.HomeScreen
import org.mozilla.tryfox.ui.screens.QrCodeScannerScreen
import org.mozilla.tryfox.ui.screens.ReceiveFromDesktopScreen
import org.mozilla.tryfox.ui.screens.ReceiveMessageHistoryScreen
import org.mozilla.tryfox.ui.screens.SearchHistoryViewModel
import org.mozilla.tryfox.ui.screens.SearchScreen
import org.mozilla.tryfox.ui.screens.SettingsScreen
import org.mozilla.tryfox.ui.screens.SearchViewModel
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
     * Represents the History screen.
     */
    data object History : NavScreen(AppRoutes.HISTORY)

    data object Settings : NavScreen(AppRoutes.SETTINGS)

    data object ReceiveFromDesktop : NavScreen(AppRoutes.RECEIVE_FROM_DESKTOP)

    data object ReceiveMessageHistory : NavScreen(AppRoutes.RECEIVE_MESSAGE_HISTORY)

    /**
     * Represents the QR code scanner screen.
     */
    data object QrScanner : NavScreen(AppRoutes.QR_SCANNER)

    /**
     * Represents the Treeherder search screen without arguments.
     */
    data object TreeherderSearch : NavScreen(AppRoutes.TREEHERDER_SEARCH)

    /**
     * Represents the Treeherder search screen with project and query arguments.
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
            query = revision,
        )
    }
}

/**
 * The main activity of the TryFox application.
 * This activity sets up the navigation host and handles deep links.
 */
class MainActivity : ComponentActivity() {
    private val installCoordinator: ApkInstallCoordinator by inject()
    private lateinit var navController: NavHostController
    private var receiveFromDesktopStartRequested by mutableStateOf(false)
    private var pendingUninstallOperationId: String? = null
    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingUninstallOperationId?.let { operationId ->
            installCoordinator.onUninstallResult(operationId, result.resultCode == RESULT_OK)
        }
        pendingUninstallOperationId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            installCoordinator.uninstallRequests.collect { request ->
                pendingUninstallOperationId = request.operationId
                uninstallLauncher.launch(
                    Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                        data = Uri.fromParts("package", request.packageName, null)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    },
                )
            }
        }
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
        val appSearchHistoryViewModel: SearchHistoryViewModel = koinViewModel()
        val installStates by installCoordinator.states.collectAsState()
        val installConflict = installStates.entries.firstOrNull { (_, state) -> state is InstallState.Conflict }
        val localNavController = rememberNavController()
        this@MainActivity.navController = localNavController

        installConflict?.let { (artifactKey, state) ->
            val conflict = state as InstallState.Conflict
            AlertDialog(
                onDismissRequest = { installCoordinator.cancelConflict(artifactKey) },
                title = { Text(stringResource(id = R.string.install_conflict_title)) },
                text = { Text(stringResource(R.string.install_conflict_message, conflict.packageName)) },
                confirmButton = {
                    Button(onClick = { installCoordinator.confirmUninstallAndRetry(artifactKey) }) {
                        Text(stringResource(id = R.string.install_conflict_confirm))
                    }
                },
                dismissButton = {
                    Button(onClick = { installCoordinator.cancelConflict(artifactKey) }) {
                        Text(stringResource(id = R.string.install_conflict_cancel))
                    }
                },
            )
        }

        LaunchedEffect(localNavController) {
            routeDeepLink(intent)
        }

        NavHost(
            navController = localNavController,
            startDestination = NavScreen.Home.route,
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(SCREEN_TRANSITION_DURATION_MS),
                    initialOffsetX = { fullWidth -> fullWidth },
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(SCREEN_TRANSITION_DURATION_MS),
                    targetOffsetX = { fullWidth -> -fullWidth },
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(SCREEN_TRANSITION_DURATION_MS),
                    initialOffsetX = { fullWidth -> -fullWidth },
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(SCREEN_TRANSITION_DURATION_MS),
                    targetOffsetX = { fullWidth -> fullWidth },
                )
            },
        ) {
            composable(NavScreen.Home.route) {
                // Inject HomeViewModel using Koin in Composable
                HomeScreen(
                    onNavigateToSearch = { localNavController.navigate(NavScreen.TreeherderSearch.route) },
                    onNavigateToQrScanner = { localNavController.navigate(NavScreen.QrScanner.route) },
                    onNavigateToReceiveFromDesktop = { localNavController.navigate(NavScreen.ReceiveFromDesktop.route) },
                    onNavigateToHistory = { localNavController.navigate(NavScreen.History.route) },
                    onNavigateToSettings = { localNavController.navigate(NavScreen.Settings.route) },
                    onNavigateToTryBuild = { project, revision ->
                        localNavController.navigate(
                            NavScreen.TreeherderSearchWithArgs.createRoute(project, revision),
                        )
                    },
                    homeViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.History.route) {
                HistoryScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    onNavigateToTreeherderRevision = { project, revision ->
                        localNavController.navigate(
                            NavScreen.TreeherderSearchWithArgs.createRoute(project, revision),
                        )
                    },
                    historyViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.Settings.route) {
                SettingsScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    settingsViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.QrScanner.route) {
                QrCodeScannerScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    onQrCodeScanned = { rawValue -> routeDeepLink(rawValue, popQrScanner = true) },
                )
            }
            composable(NavScreen.ReceiveFromDesktop.route) {
                ReceiveFromDesktopScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    onNavigateToMessageHistory = {
                        localNavController.navigate(NavScreen.ReceiveMessageHistory.route)
                    },
                    onNavigateToTreeherderRevision = { project, revision ->
                        localNavController.navigate(
                            NavScreen.TreeherderSearchWithArgs.createRoute(project, revision),
                        )
                    },
                    receiveFromDesktopViewModel = koinViewModel(),
                    startReceiverOnEnter = receiveFromDesktopStartRequested,
                    onStartReceiverOnEnterConsumed = {
                        receiveFromDesktopStartRequested = false
                    },
                )
            }
            composable(NavScreen.ReceiveMessageHistory.route) {
                ReceiveMessageHistoryScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    onOpenDeepLink = { rawValue -> routeDeepLink(rawValue, popQrScanner = false) },
                    receiveMessageHistoryViewModel = koinViewModel(),
                )
            }
            composable(NavScreen.TreeherderSearch.route) {
                val searchHistory by appSearchHistoryViewModel.searchHistory.collectAsState()
                // mainActivityViewModel is already injected and passed as a parameter
                SearchScreen(
                    searchViewModel = koinViewModel<SearchViewModel> { parametersOf("", "try") },
                    deepLinkProject = null,
                    deepLinkQuery = null,
                    onNavigateUp = { localNavController.popBackStack() },
                    searchHistory = searchHistory,
                )
            }
            composable(
                route = NavScreen.TreeherderSearchWithArgs.route,
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("query") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val project = backStackEntry.arguments?.getString("project")
                val query = backStackEntry.arguments?.getString("query")?.let(Uri::decode).orEmpty()
                val searchHistory by appSearchHistoryViewModel.searchHistory.collectAsState()
                SearchScreen(
                    searchViewModel = koinViewModel<SearchViewModel> { parametersOf("", project) },
                    deepLinkProject = project,
                    deepLinkQuery = query,
                    onNavigateUp = { localNavController.popBackStack() },
                    searchHistory = searchHistory,
                )
            }
        }
    }

    private fun routeDeepLink(intent: Intent?) {
        val internalRoute = intent?.getStringExtra(EXTRA_NAVIGATION_ROUTE)
        val startReceiverOnEnter = intent?.getBooleanExtra(EXTRA_RECEIVE_FROM_DESKTOP_START_REQUESTED, false) == true
        if (startReceiverOnEnter && internalRoute == AppRoutes.RECEIVE_FROM_DESKTOP) {
            receiveFromDesktopStartRequested = true
        }
        intent?.removeExtra(EXTRA_RECEIVE_FROM_DESKTOP_START_REQUESTED)
        if (!internalRoute.isNullOrBlank()) {
            navController.navigate(internalRoute) {
                launchSingleTop = true
            }
            return
        }
        routeDeepLink(intent?.data?.toString(), popQrScanner = false)
    }

    private fun routeDeepLink(rawValue: String?, popQrScanner: Boolean): Boolean {
        when (val destination = AppDeepLinkParser.parse(rawValue)) {
            is AppDeepLinkDestination.TreeherderSearch -> {
                navigateToDeepLinkRoute(
                    AppRoutes.createTreeherderSearchRoute(destination.project, destination.revision),
                    popQrScanner,
                )
                return true
            }

            is AppDeepLinkDestination.Profile -> {
                navigateToDeepLinkRoute(
                    AppRoutes.createTreeherderSearchRoute(destination.project, destination.email),
                    popQrScanner,
                )
                return true
            }

            null -> return false
        }
    }

    private fun navigateToDeepLinkRoute(route: String, popQrScanner: Boolean) {
        navController.navigate(route) {
            launchSingleTop = true
            if (popQrScanner) {
                popUpTo(NavScreen.QrScanner.route) {
                    inclusive = true
                }
            }
        }
    }

    private companion object {
        private const val SCREEN_TRANSITION_DURATION_MS = 200
    }

}
