package org.mozilla.tryfox.ui.composables

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mozilla.tryfox.R

@Composable
fun NotificationPermissionCard(
    modifier: Modifier = Modifier,
    onEnableNotifications: () -> Unit,
) {
    FloatingActionCard(
        modifier = modifier,
        text = { textModifier ->
            Text(
                text = stringResource(R.string.notification_permission_card_message),
                style = MaterialTheme.typography.titleMedium,
                modifier = textModifier,
            )
        },
        action = {
            Button(onClick = onEnableNotifications) {
                Text(stringResource(R.string.notification_permission_card_enable))
            }
        },
    )
}
