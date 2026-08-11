package org.mozilla.tryfox.ui.composables

import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.mozilla.tryfox.R
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.NightlyBuildOption
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.FeatureFlags
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.parseDateToLocalDate

private object ArchiveGroupCardTokens {
    val CardPaddingTop = 4.dp
    val CardElevation = 6.dp
    val ColumnPadding = 16.dp
    val AppIconSize = 36.dp
    val SpacerHeight = 16.dp
    val NoApksPaddingTop = 8.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveGroupCard(
    modifier: Modifier = Modifier,
    apks: List<ApkUiModel>,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (ApkUiModel) -> Unit,
    onOpenAppClick: () -> Unit,
    onUninstallClick: () -> Unit,
    appState: AppState?,
    onDateSelected: (LocalDate) -> Unit,
    userPickedDate: LocalDate?,
    selectedReleaseVersion: String?,
    availableReleaseVersions: List<String>,
    appName: String,
    errorMessage: String?,
    isLoading: Boolean,
    dateValidator: (LocalDate) -> Boolean,
    onClearDate: () -> Unit,
    onReleaseVersionSelected: (String) -> Unit,
    pendingBuildOptions: List<NightlyBuildOption> = emptyList(),
    onBuildSelected: (String) -> Unit = {},
    onDismissBuildPicker: () -> Unit = {},
    installStates: Map<String, InstallState> = emptyMap(),
    onOpenInstalledApp: (String) -> Unit = {},
) {
    if (pendingBuildOptions.isNotEmpty()) {
        NightlyBuildPickerDialog(
            options = pendingBuildOptions,
            onSelect = onBuildSelected,
            onDismiss = onDismissBuildPicker,
        )
    }
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = ArchiveGroupCardTokens.CardPaddingTop),
        elevation = CardDefaults.cardElevation(defaultElevation = ArchiveGroupCardTokens.CardElevation),
    ) {
        val firstApk = apks.firstOrNull()
        val version = firstApk?.version ?: ""
        val dateFromApk = firstApk?.date ?: ""
        // Release, beta and Focus release all pick a specific version from a dropdown.
        val hasReleaseVersionPicker =
            appName == FENIX_RELEASE || appName == FOCUS_RELEASE || appName == FENIX_BETA || appName == FOCUS_BETA
        val isDatePickerEnabled = appName != REFERENCE_BROWSER && !hasReleaseVersionPicker

        Column(modifier = Modifier.padding(ArchiveGroupCardTokens.ColumnPadding)) {
            CurrentInstallState(
                appState = appState,
                appDisplayName = getFriendlyAppName(appName),
            )

            ArchiveGroupHeader(
                appName = appName,
                version = version,
                date = dateFromApk,
                onDateSelected = onDateSelected,
                userPickedDate = userPickedDate,
                selectedReleaseVersion = selectedReleaseVersion,
                availableReleaseVersions = availableReleaseVersions,
                hasReleaseVersionPicker = hasReleaseVersionPicker,
                isDatePickerEnabled = isDatePickerEnabled,
                dateValidator = dateValidator,
                onClearDate = onClearDate,
                onOpenAppClick = onOpenAppClick,
                onReleaseVersionSelected = onReleaseVersionSelected,
            )
            Spacer(modifier = Modifier.height(ArchiveGroupCardTokens.SpacerHeight))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = ArchiveGroupCardTokens.NoApksPaddingTop),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                apks.isNotEmpty() -> {
                    ArchiveGroupAbiSelector(
                        apks,
                        onDownloadClick,
                        onInstallClick,
                        onUninstallClick,
                        appState,
                        installStates,
                        onOpenInstalledApp,
                    )
                }

                else -> {
                    Text(
                        stringResource(
                            id = if (hasReleaseVersionPicker) {
                                R.string.archive_group_card_no_apks_for_release_version
                            } else {
                                R.string.archive_group_card_no_apks_for_date
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = ArchiveGroupCardTokens.NoApksPaddingTop),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveGroupHeader(
    appName: String,
    version: String,
    date: String,
    onDateSelected: (LocalDate) -> Unit,
    onOpenAppClick: () -> Unit,
    userPickedDate: LocalDate?,
    selectedReleaseVersion: String?,
    availableReleaseVersions: List<String>,
    hasReleaseVersionPicker: Boolean,
    isDatePickerEnabled: Boolean,
    dateValidator: (LocalDate) -> Boolean,
    onClearDate: () -> Unit,
    onReleaseVersionSelected: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    // Prefer the loaded build's full timestamp; fall back to the picked date while it loads.
    val displayDate = date.ifBlank { userPickedDate?.toString() ?: "" }
    val friendlyAppName = getFriendlyAppName(appName)
    val showsReleaseVersionPicker = hasReleaseVersionPicker

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("app_header_row_${appName.lowercase()}"),
        ) {
            AppIcon(
                appName = appName,
                modifier = Modifier
                    .size(ArchiveGroupCardTokens.AppIconSize)
                    .clickable { onOpenAppClick() },
            )
            if (showsReleaseVersionPicker) {
                Text(
                    text = friendlyAppName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("app_title_text_${appName.lowercase()}"),
                )
                // Push the version selector to the right edge of the row.
                Spacer(modifier = Modifier.weight(1f))
                VersionSelector(
                    appName = appName,
                    selectedReleaseVersion = selectedReleaseVersion ?: version.takeIf { it.isNotEmpty() },
                    availableReleaseVersions = availableReleaseVersions,
                    onReleaseVersionSelected = onReleaseVersionSelected,
                )
            } else {
                // Fenix/Focus nightlies are identified by name + the timestamp chip below, so the
                // build version is redundant on the title. Other cards still show it.
                val title = if (appName == FENIX || appName == FOCUS) {
                    friendlyAppName
                } else {
                    "$friendlyAppName $version"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("app_title_text_${appName.lowercase()}"),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (!showsReleaseVersionPicker && displayDate.isNotBlank()) {
            val chipColors = if (userPickedDate != null) {
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                )
            } else {
                AssistChipDefaults.assistChipColors()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AssistChip(
                    onClick = { if (isDatePickerEnabled) showDatePicker = true },
                    label = { Text(displayDate) },
                    colors = chipColors,
                    trailingIcon = {
                        if (userPickedDate != null) {
                            IconButton(
                                onClick = onClearDate,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_date_selection),
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("app_date_chip_${appName.lowercase()}"),
                )
            }
        }
    }

    if (showDatePicker) {
        val initialDate = userPickedDate ?: parseDateToLocalDate(date) ?: Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val localDate = Instant.fromEpochMilliseconds(utcTimeMillis)
                        .toLocalDateTime(TimeZone.UTC).date
                    return dateValidator(localDate)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let {
                            onDateSelected(
                                Instant.fromEpochMilliseconds(it)
                                    .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                            )
                        }
                    },
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
internal fun VersionSelector(
    appName: String,
    selectedReleaseVersion: String?,
    availableReleaseVersions: List<String>,
    onReleaseVersionSelected: (String) -> Unit,
) {
    var showSelector by remember { mutableStateOf(false) }
    val selectedVersion = selectedReleaseVersion ?: availableReleaseVersions.firstOrNull()

    Surface(
        modifier = Modifier
            .clickable(enabled = availableReleaseVersions.isNotEmpty()) { showSelector = true }
            .semantics {
                contentDescription = "Selected version ${selectedVersion ?: ""}"
            }
            .testTag("release_version_chip_${appName.lowercase()}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
    ) {
        Text(
            text = selectedVersion ?: "--",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    if (showSelector) {
        VersionSelectorSheet(
            appName = appName,
            selectedVersion = selectedVersion,
            availableVersions = availableReleaseVersions,
            onDismiss = { showSelector = false },
            onConfirm = { version ->
                showSelector = false
                onReleaseVersionSelected(version)
            },
        )
    }
}

private const val MINIMUM_SUPPORTED_MAJOR_VERSION = 117
private const val MAXIMUM_SUPPORTED_MAJOR_VERSION = 154

private fun versionMajor(version: String): Int? =
    Regex("^(\\d+)(?:\\.|$)").find(version)?.groupValues?.getOrNull(1)?.toIntOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSelectorSheet(
    appName: String,
    selectedVersion: String?,
    availableVersions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val versionsByMajor = remember(availableVersions) {
        availableVersions.mapNotNull { version -> versionMajor(version)?.let { it to version } }.groupBy({ it.first }, { it.second })
    }
    val initialMajor = versionMajor(selectedVersion.orEmpty())
        ?: versionsByMajor.keys.maxOrNull()
        ?: MINIMUM_SUPPORTED_MAJOR_VERSION
    var activeMajor by remember(selectedVersion, availableVersions) { mutableStateOf(initialMajor) }
    var draftVersion by remember(selectedVersion, availableVersions) { mutableStateOf(selectedVersion) }
    val isActiveMajorSelectable = activeMajor in MINIMUM_SUPPORTED_MAJOR_VERSION..MAXIMUM_SUPPORTED_MAJOR_VERSION
    val variants = if (isActiveMajorSelectable) versionsByMajor[activeMajor].orEmpty() else emptyList()
    val title = if (appName == FENIX_BETA || appName == FOCUS_BETA) {
        stringResource(R.string.version_selector_beta_title)
    } else {
        stringResource(R.string.version_selector_release_title)
    }
    val pickerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()

    fun selectMajor(major: Int) {
        activeMajor = major
        draftVersion = draftVersion?.takeIf { versionMajor(it) == major }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("release_version_selector_sheet_${appName.lowercase()}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.version_selector_helper),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.version_selector_major_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                AndroidView(
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = MINIMUM_SUPPORTED_MAJOR_VERSION
                            maxValue = MAXIMUM_SUPPORTED_MAJOR_VERSION
                            value = activeMajor.coerceIn(minValue, maxValue)
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                            setOnValueChangedListener { _, _, newValue -> selectMajor(newValue) }
                            post {
                                setSelectorTextColor(pickerTextColor)
                                editableInput()?.apply {
                                    inputType = InputType.TYPE_CLASS_NUMBER
                                    setSelectAllOnFocus(true)
                                    addTextChangedListener(object : TextWatcher {
                                        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                                        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

                                        override fun afterTextChanged(text: Editable?) {
                                            text?.toString()?.toIntOrNull()?.takeIf {
                                                it in MINIMUM_SUPPORTED_MAJOR_VERSION..MAXIMUM_SUPPORTED_MAJOR_VERSION
                                            }?.let(::selectMajor)
                                        }
                                    })
                                }
                            }
                        }
                    },
                    update = { picker ->
                        if (picker.value != activeMajor) picker.value = activeMajor
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(start = 16.dp)
                        .testTag("release_version_major_picker_${appName.lowercase()}"),
                )
            }
            Text(
                text = stringResource(R.string.version_selector_available_builds),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            if (variants.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = if (isActiveMajorSelectable) {
                            stringResource(R.string.version_selector_no_builds)
                        } else {
                            stringResource(R.string.version_selector_current_major_unavailable, activeMajor)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(variants, key = { it }) { version ->
                        val selected = draftVersion == version
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { draftVersion = version }
                                .semantics { this.selected = selected }
                                .testTag("release_version_variant_${version.replace('.', '_')}")
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(selected = selected, onClick = { draftVersion = version })
                            Text(
                                text = version,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.cancel)) }
                Button(
                    onClick = { draftVersion?.let(onConfirm) },
                    enabled = isActiveMajorSelectable && draftVersion != null && variants.contains(draftVersion),
                ) { Text(stringResource(R.string.version_selector_confirm)) }
            }
        }
    }
}

private fun NumberPicker.editableInput(): EditText? {
    val inputId = resources.getIdentifier("numberpicker_input", "id", "android")
    return inputId.takeIf { it != 0 }?.let { findViewById(it) as? EditText }
}

private fun NumberPicker.setSelectorTextColor(color: Int) {
    editableInput()?.setTextColor(color)
    if (Build.VERSION.SDK_INT >= 36) {
        setTextColor(color)
    }
}

@Suppress("KotlinConstantConditions")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveGroupAbiSelector(
    apks: List<ApkUiModel>,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (ApkUiModel) -> Unit,
    onUninstallClick: () -> Unit,
    appState: AppState?,
    installStates: Map<String, InstallState>,
    onOpenInstalledApp: (String) -> Unit,
) {
    val firstSupportedIndex = apks.indexOfFirst { it.abi.isSupported }.takeIf { it != -1 } ?: 0
    var selectedIndex by remember { mutableStateOf(firstSupportedIndex) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (FeatureFlags.SHOW_ABI_SELECTOR) {
            SingleChoiceSegmentedButtonRow {
                apks.forEachIndexed { index, apk ->
                    val colors =
                        if (!apk.abi.isSupported) {
                            SegmentedButtonDefaults.colors(
                                inactiveContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                activeContainerColor = MaterialTheme.colorScheme.error,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                activeContentColor = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            SegmentedButtonDefaults.colors()
                        }
                    SegmentedButton(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = apks.size),
                        colors = colors,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!apk.abi.isSupported) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = stringResource(R.string.unsupported_abi),
                                    modifier = Modifier
                                        .size(ButtonDefaults.IconSize)
                                        .padding(end = 4.dp),
                                )
                            }
                            Text(
                                text = apk.abi.name ?: "",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(ArchiveGroupCardTokens.SpacerHeight))
        }

        val selectedApk = apks[selectedIndex]
        val installState = installStates[selectedApk.uniqueKey] ?: InstallState.Idle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (appState?.isInstalled == true) {
                Button(
                    onClick = onUninstallClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(text = stringResource(id = R.string.uninstall_button_label))
                }
            }

            DownloadButton(
                downloadState = selectedApk.downloadState,
                onDownloadClick = { onDownloadClick(selectedApk) },
                onInstallClick = { onInstallClick(selectedApk) },
                installState = installState,
                onOpenClick = onOpenInstalledApp,
                debugLabel = "home:${selectedApk.uniqueKey}",
            )
        }
        (installState as? InstallState.Failed)?.let { failure ->
            Text(
                text = failure.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * One-shot prompt shown right after picking a Nightly date that has more than one build. Choosing a
 * build applies it; dismissing keeps the latest build (already shown). To change later, the user
 * re-opens the date picker, which re-triggers this prompt.
 */
@Composable
private fun NightlyBuildPickerDialog(
    options: List<NightlyBuildOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.nightly_build_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun getFriendlyAppName(appName: String): String =
    when (appName) {
        FENIX -> stringResource(id = R.string.app_name_fenix)
        FENIX_RELEASE -> stringResource(R.string.app_name_fenix_release)
        FENIX_BETA -> stringResource(R.string.app_name_fenix_beta)
        FOCUS -> stringResource(id = R.string.app_name_focus)
        FOCUS_BETA -> stringResource(id = R.string.app_name_focus_beta)
        FOCUS_RELEASE -> stringResource(id = R.string.app_name_focus_release)
        REFERENCE_BROWSER -> stringResource(R.string.app_name_reference_browser)
        else -> appName
    }
