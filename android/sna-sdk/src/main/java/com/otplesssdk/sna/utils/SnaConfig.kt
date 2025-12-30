package com.otplesssdk.sna.utils

internal object SnaConfig {
    const val DEFAULT_TIMEOUT_SECONDS = 30L
    const val CELLULAR_ACQUIRE_TIMEOUT_MS = 5_000
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 10L
    const val MAX_FINAL_RESPONSE_BYTES: Long = 64 * 1024
}
