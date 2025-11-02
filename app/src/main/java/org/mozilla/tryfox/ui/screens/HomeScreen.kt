package org.mozilla.tryfox.ui.screens

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.ArchiveGroupCard
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.util.parseDateToMillis
import java.io.File

/**
 * Composable function for the Home screen, which displays a list of available apps and allows users to interact with them.
 *
 * @param modifier The modifier to be applied to the component.
 * @param onNavigateToTreeherder Callback to navigate to the Treeherder search screen.
 * @param onNavigateToProfile Callback to navigate to the Profile screen.
 * @param homeViewModel The ViewModel for the Home screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToTreeherder: () -> Unit,
    onNavigateToProfile: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val screenState by homeViewModel.homeScreenState.collectAsState()
    val isRefreshing by homeViewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, { homeViewModel.refreshData() })

    LaunchedEffect(Unit) {
        homeViewModel.initialLoad()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val loadedState = screenState as? HomeScreenState.Loaded
            val currentCacheState =
                loadedState?.cacheManagementState ?: CacheManagementState.IdleEmpty
            val isDownloading = loadedState?.isDownloadingAnyFile ?: false
            val binButtonEnabled =
                !isDownloading && currentCacheState == CacheManagementState.IdleNonEmpty

            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = stringResource(id = R.string.home_profile_button_description),
                        )
                    }
                    IconButton(onClick = onNavigateToTreeherder) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(
                                id = R.string.home_search_treeherder_button_description,
                            ),
                        )
                    }
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.bin_button_tooltip_clear_downloaded_apks))
                            }
                        },
                        state = tooltipState,
                    ) {
                        BinButton(
                            cacheState = currentCacheState,
                            onConfirm = { homeViewModel.clearAppCache() },
                            enabled = binButtonEnabled,
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
                    val otherApps = currentScreenState.apps.values.toList()

                    val targetSpacerHeight = if (tryFoxApp != null) tryFoxCardHeight + 4.dp else 0.dp
                    val animatedSpacerHeight by animateDpAsState(targetValue = targetSpacerHeight, label = "tryFoxSpacerHeight")

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(animatedSpacerHeight))
                        }

                        items(otherApps) { app ->
                            AppComponent(
                                app = app,
                                onDownloadClick = { homeViewModel.downloadNightlyApk(it) },
                                onInstallClick = { homeViewModel.installApk(it) },
                                onOpenAppClick = { homeViewModel.openApp(it) },
                                onUninstallClick = { homeViewModel.uninstallApp(it) },
                                onDateSelected = { appName, date ->
                                    homeViewModel.onDateSelected(
                                        appName,
                                        date,
                                    )
                                },
                                dateValidator = homeViewModel.getDateValidator(app.name),
                                onClearDate = { appName -> homeViewModel.onClearDate(appName) },
                            )
                        }
                    }

                    if (tryFoxApp != null) {
                        TryFoxCardComponent(
                            modifier = Modifier.align(Alignment.TopCenter),
                            tryFoxApp = tryFoxApp,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(it) },
                            onInstallClick = { homeViewModel.installApk(it) },
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

/**
 * Composable function for displaying a single app component, which includes information about the app and actions that can be performed.
 *
 * @param app The UI model for the app.
 * @param onDownloadClick Callback for when the download button is clicked.
 * @param onInstallClick Callback for when the install button is clicked.
 * @param onOpenAppClick Callback for when the open app button is clicked.
 * @param onDateSelected Callback for when a date is selected in the date picker.
 * @param dateValidator A function to validate the selectable dates in the date picker.
 * @param onClearDate Callback for when the selected date is cleared.
 */
@Composable
fun AppComponent(
    app: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (File) -> Unit,
    onOpenAppClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onDateSelected: (String, LocalDate) -> Unit,
    dateValidator: (LocalDate) -> Boolean,
    onClearDate: (String) -> Unit,
) {
    val apksResult = app.apks

    val appState = if (app.installedVersion != null) {
        AppState(
            name = app.name,
            packageName = app.packageName,
            version = app.installedVersion,
            installDateMillis = app.installedDate?.let { parseDateToMillis(it) },
        )
    } else {
        null
    }

    ArchiveGroupCard(
        modifier = Modifier.padding(vertical = 8.dp),
        apks = (apksResult as? ApksResult.Success)?.apks ?: emptyList(),
        appState = appState,
        onDownloadClick = onDownloadClick,
        onInstallClick = onInstallClick,
        onOpenAppClick = {
            appState?.packageName?.let {
                onOpenAppClick(it)
            }
        },
        onUninstallClick = {
            appState?.packageName?.let {
                onUninstallClick(it)
            }
        },
        onDateSelected = { date -> onDateSelected(app.name, date) },
        userPickedDate = app.userPickedDate,
        appName = app.name,
        errorMessage = (apksResult as? ApksResult.Error)?.message,
        isLoading = apksResult is ApksResult.Loading,
        dateValidator = dateValidator,
        onClearDate = { onClearDate(app.name) },
    )
    Spacer(modifier = Modifier.height(16.dp))
}
