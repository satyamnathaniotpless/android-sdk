package com.otplesssdk.sna.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import com.otplesssdk.utils.logger.SdkLogger

/**
 * Utility class for network operations
 */
internal object NetworkUtils {
    private const val TAG = "SNASdk:NetworkUtils"
    
    /**
     * Conservative best-effort check for whether mobile data is available/usable for the default
     * data SIM.
     *
     * Requirement: if we can't identify with confidence, return false.
     *
     * ## API-level semantic differences
     * - **Android O+ (API 26+)**: returns `TelephonyManager.isDataEnabled`, which indicates whether
     *   mobile data is **enabled** (user/OS setting) for the default data subscription. This does
     *   **not** guarantee that the device is currently connected to a mobile data network (signal,
     *   carrier availability, data roaming, captive portal, etc.).
     * - **Pre-O (< API 26)**: there is no reliable public API to read a pure "data enabled" toggle.
     *   We instead infer **current availability/connectivity** by first checking the active network
     *   (`ConnectivityManager.getActiveNetworkInfo()`) is **MOBILE**, and then falling back to
     *   `TelephonyManager.dataState` as a secondary signal.
     *
     * Because of these platform limitations, the same `true` value can mean "**enabled**" (API 26+)
     * or "**currently connected/connecting**" (pre-O).
     */
    fun isMobileDataAvailable(context: Context): Boolean {
        // First, try using TelephonyManager for the default data SIM (if available)
        // This aligns with the SNA SDK requirement to use the default data SIM
        val tmForDataCheck: TelephonyManager? = TelephonyHelper.getDefaultDataTelephonyManager(context)
        
        if (tmForDataCheck == null) return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tmForDataCheck.isDataEnabled
            } else {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

                @Suppress("DEPRECATION")
                val activeInfo = cm?.activeNetworkInfo

                @Suppress("DEPRECATION")
                if (activeInfo != null && activeInfo.type == ConnectivityManager.TYPE_MOBILE) {
                    @Suppress("DEPRECATION")
                    if (activeInfo.isConnectedOrConnecting) return@try true
                }

                // Fallback (pre-O): `dataState` reports current data connection state, not the user
                // "mobile data enabled" toggle.
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

    /**
     * Deprecated: prefer [isMobileDataAvailable]. This name is misleading because:
     * - API 26+ returns "enabled"
     * - pre-O returns "connected/connecting" (best-effort)
     */
    @Deprecated(
        message = "Use isMobileDataAvailable(); API 26+ reports enabled, pre-O reports connected/available.",
        replaceWith = ReplaceWith("isMobileDataAvailable(context)")
    )
    fun isMobileDataEnabled(context: Context): Boolean = isMobileDataAvailable(context)
}
