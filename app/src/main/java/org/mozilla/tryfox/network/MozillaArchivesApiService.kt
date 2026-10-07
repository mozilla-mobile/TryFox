package org.mozilla.tryfox.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Url

/**
 * A Retrofit service for interacting with the Mozilla Archives API.
 */
interface MozillaArchivesApiService {
    @GET
    suspend fun getHtmlPage(@Url url: String): String

    @HEAD
    suspend fun head(@Url url: String): Response<Void>
}
