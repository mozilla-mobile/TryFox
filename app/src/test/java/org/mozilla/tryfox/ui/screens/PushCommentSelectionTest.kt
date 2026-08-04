package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.data.RevisionDetail

class PushCommentSelectionTest {

    @Test
    fun `uses the first real commit after a fuzzy Try trigger`() {
        assertEquals(
            "Bug 123 - Make the search result title useful",
            selectPreferredPushComment(
                revisions = listOf(
                    revision("Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`"),
                    revision("Bug 123 - Make the search result title useful"),
                ),
            ),
        )
    }

    @Test
    fun `uses the first real commit even when it does not start with Bug`() {
        assertEquals(
            "Update Fenix dependencies",
            selectPreferredPushComment(
                revisions = listOf(
                    revision("Pushed via `mach try chooser`"),
                    revision("Update Fenix dependencies"),
                    revision("Bug 456 - A later commit"),
                ),
            ),
        )
    }

    @Test
    fun `keeps a Try trigger title when no real commit is available`() {
        val triggerComment = "Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`"

        assertEquals(
            triggerComment,
            selectPreferredPushComment(revisions = listOf(revision(triggerComment))),
        )
    }

    @Test
    fun `uses the preceding real commit when a fuzzy push contains no real commit`() {
        assertEquals(
            "Update Fenix dependencies",
            selectPreferredPushComment(
                revisions = listOf(revision("Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`")),
                precedingPushRevisions = listOf(
                    listOf(
                        revision("Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`"),
                        revision("Update Fenix dependencies"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `identifies trigger-only pushes that need a preceding commit`() {
        assertTrue(
            needsPrecedingRealCommit(
                listOf(revision("Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`")),
            ),
        )
        assertFalse(
            needsPrecedingRealCommit(
                listOf(
                    revision("Fuzzy query='build-apk-fenix-debug'\n\nPushed via `mach try fuzzy`"),
                    revision("Update Fenix dependencies"),
                ),
            ),
        )
    }

    private fun revision(comments: String) = RevisionDetail(
        resultSetId = 1,
        repositoryId = 4,
        revision = "abc123",
        author = "tcampbell@mozilla.com",
        comments = comments,
    )
}
