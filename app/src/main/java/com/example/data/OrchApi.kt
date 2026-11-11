package com.example.data

import com.example.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

val OrchJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Like [runCatching], but never swallows coroutine cancellation. */
inline fun <T> runCatchingSuspend(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

interface OrchApi {
    @GET("cloud/models")
    suspend fun models(): List<ModelInfo>

    @GET("budget")
    suspend fun budget(): Budget

    @POST("cloud/workspace/init")
    suspend fun initWorkspace(@Body body: WorkspaceInit): Workspace

    @GET("cloud/sessions")
    suspend fun sessions(): List<SessionSummary>

    @GET("cloud/sessions/{id}/history")
    suspend fun history(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("before") before: Long?
    ): HistoryPage

    @DELETE("cloud/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String)

    @POST("cloud/chat/cancel")
    suspend fun cancel(@Body body: CancelRequest)

    @GET("cloud/files/list")
    suspend fun files(
        @Query("sessionId") sessionId: String,
        @Query("query") query: String?,
        @Query("limit") limit: Int
    ): List<FileEntry>

    @GET("cloud/files/metadata")
    suspend fun fileMetadata(
        @Query("sessionId") sessionId: String,
        @Query("path") path: String
    ): FileMetadata

    @GET("cloud/files/text")
    suspend fun fileText(
        @Query("sessionId") sessionId: String,
        @Query("path") path: String
    ): FileContent

    @GET("cloud/files/image")
    suspend fun fileImage(
        @Query("sessionId") sessionId: String,
        @Query("path") path: String
    ): ImageData

    @Streaming
    @GET("cloud/files/binary")
    suspend fun fileBinary(
        @Query("sessionId") sessionId: String,
        @Query("path") path: String
    ): ResponseBody

    @POST("transcribe")
    suspend fun transcribe(@Body body: TranscribeRequest): TranscribeResponse
}

class ApiError(
    val status: Int,
    val code: String?,
    override val message: String
) : IOException(message) {
    val retryable: Boolean
        get() = status == 0 || status >= 500 || status == 408
}

fun Throwable.toApiError(): ApiError = when (this) {
    is ApiError -> this
    is HttpException -> {
        val body = response()?.errorBody()?.string().orEmpty()
        parseErrorBody(code(), body)
    }
    is UnknownHostException -> ApiError(0, "NETWORK", "No internet connection.")
    is IOException -> ApiError(0, "NETWORK", message ?: "Connection lost.")
    else -> ApiError(-1, null, message ?: "Something went wrong.")
}

fun parseErrorBody(status: Int, body: String): ApiError {
    val root: JsonObject? = runCatching { OrchJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val message = root?.get("error")?.jsonPrimitive?.contentOrNull
    val code = root?.get("code")?.jsonPrimitive?.contentOrNull
    return ApiError(status, code, message ?: "Request failed (HTTP $status).")
}

private const val RETRY_HEADER = "X-Auth-Retry"

private fun idToken(forceRefresh: Boolean): String? =
    Firebase.auth.currentUser?.let { Tasks.await(it.getIdToken(forceRefresh)).token }

private val authInterceptor = Interceptor { chain ->
    val token = runCatching { idToken(false) }.getOrNull()
    val request = chain.request().newBuilder().apply {
        if (token != null) header("Authorization", "Bearer $token")
    }.build()
    chain.proceed(request)
}

private val tokenAuthenticator = Authenticator { _, response: Response ->
    if (response.request.header(RETRY_HEADER) != null) return@Authenticator null
    val token = runCatching { idToken(true) }.getOrNull() ?: return@Authenticator null
    response.request.newBuilder()
        .header("Authorization", "Bearer $token")
        .header(RETRY_HEADER, "1")
        .build()
}

class OrchClient {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    val sseHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val baseUrl: HttpUrl = (BuildConfig.ORCH_API_BASE_URL.trimEnd('/') + "/").toHttpUrl()

    val api: OrchApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(OrchJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OrchApi::class.java)
}
