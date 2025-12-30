package com.otplesssdk.utils.event

import android.content.Context
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector
import com.otplesssdk.utils.ids.SessionIdManager
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility for sending events to the event database.
 * Events are sent asynchronously and failures are logged but don't throw exceptions.
 */
object EventSender {
    private const val TAG = "EventSender"
    private const val DEFAULT_TIMEOUT_SECONDS = 10L
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Session-based event counter (resets when app restarts)
    private val sessionEventCounter = AtomicInteger(0)
    
    // Context for device info collection (set by SDKs during initialization)
    @Volatile
    private var appContext: Context? = null
    
    // SDK information (set during initialization or globally)
    @Volatile
    var sdkVersion: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            if (value != null && value.isNotBlank()) {
                SdkLogger.d(TAG, "SDK version set globally: $value")
            }
        }
    
    @Volatile
    var sdkName: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            if (value != null && value.isNotBlank()) {
                SdkLogger.d(TAG, "SDK name set globally: $value")
            }
        }
    
    // App ID for x-app-id header (optional, set during initialization)
    @Volatile
    private var appId: String? = null

    /**
     * Event database endpoint URL.
     * Set this before sending events, or events will be logged but not sent.
     */
    @Volatile
    var eventEndpoint: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            if (value != null && value.isNotBlank()) {
                SdkLogger.d(TAG, "Event endpoint set: $value")
            } else {
                SdkLogger.w(TAG, "Event endpoint cleared")
            }
        }
    
    /**
     * Initialize the event sender with application context.
     * This enables automatic device info collection, install ID, and session ID generation.
     * 
     * @param context Application context
     * @param sdkName SDK name (e.g., "otp-sdk", "sna-sdk") - will be used only if global EventSender.sdkName is not set
     * @param sdkVersion SDK version (e.g., "1.0.0") - will be used only if global EventSender.sdkVersion is not set
     * @param appId Optional app ID for x-app-id header
     * @param eventEndpoint Optional default event endpoint URL for this SDK (will be used only if global EventSender.eventEndpoint is not set)
     */
    fun initialize(
        context: Context,
        sdkName: String? = null,
        sdkVersion: String? = null,
        appId: String? = null,
        eventEndpoint: String? = null
    ) {
        appContext = context.applicationContext
        this.appId = appId?.takeIf { it.isNotBlank() }
        
        // Set SDK name only if:
        // 1. A name is provided during initialization, AND
        // 2. No global name has been set yet (allows global override to take precedence)
        if (sdkName != null && sdkName.isNotBlank() && this.sdkName.isNullOrBlank()) {
            this.sdkName = sdkName
            SdkLogger.d(TAG, "SDK name set from SDK initialization: $sdkName")
        } else if (this.sdkName != null) {
            SdkLogger.d(TAG, "Using global SDK name (SDK name ignored): ${this.sdkName}")
        }
        
        // Set SDK version only if:
        // 1. A version is provided during initialization, AND
        // 2. No global version has been set yet (allows global override to take precedence)
        if (sdkVersion != null && sdkVersion.isNotBlank() && this.sdkVersion.isNullOrBlank()) {
            this.sdkVersion = sdkVersion
            SdkLogger.d(TAG, "SDK version set from SDK initialization: $sdkVersion")
        } else if (this.sdkVersion != null) {
            SdkLogger.d(TAG, "Using global SDK version (SDK version ignored): ${this.sdkVersion}")
        }
        
        // Set event endpoint only if:
        // 1. An endpoint is provided during initialization, AND
        // 2. No global endpoint has been set yet (allows global override to take precedence)
        if (eventEndpoint != null && eventEndpoint.isNotBlank() && this.eventEndpoint.isNullOrBlank()) {
            this.eventEndpoint = eventEndpoint
            SdkLogger.d(TAG, "Event endpoint set from SDK initialization: $eventEndpoint")
        } else if (this.eventEndpoint != null) {
            SdkLogger.d(TAG, "Using global event endpoint (SDK endpoint ignored): ${this.eventEndpoint}")
        }
        
        // Get or generate device ID (will be generated if not exists)
        val deviceId = SessionIdManager.getDeviceId(context.applicationContext)
        SdkLogger.d(TAG, "Device ID: $deviceId")
        
        // Get or generate session ID (will be generated if not exists)
        val sessionId = SessionIdManager.getSessionId(context.applicationContext)
        SdkLogger.d(TAG, "Session ID: $sessionId")
        
        SdkLogger.d(TAG, "EventSender initialized - SDK: ${this.sdkName}, Version: ${this.sdkVersion}, App ID: ${this.appId ?: "not set"}, Endpoint: ${this.eventEndpoint ?: "not set"}")
        
        // Start collecting install referrer in background (non-blocking)
        DeviceInfoCollector.startReferrerCollection(context.applicationContext)
    }
    
    /**
     * Get the persistent device ID (persists across app installs).
     * If no device ID exists, a new one will be generated.
     * Returns null if EventSender has not been initialized.
     * 
     * @return Device ID or null if not initialized
     */
    fun getDeviceId(): String? {
        val context = appContext ?: return null
        return SessionIdManager.getDeviceId(context)
    }
    
    /**
     * Get the current session ID (persists until app restart).
     * If no session ID exists, a new one will be generated.
     * Returns null if EventSender has not been initialized.
     * 
     * @return Session ID or null if not initialized
     */
    fun getSessionId(): String? {
        val context = appContext ?: return null
        return SessionIdManager.getSessionId(context)
    }
    
    /**
     * Reset the session event counter (call when starting a new session).
     * Note: This only resets the event counter, not the session ID.
     */
    fun resetSessionCounter() {
        sessionEventCounter.set(0)
        SdkLogger.d(TAG, "Session event counter reset")
    }
    
    /**
     * Start a new session (generates new session ID and resets event counter).
     * Call this when you want to start a fresh session.
     */
    fun startNewSession() {
        val context = appContext
        if (context == null) {
            SdkLogger.w(TAG, "Cannot start new session: EventSender not initialized")
            return
        }
        
        val newSessionId = SessionIdManager.startNewSession(context)
        sessionEventCounter.set(0)
        SdkLogger.d(TAG, "New session started: $newSessionId")
    }
    
    /**
     * Get the next event ID for the current session.
     */
    private fun getNextEventId(): Int {
        return sessionEventCounter.incrementAndGet()
    }
    
    /**
     * Enhance event data with automatic fields (eventId, deviceId, sessionId, deviceInfo).
     */
    private fun enhanceEventData(event: EventData): EventData {
        val context = appContext
        val nextEventId = getNextEventId()
        
        // Get device ID if context is available
        val deviceId = if (context != null) {
            SessionIdManager.getDeviceId(context)
        } else {
            event.deviceId
        }
        
        // Get session ID (use existing from event or get/generate from SessionIdManager)
        val enhancedSessionId = if (context != null) {
            event.sessionId ?: SessionIdManager.getSessionId(context)
        } else {
            event.sessionId
        }
        
        // Use SDK name from initialization or from event
        val finalSdkName = sdkName?.takeIf { it.isNotBlank() } ?: event.sdkName
        
        // Note: Device info is now collected as JSON string during event building,
        // not stored in the EventData object. This allows other SDKs to use device info independently.
        
        return event.copy(
            eventId = nextEventId,
            deviceId = deviceId,
            sessionId = enhancedSessionId,
            sdkName = finalSdkName
            // deviceInfo is no longer stored in EventData - it's collected as JSON during event building
        )
    }

    /**
     * Send an event to the event database asynchronously.
     * This method is fire-and-forget and will not throw exceptions.
     * Events are always sent asynchronously.
     *
     * @param eventName Event name/type
     * @param properties Event properties/metadata
     * @param requestId Optional request ID
     * @param asid Optional ASID
     * @param state Optional state parameter
     * @param userId Optional user ID
     */
    fun sendEvent(
        eventName: String,
        properties: Map<String, Any?> = emptyMap(),
        requestId: String? = null,
        asid: String? = null,
        state: String? = null,
        userId: String? = null
    ) {
        val scope = SdkCoroutineScope.createIOScope()
        scope.launch {
            val event = EventData(
                sdkName = sdkName ?: "unknown",
                eventName = eventName,
                properties = properties,
                requestId = requestId,
                asid = asid,
                state = state,
                userId = userId
            )
            sendEventInternal(event)
        }
    }
    
    /**
     * Internal method to send event.
     */
    private suspend fun sendEventInternal(event: EventData) {
        // Validate required fields
        if (event.eventName.isEmpty()) {
            SdkLogger.e(TAG, "Event validation failed: eventName is required")
            return
        }
        val effectiveSdkName = event.sdkName.takeIf { it.isNotBlank() } ?: sdkName
        if (effectiveSdkName.isNullOrBlank()) {
            SdkLogger.e(TAG, "Event validation failed: sdkName is required")
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                val endpoint = eventEndpoint
                if (endpoint.isNullOrBlank()) {
                    SdkLogger.w(TAG, "Event endpoint not configured, skipping event: ${event.eventName}")
                    return@withContext
                }
                
                // Validate endpoint URL format
                if (!isValidUrl(endpoint)) {
                    SdkLogger.e(TAG, "Invalid endpoint URL format: $endpoint")
                    return@withContext
                }

                val enhancedEvent = enhanceEventData(event.copy(sdkName = effectiveSdkName))
                val jsonBody = buildEventJson(enhancedEvent, appContext)
                val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .addHeader("Content-Type", JSON_MEDIA_TYPE)
                
                // Add x-app-id header if set during initialization
                appId?.let { id ->
                    requestBuilder.addHeader("x-app-id", id)
                }
                
                // Add state header if provided in event
                enhancedEvent.state?.let { state ->
                    requestBuilder.addHeader("state", state)
                }
                
                val request = requestBuilder.build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    SdkLogger.d(TAG, "Event sent successfully: ${enhancedEvent.eventName} (eventId=${enhancedEvent.eventId})")
                } else {
                    SdkLogger.w(TAG, "Event send failed with status ${response.code}: ${enhancedEvent.eventName} (eventId=${enhancedEvent.eventId})")
                }
                
                response.close()
            } catch (e: Exception) {
                SdkLogger.e(TAG, "Failed to send event: ${event.eventName}", e)
            }
        }
    }

    private fun buildEventJson(event: EventData, context: Context?): String {
        val json = StringBuilder()
        json.append("{")
        json.append("\"sdk_name\":").append(escapeJson(event.sdkName)).append(",")
        json.append("\"event_name\":").append(escapeJson(event.eventName)).append(",")
        json.append("\"timestamp\":").append(event.timestamp).append(",")
        json.append("\"event_id\":").append(event.eventId)
        
        if (event.deviceId != null) {
            json.append(",\"device_id\":").append(escapeJson(event.deviceId))
        }
        
        if (event.userId != null) {
            json.append(",\"user_id\":").append(escapeJson(event.userId))
        }
        
        if (event.sessionId != null) {
            json.append(",\"session_id\":").append(escapeJson(event.sessionId))
        }
        
        if (event.requestId != null) {
            json.append(",\"request_id\":").append(escapeJson(event.requestId))
        }
        
        if (event.asid != null) {
            json.append(",\"asid\":").append(escapeJson(event.asid))
        }
        
        if (event.properties.isNotEmpty()) {
            json.append(",\"properties\":{")
            val props = event.properties.entries.joinToString(",") { (key, value) ->
                "\"${escapeJsonKey(key)}\":${toJsonValue(value)}"
            }
            json.append(props)
            json.append("}")
        }
        
        // Get device info JSON from DeviceInfoCollector (reusable by other SDKs)
        val deviceInfoJson = if (context != null) {
            DeviceInfoCollector.getDeviceInfoJson(
                context,
                sdkVersion = sdkVersion,
                sdkName = event.sdkName
            )
        } else {
            ""
        }
        
        if (deviceInfoJson.isNotEmpty()) {
            json.append(",\"device_info\":{")
            json.append(deviceInfoJson)
            json.append("}")
        }
        
        json.append("}")
        return json.toString()
    }
    
    /**
     * Escape JSON string value. Handles all special characters including control characters.
     */
    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 10)
        sb.append('"')
        for (i in value.indices) {
            val ch = value[i]
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f") // Form feed
                else -> {
                    // Escape control characters (U+0000 to U+001F)
                    if (ch < ' ') {
                        sb.append("\\u")
                        sb.append(String.format("%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
    
    /**
     * Escape JSON key. Keys should not contain control characters or quotes.
     */
    private fun escapeJsonKey(key: String): String {
        val sb = StringBuilder(key.length + 5)
        for (i in key.indices) {
            val ch = key[i]
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                else -> {
                    // Control characters should not appear in keys, but handle them safely
                    if (ch < ' ') {
                        sb.append("\\u")
                        sb.append(String.format("%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
    
    private fun toJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> escapeJson(value)
            is Map<*, *> -> mapToJson(value)
            is Iterable<*> -> listToJson(value)
            is Array<*> -> listToJson(value.asList())
            else -> escapeJson(value.toString())
        }
    }

    private fun mapToJson(map: Map<*, *>): String {
        val entries = map.entries.joinToString(",") { (key, value) ->
            val keyString = key?.toString() ?: "null"
            "\"${escapeJsonKey(keyString)}\":${toJsonValue(value)}"
        }
        return "{$entries}"
    }

    private fun listToJson(values: Iterable<*>): String {
        val entries = values.joinToString(",") { item ->
            toJsonValue(item)
        }
        return "[$entries]"
    }
    
    /**
     * Validate URL format (basic validation).
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol == "http" || urlObj.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }
}
