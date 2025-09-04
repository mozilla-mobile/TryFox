package org.mozilla.fenixinstaller.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

// Custom serializer for JobDetails to handle JsonArray to object mapping by index.
object JobDetailsSerializer : KSerializer<JobDetails> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JobDetails")

    override fun deserialize(decoder: Decoder): JobDetails {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This deserializer can only be used with JSON")
        val jsonArray = jsonInput.decodeJsonElement() as? JsonArray ?: throw SerializationException("Expected JsonArray")

        // Indices based on previous ViewModel logic:
        // appName: index 3
        // jobName: index 4
        // jobSymbol: index 5
        // taskId: index 14
        if (jsonArray.size <= 14) { // Ensure all required indices are accessible
            throw SerializationException("JsonArray too short to deserialize into JobDetails. Size: ${jsonArray.size}, expected at least 15 elements.")
        }

        try {
            val appName = jsonArray[3].jsonPrimitive.content
            val jobName = jsonArray[4].jsonPrimitive.content
            val jobSymbol = jsonArray[5].jsonPrimitive.content
            val taskId = jsonArray[14].jsonPrimitive.content
            return JobDetails(appName, jobName, jobSymbol, taskId)
        } catch (e: Exception) {
            throw SerializationException("Error parsing JobDetails from JsonArray: ${e.message}", e)
        }
    }

    override fun serialize(encoder: Encoder, value: JobDetails) {
        // Serialization back to JsonArray is not strictly needed for this use case
        // as we are only deserializing the response.
        throw UnsupportedOperationException("Serialization of JobDetails to JsonArray not supported.")
    }
}