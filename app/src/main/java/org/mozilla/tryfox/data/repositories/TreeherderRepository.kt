package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse

interface TreeherderRepository {
    suspend fun getPushByRevision(project: String, revision: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse>
    suspend fun getJobsForPushPage(
        pushId: Int,
        page: Int,
        count: Int = 2000,
    ): NetworkResult<TreeherderJobsResponse>
    suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse>
}
