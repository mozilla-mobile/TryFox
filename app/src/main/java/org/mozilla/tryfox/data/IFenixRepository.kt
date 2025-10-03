package org.mozilla.tryfox.data

import java.io.File

interface IFenixRepository {
    suspend fun getPushByRevision(project: String, revision: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse>
    suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse>
    suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse>
    suspend fun downloadArtifact(
        downloadUrl: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): NetworkResult<File>
}
