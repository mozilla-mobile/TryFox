package org.mozilla.tryfox

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test
import org.mozilla.tryfox.data.JobDetails
import org.mozilla.tryfox.data.JobDetailsSerializer

class JobDetailsSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserializeValidJson() {
        val jsonString = """
            [
              1,
              524250358,
              "unknown",
              "?",
              "Gecko Decision Task",
              "D",
              "2025-08-26T15:59:58.199050",
              "gecko-decision",
              1711524,
              "c2f3f652a3a063cb7933c2781038a25974cd09ec",
              "success",
              "2aa083621bb989d6acf1151667288d5fe9616178",
              "completed",
              1,
              "YtQZ5UEQRRmVgQNlqvzyhw",
              0,
              4,
              "opt"
            ]
        """
        val expectedJobDetails = JobDetails(
            appName = "?",
            jobName = "Gecko Decision Task",
            jobSymbol = "D",
            taskId = "YtQZ5UEQRRmVgQNlqvzyhw"
        )
        val actualJobDetails = json.decodeFromString(JobDetailsSerializer, jsonString)
        Assert.assertEquals(expectedJobDetails, actualJobDetails)
    }

    @Test
    fun testDeserializeShortJsonArray() {
        val jsonString = """
            [
              1,
              524250358,
              "unknown",
              "?",
              "Gecko Decision Task",
              "D" 
            ]
        """ // Array with only 6 elements, expecting at least 15
        val exception = Assert.assertThrows(SerializationException::class.java) {
            json.decodeFromString(JobDetailsSerializer, jsonString)
        }
        Assert.assertEquals(
            "JsonArray too short to deserialize into JobDetails. Size: 6, expected at least 15 elements.",
            exception.message
        )
    }

    @Test
    fun testDeserializeInvalidJsonType() {
        val jsonString = """
            {
              "key": "value"
            }
        """ // JSON object instead of JsonArray
        val exception = Assert.assertThrows(SerializationException::class.java) {
            json.decodeFromString(JobDetailsSerializer, jsonString)
        }
        Assert.assertEquals("Expected JsonArray", exception.message)
    }

    @Test
    fun testSerializeNotSupported() {
        val jobDetails = JobDetails(
            appName = "TestApp",
            jobName = "TestJob",
            jobSymbol = "T",
            taskId = "test-task-id"
        )
        Assert.assertThrows(UnsupportedOperationException::class.java) {
            json.encodeToString(JobDetailsSerializer, jobDetails)
        }
    }
}