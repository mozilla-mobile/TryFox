package org.mozilla.tryfox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppDeepLinkRouteMapperTest {

    @Test
    fun `maps scanned tryfox revision link to treeherder route`() {
        val route = AppDeepLinkRouteMapper.routeFor(
            "tryfox://jobs?repo=try&revision=abcdef123456",
        )

        assertEquals("treeherder_search/try/abcdef123456", route)
    }

    @Test
    fun `maps scanned treeherder revision link to treeherder route`() {
        val route = AppDeepLinkRouteMapper.routeFor(
            "https://treeherder.mozilla.org/jobs?repo=mozilla-central&revision=abcdef123456",
        )

        assertEquals("treeherder_search/mozilla-central/abcdef123456", route)
    }

    @Test
    fun `encodes treeherder route path segments`() {
        val route = AppDeepLinkRouteMapper.routeFor(
            "tryfox://jobs?repo=try%2Fweird&revision=abc%2Fdef%3Fghi",
        )

        assertEquals("treeherder_search/try%2Fweird/abc%2Fdef%3Fghi", route)
    }

    @Test
    fun `maps scanned author link to encoded profile route`() {
        val route = AppDeepLinkRouteMapper.routeFor(
            "tryfox://jobs?author=try%2Buser%40mozilla.com",
        )

        assertEquals("profile_by_email?email=try%2Buser%40mozilla.com", route)
    }

    @Test
    fun `returns null for unsupported scanned QR content`() {
        val route = AppDeepLinkRouteMapper.routeFor("not a TryFox link")

        assertNull(route)
    }
}
