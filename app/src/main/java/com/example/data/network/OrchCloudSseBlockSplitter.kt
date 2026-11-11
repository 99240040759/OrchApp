package com.example.data.network

import com.example.data.model.SseStreamEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class OrchCloudSseBlockSplitter {
    private val buffer = StringBuilder()

    fun push(chunk: String) {
        for (ch in chunk) {
            if (ch != '\r') {
                buffer.append(ch)
            }
        }
    }

    fun nextBlock(): String? {
        val separatorIndex = buffer.indexOf("\n\n")
        if (separatorIndex < 0) return null
        val block = buffer.substring(0, separatorIndex)
        buffer.delete(0, separatorIndex + 2)
        return block
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private fun readTokenLong(obj: JsonObject?, primaryKey: String, fallbackKey: String): Long {
            if (obj == null) return 0L
            return obj[primaryKey]?.jsonPrimitive?.longOrNull
                ?: obj[fallbackKey]?.jsonPrimitive?.longOrNull
                ?: 0L
        }

        fun parseEvent(rawBlock: String): SseStreamEvent? {
            val trimmed = rawBlock.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.lines().all { it.trimStart().startsWith(":") }) {
                return SseStreamEvent.KeepAlive
            }

            var eventId: ULong? = null
            var eventName: String? = null
            val dataLines = mutableListOf<String>()

            for (line in rawBlock.lines()) {
                when {
                    line.startsWith(":") -> Unit
                    line.startsWith("id:") -> {
                        eventId = line.removePrefix("id:").trim().toULongOrNull()
                    }
                    line.startsWith("event:") -> {
                        eventName = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val rest = line.removePrefix("data:")
                        dataLines.add(if (rest.startsWith(" ")) rest.substring(1) else rest)
                    }
                }
            }

            val payloadText = dataLines.joinToString("\n").trim()
            if (eventName == "done" || payloadText == "[DONE]") {
                return SseStreamEvent.Done
            }
            if (payloadText.isEmpty()) return null

            val rootObj: JsonObject = try {
                json.parseToJsonElement(payloadText).jsonObject
            } catch (_: Exception) {
                return null
            }

            val resolvedType = eventName
                ?: rootObj["event_type"]?.jsonPrimitive?.contentOrNull
                ?: rootObj["type"]?.jsonPrimitive?.contentOrNull
                ?: return null

            return when (resolvedType) {
                "step.start" -> {
                    val index = rootObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val step = rootObj["step"] as? JsonObject ?: rootObj
                    SseStreamEvent.StepStart(eventId = eventId, index = index, step = step)
                }
                "step.delta" -> {
                    val index = rootObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val delta = rootObj["delta"] ?: return null
                    SseStreamEvent.StepDelta(eventId = eventId, index = index, delta = delta)
                }
                "step.stop" -> {
                    val index = rootObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val usageObj = (rootObj["step_usage"] as? JsonObject)
                        ?: (rootObj["usage"] as? JsonObject)
                    val input = readTokenLong(usageObj, "total_input_tokens", "input_tokens")
                    val output = readTokenLong(usageObj, "total_output_tokens", "output_tokens")
                    val thought = readTokenLong(usageObj, "total_thought_tokens", "thought_tokens")
                    SseStreamEvent.StepStop(
                        eventId = eventId,
                        index = index,
                        inputTokens = input,
                        outputTokens = output,
                        thoughtTokens = thought
                    )
                }
                "interaction.created" -> {
                    val interaction = rootObj["interaction"] as? JsonObject ?: rootObj
                    val id = interaction["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val envId = interaction["environment_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    SseStreamEvent.InteractionCreated(
                        eventId = eventId,
                        interactionId = id,
                        environmentId = envId
                    )
                }
                "interaction.status_update" -> {
                    val status = rootObj["status"]?.jsonPrimitive?.contentOrNull ?: "in_progress"
                    SseStreamEvent.InteractionStatusUpdate(eventId = eventId, status = status)
                }
                "interaction.completed" -> {
                    val interaction = rootObj["interaction"] as? JsonObject ?: rootObj
                    val usage = (interaction["usage"] as? JsonObject)
                        ?: (rootObj["usage"] as? JsonObject)
                    val input = readTokenLong(usage, "total_input_tokens", "input_tokens")
                    val output = readTokenLong(usage, "total_output_tokens", "output_tokens")
                    val thought = readTokenLong(usage, "total_thought_tokens", "thought_tokens")
                    SseStreamEvent.InteractionCompleted(
                        eventId = eventId,
                        inputTokens = input,
                        outputTokens = output,
                        thoughtTokens = thought
                    )
                }
                "orch.resync" -> {
                    val reason = rootObj["reason"]?.jsonPrimitive?.contentOrNull ?: "tail"
                    SseStreamEvent.OrchResync(eventId = eventId, reason = reason)
                }
                "error" -> {
                    val errObj = rootObj["error"] as? JsonObject ?: rootObj
                    val code = errObj["code"]?.jsonPrimitive?.contentOrNull ?: "stream_error"
                    val message = errObj["message"]?.jsonPrimitive?.contentOrNull
                        ?: "An unexpected stream error occurred."
                    SseStreamEvent.StreamError(eventId = eventId, code = code, message = message)
                }
                else -> null
            }
        }
    }
}
