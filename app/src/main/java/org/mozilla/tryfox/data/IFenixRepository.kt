package org.mozilla.tryfox.data

interface IFenixRepository {
    suspend fun getPushByRevision(project: String, revision: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse>
    suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse>
}
