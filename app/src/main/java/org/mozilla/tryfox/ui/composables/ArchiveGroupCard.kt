package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.parseDateToLocalDate
import java.io.File

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
    onInstallClick: (File) -> Unit,
    onOpenAppClick: () -> Unit,
    onUninstallClick: () -> Unit,
    appState: AppState?,
    onDateSelected: (LocalDate) -> Unit,
    userPickedDate: LocalDate?,
    appName: String,
    errorMessage: String?,
    isLoading: Boolean,
    dateValidator: (LocalDate) -> Boolean,
    onClearDate: () -> Unit,
) {
    ElevatedCard(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(top = ArchiveGroupCardTokens.CardPaddingTop),
        elevation = CardDefaults.cardElevation(defaultElevation = ArchiveGroupCardTokens.CardElevation),
    ) {
        val firstApk = apks.firstOrNull()
        val friendlyAppName = getFriendlyAppName(appName)
        val version = firstApk?.version ?: ""
        val dateFromApk = firstApk?.date ?: ""
        val isDatePickerEnabled = appName != REFERENCE_BROWSER

        Column(modifier = Modifier.padding(ArchiveGroupCardTokens.ColumnPadding)) {
            CurrentInstallState(
                appState = appState,
            )

            ArchiveGroupHeader(
                appName = friendlyAppName,
                version = version,
                date = dateFromApk,
                onDateSelected = onDateSelected,
                userPickedDate = userPickedDate,
                isDatePickerEnabled = isDatePickerEnabled,
                dateValidator = dateValidator,
                onClearDate = onClearDate,
                onOpenAppClick = onOpenAppClick,
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
                    ArchiveGroupAbiSelector(apks, onDownloadClick, onInstallClick, onUninstallClick, appState)
                }
                else -> {
                    Text(
                        stringResource(id = R.string.archive_group_card_no_apks_for_date),
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
    isDatePickerEnabled: Boolean,
    dateValidator: (LocalDate) -> Boolean,
    onClearDate: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val displayDate = userPickedDate?.toString() ?: date

    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(
            appName = "$appName-nightly",
            modifier = Modifier.size(ArchiveGroupCardTokens.AppIconSize)
                .clickable { onOpenAppClick() },
        )
        Text(
            text = "$appName $version",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("app_title_text_${appName.lowercase()}"),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (displayDate.isNotBlank()) {
            val chipColors = if (userPickedDate != null) {
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                )
            } else {
                AssistChipDefaults.assistChipColors()
            }

            AssistChip(
                onClick = { if (isDatePickerEnabled) showDatePicker = true },
                label = { Text(displayDate) },
                colors = chipColors,
                trailingIcon = {
                    if (userPickedDate != null) {
                        IconButton(onClick = onClearDate, modifier = Modifier.size(AssistChipDefaults.IconSize)) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_date_selection),
                            )
                        }
                    }
                },
            )
        }
    }

    if (showDatePicker) {
        val initialDate = userPickedDate ?: parseDateToLocalDate(date) ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val localDate = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date
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
                                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveGroupAbiSelector(
    apks: List<ApkUiModel>,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (File) -> Unit,
    onUninstallClick: () -> Unit,
    appState: AppState?,
) {
    val firstSupportedIndex = apks.indexOfFirst { it.abi.isSupported }.takeIf { it != -1 } ?: 0
    var selectedIndex by remember { mutableStateOf(firstSupportedIndex) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                                modifier = Modifier.size(ButtonDefaults.IconSize).padding(end = 4.dp),
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (appState?.isInstalled == true) {
                Button(
                    onClick = onUninstallClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                ) {
                    Text(text = stringResource(id = R.string.uninstall_button_label))
                }
            }

            val selectedApk = apks[selectedIndex]
            DownloadButton(
                downloadState = selectedApk.downloadState,
                onDownloadClick = { onDownloadClick(selectedApk) },
                onInstallClick = { file -> onInstallClick(file) },
            )
        }
    }
}

@Composable
private fun getFriendlyAppName(appName: String): String =
    when (appName) {
        FENIX -> stringResource(id = R.string.app_name_fenix)
        FOCUS -> stringResource(id = R.string.app_name_focus)
        REFERENCE_BROWSER -> stringResource(R.string.app_name_reference_browser)
        else -> appName
    }
