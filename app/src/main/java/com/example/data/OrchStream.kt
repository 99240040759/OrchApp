package com.example.data

import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream

data class SseFrame(
    val id: Long?,
    val type: String,
    val data: JsonObject
)

/** A file that is read lazily and streamed into the request body. */
class AttachmentSource(val name: String, val open: () -> InputStream?)

class OrchStream(private val client: OrchClient) {

    fun chat(request: ChatRequest, attachments: List<AttachmentSource>): Flow<SseFrame> = events(
        Request.Builder()
            .url(client.baseUrl.newBuilder().addPathSegments("cloud/chat/stream").build())
            .post(ChatBody(OrchJson.encodeToString(request), attachments))
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

    /**
     * Writes the chat JSON with attachments encoded as Base64 straight from their
     * input streams, so memory stays flat regardless of attachment size.
     * The wire format is identical to serializing the attachments up front.
     */
    private class ChatBody(
        private val head: String,
        private val attachments: List<AttachmentSource>
    ) : RequestBody() {

        override fun contentType() = JSON

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8(head.removeSuffix("}"))
            if (attachments.isNotEmpty()) {
                sink.writeUtf8(",\"attachments\":[")
                attachments.forEachIndexed { index, attachment ->
                    if (index > 0) sink.writeUtf8(",")
                    sink.writeUtf8("{\"name\":")
                    sink.writeUtf8(JsonPrimitive(attachment.name).toString())
                    sink.writeUtf8(",\"dataBase64\":\"")
                    val input = attachment.open() ?: throw IOException("Unable to read ${attachment.name}.")
                    input.use { source ->
                        Base64OutputStream(sink.outputStream(), Base64.NO_WRAP or Base64.NO_CLOSE).use { encoded ->
                            source.copyTo(encoded)
                        }
                    }
                    sink.writeUtf8("\"}")
                }
                sink.writeUtf8("]")
            }
            sink.writeUtf8("}")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
