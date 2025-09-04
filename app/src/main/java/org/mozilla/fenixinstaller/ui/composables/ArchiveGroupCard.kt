package org.mozilla.fenixinstaller.ui.composables

import android.content.Context
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
import androidx.compose.ui.platform.testTag // Added import
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.fenixinstaller.R
import org.mozilla.fenixinstaller.model.AppState
import org.mozilla.fenixinstaller.ui.models.ApkUiModel
import org.mozilla.fenixinstaller.ui.screens.HomeViewModel

@Composable
fun ArchiveGroupCard(
    modifier: Modifier = Modifier,
    apks: List<ApkUiModel>,
    homeViewModel: HomeViewModel,
    context: Context,
    appState: AppState?
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
        val appName = firstApk.appName // appName is "fenix" or "focus"

        Column(modifier = Modifier.padding(16.dp)) {
            CurrentInstallState(
                appState = appState,
                apkDisplayDateString = date
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(appName = "$appName-nightly", modifier = Modifier.size(36.dp))
                Text(
                    text = "$appName $version",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("app_title_text_${appName.lowercase()}") // Applied testTag
                )
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(onClick = { /* No action */ }, label = { Text(date) })
            }
            Spacer(modifier = Modifier.height(16.dp))

            val (supportedApks, unsupportedApks) = apks.partition { it.abi.isSupported }

            if (supportedApks.isNotEmpty()) {
                ExpandableListView(
                    title = stringResource(id = R.string.archive_group_card_supported_apks, supportedApks.size),
                    initiallyExpanded = true
                ) {
                    supportedApks.forEach { apk ->
                        ArtifactCard(
                            downloadState = apk.downloadState,
                            abi = apk.abi,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(apk, context) },
                            onInstallClick = { homeViewModel.onInstallApk?.invoke(it) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            if (unsupportedApks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (supportedApks.isNotEmpty()) 16.dp else 0.dp))
                ExpandableListView(
                    title = stringResource(id = R.string.archive_group_card_unsupported_apks, unsupportedApks.size),
                    initiallyExpanded = false
                ) {
                    unsupportedApks.forEach { apk ->
                        ArtifactCard(
                            downloadState = apk.downloadState,
                            abi = apk.abi,
                            onDownloadClick = { homeViewModel.downloadNightlyApk(apk, context) },
                            onInstallClick = { homeViewModel.onInstallApk?.invoke(it) }
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
