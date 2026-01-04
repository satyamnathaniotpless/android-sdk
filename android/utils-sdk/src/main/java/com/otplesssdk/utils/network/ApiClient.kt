package com.otplesssdk.utils.network

import okhttp3.Call
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import okio.Buffer

object ApiClient {
    data class NetworkPolicy(
        val timeoutMs: Long = 10_000L,
        val maxBodyBytes: Long = 64 * 1024L,
    )

    sealed class ApiError {
        data object Timeout : ApiError()
        data object Canceled : ApiError()
        data class Network(val kind: NetworkErrorKind, val message: String? = null) : ApiError()
        data class Http(val code: Int) : ApiError()
        data class Unknown(val message: String? = null) : ApiError()
    }

    enum class NetworkErrorKind {
        DNS,
        CONNECT,
        TLS,
        IO,
    }

    sealed class ApiResult {
        data class Success(
            val code: Int,
            val headers: Map<String, String>,
            val body: String?,
            val contentType: String?,
        ) : ApiResult()

        data class Failure(
            val error: ApiError,
            val code: Int? = null,
            val headers: Map<String, String> = emptyMap(),
            val bodySnippet: String? = null,
            val contentType: String? = null,
        ) : ApiResult()
    }

    suspend fun execute(
        request: Request,
        policy: NetworkPolicy = NetworkPolicy(),
    ): ApiResult {
        val timeoutMs = policy.timeoutMs.coerceAtLeast(1L)
        return try {
            val client = HttpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            val response = client.newCall(request).await()
            response.use { r ->
                val headers = headersFirstValues(r.headers)
                val contentType = r.body?.contentType()?.toString()
                val bodyRead = readBodyLimited(r, policy.maxBodyBytes)

                if (r.isSuccessful) {
                    ApiResult.Success(
                        code = r.code,
                        headers = headers,
                        body = bodyRead.body,
                        contentType = contentType,
                    )
                } else {
                    ApiResult.Failure(
                        error = ApiError.Http(r.code),
                        code = r.code,
                        headers = headers,
                        bodySnippet = bodyRead.body,
                        contentType = contentType,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(error = NetworkExceptionClassifier.classify(e))
        }
    }

    private data class BodyRead(val body: String?, val truncated: Boolean)

    private fun readBodyLimited(response: Response, maxBytes: Long): BodyRead {
        val body = response.body ?: return BodyRead(null, truncated = false)
        val limit = maxBytes.coerceAtLeast(0L)
        val source = body.source()
        val buffer = Buffer()

        // Request up to limit+1 bytes to detect truncation.
        val toRequest = if (limit == 0L) 1L else limit + 1L
        source.request(toRequest)
        val byteCount = minOf(source.buffer.size, toRequest)
        source.read(buffer, byteCount)

        if (limit == 0L) {
            val hasAny = buffer.size > 0L
            return BodyRead(body = "", truncated = hasAny)
        }

        val truncated = buffer.size > limit
        if (truncated) {
            val prefix = Buffer()
            buffer.read(prefix, limit)
            return BodyRead(body = prefix.readUtf8(), truncated = true)
        }

        return BodyRead(body = buffer.readUtf8(), truncated = false)
    }

    private fun headersFirstValues(headers: Headers): Map<String, String> {
        if (headers.size == 0) return emptyMap()
        val out = LinkedHashMap<String, String>(headers.size)
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            if (!out.containsKey(name)) out[name] = headers.value(i)
        }
        return out
    }
}

