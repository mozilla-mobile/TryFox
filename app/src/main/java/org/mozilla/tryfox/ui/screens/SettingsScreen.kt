package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.HomeScreenLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_message)) },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.clearCache()
                    showClearConfirmation = false
                }) { Text(stringResource(R.string.settings_clear_cache_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.settings_clear_cache_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back_button_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CacheSettingsCard(
                uiState = uiState,
                onClearCache = { showClearConfirmation = true },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            HomeLayoutSettingsCard(
                selectedLayout = uiState.homeScreenLayout,
                onLayoutSelected = settingsViewModel::selectHomeScreenLayout,
            )
        }
    }
}

@Composable
private fun CacheSettingsCard(uiState: SettingsUiState, onClearCache: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(R.string.settings_cache_section_title)
        PreferenceGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_cache_size_label)) },
                supportingContent = { Text(stringResource(R.string.settings_cache_description)) },
                trailingContent = {
                    Text(
                        text = formatCacheSize(uiState.cacheSizeBytes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
        if (uiState.cacheState == CacheManagementState.Clearing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.settings_cache_clearing))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onClearCache, enabled = uiState.canClearCache) {
                    Text(stringResource(R.string.settings_clear_cache_button))
                }
            }
        }
    }
}

@Composable
private fun HomeLayoutSettingsCard(
    selectedLayout: HomeScreenLayout,
    onLayoutSelected: (HomeScreenLayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(R.string.settings_home_layout_section_title)
        Text(
            text = stringResource(R.string.settings_home_layout_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PreferenceGroup {
            LayoutOption(
                layout = HomeScreenLayout.OneCardPerApp,
                selectedLayout = selectedLayout,
                label = stringResource(R.string.settings_layout_one_card_per_app),
                onSelected = onLayoutSelected,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            LayoutOption(
                layout = HomeScreenLayout.OneCardPerFlavor,
                selectedLayout = selectedLayout,
                label = stringResource(R.string.settings_layout_one_card_per_flavor),
                onSelected = onLayoutSelected,
            )
        }
    }
}

@Composable
private fun PreferenceGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionTitle(stringId: Int) {
    Text(
        stringResource(stringId),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun LayoutOption(
    layout: HomeScreenLayout,
    selectedLayout: HomeScreenLayout,
    label: String,
    onSelected: (HomeScreenLayout) -> Unit,
) {
    val selected = layout == selectedLayout
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) { onSelected(layout) },
    )
}

internal fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
