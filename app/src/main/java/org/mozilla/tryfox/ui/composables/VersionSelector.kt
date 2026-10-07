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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.tryfox.R
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FOCUS_BETA

@Composable
internal fun VersionSelector(
    appName: String,
    selectedReleaseVersion: String?,
    availableReleaseVersions: List<String>,
    onReleaseVersionSelected: (String) -> Unit,
) {
    var showSelector by remember { mutableStateOf(false) }
    val selectedVersion = selectedReleaseVersion ?: availableReleaseVersions.firstOrNull()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = availableReleaseVersions.isNotEmpty()) { showSelector = true }
            .semantics {
                contentDescription = "Selected Firefox Release version ${selectedVersion ?: ""}"
            }
            .testTag("release_version_chip_${appName.lowercase()}")
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = selectedVersion ?: "--",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (showSelector) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = stringResource(R.string.release_version_chip_description),
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
private val VersionPickerWheelHeight = 156.dp

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
    val maximumSupportedMajorVersion = versionsByMajor.keys.maxOrNull() ?: MINIMUM_SUPPORTED_MAJOR_VERSION
    var activeMajor by remember(selectedVersion, availableVersions) { mutableStateOf(initialMajor) }
    var draftVersion by remember(selectedVersion, availableVersions) { mutableStateOf(selectedVersion) }
    val isActiveMajorSelectable = activeMajor in MINIMUM_SUPPORTED_MAJOR_VERSION..maximumSupportedMajorVersion
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, top = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.wrapContentWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.version_selector_version_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    AndroidView(
                        factory = { context ->
                            NumberPicker(context).apply {
                                minValue = MINIMUM_SUPPORTED_MAJOR_VERSION
                                maxValue = maximumSupportedMajorVersion
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
                                                it in MINIMUM_SUPPORTED_MAJOR_VERSION..maximumSupportedMajorVersion
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
                            .height(VersionPickerWheelHeight)
                            .clipToBounds()
                            .testTag("release_version_major_picker_${appName.lowercase()}"),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.version_selector_available_builds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (variants.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isActiveMajorSelectable) {
                                    stringResource(R.string.version_selector_no_builds)
                                } else {
                                    stringResource(
                                        R.string.version_selector_current_major_unavailable,
                                        activeMajor,
                                        MINIMUM_SUPPORTED_MAJOR_VERSION,
                                        maximumSupportedMajorVersion,
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .wrapContentWidth(),
                        ) {
                            items(variants, key = { it }) { version ->
                                val selected = draftVersion == version
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .clickable {
                                            draftVersion = version
                                            onConfirm(version)
                                        }
                                        .semantics { this.selected = selected }
                                        .testTag("release_version_variant_${version.replace('.', '_')}")
                                        .padding(vertical = 2.dp),
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            draftVersion = version
                                            onConfirm(version)
                                        },
                                    )
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
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.cancel)) }
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
