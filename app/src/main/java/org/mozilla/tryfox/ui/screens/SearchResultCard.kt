package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.ui.composables.AppIcon
import org.mozilla.tryfox.ui.composables.DownloadButton
import org.mozilla.tryfox.ui.composables.rememberLinkedPushComment
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.ui.models.PushUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_NIGHTLY
import org.mozilla.tryfox.util.FOCUS_RELEASE
import java.util.Locale

@Suppress("LongParameterList")
@Composable
internal fun PushResultCard(
    push: PushUiModel,
    onDownloadClick: (ArtifactUiModel) -> Unit,
    onInstallClick: (ArtifactUiModel) -> Unit,
    onOpenClick: (String) -> Unit,
    installStates: Map<String, InstallState>,
    activeInstallKey: String?,
    testTag: String,
) {
    val commitTitle = remember(push.pushComment) { push.pushComment.lineSequence().firstOrNull().orEmpty().trim().ifBlank { "Revision ${push.revision?.take(12).orEmpty()}" } }
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(rememberLinkedPushComment(commitTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (push.pushTimestamp > 0L || push.author.isNotBlank()) {
                Text(listOfNotNull(formatRelativePushTime(push.pushTimestamp).takeIf { push.pushTimestamp > 0L }, push.author.takeIf(String::isNotBlank)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
            push.jobs.forEachIndexed { index, job ->
                if (index > 0) HorizontalDivider()
                CompactApkRow(job, onDownloadClick, onInstallClick, onOpenClick, installStates, activeInstallKey)
            }
        }
    }
}

@Composable
private fun CompactApkRow(
    job: JobDetailsUiModel,
    onDownloadClick: (ArtifactUiModel) -> Unit,
    onInstallClick: (ArtifactUiModel) -> Unit,
    onOpenClick: (String) -> Unit,
    installStates: Map<String, InstallState>,
    activeInstallKey: String?,
) {
    val apk = remember(job.artifacts) { job.artifacts.firstOrNull { it.abi.isSupported } }
    val appIconName = remember(job.jobName, job.appName) { appIconNameForJob(job.jobName, job.appName) }
    val installState = apk?.let { installStates[it.uniqueKey] ?: InstallState.Idle }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(appName = appIconName, modifier = Modifier.size(34.dp), useSearchResultVariant = true)
            Text(job.jobName.ifBlank { formatAppNameForDisplay(job.appName) }.let(::formatJobNameForDisplay), style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            apk?.let { artifact ->
                DownloadButton(
                    downloadState = artifact.downloadState,
                    onDownloadClick = { onDownloadClick(artifact) },
                    onInstallClick = { onInstallClick(artifact) },
                    modifier = Modifier.width(112.dp),
                    inProgressText = stringResource(id = R.string.download_button_download),
                    installState = installState ?: InstallState.Idle,
                    installDisabled = activeInstallKey != null && activeInstallKey != artifact.uniqueKey,
                    onOpenClick = onOpenClick,
                )
            }
        }
        (installState as? InstallState.Failed)?.let { failure ->
            Text(
                text = failure.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 42.dp, top = 6.dp),
            )
        }
    }
}

private fun formatAppNameForDisplay(appName: String): String = when (appName.lowercase(Locale.getDefault())) {
    FENIX_NIGHTLY -> "Fenix Nightly"; FENIX -> "Fenix"; FOCUS -> "Focus Nightly"; FOCUS_RELEASE -> "Focus Release"
    else -> appName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private val signingApkJobNamePattern = Regex("signing-apk-(fenix|focus)-(debug|nightly|beta|release)(-(firebase|simulation))?", RegexOption.IGNORE_CASE)
internal fun formatJobNameForDisplay(jobName: String): String {
    val match = signingApkJobNamePattern.matchEntire(jobName.trim()) ?: return jobName
    val appName = when (match.groupValues[1].lowercase(Locale.ROOT)) { FENIX -> "Fenix"; FOCUS -> "Focus"; else -> return jobName }
    val suffix = when (match.groupValues[4].lowercase(Locale.ROOT)) { "firebase" -> " (firebase)"; "simulation" -> " (perftests)"; else -> "" }
    return "$appName ${match.groupValues[2].lowercase(Locale.ROOT)}$suffix"
}
internal fun appIconNameForJob(jobName: String, fallbackAppName: String): String = when {
    "focus-debug" in jobName.lowercase(Locale.ROOT) -> FOCUS; "focus-nightly" in jobName.lowercase(Locale.ROOT) -> FOCUS_NIGHTLY; "focus-beta" in jobName.lowercase(Locale.ROOT) -> FOCUS_BETA; "focus" in jobName.lowercase(Locale.ROOT) -> FOCUS
    "fenix-debug" in jobName.lowercase(Locale.ROOT) -> FENIX; "fenix-nightly" in jobName.lowercase(Locale.ROOT) -> FENIX_NIGHTLY; "fenix-release" in jobName.lowercase(Locale.ROOT) -> FENIX_RELEASE; "fenix-beta" in jobName.lowercase(Locale.ROOT) -> FENIX_BETA
    else -> fallbackAppName
}
