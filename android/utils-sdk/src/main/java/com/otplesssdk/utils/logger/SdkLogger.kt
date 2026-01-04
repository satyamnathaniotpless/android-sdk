package com.otplesssdk.utils.logger

import android.util.Log

/**
 * Shared logger utility for all SDKs.
 *
 * Enterprise default: logging is disabled unless explicitly enabled by the host app.
 * 
 * Since all SDKs (OTP SDK, SNA SDK, Utils SDK) share this logger, enabling logging
 * once will enable it for all SDKs. You can enable it either:
 * - Globally: SdkLogger.setEnabled(true)
 * - Per SDK: OtpSdk.setLoggingEnabled(true) or SNASdk.setLoggingEnabled(true)
 * 
 * Both methods update the same shared logger instance.
 */
object SdkLogger {
    @Volatile
    private var enabled: Boolean = false

    /**
     * Enable or disable logging for all SDKs.
     * Since all SDKs share this logger, enabling it here will enable logging
     * for all SDKs.
     * 
     * @param enabled true to enable logging, false to disable
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Check if logging is enabled.
     */
    fun isEnabled(): Boolean = enabled

    private const val TAG_PREFIX = "Otpless"
    private const val MAX_TAG_LEN = 23

    private fun normalizeTag(tag: String): String {
        val t = if (tag.startsWith(TAG_PREFIX)) tag else "$TAG_PREFIX-$tag"
        return if (t.length <= MAX_TAG_LEN) t else t.take(MAX_TAG_LEN)
    }

    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String) {
        if (enabled) {
            Log.d(normalizeTag(tag), message)
        }
    }

    fun d(tag: String, message: () -> String) {
        if (!enabled) return
        val msg = try {
            message()
        } catch (_: Exception) {
            return
        }
        Log.d(normalizeTag(tag), msg)
    }

    fun i(tag: String, message: String) {
        if (enabled) {
            Log.i(normalizeTag(tag), message)
        }
    }

    fun i(tag: String, message: () -> String) {
        if (!enabled) return
        val msg = try {
            message()
        } catch (_: Exception) {
            return
        }
        Log.i(normalizeTag(tag), msg)
    }

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.w(normalizeTag(tag), message, throwable)
            } else {
                Log.w(normalizeTag(tag), message)
            }
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (!enabled) return
        val msg = try {
            message()
        } catch (_: Exception) {
            return
        }
        if (throwable != null) {
            Log.w(normalizeTag(tag), msg, throwable)
        } else {
            Log.w(normalizeTag(tag), msg)
        }
    }

    /**
     * Log an error message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.e(normalizeTag(tag), message, throwable)
            } else {
                Log.e(normalizeTag(tag), message)
            }
        }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (!enabled) return
        val msg = try {
            message()
        } catch (_: Exception) {
            return
        }
        if (throwable != null) {
            Log.e(normalizeTag(tag), msg, throwable)
        } else {
            Log.e(normalizeTag(tag), msg)
        }
    }
}

