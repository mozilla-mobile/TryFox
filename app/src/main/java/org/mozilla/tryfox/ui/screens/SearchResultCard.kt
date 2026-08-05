package org.mozilla.tryfox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import org.mozilla.tryfox.util.withoutTrailingReviewerDirective
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
    val commitTitle = remember(push.pushComment) {
        push.pushComment.withoutTrailingReviewerDirective().lineSequence().firstOrNull().orEmpty().trim()
            .ifBlank { "Revision ${push.revision?.take(12).orEmpty()}" }
    }
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
            if (push.unsignedJobs.isNotEmpty()) {
                UnsignedApksSection(push, onDownloadClick, onInstallClick, onOpenClick, installStates, activeInstallKey)
            }
        }
    }
}

@Composable
private fun UnsignedApksSection(
    push: PushUiModel,
    onDownloadClick: (ArtifactUiModel) -> Unit,
    onInstallClick: (ArtifactUiModel) -> Unit,
    onOpenClick: (String) -> Unit,
    installStates: Map<String, InstallState>,
    activeInstallKey: String?,
) {
    var expanded by rememberSaveable(push.revision) { mutableStateOf(false) }
    val stateDescription = stringResource(
        if (expanded) R.string.search_result_unsigned_apks_expanded else R.string.search_result_unsigned_apks_collapsed,
    )

    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .semantics { this.stateDescription = stateDescription }
            .testTag("unsigned_apks_toggle_${push.revision}")
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.search_result_unsigned_apks,
                push.unsignedJobs.size,
                push.unsignedJobs.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.search_result_unsigned_apks_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            push.unsignedJobs.forEach { job ->
                HorizontalDivider()
                CompactApkRow(
                    job = job,
                    onDownloadClick = onDownloadClick,
                    onInstallClick = onInstallClick,
                    onOpenClick = onOpenClick,
                    installStates = installStates,
                    activeInstallKey = activeInstallKey,
                    modifier = Modifier.testTag("unsigned_apk_row_${job.taskId}"),
                )
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
    modifier: Modifier = Modifier,
) {
    val apk = remember(job.artifacts) { job.artifacts.firstOrNull { it.abi.isSupported } }
    val appIconName = remember(job.jobName, job.appName) { appIconNameForJob(job.jobName, job.appName) }
    val installState = apk?.let { installStates[it.uniqueKey] ?: InstallState.Idle }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
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
