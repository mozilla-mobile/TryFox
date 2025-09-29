package org.mozilla.tryfox.network

import okhttp3.ResponseBody
import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @GET("project/{project}/push/")
    suspend fun getPushByRevision(
        @Path("project") project: String,
        @Query("revision") revision: String
    ): TreeherderRevisionResponse

    @GET("project/try/push/")
    suspend fun getPushByAuthor(
        @Query("full") full: Boolean = true,
        @Query("count") count: Int = 10,
        @Query("author") author: String
    ): TreeherderRevisionResponse

    @GET("jobs/")
    suspend fun getJobsForPush(@Query("push_id") pushId: Int): TreeherderJobsResponse

    @GET
    suspend fun getArtifactsForTask(@Url url: String): ArtifactsResponse

    @DisableLogs
    @Streaming
    @GET
    suspend fun downloadFile(@Url downloadUrl: String): ResponseBody

    @GET
    suspend fun getHtmlPage(@Url url: String): String
}