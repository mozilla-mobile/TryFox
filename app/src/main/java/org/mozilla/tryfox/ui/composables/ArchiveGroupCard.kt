package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.screens.HomeViewModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER

@Composable
fun ArchiveGroupCard(
    modifier: Modifier = Modifier,
    apks: List<ApkUiModel>,
    homeViewModel: HomeViewModel,
    appState: AppState?,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        val firstApk = apks.firstOrNull()
        if (firstApk == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(id = R.string.archive_group_card_no_apk_details), style = MaterialTheme.typography.bodyMedium)
            }
            return@ElevatedCard
        }

        val date = firstApk.date
        val version = firstApk.version
        val appName = getFriendlyAppName(firstApk.appName)

        Column(modifier = Modifier.padding(16.dp)) {
            CurrentInstallState(
                appState = appState,
                apkDisplayDateString = date,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(appName = "$appName-nightly", modifier = Modifier.size(36.dp))
                Text(
                    text = "$appName $version",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("app_title_text_${appName.lowercase()}"), // Applied testTag
                )
                Spacer(modifier = Modifier.weight(1f))
                if (date.isNotBlank()) {
                    AssistChip(onClick = { /* No action */ }, label = { Text(date) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val (supportedApks, unsupportedApks) = apks.partition { it.abi.isSupported }

            if (supportedApks.isNotEmpty()) {
                ExpandableListView(
                    title = stringResource(id = R.string.archive_group_card_supported_apks, supportedApks.size),
                    initiallyExpanded = true,
                ) {
                    supportedApks.forEach { apk ->
                        ArtifactCard(
                            downloadState = apk.downloadState,
                            abi = apk.abi,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(apk) },
                            onInstallClick = { homeViewModel.onInstallApk?.invoke(it) },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            if (unsupportedApks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (supportedApks.isNotEmpty()) 16.dp else 0.dp))
                ExpandableListView(
                    title = stringResource(id = R.string.archive_group_card_unsupported_apks, unsupportedApks.size),
                    initiallyExpanded = false,
                ) {
                    unsupportedApks.forEach { apk ->
                        ArtifactCard(
                            downloadState = apk.downloadState,
                            abi = apk.abi,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(apk) },
                            onInstallClick = { homeViewModel.onInstallApk?.invoke(it) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            if (supportedApks.isEmpty() && unsupportedApks.isEmpty()) {
                Text(stringResource(id = R.string.archive_group_card_no_apks_for_date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun getFriendlyAppName(appName: String) = when (appName) {
    FENIX -> stringResource(id = R.string.app_name_fenix)
    FOCUS -> stringResource(id = R.string.app_name_focus)
    REFERENCE_BROWSER -> stringResource(R.string.app_name_reference_browser)
    else -> "Unknown"
}
