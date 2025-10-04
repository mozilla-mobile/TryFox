package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToTreeherder: () -> Unit,
    onNavigateToProfile: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val screenState by homeViewModel.homeScreenState.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.initialLoad()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val loadedState = screenState as? HomeScreenState.Loaded
            val currentCacheState = loadedState?.cacheManagementState ?: CacheManagementState.IdleEmpty
            val isDownloading = loadedState?.isDownloadingAnyFile ?: false
            val binButtonEnabled = !isDownloading && currentCacheState == CacheManagementState.IdleNonEmpty

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
                            contentDescription = stringResource(id = R.string.home_search_treeherder_button_description),
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
        when (val currentScreenState = screenState) {
            is HomeScreenState.InitialLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(id = R.string.home_loading_initial_data),
                        modifier = Modifier.padding(top = 70.dp), // Adjust as needed to place below indicator
                    )
                }
            }
            is HomeScreenState.Loaded -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(currentScreenState.apps.values.toList()) { app ->
                            AppComponent(
                                app = app,
                                onDownloadClick = { homeViewModel.downloadNightlyApk(it) },
                                onInstallClick = { homeViewModel.onInstallApk?.invoke(it) },
                                onDateSelected = { appName, date -> homeViewModel.onDateSelected(appName, date) },
                                dateValidator = homeViewModel.getDateValidator(app.name),
                                onClearDate = { appName -> homeViewModel.onClearDate(appName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppComponent(
    app: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (File) -> Unit,
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
