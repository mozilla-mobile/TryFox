package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.theme.customColors
import java.io.File

@Composable
fun TryFoxCard(
    modifier: Modifier = Modifier,
    app: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (File) -> Unit,
) {
    if (app.apks !is ApksResult.Success) return
    val apksResult = app.apks

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.customColors.tryFoxCardBackground,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (apksResult) {
                is ApksResult.Loading -> {
                    CircularProgressIndicator()
                }

                is ApksResult.Error -> {
                    Text(text = apksResult.message)
                }

                is ApksResult.Success -> {
                    val latestApk = apksResult.apks.firstOrNull() ?: return@Row

                    Column {
                        Text(
                            text = stringResource(id = R.string.tryfox_card_title, latestApk.version),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    DownloadButton(
                        downloadState = latestApk.downloadState,
                        onDownloadClick = { onDownloadClick(latestApk) },
                        onInstallClick = {
                            val downloadedFile = (latestApk.downloadState as? DownloadState.Downloaded)?.file
                            downloadedFile?.let { onInstallClick(it) }
                        },
                    )
                }
            }
        }
    }
}
