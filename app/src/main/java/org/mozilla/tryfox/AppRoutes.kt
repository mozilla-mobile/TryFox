package org.mozilla.tryfox

import java.net.URLEncoder

object AppRoutes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val RECEIVE_FROM_DESKTOP = "receive_from_desktop"
    const val RECEIVE_MESSAGE_HISTORY = "receive_message_history"
    const val QR_SCANNER = "qr_scanner"
    const val TREEHERDER_SEARCH = "treeherder_search"
    const val TREEHERDER_SEARCH_WITH_ARGS = "treeherder_search/{project}/{query}"

    fun createTreeherderSearchRoute(project: String, query: String): String {
        return "treeherder_search/${encode(project)}/${encode(query)}"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
