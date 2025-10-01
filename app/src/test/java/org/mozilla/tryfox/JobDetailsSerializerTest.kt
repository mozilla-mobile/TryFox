package org.mozilla.tryfox

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mozilla.tryfox.data.JobDetails
import org.mozilla.tryfox.data.JobDetailsSerializer

class JobDetailsSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test deserialize valid json`() {
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
            taskId = "YtQZ5UEQRRmVgQNlqvzyhw",
        )
        val actualJobDetails = json.decodeFromString(JobDetailsSerializer, jsonString)
        assertEquals(expectedJobDetails, actualJobDetails)
    }

    @Test
    fun `test deserialize short json array`() {
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
        val exception = assertThrows<SerializationException> {
            json.decodeFromString(JobDetailsSerializer, jsonString)
        }
        assertEquals(
            "JsonArray too short to deserialize into JobDetails. Size: 6, expected at least 15 elements.",
            exception.message,
        )
    }

    @Test
    fun `test deserialize invalid json type`() {
        val jsonString = """
            {
              "key": "value"
            }
        """ // JSON object instead of JsonArray
        val exception = assertThrows<SerializationException> {
            json.decodeFromString(JobDetailsSerializer, jsonString)
        }
        assertEquals("Expected JsonArray", exception.message)
    }

    @Test
    fun `test serialize not supported`() {
        val jobDetails = JobDetails(
            appName = "TestApp",
            jobName = "TestJob",
            jobSymbol = "T",
            taskId = "test-task-id",
        )
        assertThrows<UnsupportedOperationException> {
            json.encodeToString(JobDetailsSerializer, jobDetails)
        }
    }
}