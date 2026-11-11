package com.example.data.network

import com.example.data.model.BudgetStatus
import com.example.data.model.CancelRequest
import com.example.data.model.ChatRequest
import com.example.data.model.FileContent
import com.example.data.model.FileEntry
import com.example.data.model.FileMetadata
import com.example.data.model.HistoryPage
import com.example.data.model.ImagePreviewResponse
import com.example.data.model.ModelInfo
import com.example.data.model.SessionSummary
import com.example.data.model.SseStreamEvent
import com.example.data.model.TranscribeRequest
import com.example.data.model.TranscribeResponse
import com.example.data.model.WorkspaceInfo
import com.example.data.model.WorkspaceInitRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OrchCloudApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String
) : IOException(message)

class OrchCloudApiClient(
    private val baseUrlProvider: suspend () -> String,
    private val fcmTokenProvider: suspend () -> String? = { null }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val sseClient = httpClient.newBuilder()
        .readTimeout(30, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun requireBearerToken(): String {
        val user = Firebase.auth.currentUser
            ?: throw OrchCloudApiException(401, "UNAUTHORIZED", "Sign in with Google to access OrchCloud.")
        val tokenResult = user.getIdToken(false).await()
        return tokenResult.token
            ?: throw OrchCloudApiException(401, "TOKEN_UNAVAILABLE", "Unable to retrieve Firebase ID token.")
    }

    private suspend fun Request.Builder.applyAuthHeaders(): Request.Builder {
        val token = requireBearerToken()
        header("Authorization", "Bearer $token")
        val fcmToken = runCatching { fcmTokenProvider() }.getOrNull()
        if (!fcmToken.isNullOrBlank()) {
            header("X-FCM-Token", fcmToken)
        }
        return this
    }

    private suspend fun buildEndpointUrl(
        path: String,
        queryParams: Map<String, String?> = emptyMap()
    ): String {
        val rawBase = baseUrlProvider().trim().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val baseHttpUrl = "$rawBase$cleanPath".toHttpUrlOrNull()
            ?: throw OrchCloudApiException(400, "INVALID_BASE_URL", "Invalid OrchCloud server URL: $rawBase")
        val builder = baseHttpUrl.newBuilder()
        for ((k, v) in queryParams) {
            if (!v.isNullOrBlank()) {
                builder.addQueryParameter(k, v)
            }
        }
        return builder.build().toString()
    }

    private fun parseApiError(statusCode: Int, bodyText: String): OrchCloudApiException {
        return try {
            val root = json.parseToJsonElement(bodyText).jsonObject
            val errObj = root["error"]?.jsonObject ?: root
            val msg = errObj["message"]?.jsonPrimitive?.content ?: "HTTP $statusCode"
            val code = errObj["code"]?.jsonPrimitive?.content ?: "HTTP_$statusCode"
            OrchCloudApiException(statusCode, code, msg)
        } catch (_: Exception) {
            OrchCloudApiException(
                statusCode,
                "HTTP_$statusCode",
                bodyText.take(240).ifBlank { "Request failed with HTTP $statusCode" }
            )
        }
    }

    suspend fun fetchCloudModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl("/cloud/models")
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
                json.decodeFromString<List<ModelInfo>>(text)
            }
        }
    }

    suspend fun fetchBudget(): Result<BudgetStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl("/budget")
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
                json.decodeFromString<BudgetStatus>(text)
            }
        }
    }

    suspend fun initWorkspace(repoUrl: String): Result<WorkspaceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl("/cloud/workspace/init")
            val body = json.encodeToString(WorkspaceInitRequest(repoUrl = repoUrl.trim()))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
                json.decodeFromString<WorkspaceInfo>(text)
            }
        }
    }

    suspend fun listSessions(workspace: String? = null): Result<List<SessionSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl("/cloud/sessions", mapOf("workspace" to workspace))
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<List<SessionSummary>>(text)
                }
            }
        }

    suspend fun fetchSessionHistory(
        sessionId: String,
        limit: Int = 200,
        beforeSeq: Long? = null
    ): Result<HistoryPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl(
                "/cloud/sessions/$sessionId/history",
                mapOf(
                    "limit" to limit.toString(),
                    "before" to beforeSeq?.toString()
                )
            )
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
                json.decodeFromString<HistoryPage>(text)
            }
        }
    }

    suspend fun fetchSessionWorkspace(sessionId: String): Result<WorkspaceInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl("/cloud/sessions/$sessionId/workspace")
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<WorkspaceInfo>(text)
                }
            }
        }

    suspend fun deleteSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl("/cloud/sessions/$sessionId")
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .delete()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
            }
        }
    }

    suspend fun cancelRun(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl("/cloud/chat/cancel")
            val body = json.encodeToString(CancelRequest(sessionId = sessionId))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
            }
        }
    }

    fun streamChat(chatRequest: ChatRequest): Flow<SseStreamEvent> = callbackFlow {
        val token = try {
            requireBearerToken()
        } catch (e: Exception) {
            trySend(SseStreamEvent.StreamError(null, "UNAUTHORIZED", e.message ?: "Auth error"))
            close()
            return@callbackFlow
        }

        val url = try {
            buildEndpointUrl("/cloud/chat/stream")
        } catch (e: Exception) {
            trySend(SseStreamEvent.StreamError(null, "INVALID_URL", e.message ?: "Invalid URL"))
            close()
            return@callbackFlow
        }

        val fcmToken = runCatching { fcmTokenProvider() }.getOrNull()
        val body = json.encodeToString(chatRequest).toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body)
        if (!fcmToken.isNullOrBlank()) {
            requestBuilder.header("X-FCM-Token", fcmToken)
        }

        val call = sseClient.newCall(requestBuilder.build())
        executeSseCall(call) { event ->
            trySend(event)
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    fun resumeChat(sessionId: String, lastEventId: ULong): Flow<SseStreamEvent> = callbackFlow {
        val token = try {
            requireBearerToken()
        } catch (e: Exception) {
            trySend(SseStreamEvent.StreamError(null, "UNAUTHORIZED", e.message ?: "Auth error"))
            close()
            return@callbackFlow
        }

        val url = try {
            buildEndpointUrl(
                "/cloud/chat/resume",
                mapOf(
                    "sessionId" to sessionId,
                    "lastEventId" to lastEventId.toString()
                )
            )
        } catch (e: Exception) {
            trySend(SseStreamEvent.StreamError(null, "INVALID_URL", e.message ?: "Invalid URL"))
            close()
            return@callbackFlow
        }

        val fcmToken = runCatching { fcmTokenProvider() }.getOrNull()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
        if (!fcmToken.isNullOrBlank()) {
            requestBuilder.header("X-FCM-Token", fcmToken)
        }

        val call = sseClient.newCall(requestBuilder.build())
        executeSseCall(call) { event ->
            trySend(event)
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun ProducerScope<SseStreamEvent>.executeSseCall(
        call: Call,
        onEvent: (SseStreamEvent) -> Unit
    ) {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onEvent(
                        SseStreamEvent.StreamError(
                            eventId = null,
                            code = "stream_interrupted",
                            message = e.localizedMessage ?: "SSE stream connection failed."
                        )
                    )
                }
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errText = resp.body?.string().orEmpty()
                        val apiErr = parseApiError(resp.code, errText)
                        onEvent(
                            SseStreamEvent.StreamError(
                                eventId = null,
                                code = apiErr.errorCode,
                                message = apiErr.message
                            )
                        )
                        close()
                        return
                    }

                    val source = resp.body?.source()
                    if (source == null) {
                        close()
                        return
                    }

                    val splitter = OrchCloudSseBlockSplitter()
                    try {
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            splitter.push("$line\n")
                            while (true) {
                                val rawBlock = splitter.nextBlock() ?: break
                                val parsed = OrchCloudSseBlockSplitter.parseEvent(rawBlock)
                                if (parsed != null) {
                                    onEvent(parsed)
                                    if (parsed is SseStreamEvent.Done) {
                                        close()
                                        return
                                    }
                                }
                            }
                        }
                        splitter.push("\n\n")
                        while (true) {
                            val rawBlock = splitter.nextBlock() ?: break
                            val parsed = OrchCloudSseBlockSplitter.parseEvent(rawBlock)
                            if (parsed != null) {
                                onEvent(parsed)
                            }
                        }
                    } catch (e: IOException) {
                        if (!call.isCanceled()) {
                            onEvent(
                                SseStreamEvent.StreamError(
                                    eventId = null,
                                    code = "stream_interrupted",
                                    message = e.localizedMessage ?: "Stream connection closed early."
                                )
                            )
                        }
                    } finally {
                        close()
                    }
                }
            }
        })
    }

    suspend fun listSandboxFiles(
        sessionId: String,
        query: String? = null,
        limit: Int = 200
    ): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildEndpointUrl(
                "/cloud/files/list",
                mapOf(
                    "sessionId" to sessionId,
                    "query" to query,
                    "limit" to limit.coerceIn(1, 2000).toString()
                )
            )
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseApiError(response.code, text)
                json.decodeFromString<List<FileEntry>>(text)
            }
        }
    }

    suspend fun fetchFileMetadata(sessionId: String, path: String): Result<FileMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl(
                    "/cloud/files/metadata",
                    mapOf("sessionId" to sessionId, "path" to path)
                )
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<FileMetadata>(text)
                }
            }
        }

    suspend fun fetchFileText(sessionId: String, path: String): Result<FileContent> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl(
                    "/cloud/files/text",
                    mapOf("sessionId" to sessionId, "path" to path)
                )
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<FileContent>(text)
                }
            }
        }

    suspend fun fetchFileImageDataUrl(sessionId: String, path: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl(
                    "/cloud/files/image",
                    mapOf("sessionId" to sessionId, "path" to path)
                )
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<ImagePreviewResponse>(text).dataUrl
                }
            }
        }

    suspend fun fetchFileBinary(sessionId: String, path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl(
                    "/cloud/files/binary",
                    mapOf("sessionId" to sessionId, "path" to path)
                )
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errText = response.body?.string().orEmpty()
                        throw parseApiError(response.code, errText)
                    }
                    response.body?.bytes() ?: ByteArray(0)
                }
            }
        }

    suspend fun transcribeAudio(base64Audio: String, format: String = "m4a"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildEndpointUrl("/transcribe")
                val body = json.encodeToString(
                    TranscribeRequest(audio = base64Audio, format = format)
                ).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .applyAuthHeaders()
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw parseApiError(response.code, text)
                    json.decodeFromString<TranscribeResponse>(text).text
                }
            }
        }
}
