package org.mozilla.fenixinstaller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import org.mozilla.fenixinstaller.R
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.ui.composables.ArchiveGroupCard
import org.mozilla.fenixinstaller.ui.composables.BinButton
import org.mozilla.fenixinstaller.ui.models.ApksState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToTreeherder: () -> Unit,
    onNavigateToProfile: () -> Unit,
    homeViewModel: HomeViewModel = viewModel() // Assuming ViewModel is provided by Hilt or default factory later
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
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = stringResource(id = R.string.home_profile_button_description)
                        )
                    }
                    IconButton(onClick = onNavigateToTreeherder) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(id = R.string.home_search_treeherder_button_description)
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
                        state = tooltipState
                    ) {
                        BinButton(
                            cacheState = currentCacheState,
                            onConfirm = { homeViewModel.clearAppCache() },
                            enabled = binButtonEnabled
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val currentScreenState = screenState) {
            is HomeScreenState.InitialLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(id = R.string.home_loading_initial_data),
                        modifier = Modifier.padding(top = 70.dp) // Adjust as needed to place below indicator
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
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            AppNightlyComponent(
                                state = currentScreenState.fenixBuildsState,
                                homeViewModel = homeViewModel, // Still needed for download/install actions
                            )
                        }
                        item {
                            AppNightlyComponent(
                                state = currentScreenState.focusBuildsState,
                                homeViewModel = homeViewModel,
                            )
                        }
                        item {
                            AppNightlyComponent(
                                state = currentScreenState.referenceBrowserBuildsState,
                                homeViewModel = homeViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppNightlyComponent(
    state: ApksState,
    homeViewModel: HomeViewModel, // Kept for actions like downloadNightlyApk
) {
    when (state) {
        is ApksState.Loading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical=16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(id = R.string.home_fetching_nightly_builds), modifier = Modifier.padding(top = 8.dp))
            }
        }
        is ApksState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = state.message ?: stringResource(id = R.string.common_unknown_error),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is ApksState.Success -> {
            ArchiveGroupCard(
                modifier = Modifier.padding(vertical = 8.dp),
                apks = state.apks,
                appState = state.appState,
                homeViewModel = homeViewModel,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
