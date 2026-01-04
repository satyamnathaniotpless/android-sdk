package com.otplesssdk.utils.time

import android.os.SystemClock

/**
 * Centralized clock helpers.
 *
 * - Use [elapsedRealtime] for TTL / "how long ago" comparisons (monotonic).
 * - Use [wallTimeMillis] only for timestamps meant to be reported externally.
 */
internal object Clock {
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    fun wallTimeMillis(): Long = System.currentTimeMillis()
}

