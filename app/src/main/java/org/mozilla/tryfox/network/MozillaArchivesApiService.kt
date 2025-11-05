package org.mozilla.tryfox.network

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * A Retrofit service for interacting with the Mozilla Archives API.
 */
interface MozillaArchivesApiService {
    @GET
    suspend fun getHtmlPage(@Url url: String): String
}
