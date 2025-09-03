package org.mozilla.fenixinstaller.data

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path // Added import
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @GET("project/{project}/push/") // Changed endpoint
    suspend fun getPushByRevision(
        @Path("project") project: String, // Added project path parameter
        @Query("revision") revision: String
    ): TreeherderRevisionResponse

    @GET("jobs/")
    suspend fun getJobsForPush(@Query("push_id") pushId: Int): TreeherderJobsResponse

    // Using @Url for the third endpoint as its base URL is different
    @GET
    suspend fun getArtifactsForTask(@Url url: String): ArtifactsResponse

    @Streaming
    @GET
    suspend fun downloadFile(@Url downloadUrl: String): ResponseBody
}
