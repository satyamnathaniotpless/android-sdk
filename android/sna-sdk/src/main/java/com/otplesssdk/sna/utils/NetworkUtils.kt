package com.otplesssdk.sna.utils

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.otplesssdk.utils.logger.SdkLogger

/**
 * Utility class for network operations
 */
internal object NetworkUtils {
    private const val TAG = "SNASdk:NetworkUtils"
    
    /**
     * Conservative check for whether mobile data is enabled/usable for the default data SIM.
     *
     * Requirement: if we can't identify with confidence, return false.
     *
     * - Android O+ (API 26+): uses `TelephonyManager.isDataEnabled` when accessible.
     * - Pre-O: returns true only when `TelephonyManager.dataState == DATA_CONNECTED`.
     * - If access is restricted/denied, returns false (no heuristics).
     */
    fun isMobileDataEnabled(context: Context): Boolean {
        // First, try using TelephonyManager for the default data SIM (if available)
        // This aligns with the SNA SDK requirement to use the default data SIM
        val tmForDataCheck: TelephonyManager? = TelephonyHelper.getDefaultDataTelephonyManager(context)
        
        if (tmForDataCheck == null) return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tmForDataCheck.isDataEnabled
            } else {
                @Suppress("DEPRECATION")
                tmForDataCheck.dataState == TelephonyManager.DATA_CONNECTED
            }
        } catch (_: SecurityException) {
            false
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error checking mobile data state", e)
            false
        }
    }
}
