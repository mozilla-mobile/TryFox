package org.mozilla.tryfox.ui.screens

/** The two Treeherder request types accepted by the unified build search. */
sealed interface SearchQuery {
    val value: String

    data class Email(override val value: String) : SearchQuery
    data class Revision(override val value: String) : SearchQuery
}

/**
 * Keeps query validation independent of Compose and networking.  An @ is deliberately
 * treated strictly: it must form an email rather than accidentally becoming a revision.
 */
object SearchQueryClassifier {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun classify(input: String): Result<SearchQuery> {
        val value = input.trim()
        return when {
            value.isBlank() -> Result.failure(IllegalArgumentException("Enter an email or revision."))
            '@' !in value -> Result.success(SearchQuery.Revision(value))
            emailPattern.matches(value) -> Result.success(SearchQuery.Email(value))
            else -> Result.failure(IllegalArgumentException("Enter a valid email address or a revision without @."))
        }
    }
}
