package com.otplesssdk.utils.deviceinfo

import android.os.Build
import java.util.Locale
import java.util.TimeZone

/**
 * Collects system-related information (timezone, locale, emulator, time).
 */
internal object SystemInfoCollector {
    fun collect(): SystemInfo {
        return SystemInfo(
            timezone = getTimezone(),
            locale = getLocale(),
            isEmulator = isEmulator()
        )
    }
    
    private fun getTimezone(): String? {
        return try {
            TimeZone.getDefault().id
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getLocale(): String? {
        return try {
            Locale.getDefault().toString()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isEmulator(): Boolean {
        return try {
            (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == Build.PRODUCT)
        } catch (e: Exception) {
            false
        }
    }
}

internal data class SystemInfo(
    val timezone: String? = null,
    val locale: String? = null,
    val isEmulator: Boolean? = null
)
