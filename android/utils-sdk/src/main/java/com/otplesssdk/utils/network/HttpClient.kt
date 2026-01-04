package com.otplesssdk.utils.network

import android.content.Context
import com.otplesssdk.utils.ids.SessionIdManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object HttpClient {
    private const val HEADER_APP_ID = "x-app-id"
    private const val HEADER_INID = "x-inid"
    private const val HEADER_TSID = "x-tsid"
    private const val HEADER_SDK_NAME = "x-sdk-name"
    private const val HEADER_SDK_VERSION = "x-sdk-version"
    private const val HEADER_USER_ID = "x-user_id"
    private const val HEADER_ASID = "x-asid"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var appId: String? = null

    @Volatile
    private var sdkName: String? = null

    @Volatile
    private var sdkVersion: String? = null

    @Volatile
    private var userId: String? = null

    @Volatile
    private var asid: String? = null

    @Volatile
    private var customHeaders: Map<String, String> = emptyMap()

    private val commonHeadersInterceptor = Interceptor { chain ->
        val req = chain.request()

        val ctx = appContext
        val currentAppId = appId
        val currentSdkName = sdkName
        val currentSdkVersion = sdkVersion
        val currentUserId = userId
        val currentAsid = asid
        val currentCustomHeaders = customHeaders

        // Only add headers when we have values and the request didn't already set them.
        val b = req.newBuilder()
        if (currentAppId != null && req.header(HEADER_APP_ID).isNullOrBlank()) {
            b.header(HEADER_APP_ID, currentAppId)
        }
        if (currentSdkName != null && req.header(HEADER_SDK_NAME).isNullOrBlank()) {
            b.header(HEADER_SDK_NAME, currentSdkName)
        }
        if (currentSdkVersion != null && req.header(HEADER_SDK_VERSION).isNullOrBlank()) {
            b.header(HEADER_SDK_VERSION, currentSdkVersion)
        }
        if (currentUserId != null && req.header(HEADER_USER_ID).isNullOrBlank()) {
            b.header(HEADER_USER_ID, currentUserId)
        }
        if (currentAsid != null && req.header(HEADER_ASID).isNullOrBlank()) {
            b.header(HEADER_ASID, currentAsid)
        }
        if (ctx != null) {
            if (req.header(HEADER_INID).isNullOrBlank()) {
                b.header(HEADER_INID, SessionIdManager.getInId(ctx))
            }
            if (req.header(HEADER_TSID).isNullOrBlank()) {
                b.header(HEADER_TSID, SessionIdManager.getTsId(ctx))
            }
        }

        if (currentCustomHeaders.isNotEmpty()) {
            for ((rawName, value) in currentCustomHeaders) {
                val name = normalizeHeaderName(rawName)
                if (name.isNotBlank() && value.isNotBlank() && req.header(name).isNullOrBlank()) {
                    b.header(name, value)
                }
            }
        }

        chain.proceed(b.build())
    }

    private val base: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(commonHeadersInterceptor)
        .build()
    private val byTimeoutSeconds = ConcurrentHashMap<Long, OkHttpClient>()

    fun configure(
        context: Context,
        appId: String? = null,
        sdkName: String? = null,
        sdkVersion: String? = null,
        userId: String? = null,
        asid: String? = null
    ) {
        appContext = context.applicationContext
        this.appId = appId?.takeIf { it.isNotBlank() }
        this.sdkName = sdkName?.takeIf { it.isNotBlank() }
        this.sdkVersion = sdkVersion?.takeIf { it.isNotBlank() }
        this.userId = userId?.takeIf { it.isNotBlank() }
        this.asid = asid?.takeIf { it.isNotBlank() }
    }

    fun setSdkInfo(sdkName: String? = null, sdkVersion: String? = null) {
        this.sdkName = sdkName?.takeIf { it.isNotBlank() }
        this.sdkVersion = sdkVersion?.takeIf { it.isNotBlank() }
    }

    fun setUserInfo(userId: String? = null, asid: String? = null) {
        this.userId = userId?.takeIf { it.isNotBlank() }
        this.asid = asid?.takeIf { it.isNotBlank() }
    }

    /**
     * Sets custom headers to be attached on every HTTP request made via [HttpClient].
     *
     * All header names are normalized to start with "x-" (case-insensitive). Per-request headers
     * always win: if a request already contains the same header name, it will not be overwritten.
     */
    fun setCustomHeaders(headers: Map<String, String>?) {
        customHeaders = headers
            ?.mapNotNull { (k, v) ->
                val name = normalizeHeaderName(k)
                val value = v.trim()
                if (name.isBlank() || value.isBlank()) null else name to value
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun putCustomHeader(name: String, value: String?) {
        val normalized = normalizeHeaderName(name)
        if (normalized.isBlank()) return
        val v = value?.trim()
        customHeaders = if (v.isNullOrBlank()) {
            customHeaders - normalized
        } else {
            customHeaders + (normalized to v)
        }
    }

    fun clearCustomHeaders() {
        customHeaders = emptyMap()
    }

    private fun normalizeHeaderName(name: String?): String {
        val raw = name?.trim().orEmpty()
        if (raw.isBlank()) return ""
        val lower = raw.lowercase()
        return if (lower.startsWith("x-")) lower else "x-$lower"
    }

    fun newBuilder(): OkHttpClient.Builder = base.newBuilder()

    fun client(timeoutSeconds: Long): OkHttpClient {
        val t = timeoutSeconds.coerceAtLeast(1)
        return byTimeoutSeconds.getOrPut(t) {
            base.newBuilder()
                .connectTimeout(t, TimeUnit.SECONDS)
                .readTimeout(t, TimeUnit.SECONDS)
                .writeTimeout(t, TimeUnit.SECONDS)
                .build()
        }
    }
}

