package org.mozilla.tryfox.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.ui.composables.AppIcon
import org.mozilla.tryfox.ui.composables.CurrentInstallState
import org.mozilla.tryfox.ui.composables.DownloadButton
import org.mozilla.tryfox.ui.composables.rememberLinkedPushComment
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.models.NightlyBuildOption
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.parseDateToMillis
import org.mozilla.tryfox.util.withoutTrailingReviewerDirective

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAppCard(
    card: HomeAppCardUiModel,
    installStates: Map<String, InstallState>,
    onFlavorSelected: (String) -> Unit,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (ApkUiModel) -> Unit,
    onOpenInstalledApp: (String) -> Unit,
    onOpenTryBuild: (String, String) -> Unit,
    onDateSelected: (String, LocalDate) -> Unit,
    dateValidator: (LocalDate) -> Boolean,
    onReleaseVersionSelected: (String, String) -> Unit,
    onBuildSelected: (String, String) -> Unit,
    onDismissBuildPicker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = card.selectedApp
    val appState = app.toAppState()
    val title = cardTitle(card.family)
    val subtitle = cardSubtitle(card.family, app.name, card.showFlavorSelector)
    val tag = card.stableKey.lowercase()
    val flavorAppNames = card.family.appNames.filter { it in card.appsByName }
    val isNightly = app.name == FENIX || app.name == FOCUS
    val isDebug = app.name == FENIX_DEBUG || app.name == FOCUS_DEBUG
    val isVersionSelectable = app.name in setOf(FENIX_RELEASE, FENIX_BETA, FOCUS_RELEASE, FOCUS_BETA)
    val selectedApk = (app.apks as? ApksResult.Success)?.apks
        ?.let { apks -> apks.firstOrNull { it.abi.isSupported } ?: apks.firstOrNull() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("home_app_card_$tag"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    appName = app.name,
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (appState?.isInstalled == true) {
                                Modifier
                                    .clickable { onOpenInstalledApp(appState.packageName) }
                                    .testTag("home_open_icon_$tag")
                            } else {
                                Modifier
                            },
                        ),
                )
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            CurrentInstallState(
                appState = appState,
                appDisplayName = title,
                modifier = Modifier.testTag("home_install_status_$tag").padding(top = 8.dp),
            )

            if (card.showFlavorSelector && flavorAppNames.size > 1) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        flavorAppNames.forEach { appName ->
                            val isInstalled = card.appsByName[appName]?.installedVersion != null
                            val installedStateDescription = stringResource(R.string.installed_chip_label)
                            FilterChip(
                                selected = appName == app.name,
                                onClick = { onFlavorSelected(appName) },
                                label = { Text(flavorLabel(appName)) },
                                border = BorderStroke(
                                    width = if (isInstalled) 2.dp else 1.dp,
                                    color = if (isInstalled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier
                                    .testTag("home_flavor_${tag}_$appName")
                                    .semantics {
                                        if (isInstalled) stateDescription = installedStateDescription
                                    },
                            )
                        }
                    }
                }
            }

            if (isDebug && app.installedTryBuild != null) {
                Spacer(modifier = Modifier.height(12.dp))
            } else if (!isDebug) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        when {
                            app.apks is ApksResult.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            app.apks is ApksResult.Error -> Text(app.apks.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            selectedApk != null && isNightly -> NightlyDetails(
                                appName = app.name,
                                version = selectedApk.version,
                                date = selectedApk.date,
                                buildDate = selectedApk.buildDate,
                                selectedDate = app.userPickedDate,
                                dateValidator = dateValidator,
                                onDateSelected = onDateSelected,
                            )
                            selectedApk != null && isVersionSelectable -> ReleaseVersionDetails(
                                appName = app.name,
                                selectedVersion = app.selectedReleaseVersion ?: selectedApk.version,
                                versions = app.availableReleaseVersions,
                                onSelected = onReleaseVersionSelected,
                            )
                            selectedApk != null -> Text(selectedApk.version, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            else -> Text(stringResource(R.string.home_no_apks_available), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (selectedApk != null || app.apks is ApksResult.Loading) {
                        Spacer(Modifier.width(12.dp))
                        DownloadButton(
                            downloadState = selectedApk?.downloadState ?: DownloadState.NotDownloaded,
                            onDownloadClick = { selectedApk?.let(onDownloadClick) },
                            onInstallClick = { selectedApk?.let(onInstallClick) },
                            installState = selectedApk?.let { installStates[it.uniqueKey] } ?: InstallState.Idle,
                            installDisabled = selectedApk == null,
                            onOpenClick = onOpenInstalledApp,
                            debugLabel = "home-card:${selectedApk?.uniqueKey ?: app.name}",
                            modifier = Modifier
                                .testTag("home_primary_action_$tag")
                                .semantics { contentDescription = "Download $title" },
                        )
                    }
                }
            }

            app.installedTryBuild?.let { build ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onOpenTryBuild(build.project, build.revision) }
                        .semantics { contentDescription = "Open Try build revision ${build.revision}" }
                        .testTag("home_try_build_revision")
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.home_try_build_revision_label,
                            build.revision.take(SHORT_REVISION_LENGTH),
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val commitTitle = build.commitMessage.withoutTrailingReviewerDirective()
                        .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    Text(
                        text = rememberLinkedPushComment(commitTitle),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (app.pendingBuildOptions.isNotEmpty()) {
        NightlyBuildPickerDialog(
            options = app.pendingBuildOptions,
            onSelect = { onBuildSelected(app.name, it) },
            onDismiss = { onDismissBuildPicker(app.name) },
        )
    }
}

private const val SHORT_REVISION_LENGTH = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightlyDetails(
    appName: String,
    version: String,
    date: String,
    buildDate: LocalDate?,
    selectedDate: LocalDate?,
    dateValidator: (LocalDate) -> Boolean,
    onDateSelected: (String, LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Text(version, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    AssistChip(
        onClick = { showPicker = true },
        label = { Text(date.ifBlank { selectedDate?.toString().orEmpty() }) },
        leadingIcon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp)) },
        modifier = Modifier.testTag("home_nightly_date_$appName"),
    )
    if (showPicker) {
        val initialDate = selectedDate ?: buildDate
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.toDatePickerSelectionMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = dateValidator(Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onDateSelected(appName, datePickerSelectionDate(it)) }; showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Material DatePicker represents a selected calendar day as midnight UTC. */
internal fun LocalDate.toDatePickerSelectionMillis(): Long =
    atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun datePickerSelectionDate(selectionMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(selectionMillis).toLocalDateTime(TimeZone.UTC).date

@Composable
private fun ReleaseVersionDetails(appName: String, selectedVersion: String, versions: List<String>, onSelected: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable(enabled = versions.isNotEmpty()) { expanded = true }
                .testTag("home_release_version_$appName").padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selectedVersion, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select version")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEach { version -> DropdownMenuItem(text = { Text(version) }, onClick = { expanded = false; onSelected(appName, version) }) }
        }
    }
}

private fun AppUiModel.toAppState(): AppState? = installedVersion?.let { version ->
    AppState(
        name = name,
        packageName = packageName,
        version = version,
        installDateMillis = installedDate?.let(::parseDateToMillis),
        installingPackageName = installingPackageName,
        versionCode = installedVersionCode,
        splitNames = splitNames,
    )
}

@Composable
private fun NightlyBuildPickerDialog(
    options: List<NightlyBuildOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_select_build)) },
        text = {
            Column {
                options.forEach { option ->
                    Text(
                        option.label,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option.id) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable private fun cardTitle(family: HomeAppFamily): String = when (family) {
    HomeAppFamily.Fenix -> stringResource(R.string.home_app_title_fenix)
    HomeAppFamily.Focus -> stringResource(R.string.home_app_title_focus)
    HomeAppFamily.ReferenceBrowser -> stringResource(R.string.home_app_title_reference_browser)
}

@Composable private fun cardSubtitle(
    family: HomeAppFamily,
    appName: String,
    showFlavorSelector: Boolean,
): String = when {
    !showFlavorSelector && family != HomeAppFamily.ReferenceBrowser -> flavorLabel(appName)
    else -> cardFamilySubtitle(family)
}

@Composable private fun cardFamilySubtitle(family: HomeAppFamily): String = when (family) {
    HomeAppFamily.Fenix -> stringResource(R.string.home_app_subtitle_fenix)
    HomeAppFamily.Focus -> stringResource(R.string.home_app_subtitle_focus)
    HomeAppFamily.ReferenceBrowser -> stringResource(R.string.home_app_subtitle_reference_browser)
}

private fun flavorLabel(appName: String): String = when (appName) {
    FENIX, FOCUS -> "Nightly"
    FENIX_BETA, FOCUS_BETA -> "Beta"
    FENIX_DEBUG, FOCUS_DEBUG -> "Debug"
    else -> "Release"
}
