package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mozilla.tryfox.R

/**
 * Composable function for the Home screen, which displays a list of available apps and allows users to interact with them.
 *
 * @param modifier The modifier to be applied to the component.
 * @param onNavigateToSearch Callback to navigate to the unified build search screen.
 * @param onNavigateToHistory Callback to navigate to the History screen.
 * @param onNavigateToSettings Callback to navigate to the Settings screen.
 * @param homeViewModel The ViewModel for the Home screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToReceiveFromDesktop: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTryBuild: (String, String) -> Unit = { _, _ -> },
    homeViewModel: HomeViewModel = viewModel(),
) {
    val screenState by homeViewModel.homeScreenState.collectAsState()
    val isRefreshing by homeViewModel.isRefreshing.collectAsState()
    val installStates by homeViewModel.installStates.collectAsState()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, { homeViewModel.refreshData() })

    LaunchedEffect(Unit) {
        homeViewModel.initialLoad()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(id = R.string.home_history_button_description),
                        )
                    }
                    TopBarActionIcon(
                        onClick = onNavigateToQrScanner,
                        onLongClick = onNavigateToReceiveFromDesktop,
                        contentDescription = stringResource(
                            id = R.string.home_scan_qr_code_button_description,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(
                                id = R.string.home_search_treeherder_button_description,
                            ),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.home_settings_button_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            var tryFoxCardHeight by remember { mutableStateOf(0.dp) }

            when (val currentScreenState = screenState) {
                is HomeScreenState.InitialLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(id = R.string.home_loading_initial_data),
                            modifier = Modifier.padding(top = 70.dp),
                        )
                    }
                }

                is HomeScreenState.Loaded -> {
                    val tryFoxApp = currentScreenState.tryfoxApp
                    val cards = homeAppCards(
                        apps = currentScreenState.apps,
                        selectedAppNames = currentScreenState.selectedAppNames,
                        layout = currentScreenState.homeScreenLayout,
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(if (tryFoxApp != null) tryFoxCardHeight + 4.dp else 0.dp)) }

                        items(cards, key = { it.stableKey }) { card ->
                            HomeAppCard(
                                card = card,
                                onFlavorSelected = { appName ->
                                    homeViewModel.selectHomeAppFlavor(card.family, appName)
                                },
                                onDownloadClick = { homeViewModel.downloadNightlyApk(it) },
                                onInstallClick = homeViewModel::installHomeApk,
                                installStates = installStates,
                                onOpenInstalledApp = homeViewModel::openInstalledApp,
                                onOpenTryBuild = onNavigateToTryBuild,
                                onDateSelected = { appName, date ->
                                    homeViewModel.onDateSelected(
                                        appName,
                                        date,
                                    )
                                },
                                dateValidator = homeViewModel.getDateValidator(card.selectedApp.name),
                                onReleaseVersionSelected = { appName, version ->
                                    homeViewModel.onReleaseVersionSelected(appName, version)
                                },
                                onBuildSelected = { appName, buildId ->
                                    homeViewModel.onNightlyBuildSelected(appName, buildId)
                                },
                                onDismissBuildPicker = { appName ->
                                    homeViewModel.onDismissBuildPicker(appName)
                                },
                            )
                        }
                    }

                    if (tryFoxApp != null) {
                        TryFoxCardComponent(
                            modifier = Modifier.align(Alignment.TopCenter),
                            tryFoxApp = tryFoxApp,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(it) },
                            onInstallClick = homeViewModel::installHomeApk,
                            installStates = installStates,
                            onOpenInstalledApp = homeViewModel::openInstalledApp,
                            onDismiss = { homeViewModel.dismissTryFoxCard() },
                            onTryFoxCardHeightChange = { tryFoxCardHeight = it },
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun TopBarActionIcon(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics {
                this.contentDescription = contentDescription
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
