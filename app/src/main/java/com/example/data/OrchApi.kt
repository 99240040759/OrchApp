package com.example.data

import com.example.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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

inline fun <T> runCatchingSuspend(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

interface OrchApi {
    @GET("models")
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

    @GET("cloud/sessions/{id}/workspace")
    suspend fun sessionWorkspace(@Path("id") id: String): Workspace

    @DELETE("cloud/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String)

    @POST("cloud/chat/cancel")
    suspend fun cancel(@Body body: CancelRequest)

    @GET("cloud/files/list")
    suspend fun files(
        @Query("workspace") workspace: String,
        @Query("query") query: String?,
        @Query("limit") limit: Int
    ): List<FileEntry>

    @GET("cloud/files/tree")
    suspend fun fileTree(
        @Query("workspace") workspace: String,
        @Query("path") path: String? = null
    ): DirListing

    @GET("cloud/files/text")
    suspend fun fileText(
        @Query("workspace") workspace: String,
        @Query("path") path: String
    ): FileContent

    @Streaming
    @GET("cloud/files/binary")
    suspend fun fileBinary(
        @Query("workspace") workspace: String,
        @Query("path") path: String
    ): ResponseBody

    @POST("transcribe")
    suspend fun transcribe(@Body body: TranscribeRequest): TranscribeResponse

    @GET("composio/accounts")
    suspend fun connectorAccounts(): ConnectorAccountsResponse

    @POST("composio/connect")
    suspend fun connectConnector(@Body body: ConnectorConnectRequest): ConnectorConnectResponse

    @DELETE("composio/accounts/{id}")
    suspend fun disconnectConnector(@Path("id") id: String)
}

class ApiError(
    val status: Int,
    override val message: String,
    val code: String? = null
) : IOException(message) {
    val retryable: Boolean
        get() = status == 0 || status >= 500 || status == 408
}

fun Throwable.toApiError(): ApiError = when (this) {
    is ApiError -> this
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
        parseErrorBody(code(), body)
    }
    is UnknownHostException -> ApiError(0, "No internet connection.")
    is IOException -> ApiError(0, message ?: "Connection lost.")
    else -> ApiError(-1, message ?: "Something went wrong.")
}

fun parseErrorBody(status: Int, body: String): ApiError {
    val root: JsonObject? = runCatching { OrchJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val message = when (val error = root?.get("error")) {
        is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> error.contentOrNull
        else -> null
    } ?: (root?.get("message") as? JsonPrimitive)?.contentOrNull
    val code = (root?.get("code") as? JsonPrimitive)?.contentOrNull
        ?: ((root?.get("error") as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
    return ApiError(status, message ?: "Request failed (HTTP $status).", code)
}

private const val RETRY_HEADER = "X-Auth-Retry"
private const val TOKEN_TIMEOUT_SECONDS = 10L
private val refreshLock = Any()

private fun idToken(forceRefresh: Boolean): String? =
    Firebase.auth.currentUser?.let {
        Tasks.await(it.getIdToken(forceRefresh), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS).token
    }

private val authInterceptor = Interceptor { chain ->
    val token = runCatching { idToken(false) }.getOrNull()
    val request = chain.request().newBuilder().apply {
        if (token != null) header("Authorization", "Bearer $token")
    }.build()
    chain.proceed(request)
}

private val tokenAuthenticator = Authenticator { _, response: Response ->
    if (response.request.header(RETRY_HEADER) != null) return@Authenticator null
    val rejected = response.request.header("Authorization")?.removePrefix("Bearer ")
    val token = synchronized(refreshLock) {
        runCatching { idToken(false)?.takeIf { it != rejected } ?: idToken(true) }.getOrNull()
    } ?: return@Authenticator null
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
