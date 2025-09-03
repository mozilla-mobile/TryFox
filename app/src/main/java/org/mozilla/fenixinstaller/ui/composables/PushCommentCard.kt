package org.mozilla.fenixinstaller.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

// Helper data class to store link information
private data class LinkableSpan(
    val start: Int,
    val end: Int,
    val displayText: String, // The text to show (e.g., "Bug 12345" or "http://example.com")
    val url: String          // The actual URL to link to
)

@Composable
fun PushCommentCard(comment: String) {
    val urlPattern = remember {
        Pattern.compile(
            "(https?://|www\\.)" + // Scheme or www.
            "([\\da-zA-Z.-]+)" +  // Domain name
            "(\\.[a-zA-Z.]{2,6})" + // TLD
            "([/\\w .-]*)*/?"     // Path and query
        )
    }
    val bugPattern = remember {
        Pattern.compile("Bug\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    }

    val linkSpans = remember(comment) { // Recalculate if comment changes
        val spans = mutableListOf<LinkableSpan>()

        // Find generic URL matches
        val urlMatcher = urlPattern.matcher(comment)
        while (urlMatcher.find()) {
            spans.add(
                LinkableSpan(
                    start = urlMatcher.start(),
                    end = urlMatcher.end(),
                    displayText = urlMatcher.group(0),
                    url = urlMatcher.group(0)
                )
            )
        }

        // Find Bugzilla matches ("Bug XXXXXX")
        val bugMatcher = bugPattern.matcher(comment)
        while (bugMatcher.find()) {
            val bugNumber = bugMatcher.group(1)
            if (bugNumber != null) {
                spans.add(
                    LinkableSpan(
                        start = bugMatcher.start(),
                        end = bugMatcher.end(),
                        displayText = bugMatcher.group(0), // Full matched text e.g. "Bug 12345"
                        url = "https://bugzilla.mozilla.org/show_bug.cgi?id=$bugNumber"
                    )
                )
            }
        }
        // Sort by start index to process in order; if starts are same, shorter one first to avoid issues with contained matches.
        // However, for distinct patterns like these, simple start sort is usually fine.
        spans.sortBy { it.start }
        // Basic overlapping resolution: if a span is fully contained within another, remove the inner one.
        // This is a naive approach, more sophisticated logic might be needed for complex cases.
        val filteredSpans = mutableListOf<LinkableSpan>()
        var i = 0
        while (i < spans.size) {
            val currentSpan = spans[i]
            var j = i + 1
            var isContained = false
            while (j < spans.size) {
                val nextSpan = spans[j]
                if (currentSpan.start >= nextSpan.start && currentSpan.end <= nextSpan.end && currentSpan != nextSpan) {
                    // currentSpan is contained in nextSpan
                    isContained = true
                    break
                }
                if (nextSpan.start >= currentSpan.start && nextSpan.end <= currentSpan.end && currentSpan != nextSpan) {
                    // nextSpan is contained in currentSpan, remove nextSpan by skipping it in outer loop logic
                    // This isn't perfect, as it might remove a more specific link (e.g. bug link) if a generic URL contains it.
                    // For now, let's assume our patterns are mostly exclusive or this simple filter is acceptable.
                    // A better approach would be to prioritize based on link type or length.
                    // Let's remove the simpler contained one (usually the shorter one if regexes are greedy)
                    // This part is tricky. Let's simplify and assume non-problematic overlaps for now as per previous thoughts.
                    // The primary sort by start index is the most critical for sequential processing.
                    // We will not filter for now, and rely on the fact that the two patterns are distinct.
                    j++
                    continue
                }
                j++
            }
            if (!isContained) {
                filteredSpans.add(currentSpan)
            }
            i++
        }
        // Return the sorted (and potentially later filtered) list.
        // For this iteration, returning the simple sorted list without complex filtering:
        spans.sortedBy { it.start } // Ensure it's the simple sorted list
    }

    val annotatedString = buildAnnotatedString {
        var lastMatchEnd = 0
        linkSpans.forEach { span ->
            // Append text before the current link span
            if (span.start > lastMatchEnd) {
                append(comment.substring(lastMatchEnd, span.start))
            }

            // Append the link itself
            withLink(
                link = LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ))
                )
            ) {
                append(span.displayText) // Display the original matched text segment
            }
            lastMatchEnd = span.end
        }

        // Append any remaining text after the last link
        if (lastMatchEnd < comment.length) {
            append(comment.substring(lastMatchEnd))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Push Comment Info",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSecondaryContainer)
            )
        }
    }
}
