package org.mozilla.tryfox.lan

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionResult
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import java.net.URLEncoder

class LanReceivedPushResolver(
    private val treeherderRepository: TreeherderRepository,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(
        message: LanIncomingMessage,
        extensionId: String,
        bodyHash: String,
    ): List<LanReceivedMessage> {
        val receivedAt = currentTimeMillisProvider()
        return when {
            !message.revision.isNullOrBlank() -> {
                val project = message.repo?.takeIf { it.isNotBlank() } ?: DEFAULT_PROJECT
                when (val response = treeherderRepository.getPushByRevision(project, message.revision)) {
                    is NetworkResult.Success -> response.data.results.map { result ->
                        result.toHistoryMessage(
                            receivedAt = receivedAt,
                            message = message,
                            extensionId = extensionId,
                            bodyHash = bodyHash,
                            repo = project,
                        )
                    }
                    is NetworkResult.Error -> emptyList()
                }
            }

            !message.author.isNullOrBlank() -> {
                when (val response = treeherderRepository.getPushesByAuthor(message.author)) {
                    is NetworkResult.Success -> {
                        val project = response.data.meta.repository.ifBlank { DEFAULT_PROJECT }
                        response.data.results.map { result ->
                            result.toHistoryMessage(
                                receivedAt = receivedAt,
                                message = message,
                                extensionId = extensionId,
                                bodyHash = bodyHash,
                                repo = project,
                            )
                        }
                    }
                    is NetworkResult.Error -> emptyList()
                }
            }

            else -> emptyList()
        }
    }

    private fun RevisionResult.toHistoryMessage(
        receivedAt: Long,
        message: LanIncomingMessage,
        extensionId: String,
        bodyHash: String,
        repo: String,
    ): LanReceivedMessage {
        val pushComment = revisions.firstOrNull { it.comments.startsWith("Bug ") }?.comments
            ?: revisions.firstOrNull()?.comments
            ?: "No comment"
        val deepLink = "tryfox://jobs?repo=${encodeQueryParameter(repo)}&revision=${encodeQueryParameter(revision)}"
        return LanReceivedMessage(
            receivedAt = receivedAt,
            accepted = true,
            messageId = message.messageId,
            extensionId = extensionId,
            sourceUrl = message.sourceUrl,
            tryfoxDeepLink = deepLink,
            repo = repo,
            revision = revision,
            author = author,
            bodyHash = bodyHash,
            title = message.title,
            pushComment = pushComment,
            pushTimestamp = pushTimestamp,
        )
    }

    private companion object {
        const val DEFAULT_PROJECT = "try"

        fun encodeQueryParameter(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
