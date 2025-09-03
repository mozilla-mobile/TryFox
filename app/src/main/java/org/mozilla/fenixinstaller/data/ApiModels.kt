package org.mozilla.fenixinstaller.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TreeherderRevisionResponse(
    val meta: RevisionMeta,
    val results: List<RevisionResult>
)

@Serializable
data class RevisionMeta(
    val revision: String,
    val count: Int,
    val repository: String
)

@Serializable
data class RevisionResult(
    val id: Int,
    val revision: String,
    val author: String,
    val revisions: List<RevisionDetail>,
    @SerialName("revision_count")
    val revisionCount: Int,
    @SerialName("push_timestamp")
    val pushTimestamp: Long,
    @SerialName("repository_id")
    val repositoryId: Int
)

@Serializable
data class RevisionDetail(
    @SerialName("result_set_id")
    val resultSetId: Int,
    @SerialName("repository_id")
    val repositoryId: Int,
    val revision: String,
    val author: String,
    val comments: String
)

@Serializable(with = JobDetailsSerializer::class) // Serializer is now in a separate file
data class JobDetails(
    val appName: String,
    val jobName: String,
    val jobSymbol: String,
    val taskId: String
) {
    val isSignedBuild: Boolean 
        get() = jobSymbol.contains("B") && jobSymbol.contains("s")

    val isTest: Boolean 
        get() = jobSymbol.contains("t")
}

@Serializable
data class TreeherderJobsResponse(
    val results: List<JobDetails> 
)

// JobInfo data class has been removed.

@Serializable
data class ArtifactsResponse(
    val artifacts: List<Artifact>
)

@Serializable
data class Artifact(
    val storageType: String,
    val name: String,
    val expires: String,
    val contentType: String
) {
    fun getDownloadUrl(taskId: String): String {
        return "https://firefox-ci-tc.services.mozilla.com/api/queue/v1/task/$taskId/runs/0/artifacts/$name"
    }
    
    val abi: String?
        get() {
            val regex = "target\\.([^.]+)\\.apk$".toRegex()
            return regex.find(name)?.groups?.get(1)?.value
        }
}
