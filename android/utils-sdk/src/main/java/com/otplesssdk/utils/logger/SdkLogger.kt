package com.otplesssdk.utils.logger

import android.util.Log

/**
 * Shared logger utility for all SDKs.
 *
 * Enterprise default: logging is disabled unless explicitly enabled by the host app.
 * 
 * Since all SDKs (OTP SDK, SNA SDK, Utils SDK) share this logger, enabling logging
 * once will enable it for all SDKs. You can enable it either:
 * - Globally: SdkLogger.setDebugEnabled(true)
 * - Per SDK: OtpSdk.setLoggingEnabled(true) or SNASdk.setLoggingEnabled(true)
 * 
 * Both methods update the same shared logger instance.
 */
object SdkLogger {
    @Volatile
    private var enabled: Boolean = false

    /**
     * Enable or disable debug logging for all SDKs.
     * Since all SDKs share this logger, enabling it here will enable logging
     * for OTP SDK, SNA SDK, and Utils SDK.
     * 
     * @param enabled true to enable logging, false to disable
     */
    fun setDebugEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Check if logging is enabled.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String) {
        if (enabled) {
            Log.d(tag, message)
        }
    }

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    /**
     * Log an error message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}

