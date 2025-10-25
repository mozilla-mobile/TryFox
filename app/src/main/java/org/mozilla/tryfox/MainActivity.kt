package org.mozilla.tryfox

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
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.mozilla.tryfox.ui.screens.HomeScreen
import org.mozilla.tryfox.ui.screens.HomeViewModel
import org.mozilla.tryfox.ui.screens.ProfileScreen
import org.mozilla.tryfox.ui.screens.ProfileViewModel
import org.mozilla.tryfox.ui.screens.TryFoxMainScreen
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import java.io.File
import java.net.URLDecoder

sealed class NavScreen(val route: String) {
    data object Home : NavScreen("home")
    data object TreeherderSearch : NavScreen("treeherder_search")
    data object TreeherderSearchWithArgs : NavScreen("treeherder_search/{project}/{revision}") {
        fun createRoute(project: String, revision: String) = "treeherder_search/$project/$revision"
    }
    data object Profile : NavScreen("profile")
    data object ProfileByEmail : NavScreen("profile_by_email?email={email}")
}

class MainActivity : ComponentActivity() {

    // Inject FenixInstallerViewModel using Koin
    private val tryFoxViewModel: TryFoxViewModel by viewModel()
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called. Intent: $intent, Data: ${intent?.data}")
        enableEdgeToEdge()
        setContent {
            TryFoxTheme {
                // Pass the Koin-injected ViewModel
                AppNavigation(tryFoxViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called. Intent: $intent, Data: ${intent.data}")
        setIntent(intent)
        if (::navController.isInitialized) {
            navController.handleDeepLink(intent)
        }
    }

    private fun installApk(file: File) {
        val fileUri: Uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.provider",
            file,
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
    fun AppNavigation(mainActivityViewModel: TryFoxViewModel) {
        val localNavController = rememberNavController()
        this@MainActivity.navController = localNavController
        Log.d("MainActivity", "AppNavigation: NavController instance assigned: $localNavController")

        NavHost(navController = localNavController, startDestination = NavScreen.Home.route) {
            composable(NavScreen.Home.route) {
                // Inject HomeViewModel using Koin in Composable
                val homeViewModel: HomeViewModel = koinViewModel()
                homeViewModel.onInstallApk = ::installApk
                HomeScreen(
                    onNavigateToTreeherder = { localNavController.navigate(NavScreen.TreeherderSearch.route) },
                    onNavigateToProfile = { localNavController.navigate(NavScreen.Profile.route) },
                    homeViewModel = homeViewModel,
                )
            }
            composable(NavScreen.TreeherderSearch.route) {
                // mainActivityViewModel is already injected and passed as a parameter
                mainActivityViewModel.onInstallApk = ::installApk
                TryFoxMainScreen(
                    tryFoxViewModel = mainActivityViewModel,
                    onNavigateUp = { localNavController.popBackStack() },
                )
            }
            composable(
                route = NavScreen.TreeherderSearchWithArgs.route,
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("revision") { type = NavType.StringType },
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern =
                            "https://treeherder.mozilla.org/#/jobs?repo={project}&revision={revision}"
                    },
                    navDeepLink {
                        uriPattern =
                            "https://treeherder.mozilla.org/jobs?repo={project}&revision={revision}"
                    },
                ),
            ) { backStackEntry ->
                val project = backStackEntry.arguments?.getString("project") ?: "try"
                val revision = backStackEntry.arguments?.getString("revision") ?: ""
                Log.d(
                    "MainActivity",
                    "TreeherderSearchWithArgs composable: project='$project', revision='$revision' from NavBackStackEntry. ID: ${backStackEntry.id}",
                )

                LaunchedEffect(project, revision) {
                    Log.d(
                        "MainActivity",
                        "TreeherderSearchWithArgs LaunchedEffect: project='$project', revision='$revision'",
                    )
                    mainActivityViewModel.setRevisionFromDeepLinkAndSearch(
                        project,
                        revision,
                    )
                }
                mainActivityViewModel.onInstallApk = ::installApk
                TryFoxMainScreen(
                    tryFoxViewModel = mainActivityViewModel,
                    onNavigateUp = { localNavController.popBackStack() },
                )
            }
            composable(NavScreen.Profile.route) {
                // Inject ProfileViewModel using Koin in Composable
                val profileViewModel: ProfileViewModel = koinViewModel()
                profileViewModel.onInstallApk = ::installApk // Assuming ProfileViewModel also needs this
                ProfileScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    profileViewModel = profileViewModel,
                )
            }
            composable(
                route = NavScreen.ProfileByEmail.route,
                arguments = listOf(navArgument("email") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "https://treeherder.mozilla.org/jobs?repo={repo}&author={email}" }),
            ) { backStackEntry ->
                val profileViewModel: ProfileViewModel = koinViewModel()
                val encodedEmail = backStackEntry.arguments?.getString("email")

                LaunchedEffect(encodedEmail) {
                    encodedEmail?.let {
                        val email = URLDecoder.decode(it, "UTF-8")
                        profileViewModel.updateAuthorEmail(email)
                        profileViewModel.searchByAuthor()
                    }
                }

                profileViewModel.onInstallApk = ::installApk
                ProfileScreen(
                    onNavigateUp = { localNavController.popBackStack() },
                    profileViewModel = profileViewModel,
                )
            }
        }
    }
}
