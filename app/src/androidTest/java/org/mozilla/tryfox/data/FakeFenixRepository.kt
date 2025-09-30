package org.mozilla.tryfox.data

import kotlinx.coroutines.delay
import java.io.File

class FakeFenixRepository(
    private val simulateNetworkError: Boolean = false,
    private val networkErrorMessage: String = "Fake network error",
    private val downloadProgressDelayMillis: Long = 100L,
) : IFenixRepository {

    override suspend fun getPushByRevision(
        project: String,
        revision: String
    ): NetworkResult<TreeherderRevisionResponse> {
        return if (simulateNetworkError) {
            NetworkResult.Error(networkErrorMessage)
        } else {
            NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 0, repository = "mozilla-central", revision = revision),
                    results = emptyList()
                )
            )
        }
    }

    override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
        return if (simulateNetworkError) {
            NetworkResult.Error(networkErrorMessage)
        } else {
            val dummyRevisionDetail = RevisionDetail(
                resultSetId = 1,
                repositoryId = 1,
                revision = "fakerevision123",
                author = author,
                comments = "Fake Bug 123456 - Test push"
            )
            val dummyRevisionResult = RevisionResult(
                id = 12345,
                revision = "fakerevision123",
                author = author,
                revisions = listOf(dummyRevisionDetail),
                revisionCount = 1,
                pushTimestamp = System.currentTimeMillis() / 1000,
                repositoryId = 1
            )
            NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(count = 1, repository = "mozilla-central"),
                    results = listOf(dummyRevisionResult)
                )
            )
        }
    }

    override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
        return if (simulateNetworkError) {
            NetworkResult.Error(networkErrorMessage)
        } else {
            val dummyJobDetails = JobDetails(
                appName = "fenix",
                jobName = "Build Fenix for arm64-v8a",
                jobSymbol = "Bsfv",
                taskId = "WDJb1HJaTr-dfSkshBxw4w"
            )
            NetworkResult.Success(TreeherderJobsResponse(results = listOf(dummyJobDetails)))
        }
    }

    override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
        return if (simulateNetworkError) {
            NetworkResult.Error(networkErrorMessage)
        } else {
            val dummyArtifact = Artifact(
                storageType = "taskcluster",
                name = "public/build/target.arm64-v8a.apk",
                expires = "2025-12-31T23:59:59Z",
                contentType = "application/vnd.android.package-archive"
            )
            NetworkResult.Success(ArtifactsResponse(artifacts = listOf(dummyArtifact)))
        }
    }

    override suspend fun downloadArtifact(
        downloadUrl: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): NetworkResult<File> {
        if (simulateNetworkError) {
            return NetworkResult.Error(networkErrorMessage, null)
        }

        val totalBytes = 10_000_000L

        onProgress(0, totalBytes)
        delay(downloadProgressDelayMillis)

        onProgress(totalBytes / 2, totalBytes)
        delay(downloadProgressDelayMillis)

        outputFile.parentFile?.mkdirs()
        try {
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }
            outputFile.writeText("This is a fake downloaded artifact: ${outputFile.name} from $downloadUrl")
        } catch (e: Exception) {
            return NetworkResult.Error("Failed to create fake artifact file: ${e.message}", e)
        }

        onProgress(totalBytes, totalBytes)
        delay(downloadProgressDelayMillis)

        return NetworkResult.Success(outputFile)
    }
}
