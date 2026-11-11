package com.example.data

import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class SseFrame(
    val id: Long?,
    val type: String,
    val data: JsonObject
)

/** A file that is read lazily when the request body is built. */
class AttachmentSource(val name: String, val open: () -> InputStream?)

class OrchStream(private val client: OrchClient, cacheDir: File) {

    private val uploads = File(cacheDir, "upload")

    init {
        // Leftovers from a run that was killed before it could clean up.
        uploads.deleteRecursively()
        uploads.mkdirs()
    }

    /**
     * Without attachments this is exactly the request the app always sent: a fixed-length
     * JSON body. With attachments the body is first written to a temp file (Base64 streamed
     * from each source, so memory stays flat) and then sent with a normal Content-Length.
     */
    fun chat(request: ChatRequest, attachments: List<AttachmentSource>): Flow<SseFrame> = flow {
        val head = OrchJson.encodeToString(request)
        if (attachments.isEmpty()) {
            emitAll(events(chatRequest(head.toRequestBody(JSON))))
        } else {
            val payload = withContext(Dispatchers.IO) { writePayload(head, attachments) }
            try {
                emitAll(events(chatRequest(FileBody(payload))))
            } finally {
                payload.delete()
            }
        }
    }

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

    private fun chatRequest(body: RequestBody): Request = Request.Builder()
        .url(client.baseUrl.newBuilder().addPathSegments("cloud/chat/stream").build())
        .post(body)
        .build()

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

    /** Same JSON as encoding the attachments up front, written to disk one Base64 chunk at a time. */
    private fun writePayload(head: String, attachments: List<AttachmentSource>): File {
        val file = File(uploads, "${UUID.randomUUID()}.json")
        try {
            file.sink().buffer().use { sink ->
                sink.writeUtf8(head.removeSuffix("}"))
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
                sink.writeUtf8("]}")
            }
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
        return file
    }

    private class FileBody(private val file: File) : RequestBody() {
        override fun contentType() = JSON
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.source().use { sink.writeAll(it) }
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
