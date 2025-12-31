package com.otplesssdk.utils.ids

import android.content.Context
import android.util.Base64
import com.otplesssdk.utils.deviceinfo.HardwareInfoCollector
import com.otplesssdk.utils.deviceinfo.IdentifiersCollector
import com.otplesssdk.utils.logger.SdkLogger
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility for managing session ID and device ID.
 * Session ID persists until app restart.
 * Device ID persistence notes: GAID can be reset by the user at any time; ANDROID_ID may change after reinstall on Android 8.0+ (API 26+), but remains stable across updates when the app is signed with the same key.
 */
object SessionIdManager {
    private const val PREFS_NAME = "otpless_sdk_ids"
    private const val KEY_DEVICE_ID = "otpless_device_id"
    private const val TAG = "SessionIdManager"
    
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
     * Get or generate a device identifier derived from available identifiers and cached locally.
     *
     * Notes on persistence/availability:
     * - GAID (Google Advertising ID) is retrieved via the Play Services Advertising ID API and
     *   respects user settings (including opt-out/reset). It may be null/unavailable (for example if
     *   Play Services is missing/restricted or the API cannot be accessed).
     * - ANDROID_ID is not a guaranteed permanent identifier: it can change on factory reset, can
     *   differ across per-user profiles on the same device, and may vary due to OEM/ROM behavior.
     *   It is not guaranteed to be stable across reinstalls.
     * - If no suitable identifier is available, this falls back to a randomly generated UUID.
     *
     * Privacy/consent:
     * - Collecting/using GAID has privacy and consent requirements. Only use GAID if your app has a
     *   lawful basis to process it, and obtain user consent where required by law and applicable
     *   platform policies.
     * 
     * Generation priority:
     * 1. Try to get GAID (Google Advertising ID) - if available, hash it
     * 2. If GAID is null/unavailable, try to get device ID (ANDROID_ID) - if available, hash it
     * 3. If both are unavailable, generate a random UUID
     * 
     * The GAID/ANDROID_ID is hashed using SHA-256 to avoid storing the raw value; the hash is
     * deterministic for the same input (but the underlying identifiers may change as noted above).
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
                    
                    // Use apply() for non-blocking persistence to avoid blocking the calling thread
                    prefs.edit()
                        .putString(KEY_DEVICE_ID, deviceId)
                        .apply()
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
            // Deterministic fallback (no UUIDs): preserve determinism across invocations for same input.
            // Use Base64 URL-safe encoding to avoid special characters and line breaks.
            SdkLogger.e(TAG, "Failed to hash identifier; falling back to Base64URL encoding", e)
            Base64.encodeToString(
                identifier.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
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
