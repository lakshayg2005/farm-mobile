package com.farm.core.api

import android.content.Context
import android.content.SharedPreferences
import com.farm.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Where the API gateway is. Defaults to `farmApiBaseUrl` from gradle.properties and can be changed
 * at runtime (login screen > Server address), so a new IP does not need a new APK.
 */
object ApiConfig {
    private const val PREFS = "farm_config"
    private const val KEY_BASE_URL = "api_base_url"
    private var prefs: SharedPreferences? = null

    val defaultBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** e.g. http://10.50.7.73/api/v1 (no trailing slash). */
    val baseUrl: String
        get() = (prefs?.getString(KEY_BASE_URL, null) ?: defaultBaseUrl).trimEnd('/')

    fun setBaseUrl(url: String?) {
        val clean = url?.trim()?.trimEnd('/')
        prefs?.edit()?.apply {
            if (clean.isNullOrEmpty() || clean == defaultBaseUrl) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, clean)
        }?.apply()
    }
}

/** Thrown when there is no network at all (the request never got an HTTP answer). */
class NetworkException(cause: Throwable? = null) :
    IOException("No connection. Check your network and try again.", cause)

/** An HTTP error from the platform, with the `{ error: { code, message } }` body decoded. */
open class ApiException(message: String, val code: String = "REQUEST_FAILED", val status: Int = 400) :
    Exception(message)

@Serializable
data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
    val detail: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class ErrorEnvelope(val error: ErrorBody? = null)

/** The raw answer of a request: status and body text. */
class HttpResult(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299

    /** The platform's error body, or null when the body is not one. */
    fun error(): ErrorBody? = runCatching { Http.json.decodeFromString<ErrorEnvelope>(body).error }.getOrNull()

    inline fun <reified T> decode(): T = Http.json.decodeFromString(body)
}

object Http {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private val JSON_TYPE = "application/json".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Bearer token attached to every request (set after login, cleared on logout). */
    @Volatile
    var authToken: String? = null

    fun url(path: String): String = ApiConfig.baseUrl + path

    fun jsonBody(text: String): RequestBody = text.toRequestBody(JSON_TYPE)

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    /**
     * One HTTP call. [path] is relative to the gateway base URL unless [absolute] is true.
     * Throws [NetworkException] when the server cannot be reached; HTTP errors are returned.
     */
    suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        token: String? = authToken,
        absolute: Boolean = false,
        rawBody: RequestBody? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(if (absolute) path else url(path))
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        val requestBody = rawBody ?: body?.let { jsonBody(it) }
        builder.method(method, requestBody ?: if (method == "POST" || method == "PUT") jsonBody("{}") else null)
        try {
            client.newCall(builder.build()).execute().use { res ->
                HttpResult(res.code, res.body.string())
            }
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): HttpResult =
        call("GET", path + queryString(query))

    suspend fun post(path: String, body: String? = "{}", headers: Map<String, String> = emptyMap()): HttpResult =
        call("POST", path, body, headers)

    fun queryString(query: Map<String, Any?>): String {
        val parts = query.filterValues { it != null }.map { (k, v) ->
            "${enc(k)}=${enc(v.toString())}"
        }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
