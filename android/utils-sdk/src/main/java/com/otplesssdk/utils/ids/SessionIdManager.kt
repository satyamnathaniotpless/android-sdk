package com.otplesssdk.utils.ids

import android.content.Context
import com.otplesssdk.utils.deviceinfo.HardwareInfoCollector
import com.otplesssdk.utils.deviceinfo.IdentifiersCollector
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility for managing session ID and device ID.
 * Session ID persists until app restart.
 * Device ID persists across app installs (based on GAID/device ID).
 */
object SessionIdManager {
    private const val PREFS_NAME = "otpless_sdk_ids"
    private const val KEY_DEVICE_ID = "otpless_device_id"
    
    // In-memory cache for session ID (faster access)
    @Volatile
    private var cachedSessionId: String? = null
    
    // Lock for thread-safe session ID operations
    private val sessionLock = Any()
    
    // Lock for thread-safe device ID operations
    private val deviceIdLock = Any()
    
    /**
     * Get or generate session ID.
     * Session ID persists until app restart.
     * If no session ID exists, a new one will be generated.
     * 
     * @param context Application context
     * @return Session ID (never null)
     */
    @Suppress("UNUSED_PARAMETER")
    fun getSessionId(context: Context): String {
        // Check in-memory cache first
        cachedSessionId?.let { return it }

        // Thread-safe: use synchronized block to prevent race condition
        synchronized(sessionLock) {
            // Double-check after acquiring lock
            cachedSessionId?.let { return it }

            // Generate new session ID
            val newSessionId = UUID.randomUUID().toString()
            cachedSessionId = newSessionId

            return newSessionId
        }
    }
    
    /**
     * Get or generate device ID.
     * Device ID persists across app installs (based on GAID/device ID).
     * 
     * Generation priority:
     * 1. Try to get GAID (Google Advertising ID) - if available, hash it
     * 2. If GAID is null, try to get device ID (ANDROID_ID) - if available, hash it
     * 3. If both are null, generate a random UUID
     * 
     * The GAID/device ID is hashed using SHA-256 to make it different from the actual value
     * but always generate the same hash for the same input (deterministic).
     * 
     * @param context Application context
     * @return Device ID (never null)
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId: String? = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            // Thread-safe: use synchronized block to prevent race condition
            synchronized(deviceIdLock) {
                // Double-check after acquiring lock
                deviceId = prefs.getString(KEY_DEVICE_ID, null)
                if (deviceId == null) {
                    // Try to get GAID first (using IdentifiersCollector)
                    val gaid = IdentifiersCollector.getGaid(context)
                    if (gaid != null) {
                        deviceId = hashIdentifier(gaid)
                    } else {
                        // If GAID is null, try to get device ID (using HardwareInfoCollector)
                        val androidId = HardwareInfoCollector.getDeviceId(context)
                        if (androidId != null) {
                            deviceId = hashIdentifier(androidId)
                        } else {
                            // If both are null, generate a random UUID
                            deviceId = UUID.randomUUID().toString()
                        }
                    }
                    
                    // Use commit() for synchronous write to ensure consistency
                    prefs.edit()
                        .putString(KEY_DEVICE_ID, deviceId)
                        .commit()
                }
            }
        }
        
        // deviceId is guaranteed to be non-null at this point
        return deviceId!!
    }
    
    /**
     * Hash an identifier using SHA-256 to make it different from the actual value
     * but always generate the same hash for the same input (deterministic).
     * 
     * @param identifier The identifier to hash (GAID or device ID)
     * @return SHA-256 hash as hex string
     */
    private fun hashIdentifier(identifier: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(identifier.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to UUID if hashing fails
            UUID.randomUUID().toString()
        }
    }
    
    /**
     * Start a new session (generates new session ID).
     * Call this when you want to start a fresh session.
     * 
     * @param context Application context
     * @return New session ID
     */
    @Suppress("UNUSED_PARAMETER")
    fun startNewSession(context: Context): String {
        synchronized(sessionLock) {
            val newSessionId = UUID.randomUUID().toString()
            cachedSessionId = newSessionId

            return newSessionId
        }
    }
    
    /**
     * Clear cached session ID (useful for testing or when app is being destroyed).
     * Note: This only clears the in-memory cache, not the stored value.
     */
    fun clearSessionCache() {
        synchronized(sessionLock) {
            cachedSessionId = null
        }
    }
}
