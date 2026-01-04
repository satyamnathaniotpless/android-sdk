package com.otplesssdk.utils.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Shared exception classification for network operations.
 *
 * Keeping this in one place avoids drift between different SDK modules (SNA/OTP/events/etc.)
 * and between different HTTP entrypoints (e.g., [ApiClient] and custom OkHttp usage).
 */
public object NetworkExceptionClassifier {
    public fun classify(e: Exception): ApiClient.ApiError {
        return when (e) {
            is SocketTimeoutException,
            is InterruptedIOException -> ApiClient.ApiError.Timeout
            is UnknownHostException -> ApiClient.ApiError.Network(ApiClient.NetworkErrorKind.DNS, e.message)
            is SSLException -> ApiClient.ApiError.Network(ApiClient.NetworkErrorKind.TLS, e.message)
            is ConnectException -> ApiClient.ApiError.Network(ApiClient.NetworkErrorKind.CONNECT, e.message)
            is IOException -> ApiClient.ApiError.Network(ApiClient.NetworkErrorKind.IO, e.message)
            else -> ApiClient.ApiError.Unknown("${e.javaClass.name}: ${e.message}")
        }
    }
}

