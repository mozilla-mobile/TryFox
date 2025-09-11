package org.mozilla.fenixinstaller

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.mozilla.fenixinstaller.ui.screens.FenixInstallerApp
import org.mozilla.fenixinstaller.ui.screens.HomeScreen
import org.mozilla.fenixinstaller.ui.screens.HomeViewModel
import org.mozilla.fenixinstaller.ui.screens.ProfileScreen
import org.mozilla.fenixinstaller.ui.screens.ProfileViewModel
import org.mozilla.fenixinstaller.ui.theme.FenixInstallerTheme
import java.io.File

sealed class NavScreen(val route: String) {
    data object Home : NavScreen("home")
    data object TreeherderSearch : NavScreen("treeherder_search")
    data object TreeherderSearchWithArgs : NavScreen("treeherder_search/{project}/{revision}") {
        fun createRoute(project: String, revision: String) = "treeherder_search/$project/$revision"
    }
    data object Profile : NavScreen("profile")
}

class MainActivity : ComponentActivity() {

    // Inject FenixInstallerViewModel using Koin
    private val fenixInstallerViewModel: FenixInstallerViewModel by viewModel()
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called. Intent: $intent, Data: ${intent?.data}")
        enableEdgeToEdge()
        setContent {
            FenixInstallerTheme {
                // Pass the Koin-injected ViewModel
                AppNavigation(fenixInstallerViewModel)
            }
        }
    }

    private fun installApk(file: File) {
        val fileUri: Uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to install APK", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "Error installing APK", e)
        }
    }

    @Composable
    fun AppNavigation(mainActivityViewModel: FenixInstallerViewModel) {
        val localNavController = rememberNavController()
        this@MainActivity.navController = localNavController
        Log.d("MainActivity", "AppNavigation: NavController instance assigned: $localNavController")

        val navBackStackEntry by localNavController.currentBackStackEntryAsState()
        var currentScreenTitle by remember { mutableStateOf("Fenix Installer") }

        LaunchedEffect(navBackStackEntry) {
            currentScreenTitle = when (navBackStackEntry?.destination?.route) {
                NavScreen.Home.route -> "Fenix Nightly Builds"
                NavScreen.TreeherderSearch.route -> "Treeherder Build Search"
                NavScreen.TreeherderSearchWithArgs.route -> "Treeherder Build Search"
                NavScreen.Profile.route -> "Profile"
                else -> "Fenix Installer"
            }
        }

        NavHost(navController = localNavController, startDestination = NavScreen.Home.route) {
            composable(NavScreen.Home.route) {
                // Inject HomeViewModel using Koin in Composable
                val homeViewModel: HomeViewModel = koinViewModel()
                homeViewModel.onInstallApk = ::installApk
                HomeScreen(
                    onNavigateToTreeherder = { localNavController.navigate(NavScreen.TreeherderSearch.route) },
                    onNavigateToProfile = { localNavController.navigate(NavScreen.Profile.route) }, 
                    homeViewModel = homeViewModel
                )
            }
            composable(NavScreen.TreeherderSearch.route) {
                // mainActivityViewModel is already injected and passed as a parameter
                mainActivityViewModel.onInstallApk = ::installApk
                FenixInstallerApp(
                    fenixInstallerViewModel = mainActivityViewModel, 
                    onNavigateUp = { localNavController.popBackStack() }
                )
            }
            composable(
                route = NavScreen.TreeherderSearchWithArgs.route,
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("revision") { type = NavType.StringType }
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern =
                            "https://treeherder.mozilla.org/#/jobs?repo={project}&revision={revision}"
                    },
                    navDeepLink {
                        uriPattern =
                            "https://treeherder.mozilla.org/jobs?repo={project}&revision={revision}"
                    })
            ) { backStackEntry ->
                val project = backStackEntry.arguments?.getString("project") ?: "try"
                val revision = backStackEntry.arguments?.getString("revision") ?: ""
                Log.d(
                    "MainActivity",
                    "TreeherderSearchWithArgs composable: project='${project}', revision='${revision}' from NavBackStackEntry. ID: ${backStackEntry.id}"
                )

                LaunchedEffect(project, revision) {
                    Log.d(
                        "MainActivity",
                        "TreeherderSearchWithArgs LaunchedEffect: project='${project}', revision='${revision}'"
                    )
                    mainActivityViewModel.setRevisionFromDeepLinkAndSearch(
                        project,
                        revision,
                    )
                }
                mainActivityViewModel.onInstallApk = ::installApk
                FenixInstallerApp(
                    fenixInstallerViewModel = mainActivityViewModel, 
                    onNavigateUp = { localNavController.popBackStack() }
                )
            }
            composable(NavScreen.Profile.route) {
                // Inject ProfileViewModel using Koin in Composable
                val profileViewModel: ProfileViewModel = koinViewModel()
                profileViewModel.onInstallApk = ::installApk // Assuming ProfileViewModel also needs this
                ProfileScreen(
                    onNavigateUp = { localNavController.popBackStack() }, 
                    profileViewModel = profileViewModel
                )
            }
        }
    }
}
