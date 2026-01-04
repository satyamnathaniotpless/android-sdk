package com.otplesssdk.sna.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.otplesssdk.sna.models.FailureReason
import com.otplesssdk.sna.models.SnaFinalResponse
import com.otplesssdk.sna.models.SnaHeader
import com.otplesssdk.sna.models.SnaResult
import com.otplesssdk.sna.models.SnaTimings
import com.otplesssdk.utils.logger.SdkLogger
import com.otplesssdk.utils.network.HttpClient
import com.otplesssdk.utils.network.NetworkExceptionClassifier
import com.otplesssdk.utils.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import okio.Buffer

internal object SnaUrlHandler {
    private const val TAG = "SNASdk:SnaUrlHandler"
    private val bindMutex = Mutex()

    private fun formatHops(redirects: List<com.otplesssdk.sna.models.SnaRedirectHop>): String {
        if (redirects.isEmpty()) return "none"
        return redirects.joinToString(separator = " | ") { hop ->
            val code = hop.httpCode?.toString() ?: "-"
            val dns = hop.dnsMs?.let { " dns=${it}ms" } ?: ""
            val connect = hop.connectMs?.let { " connect=${it}ms" } ?: ""
            val tls = hop.tlsMs?.let { " tls=${it}ms" } ?: ""
            "$code ${hop.url} ${hop.durationMs}ms$dns$connect$tls"
        }
    }

    private fun classifyException(e: Exception): Pair<FailureReason, String?> {
        return when (e) {
            is SocketTimeoutException -> FailureReason.TIMEOUT to "SOCKET_TIMEOUT"
            is InterruptedIOException -> FailureReason.TIMEOUT to "IO_TIMEOUT"
            is ProtocolException -> {
                if (e.message?.contains("Too many follow-up requests") == true) {
                    FailureReason.REDIRECT_FAILURE to "TOO_MANY_REDIRECTS"
                } else {
                    FailureReason.NETWORK_ERROR to e.message
                }
            }
            is UnknownServiceException -> {
                if (e.message?.contains("CLEARTEXT communication") == true) {
                    FailureReason.NETWORK_ERROR to "CLEARTEXT_NOT_PERMITTED"
                } else {
                    FailureReason.NETWORK_ERROR to e.message
                }
            }
            else -> {
                // Base classification on the shared utils-sdk classifier to avoid drift with other modules.
                when (val apiError = NetworkExceptionClassifier.classify(e)) {
                    is com.otplesssdk.utils.network.ApiClient.ApiError.Timeout ->
                        FailureReason.TIMEOUT to "IO_TIMEOUT"
                    is com.otplesssdk.utils.network.ApiClient.ApiError.Network -> {
                        val detail = when (apiError.kind) {
                            com.otplesssdk.utils.network.ApiClient.NetworkErrorKind.DNS -> "DNS_FAILED"
                            com.otplesssdk.utils.network.ApiClient.NetworkErrorKind.CONNECT -> "CONNECT_FAILED"
                            com.otplesssdk.utils.network.ApiClient.NetworkErrorKind.TLS -> "TLS_ERROR"
                            com.otplesssdk.utils.network.ApiClient.NetworkErrorKind.IO -> apiError.message
                        }
                        FailureReason.NETWORK_ERROR to detail
                    }
                    // SnaUrlHandler preserves coroutine cancellation separately (see catch(CancellationException)).
                    is com.otplesssdk.utils.network.ApiClient.ApiError.Canceled ->
                        FailureReason.UNKNOWN_ERROR to "CANCELED"
                    is com.otplesssdk.utils.network.ApiClient.ApiError.Http ->
                        FailureReason.NETWORK_ERROR to "HTTP_${apiError.code}"
                    is com.otplesssdk.utils.network.ApiClient.ApiError.Unknown ->
                        FailureReason.NETWORK_ERROR to apiError.message
                }
            }
        }
    }

    private fun headersFrom(response: Response): List<SnaHeader> {
        val headers = response.headers
        val out = ArrayList<SnaHeader>(headers.size)
        for (i in 0 until headers.size) {
            out.add(SnaHeader(headers.name(i), headers.value(i)))
        }
        return out
    }

    private data class BodyRead(
        val body: String?,
        val truncated: Boolean,
        val contentType: String?
    )

    private fun readBodyLimited(response: Response): BodyRead {
        val responseBody = response.body ?: return BodyRead(body = null, truncated = false, contentType = null)
        val contentType = responseBody.contentType()
        val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8

        val maxBytes = SnaConfig.MAX_FINAL_RESPONSE_BYTES
        val source = responseBody.source()
        val buffer = Buffer()

        var totalRead = 0L
        while (totalRead < maxBytes + 1) {
            val read = source.read(buffer, (maxBytes + 1) - totalRead)
            if (read == -1L) break
            totalRead += read
        }

        val truncated = totalRead > maxBytes
        val bytes = if (truncated) {
            buffer.readByteArray(maxBytes)
        } else {
            buffer.readByteArray()
        }

        return BodyRead(
            body = String(bytes, charset),
            truncated = truncated,
            contentType = contentType?.toString()
        )
    }

    suspend fun execute(
        context: Context,
        urlString: String,
        timeoutSeconds: Long
    ): SnaResult = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        var cellularAcquireMs: Long? = null
        var processBindMs: Long? = null

        fun timingsNow(): SnaTimings =
            SnaTimings(
                totalMs = SystemClock.elapsedRealtime() - startMs,
                cellularAcquireMs = cellularAcquireMs,
                processBindMs = processBindMs
            )

        val httpUrl = urlString.toHttpUrlOrNull()
            ?: return@withContext SnaResult.Failure(
                reason = FailureReason.INVALID_URL,
                detail = "Invalid URL",
                timings = timingsNow()
            )

