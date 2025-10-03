package org.mozilla.tryfox.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableListView(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "ArrowRotation")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotationAngle),
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                content()
            }
        }
    }
}
