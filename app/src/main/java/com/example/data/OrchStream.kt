package com.example.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

data class SseFrame(
    val id: Long?,
    val type: String,
    val data: JsonObject
)

class OrchStream(private val client: OrchClient) {

    fun chat(request: ChatRequest): Flow<SseFrame> = events(
        Request.Builder()
            .url(client.baseUrl.newBuilder().addPathSegments("cloud/chat/stream").build())
            .post(OrchJson.encodeToString(request).toRequestBody(JSON))
            .build()
    )

    fun resume(sessionId: String, lastEventId: Long): Flow<SseFrame> = events(
        Request.Builder()
            .url(
                client.baseUrl.newBuilder()
                    .addPathSegments("cloud/chat/resume")
                    .addQueryParameter("sessionId", sessionId)
                    .addQueryParameter("lastEventId", lastEventId.toString())
                    .build()
            )
            .get()
            .build()
    )

    private fun events(request: Request): Flow<SseFrame> = callbackFlow {
        val source = EventSources.createFactory(client.sseHttp).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val payload = runCatching { OrchJson.parseToJsonElement(data).jsonObject }.getOrNull()
                    val kind = type?.takeIf { it.isNotBlank() }
                        ?: payload?.get("event_type")?.jsonPrimitive?.contentOrNull
                        ?: return
                    trySend(SseFrame(id?.toLongOrNull(), kind, payload ?: JsonObject(emptyMap())))
                    if (kind == "done") close()
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val failure = if (response != null && !response.isSuccessful) {
                        parseErrorBody(response.code, response.body?.string().orEmpty())
                    } else {
                        t?.toApiError() ?: ApiError(0, null, "Connection lost.")
                    }
                    close(failure)
                }
            }
        )
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
