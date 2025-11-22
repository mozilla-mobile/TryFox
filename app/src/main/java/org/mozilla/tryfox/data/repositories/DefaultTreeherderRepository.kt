package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import org.mozilla.tryfox.network.TreeherderApiService

class DefaultTreeherderRepository(
    private val treeherderApiService: TreeherderApiService,
) : TreeherderRepository {

    companion object {
        const val TASKCLUSTER_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/queue/v1/"
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(apiCall.invoke())
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun getPushByRevision(
        project: String,
        revision: String,
    ): NetworkResult<TreeherderRevisionResponse> {
        return safeApiCall { treeherderApiService.getPushByRevision(project, revision) }
    }

    override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
        return safeApiCall { treeherderApiService.getPushByAuthor(author = author) }
    }

    override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
        return safeApiCall { treeherderApiService.getJobsForPush(pushId) }
    }

    override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
        val artifactsUrl = "${TASKCLUSTER_BASE_URL}task/$taskId/runs/0/artifacts"
        return safeApiCall { treeherderApiService.getArtifactsForTask(artifactsUrl) }
    }
}
