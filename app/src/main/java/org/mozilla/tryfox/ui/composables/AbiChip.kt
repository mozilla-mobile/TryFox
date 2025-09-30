package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.ui.models.AbiUiModel

@Composable
fun AbiChip(
    abi: AbiUiModel
) {
    val (containerColor, labelColor) = when(abi.isSupported) {
        false -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        true -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    AssistChip(
        onClick = { /* No action */ },
        label = { Text(abi.name ?: stringResource(id = R.string.abi_chip_unknown_abi_name), style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            if (!abi.isSupported) { // Use derived state
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(id = R.string.abi_chip_warning_unsupported_abi_description),
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        ),
        modifier = Modifier.padding(end = 8.dp)
    )
}