package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse

interface TreeherderRepository {
    suspend fun getPushByRevision(project: String, revision: String): NetworkResult<TreeherderRevisionResponse>

    /** Legacy, project-independent lookup kept for callers which predate project selection. */
    suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse>

    /** Looks up an author's pushes in the selected Treeherder project. */
    suspend fun getPushesByAuthor(
        project: String,
        author: String,
        count: Int = 10,
        offset: Int = 0,
        pushTimestampLte: Long? = null,
    ): NetworkResult<TreeherderRevisionResponse> =
        getPushesByAuthor(author)

    /** Gets the most recent pushes for a project, as shown by Treeherder without a query. */
    suspend fun getRecentPushes(
        project: String,
        count: Int = 10,
        offset: Int = 0,
    ): NetworkResult<TreeherderRevisionResponse> =
        NetworkResult.Error("Recent-push lookup is not implemented.")
    suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse>
    suspend fun getJobsForPushPage(
        pushId: Int,
        page: Int,
        count: Int = 2000,
    ): NetworkResult<TreeherderJobsResponse>
    suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse>
}
