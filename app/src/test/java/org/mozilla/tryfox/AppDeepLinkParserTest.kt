package org.mozilla.tryfox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppDeepLinkParserTest {

    @Test
    fun `parses treeherder jobs link with revision`() {
        val uri = "https://treeherder.mozilla.org/jobs?repo=try&revision=abcdef123456"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.TreeherderSearch(
                project = "try",
                revision = "abcdef123456",
            ),
            destination,
        )
    }

    @Test
    fun `parses treeherder hash link with revision`() {
        val uri = "https://treeherder.mozilla.org/#/jobs?repo=mozilla-central&revision=abcdef123456"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.TreeherderSearch(
                project = "mozilla-central",
                revision = "abcdef123456",
            ),
            destination,
        )
    }

    @Test
    fun `parses tryfox jobs link with revision`() {
        val uri = "tryfox://jobs?repo=mozilla-beta&revision=abcdef123456"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.TreeherderSearch(
                project = "mozilla-beta",
                revision = "abcdef123456",
            ),
            destination,
        )
    }

    @Test
    fun `parses tryfox jobs link with author`() {
        val uri = "tryfox://jobs?author=tthibaud%40mozilla.com"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.Profile(email = "tthibaud@mozilla.com"),
            destination,
        )
    }

    @Test
    fun `parses treeherder hash link with encoded author`() {
        val uri = "https://treeherder.mozilla.org/#/jobs?author=try%2Buser%40mozilla.com"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.Profile(email = "try+user@mozilla.com"),
            destination,
        )
    }

    @Test
    fun `defaults repo to try when missing`() {
        val uri = "tryfox://jobs?revision=abcdef123456"

        val destination = AppDeepLinkParser.parse(uri)

        assertEquals(
            AppDeepLinkDestination.TreeherderSearch(
                project = "try",
                revision = "abcdef123456",
            ),
            destination,
        )
    }

    @Test
    fun `ignores unsupported targets`() {
        val uri = "tryfox://pushes?revision=abcdef123456"

        val destination = AppDeepLinkParser.parse(uri)

        assertNull(destination)
    }
}
