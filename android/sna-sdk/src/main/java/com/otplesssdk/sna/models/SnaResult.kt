package com.otplesssdk.sna.models

/**
 * Result of a single SNA URL execution attempt.
 *
 * - [Success] includes the final HTTP response, timings, and redirect hop trace.
 * - [Failure] includes a failure reason, optional details, timings, and any trace gathered before failure.
 */
sealed class SnaResult {
    data class Success(
        val response: SnaFinalResponse,
        val timings: SnaTimings,
        val redirects: List<SnaRedirectHop>
    ) : SnaResult()

    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
        val timings: SnaTimings,
        val response: SnaFinalResponse? = null,
        val redirects: List<SnaRedirectHop> = emptyList()
    ) : SnaResult()
}

/**
 * High-level reason for why an SNA call failed.
 */
enum class FailureReason {
    INVALID_URL,
    MOBILE_DATA_UNAVAILABLE,
    TIMEOUT,
    REDIRECT_FAILURE,
    NETWORK_ERROR,
    UNKNOWN_ERROR
}

/**
 * Timing breakdown for the overall SNA operation.
 */
data class SnaTimings(
    val totalMs: Long,
    val cellularAcquireMs: Long? = null,
    val processBindMs: Long? = null
)

/**
 * One HTTP header key/value pair.
 */
data class SnaHeader(
    val name: String,
    val value: String
)

/**
 * Final HTTP response metadata for the SNA call (after following redirects).
 *
 * Note: [finalUrl] and [body] can contain sensitive data (tokens/IDs); handle carefully.
 * [body] is capped in size and may be truncated.
 */
data class SnaFinalResponse(
    val finalUrl: String,
    val httpCode: Int,
    val headers: List<SnaHeader>,
    val body: String?,
    val bodyTruncated: Boolean,
    val contentType: String?
)

/**
 * One hop in the redirect chain, with best-effort timing telemetry.
 */
data class SnaRedirectHop(
    val url: String,
    val httpCode: Int?,
    val durationMs: Long,
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null
)
