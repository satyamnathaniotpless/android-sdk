package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.content.pm.PackageInfo
import com.otplesssdk.utils.ids.SessionIdManager
import com.otplesssdk.utils.logger.SdkLogger
import android.os.Build

/**
 * Utility to automatically collect comprehensive device information for events.
 * Can be used independently by any SDK to get device info as JSON.
 */
object DeviceInfoCollector {
    private const val TAG = "DeviceInfoCollector"
    
    /**
     * Generate or retrieve a persistent device ID.
     * This ID persists across app installs (based on GAID/device ID).
     * 
     * @deprecated Use SessionIdManager.getDeviceId() instead for consistency.
     * This method is kept for backward compatibility.
     */
    @Deprecated("Use SessionIdManager.getDeviceId() instead", ReplaceWith("SessionIdManager.getDeviceId(context)"))
    fun getDeviceId(context: Context): String {
        return SessionIdManager.getDeviceId(context)
    }
    
    
    /**
     * Collect comprehensive device information as JSON (without outer braces).
     * This method never throws exceptions to ensure event sending is never blocked.
     *
     * Device info JSON is cached for 5 minutes (keyed by sdkName/sdkVersion) to avoid expensive re-collection.
     *
     * Permissions (best-effort; missing permissions never throw):
     * - Optional (recommended for richer network fields): `android.permission.ACCESS_NETWORK_STATE` (normal)
     * - Optional (recommended for richer telephony/subscription fields): `android.permission.READ_PHONE_STATE`
     *   or `android.permission.READ_BASIC_PHONE_STATE` (Android 13+). If not granted (or not declared),
     *   telephony/subscription fields will be omitted (null) and collection will continue.
     */
    private fun collectDeviceInfoJson(context: Context, sdkVersion: String? = null, sdkName: String? = null): String {
        // Check cache first
        val cached = DeviceInfoCache.getCachedJson(sdkVersion, sdkName)
        if (cached != null) {
            return cached
        }
        
        // Cache miss or expired - collect fresh device info JSON
        val deviceInfoJson = try {
            // Get system services
            val packageInfo = getPackageInfo(context)
            val telephonyManager = try {
                context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            } catch (e: Exception) {
                null
            }
            val connectivityManager = try {
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            } catch (e: Exception) {
                null
            }
            // Collect information using modular collectors
            val osInfo = OsInfoCollector.collect()
            val buildInfo = BuildInfoCollector.collect()
            val hardwareInfo = HardwareInfoCollector.collect(context)
            val networkInfo = NetworkInfoCollector.collect(context, connectivityManager, telephonyManager)
            val appInfo = AppInfoCollector.collect(context, packageInfo, IdentifiersCollector.getReferrer())
            val systemInfo = SystemInfoCollector.collect()
            val identifiersInfo = IdentifiersCollector.collect(context)
            DeviceInfoJsonBuilder.build(
                osInfo = osInfo,
                buildInfo = buildInfo,
                hardwareInfo = hardwareInfo,
                networkInfo = networkInfo,
                appInfo = appInfo,
                systemInfo = systemInfo,
                identifiersInfo = identifiersInfo,
                sdkVersion = sdkVersion,
                sdkName = sdkName
            )
        } catch (e: Exception) {
            // If anything fails, return minimal device info JSON to ensure event sending is never blocked
            SdkLogger.w(TAG, "Error collecting device info, returning minimal info: ${e.message}")
            DeviceInfoJsonBuilder.buildMinimal(
                sdkVersion = sdkVersion,
                sdkName = sdkName,
                osVersion = Build.VERSION.RELEASE,
                osApiLevel = Build.VERSION.SDK_INT,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                deviceBrand = Build.BRAND,
                deviceProduct = Build.PRODUCT
            )
        }
        
        // Cache the collected device info JSON
        DeviceInfoCache.setCachedJson(deviceInfoJson, sdkVersion, sdkName)
        
        return deviceInfoJson
    }
    
    /**
     * Get device information as JSON string.
     * This is the primary method for other SDKs to get device info.
     * 
     * @param context Application context
     * @param sdkVersion Optional SDK version
     * @param sdkName Optional SDK name
     * @return JSON string containing device information content (without outer braces).
     *         Returns empty string if collection fails. Can be embedded in another JSON object.
     *         To get a complete JSON object, wrap with: {"device_info":{...}}
     *
     * Permissions (all optional; data returned is best-effort):
     * - `android.permission.ACCESS_NETWORK_STATE` (normal): improves network transport detection.
     * - `android.permission.READ_PHONE_STATE` / `android.permission.READ_BASIC_PHONE_STATE` (Android 13+):
     *   enables richer telephony/subscription fields. Without these, those fields will be null.
     */
    fun getDeviceInfoJson(
        context: Context,
        sdkVersion: String? = null,
        sdkName: String? = null
    ): String {
        return try {
            collectDeviceInfoJson(context, sdkVersion, sdkName)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Failed to get device info JSON", e)
            ""
        }
    }

    /**
     * Best-effort PackageInfo (may be null).
     */
    @Suppress("DEPRECATION")
    private fun getPackageInfo(context: Context): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    
    // All helper functions have been moved to dedicated collectors:
    // - NetworkInfoCollector: network, telephony, SIM, VPN, proxy
    // - AppInfoCollector: app version, package, install source, signing
    // - IdentifiersCollector: GAID, DRM ID
    // - HardwareInfoCollector: manufacturer, model, brand, product, RAM, storage
    // - SystemInfoCollector: timezone, locale, emulator
    // - BuildInfoCollector: build information
    // - OsInfoCollector: OS information
    
    /**
     * Start collecting install referrer in the background (non-blocking).
     * This should be called during SDK initialization.
     * The referrer will be cached and included in subsequent device info collections.
     */
    fun startReferrerCollection(context: Context) {
        IdentifiersCollector.startReferrerCollection(context)
    }

    /**
     * Clear in-memory device info cache. Useful when runtime permissions change (e.g., READ_PHONE_STATE granted),
     * so subsequent calls can re-collect richer network/subscription fields immediately.
     */
    fun clearCache() {
        DeviceInfoCache.clear()
    }
}
