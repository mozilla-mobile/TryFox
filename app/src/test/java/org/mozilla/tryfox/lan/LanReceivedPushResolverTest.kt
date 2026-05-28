package org.mozilla.tryfox.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionDetail
import org.mozilla.tryfox.data.RevisionMeta
import org.mozilla.tryfox.data.RevisionResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import org.mozilla.tryfox.data.repositories.TreeherderRepository

class LanReceivedPushResolverTest {
    private val receivedAt = 1_760_000_000_000L

    @Test
    fun `resolves revision message to accepted history messages`() = runTest {
        val repository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "try", revision = "abc123"),
                    results = listOf(
                        revisionResult(
                            revision = "abc123",
                            author = "dev@mozilla.com",
                            comments = listOf("No bug comment", "Bug 123 - Fix tests"),
                            pushTimestamp = 1_716_460_800L,
                        ),
                    ),
                ),
            ),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(revision = "abc123", author = null, title = "Nightly run"),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals(listOf("try:abc123"), repository.revisionRequests)
        assertTrue(repository.authorRequests.isEmpty())
        assertEquals(1, messages.size)
        assertEquals(
            LanReceivedMessage(
                receivedAt = receivedAt,
                accepted = true,
                messageId = "message-1",
                extensionId = "extension-id",
                sourceUrl = "https://treeherder.mozilla.org/jobs?repo=try&revision=abc123",
                tryfoxDeepLink = "tryfox://jobs?repo=try&revision=abc123",
                repo = "try",
                revision = "abc123",
                author = "dev@mozilla.com",
                bodyHash = "body-hash",
                title = "Nightly run",
                pushComment = "Bug 123 - Fix tests",
                pushTimestamp = 1_716_460_800L,
            ),
            messages.single(),
        )
    }

    @Test
    fun `revision target takes precedence when author is also present`() = runTest {
        val repository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "mozilla-central", revision = "abc123"),
                    results = listOf(revisionResult(revision = "abc123")),
                ),
            ),
            authorResponse = NetworkResult.Error("author should not be requested"),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(repo = "mozilla-central", revision = "abc123", author = "dev@mozilla.com"),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals(listOf("mozilla-central:abc123"), repository.revisionRequests)
        assertTrue(repository.authorRequests.isEmpty())
        assertEquals("mozilla-central", messages.single().repo)
    }

    @Test
    fun `blank revision repository falls back to try`() = runTest {
        val repository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "try", revision = "abc123"),
                    results = listOf(revisionResult(revision = "abc123")),
                ),
            ),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(repo = "", revision = "abc123", author = null),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals(listOf("try:abc123"), repository.revisionRequests)
        assertEquals("try", messages.single().repo)
    }

    @Test
    fun `encodes generated custom deep link query parameters`() = runTest {
        val repository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "try/weird", revision = "abc/def?ghi"),
                    results = listOf(revisionResult(revision = "abc/def?ghi")),
                ),
            ),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(repo = "try/weird", revision = "abc/def?ghi", author = null),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals("tryfox://jobs?repo=try%2Fweird&revision=abc%2Fdef%3Fghi", messages.single().tryfoxDeepLink)
    }

    @Test
    fun `resolves author message using response repository metadata`() = runTest {
        val repository = FakeTreeherderRepository(
            authorResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "mozilla-central"),
                    results = listOf(revisionResult(revision = "def456", author = "dev@mozilla.com")),
                ),
            ),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(revision = null, author = "dev@mozilla.com"),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals(listOf("dev@mozilla.com"), repository.authorRequests)
        assertEquals("mozilla-central", messages.single().repo)
        assertEquals("tryfox://jobs?repo=mozilla-central&revision=def456", messages.single().tryfoxDeepLink)
    }

    @Test
    fun `falls back to try repository for blank author response metadata`() = runTest {
        val repository = FakeTreeherderRepository(
            authorResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = ""),
                    results = listOf(revisionResult(revision = "def456")),
                ),
            ),
        )
        val resolver = resolver(repository)

        val messages = resolver.resolve(
            message = incomingMessage(revision = null, author = "dev@mozilla.com"),
            extensionId = "extension-id",
            bodyHash = "body-hash",
        )

        assertEquals("try", messages.single().repo)
    }

    @Test
    fun `returns empty list for no results and network errors`() = runTest {
        val emptyRepository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 0, repository = "try", revision = "abc123"),
                    results = emptyList(),
                ),
            ),
        )
        val errorRepository = FakeTreeherderRepository(
            revisionResponse = NetworkResult.Error("network failed"),
        )

        assertTrue(
            resolver(emptyRepository).resolve(
                message = incomingMessage(revision = "abc123", author = null),
                extensionId = "extension-id",
                bodyHash = "body-hash",
            ).isEmpty(),
        )
        assertTrue(
            resolver(errorRepository).resolve(
                message = incomingMessage(revision = "abc123", author = null),
                extensionId = "extension-id",
                bodyHash = "body-hash",
            ).isEmpty(),
        )
    }

    private fun resolver(repository: TreeherderRepository) =
        LanReceivedPushResolver(
            treeherderRepository = repository,
            currentTimeMillisProvider = { receivedAt },
        )

    private fun incomingMessage(
        repo: String? = "try",
        revision: String? = "abc123",
        author: String? = null,
        title: String? = null,
    ) = LanIncomingMessage(
        version = 1,
        type = "try-revision",
        messageId = "message-1",
        sentAt = receivedAt,
        title = title,
        sourceUrl = "https://treeherder.mozilla.org/jobs?repo=${repo ?: "try"}&revision=${revision ?: "abc123"}",
        tryfoxDeepLink = "tryfox://jobs?repo=${repo ?: "try"}&revision=${revision ?: "abc123"}",
        repo = repo,
        revision = revision,
        author = author,
    )

    private fun revisionResult(
        revision: String = "abc123",
        author: String = "dev@mozilla.com",
        comments: List<String> = listOf("Bug 123 - Fix tests"),
        pushTimestamp: Long = 1_716_460_800L,
    ) = RevisionResult(
        id = 123,
        revision = revision,
        author = author,
        revisions = comments.mapIndexed { index, comment ->
            RevisionDetail(
                resultSetId = 123,
                repositoryId = 1,
                revision = "$revision-$index",
                author = author,
                comments = comment,
            )
        },
        revisionCount = comments.size,
        pushTimestamp = pushTimestamp,
        repositoryId = 1,
    )

    private class FakeTreeherderRepository(
        private val revisionResponse: NetworkResult<TreeherderRevisionResponse> = NetworkResult.Error("unexpected revision"),
        private val authorResponse: NetworkResult<TreeherderRevisionResponse> = NetworkResult.Error("unexpected author"),
    ) : TreeherderRepository {
        val revisionRequests = mutableListOf<String>()
        val authorRequests = mutableListOf<String>()

        override suspend fun getPushByRevision(
            project: String,
            revision: String,
        ): NetworkResult<TreeherderRevisionResponse> {
            revisionRequests += "$project:$revision"
            return revisionResponse
        }

        override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
            authorRequests += author
            return authorResponse
        }

        override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> =
            error("Not used")

        override suspend fun getJobsForPushPage(
            pushId: Int,
            page: Int,
            count: Int,
        ): NetworkResult<TreeherderJobsResponse> = error("Not used")

        override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> =
            error("Not used")
    }
}
