package org.mozilla.tryfox.ui.composables

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProjectSelector(
    projects: List<String>,
    selectedProject: String,
    projectLabel: (String) -> String,
    onProjectSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = projects.indexOf(selectedProject).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(4.dp)
            .testTag("unified_search_project_input"),
    ) {
        val segmentWidth = maxWidth / projects.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 220),
            label = "project selector indicator offset",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(20.dp)),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            projects.forEach { project ->
                TextButton(
                    onClick = { onProjectSelected(project) },
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .testTag("unified_search_project_$project"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        text = projectLabel(project),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
