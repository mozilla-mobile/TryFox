package org.mozilla.fenixinstaller.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.mozilla.fenixinstaller.BuildConfig
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class FenixRepository {

    companion object {
        const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
        const val TASKCLUSTER_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/queue/v1/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private fun createOkHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            clientBuilder.addInterceptor(loggingInterceptor)
        }
        return clientBuilder.build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TREEHERDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val treeherderApiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(apiCall.invoke())
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown error", e)
        }
    }

    suspend fun getPushByRevision(project: String, revision: String): NetworkResult<TreeherderRevisionResponse> { // Added project parameter
        return safeApiCall { treeherderApiService.getPushByRevision(project, revision) } // Pass project
    }

    suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
        return safeApiCall { treeherderApiService.getJobsForPush(pushId) }
    }

    suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
        val artifactsUrl = "${TASKCLUSTER_BASE_URL}task/$taskId/runs/0/artifacts"
        return safeApiCall { treeherderApiService.getArtifactsForTask(artifactsUrl) }
    }

    suspend fun downloadArtifact(
        context: Context,
        taskId: String,
        downloadUrl: String,
        fileName: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): NetworkResult<File> {
        return try {
            println("Titouan - Downloading file: $downloadUrl")
            val responseBody = treeherderApiService.downloadFile(downloadUrl)
            println("Titouan - service end of downloadFile")
            val taskSpecificDir = File(context.cacheDir, taskId)
            if (!taskSpecificDir.exists()) {
                taskSpecificDir.mkdirs()
            }
            val outputFile = File(taskSpecificDir, fileName)
            
            val totalBytes = responseBody.contentLength()
            var bytesDownloaded: Long = 0

            withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    inputStream = responseBody.byteStream()
                    outputStream = FileOutputStream(outputFile)
                    val buffer = ByteArray(4 * 1024) // 4KB buffer
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress(bytesDownloaded, totalBytes)
                    }
                    outputStream.flush()
                } catch (e: Exception) {
                    throw e
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            }
            NetworkResult.Success(outputFile)
        } catch (e: Exception) {
            NetworkResult.Error("Download failed: ${e.message}", e)
        }
    }
}
