package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.models.AbiUiModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class) // Needed for AssistChip
@Composable
fun ArtifactCard(
    downloadState: DownloadState,
    abi: AbiUiModel,
    onDownloadClick: () -> Unit,
    onInstallClick: (File) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AbiChip(abi)

                Spacer(modifier = Modifier.width(8.dp))

                DownloadButton(
                    downloadState = downloadState,
                    onDownloadClick = onDownloadClick,
                    onInstallClick = onInstallClick
                )
            }
        }
    }
}
