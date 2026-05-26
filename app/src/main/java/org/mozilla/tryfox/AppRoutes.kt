package org.mozilla.tryfox

import java.net.URLEncoder

object AppRoutes {
    const val HOME = "home"
    const val QR_SCANNER = "qr_scanner"
    const val TREEHERDER_SEARCH = "treeherder_search"
    const val TREEHERDER_SEARCH_WITH_ARGS = "treeherder_search/{project}/{revision}"
    const val PROFILE = "profile"
    const val PROFILE_BY_EMAIL = "profile_by_email?email={email}"

    fun createTreeherderSearchRoute(project: String, revision: String): String {
        return "treeherder_search/${encode(project)}/${encode(revision)}"
    }

    fun createProfileByEmailRoute(email: String): String {
        return "profile_by_email?email=${encode(email)}"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
