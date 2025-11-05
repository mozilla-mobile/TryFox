package org.mozilla.tryfox.network

import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface TreeherderApiService {

    @GET("project/{project}/push/")
    suspend fun getPushByRevision(
        @Path("project") project: String,
        @Query("revision") revision: String,
    ): TreeherderRevisionResponse

    @GET("project/try/push/")
    suspend fun getPushByAuthor(
        @Query("full") full: Boolean = true,
        @Query("count") count: Int = 10,
        @Query("author") author: String,
    ): TreeherderRevisionResponse

    @GET("jobs/")
    suspend fun getJobsForPush(@Query("push_id") pushId: Int): TreeherderJobsResponse

    @GET
    suspend fun getArtifactsForTask(@Url url: String): ArtifactsResponse
}
