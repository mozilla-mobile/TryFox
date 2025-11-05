package org.mozilla.tryfox.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * A Retrofit service for downloading files from a given URL.
 */
interface DownloadApiService {
    @DisableLogs
    @Streaming
    @GET
    suspend fun downloadFile(@Url downloadUrl: String): ResponseBody
}
