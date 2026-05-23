package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag // Added import
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import java.util.regex.Pattern

// Helper data class to store link information
private data class LinkableSpan(
    val start: Int,
    val end: Int,
    val displayText: String,
    val url: String,
)

@OptIn(FormatStringsInDatetimeFormats::class)
@Composable
fun PushCommentCard(
    comment: String,
    author: String?,
    revision: String,
    pushTimestamp: Long,
) {
    val urlPattern = remember {
        Pattern.compile(
            "(https?://|www\\.)" + // Scheme or www.
            "([\\da-zA-Z.-]+)" + // Domain name
            "(\\.[a-zA-Z.]{2,6})" + // TLD
            "([/\\w .-]*)*/?", // Path and query
        )
    }
    val bugPattern = remember {
        Pattern.compile("Bug\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    }

    val linkSpans = remember(comment) {
        // Recalculate if comment changes
        val spans = mutableListOf<LinkableSpan>()

        val urlMatcher = urlPattern.matcher(comment)
        while (urlMatcher.find()) {
            spans.add(
                LinkableSpan(
                    start = urlMatcher.start(),
                    end = urlMatcher.end(),
                    displayText = urlMatcher.group(0) ?: "",
                    url = urlMatcher.group(0) ?: "",
                ),
            )
        }

        val bugMatcher = bugPattern.matcher(comment)
        while (bugMatcher.find()) {
            val bugNumber = bugMatcher.group(1)
            if (bugNumber != null) {
                spans.add(
                    LinkableSpan(
                        start = bugMatcher.start(),
                        end = bugMatcher.end(),
                        displayText = bugMatcher.group(0) ?: "",
                        url = "https://bugzilla.mozilla.org/show_bug.cgi?id=$bugNumber",
                    ),
                )
            }
        }
        spans.sortBy { it.start }
        spans
    }

    val annotatedString = buildAnnotatedString {
        var lastMatchEnd = 0
        linkSpans.forEach { span ->
            if (span.start > lastMatchEnd) {
                append(comment.substring(lastMatchEnd, span.start))
            }
            withLink(
                link = LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    ),
                ),
            ) {
                append(span.displayText)
            }
            lastMatchEnd = span.end
        }
        if (lastMatchEnd < comment.length) {
            append(comment.substring(lastMatchEnd))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("push_card_$revision"), // Applied testTag
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Push Comment Info",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                )
            }

            author?.takeIf { it.isNotBlank() }?.let { authorString ->
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { /* TODO: Potentially handle click, e.g. view author profile or filter by author */ },
                    label = { Text(authorString) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Author",
                        )
                    },
                )
            }

            val formattedTimestamp = remember(pushTimestamp) {
                val format = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
                format.format(
                    Instant.fromEpochSeconds(pushTimestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault()),
                )
            }
            AssistChip(
                onClick = { },
                label = { Text(formattedTimestamp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Push timestamp",
                    )
                },
                modifier = Modifier.testTag("push_timestamp_chip_$revision"),
            )
        }
    }
}

private data class PushCommentCardPreviewState(
    val name: String,
    val comment: String,
    val author: String?,
)

private class PushCommentCardStateProvider : PreviewParameterProvider<PushCommentCardPreviewState> {
    override val values: Sequence<PushCommentCardPreviewState> = sequenceOf(
        PushCommentCardPreviewState(
            name = "With bug and URL",
            comment = "Bug 1234567 - Fix flaky test. See https://treeherder.mozilla.org/jobs for details.",
            author = "developer@mozilla.com",
        ),
        PushCommentCardPreviewState(
            name = "Plain text, no author",
            comment = "Try push for performance investigation on macOS.",
            author = null,
        ),
        PushCommentCardPreviewState(
            name = "Multiple bugs",
            comment = "Bug 1000001, Bug 1000002 - Backout for build bustage.",
            author = "releng@mozilla.com",
        ),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PushCommentCardPreview(
    @PreviewParameter(PushCommentCardStateProvider::class) state: PushCommentCardPreviewState,
) {
    TryFoxTheme {
        PushCommentCard(
            comment = state.comment,
            author = state.author,
            revision = "abc123def456",
            pushTimestamp = 1_716_460_800L,
        )
    }
}