        val timeoutMs = timeoutSeconds.coerceAtLeast(1) * 1000
        val tracer = SnaRedirectTracer()

        try {
            withTimeout(timeoutMs) {
                bindMutex.withLock {
                    SdkLogger.d(
                        TAG,
                        "Starting SNA call url=${httpUrl} timeoutSeconds=$timeoutSeconds"
                    )
                    executeWithOptionalCellularBinding(
                        context = context,
                        httpUrlString = httpUrl.toString(),
                        timeoutSeconds = timeoutSeconds,
                        tracer = tracer,
                        timingsNow = ::timingsNow,
                        onCellularAcquireMs = { cellularAcquireMs = it },
                        onProcessBindMs = { processBindMs = it }
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            SdkLogger.w(TAG, "SNA call timed out after ${timeoutSeconds}s")
            SnaResult.Failure(
                reason = FailureReason.TIMEOUT,
                detail = "Timed out after ${timeoutSeconds}s",
                timings = timingsNow(),
                redirects = tracer.snapshot()
            )
        } catch (e: CancellationException) {
            // Preserve cancellation semantics for callers; do not convert into a Failure.
            throw e
        } catch (e: Exception) {
            val (reason, detail) = classifyException(e)
            SdkLogger.e(TAG, "SNA failed reason=$reason detail=$detail", e)
            SnaResult.Failure(
                reason = reason,
                detail = detail,
                timings = timingsNow(),
                redirects = tracer.snapshot()
            )
        }
    }

    private suspend fun executeWithOptionalCellularBinding(
        context: Context,
        httpUrlString: String,
        timeoutSeconds: Long,
        tracer: SnaRedirectTracer,
        timingsNow: () -> SnaTimings,
        onCellularAcquireMs: (Long) -> Unit,
        onProcessBindMs: (Long) -> Unit
    ): SnaResult {
        val client = HttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .eventListener(tracer)
            .callTimeout(timeoutSeconds.coerceAtLeast(1), TimeUnit.SECONDS)
            .connectTimeout(SnaConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SnaConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(httpUrlString)
            .build()

        val call = client.newCall(request)
        var releaseNetworkRequest: (() -> Unit)? = null

        val bound: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SdkLogger.d(TAG, "Acquiring cellular network for SNA (API 23+)")
            val acquireStartMs = SystemClock.elapsedRealtime()
            val networkResult = CellularNetworkBinder.acquireCellularNetwork(
                context = context,
                timeoutMs = SnaConfig.CELLULAR_ACQUIRE_TIMEOUT_MS
            )
            onCellularAcquireMs(SystemClock.elapsedRealtime() - acquireStartMs)
            if (networkResult == null) {
                return SnaResult.Failure(
                    reason = FailureReason.MOBILE_DATA_UNAVAILABLE,
                    detail = "No cellular network available",
                    timings = timingsNow(),
                    redirects = tracer.snapshot()
                )
            }
            val (network, release) = networkResult
            releaseNetworkRequest = release
            val bindStartMs = SystemClock.elapsedRealtime()
            val boundOk = CellularNetworkBinder.bindProcessToNetwork(context, network)
            onProcessBindMs(SystemClock.elapsedRealtime() - bindStartMs)
            if (!boundOk) {
                SdkLogger.w(TAG, "Failed to bind process to cellular network; proceeding anyway")
            } else {
                SdkLogger.d(TAG, "Process bound to cellular; executing HTTP call")
            }
            boundOk
        } else {
            SdkLogger.w(TAG, "API < 23: cannot bind process to a specific network; using default route")
            false
        }

        return try {
            SdkLogger.d(TAG, "Executing request: $httpUrlString")
            val response = call.await()
            val finalUrl = response.request.url.toString()
            val code = response.code

            response.use {
                val bodyRead = readBodyLimited(response)
                val finalResponse = SnaFinalResponse(
                    finalUrl = finalUrl,
                    httpCode = code,
                    headers = headersFrom(response),
                    body = bodyRead.body,
                    bodyTruncated = bodyRead.truncated,
                    contentType = bodyRead.contentType
                )

                if (code in 200..299) {
                    val hops = tracer.snapshot()
                    SdkLogger.d(
                        TAG,
                        "SNA success httpCode=$code totalMs=${timingsNow().totalMs} finalUrl=$finalUrl hops=${hops.size} (${formatHops(hops)})"
                    )
                    SnaResult.Success(
                        response = finalResponse,
                        timings = timingsNow(),
                        redirects = hops
                    )
                } else {
                    val hops = tracer.snapshot()
                    SdkLogger.w(
                        TAG,
                        "SNA non-2xx httpCode=$code totalMs=${timingsNow().totalMs} finalUrl=$finalUrl hops=${hops.size} (${formatHops(hops)})"
                    )
                    SnaResult.Failure(
                        reason = FailureReason.NETWORK_ERROR,
                        detail = "HTTP $code",
                        timings = timingsNow(),
                        response = finalResponse,
                        redirects = hops
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val (reason, detail) = classifyException(e)
            val hops = tracer.snapshot()
            SdkLogger.e(
                TAG,
                "Network error url=$httpUrlString reason=$reason detail=$detail hops=${hops.size} (${formatHops(hops)})",
                e
            )
            SnaResult.Failure(
                reason = reason,
                detail = detail,
                timings = timingsNow(),
                redirects = hops
            )
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Always unbind; this is a process-wide setting.
                if (bound) CellularNetworkBinder.bindProcessToNetwork(context, null)
            }
            if (releaseNetworkRequest != null) {
                SdkLogger.d(TAG, "Releasing cellular network request")
            }
            releaseNetworkRequest?.invoke()
        }
    }
}
