package com.otplesssdk.utils.deviceinfo

/**
 * Handles caching of device info to avoid expensive re-collection.
 */
internal object DeviceInfoCache {
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    
    // Cached device info JSON (without outer braces) with timestamp
    @Volatile
    private var cachedDeviceInfoJson: String? = null
    
    @Volatile
    private var cachedDeviceInfoTimestamp: Long = 0

    @Volatile
    private var cachedKey: String? = null
    
    // Lock for device info cache operations
    private val cacheLock = Any()
    
    /**
     * Get cached device info JSON if available and not expired.
     */
    fun getCachedJson(sdkVersion: String? = null, sdkName: String? = null): String? {
        val key = "${sdkName.orEmpty()}|${sdkVersion.orEmpty()}"
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            if (cachedDeviceInfoJson != null &&
                (now - cachedDeviceInfoTimestamp) < CACHE_TTL_MS &&
                cachedKey == key
            ) {
                return cachedDeviceInfoJson
            }
        }
        return null
    }
    
    /**
     * Cache device info JSON with current timestamp.
     */
    fun setCachedJson(deviceInfoJson: String, sdkVersion: String? = null, sdkName: String? = null) {
        val key = "${sdkName.orEmpty()}|${sdkVersion.orEmpty()}"
        synchronized(cacheLock) {
            cachedDeviceInfoJson = deviceInfoJson
            cachedDeviceInfoTimestamp = System.currentTimeMillis()
            cachedKey = key
        }
    }
    
    /**
     * Clear the cache.
     */
    fun clear() {
        synchronized(cacheLock) {
            cachedDeviceInfoJson = null
            cachedDeviceInfoTimestamp = 0
            cachedKey = null
        }
    }
}

