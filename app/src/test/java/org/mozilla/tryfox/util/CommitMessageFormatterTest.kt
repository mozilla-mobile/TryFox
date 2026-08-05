package org.mozilla.tryfox.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommitMessageFormatterTest {
    @Test
    fun `removes trailing reviewer group directive`() {
        assertEquals(
            "Bug 123: Update the feature",
            "Bug 123: Update the feature r=#android-reviewers".withoutTrailingReviewerDirective(),
        )
    }

    @Test
    fun `removes trailing reviewer request directive`() {
        assertEquals(
            "Bug 123: Update the feature",
            "Bug 123: Update the feature r?#android-reviewers".withoutTrailingReviewerDirective(),
        )
    }

    @Test
    fun `removes comma separated reviewers and approval marker`() {
        assertEquals(
            "Bug 123: Update the feature",
            "Bug 123: Update the feature r=reviewer1, reviewer2!".withoutTrailingReviewerDirective(),
        )
    }

    @Test
    fun `preserves commit description after cleaning the subject`() {
        assertEquals(
            "Bug 123: Update the feature\n\nExplain why the change is needed.",
            "Bug 123: Update the feature r=reviewer1\n\nExplain why the change is needed."
                .withoutTrailingReviewerDirective(),
        )
    }

    @Test
    fun `leaves messages without a trailing reviewer directive unchanged`() {
        assertEquals(
            "Bug 123: Explain r=reviewer1 in the description",
            "Bug 123: Explain r=reviewer1 in the description".withoutTrailingReviewerDirective(),
        )
        assertEquals(
            "Bug 123: Update the feature\nReviewer metadata: r=reviewer1",
            "Bug 123: Update the feature\nReviewer metadata: r=reviewer1".withoutTrailingReviewerDirective(),
        )
    }
}
