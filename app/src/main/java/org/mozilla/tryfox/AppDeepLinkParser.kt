package org.mozilla.tryfox

import android.net.Uri
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface AppDeepLinkDestination {
    data class TreeherderSearch(
        val project: String,
        val revision: String,
    ) : AppDeepLinkDestination

    data class Profile(
        val email: String,
    ) : AppDeepLinkDestination
}

object AppDeepLinkParser {
    private const val TREEHERDER_HOST = "treeherder.mozilla.org"
    private const val TRYFOX_SCHEME = "tryfox"
    private const val JOBS_TARGET = "jobs"
    private const val DEFAULT_PROJECT = "try"

    fun parse(uri: Uri?): AppDeepLinkDestination? = parse(uri?.toString())

    fun parse(uriString: String?): AppDeepLinkDestination? {
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { URI(uriString) }.getOrNull() ?: return null

        return when (uri.scheme?.lowercase()) {
            "https" -> parseTreeherderUri(uri)
            TRYFOX_SCHEME -> parseTryFoxUri(uri)
            else -> null
        }
    }

    private fun parseTreeherderUri(uri: URI): AppDeepLinkDestination? {
        if (uri.host?.lowercase() != TREEHERDER_HOST) return null

        val target = parseTarget(uri)
        if (target != JOBS_TARGET) return null

        return destinationFromParameters(parseParameters(uri))
    }

    private fun parseTryFoxUri(uri: URI): AppDeepLinkDestination? {
        val target = parseTarget(uri)
        if (target != JOBS_TARGET) return null

        return destinationFromParameters(parseParameters(uri))
    }

    private fun destinationFromParameters(parameters: Map<String, String>): AppDeepLinkDestination? {
        val revision = parameters["revision"]?.takeIf { it.isNotBlank() }
        if (revision != null) {
            val project = parameters["repo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_PROJECT
            return AppDeepLinkDestination.TreeherderSearch(
                project = project,
                revision = revision,
            )
        }

        val author = parameters["author"]?.takeIf { it.isNotBlank() }
        if (author != null) {
            return AppDeepLinkDestination.Profile(email = author)
        }

        return null
    }

    private fun parseTarget(uri: URI): String? {
        val fragmentTarget = parseTargetFromFragment(uri.fragment)
        if (fragmentTarget != null) {
            return fragmentTarget
        }

        uri.host?.takeIf { it.equals(JOBS_TARGET, ignoreCase = true) }?.let {
            return JOBS_TARGET
        }

        val pathSegments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() }.orEmpty()
        return when {
            uri.host.equals("treeherder", ignoreCase = true) -> pathSegments.firstOrNull()
            else -> pathSegments.firstOrNull()
        }?.lowercase()
    }

    private fun parseParameters(uri: URI): Map<String, String> {
        val directQuery = uri.rawQuery
        if (!directQuery.isNullOrBlank()) {
            return parseQueryString(directQuery)
        }

        return parseParametersFromFragment(uri.fragment)
    }

    private fun parseParametersFromFragment(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank() || !fragment.contains("?")) return emptyMap()
        val query = fragment.substringAfter('?', "")
        return parseQueryString(query)
    }

    private fun parseTargetFromFragment(fragment: String?): String? {
        if (fragment.isNullOrBlank()) return null
        val path = fragment.substringBefore('?').trim('/')
        if (path.isBlank()) return null
        return path.substringAfterLast('/').lowercase()
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split('&')
            .mapNotNull { parameter ->
                if (parameter.isBlank()) return@mapNotNull null
                val key = decode(parameter.substringBefore('='))
                val value = decode(parameter.substringAfter('=', ""))
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .toMap()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
