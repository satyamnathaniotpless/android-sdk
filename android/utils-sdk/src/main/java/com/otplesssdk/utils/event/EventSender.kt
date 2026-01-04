package com.otplesssdk.utils.event

import android.content.Context
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector
import com.otplesssdk.utils.ids.SessionIdManager
import com.otplesssdk.utils.json.Json
import com.otplesssdk.utils.logger.SdkLogger
import com.otplesssdk.utils.network.ApiClient
import com.otplesssdk.utils.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility for sending events to the event database.
 * Events are sent asynchronously and failures are logged but don't throw exceptions.
 */
object EventSender {
    private const val TAG = "EventSender"
    private const val DEFAULT_TIMEOUT_SECONDS = 10L
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    
    // Scope used for fire-and-forget event sending.
    // Must be safe to use even before EventSender.initialize().
    private val eventJob = SupervisorJob()
    private val eventScope = SdkCoroutineScope.createIOScope(parentJob = eventJob)
    private val isShutdown = AtomicBoolean(false)

    // Session-based event counter (resets when app restarts)
    private val sessionEventCounter = AtomicInteger(0)
    
    // Context for device info collection (set by SDKs during initialization)
    @Volatile
    private var appContext: Context? = null
    
    /**
     * @return true if [initialize] has been called with a valid application context.
     */
    @JvmStatic
    fun isInitialized(): Boolean = appContext != null

    /**
     * Prevents any new event submissions and begins a graceful shutdown.
     *
     * Most apps should not need to call this. Use only when you want to explicitly stop
     * background work (e.g., during teardown in tests).
     */
    @JvmStatic
    fun shutdown() {
        // Set the state first so concurrent sendEvent() calls can observe shutdown immediately.
        if (!isShutdown.compareAndSet(false, true)) {
            return
        }
        // Stop accepting new work without abruptly cancelling in-flight tasks.
        eventJob.complete()
    }
    
    // SDK information (set during initialization or globally)
    @Volatile
    var sdkVersion: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            HttpClient.setSdkInfo(sdkName = sdkName, sdkVersion = field)
            if (value != null && value.isNotBlank()) {
                SdkLogger.d(TAG, "SDK version set globally: $value")
            }
        }
    
    @Volatile
    var sdkName: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            HttpClient.setSdkInfo(sdkName = field, sdkVersion = sdkVersion)
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
     * This enables automatic device info collection, inid and tsid generation.
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

        // Configure shared HTTP client to attach common headers (x-app-id/inid/tsid) on all requests.
        HttpClient.configure(
            context = context.applicationContext,
            appId = this.appId,
            sdkName = this.sdkName,
            sdkVersion = this.sdkVersion
        )
        
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
        
        val inid = SessionIdManager.getInId(context.applicationContext)
        SdkLogger.d(TAG) { "inid: $inid" }
        
        val tsid = SessionIdManager.getTsId(context.applicationContext)
        SdkLogger.d(TAG) { "tsid: $tsid" }
        
        SdkLogger.d(TAG, "EventSender initialized - SDK: ${this.sdkName}, Version: ${this.sdkVersion}, App ID: ${this.appId ?: "not set"}, Endpoint: ${this.eventEndpoint ?: "not set"}")

        // Warm up device info cache so subsequent events/API calls have richer device_info sooner.
        DeviceInfoCollector.warmUp(
            context = context.applicationContext,
            sdkVersion = this.sdkVersion,
            sdkName = this.sdkName
        )
    }
    
    /**
     * Get the install ID (persists across app installs until uninstall).
     * Returns null if EventSender has not been initialized.
     * 
     * @return inid or null if not initialized
     */
    fun getInId(): String? {
        val context = appContext ?: return null
        return SessionIdManager.getInId(context)
    }
    
    /**
     * Get the current session ID (persists for this app process/session).
     * Returns null if EventSender has not been initialized.
     * 
     * @return tsid or null if not initialized
     */
    fun getTsId(): String? {
        val context = appContext ?: return null
        return SessionIdManager.getTsId(context)
    }
    
    /**
     * Get the next event ID for the current session.
     */
    private fun getNextEventId(): Int {
        return sessionEventCounter.incrementAndGet()
    }
    
    /**
     * Enhance event data with automatic fields (eventId, inid, tsid, deviceInfo).
     */
    private fun enhanceEventData(event: EventData): EventData {
        val context = appContext
        val nextEventId = getNextEventId()
        
        val inid = if (context != null) SessionIdManager.getInId(context) else event.inid
        val tsid = if (context != null) (event.tsid ?: SessionIdManager.getTsId(context)) else event.tsid
        
        // Use SDK name from initialization or from event
        val finalSdkName = sdkName?.takeIf { it.isNotBlank() } ?: event.sdkName
        
        // Note: Device info is now collected as JSON string during event building,
        // not stored in the EventData object. This allows other SDKs to use device info independently.
        
        return event.copy(
            eventId = nextEventId,
            inid = inid,
            tsid = tsid,
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
        if (isShutdown.get() || !eventJob.isActive) {
            SdkLogger.w(
                TAG,
                "sendEvent() called after shutdown; dropping event: $eventName"
            )
            return
        }
        eventScope.launch {
            // Re-check before starting work to avoid races with shutdown/cancellation.
            if (isShutdown.get() || !eventJob.isActive) return@launch
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
                
                // If user_id/asid are available for this event, promote them to common headers
                // so other API calls can also include them (until updated/cleared).
                if (!enhancedEvent.userId.isNullOrBlank() || !enhancedEvent.asid.isNullOrBlank()) {
                    HttpClient.setUserInfo(
                        userId = enhancedEvent.userId,
                        asid = enhancedEvent.asid
                    )
                }

                // Add state header if provided in event
                enhancedEvent.state?.let { state ->
                    requestBuilder.addHeader("x-state", state)
                }
                
                val request = requestBuilder.build()
                val result = ApiClient.execute(
                    request = request,
                    policy = ApiClient.NetworkPolicy(
                        timeoutMs = DEFAULT_TIMEOUT_SECONDS * 1000L,
                        maxBodyBytes = 32 * 1024L,
                    )
                )
                when (result) {
                    is ApiClient.ApiResult.Success -> {
                        SdkLogger.d(
                            TAG,
                            "Event sent successfully: ${enhancedEvent.eventName} (eventId=${enhancedEvent.eventId})"
                        )
                    }
                    is ApiClient.ApiResult.Failure -> {
                        val code = result.code
                        if (code != null) {
                            SdkLogger.w(
                                TAG,
                                "Event send failed with status $code: ${enhancedEvent.eventName} (eventId=${enhancedEvent.eventId})"
                            )
                        } else {
                            SdkLogger.w(
                                TAG,
                                "Event send failed: ${enhancedEvent.eventName} (eventId=${enhancedEvent.eventId}) error=${result.error}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SdkLogger.e(TAG, "Failed to send event: ${event.eventName}", e)
            }
        }
    }

    private fun buildEventJson(event: EventData, context: Context?): String {
        val root = linkedMapOf<String, Any?>()
        root["sdk_name"] = event.sdkName
        root["event_name"] = event.eventName
        root["timestamp"] = event.timestamp

        event.eventId?.let { root["event_id"] = it }
        event.inid?.let { root["inid"] = it }
        event.userId?.let { root["user_id"] = it }
        event.tsid?.let { root["tsid"] = it }
        event.requestId?.let { root["request_id"] = it }
        event.asid?.let { root["asid"] = it }

        if (event.properties.isNotEmpty()) {
            root["properties"] = event.properties
        }

        val deviceInfoContent = if (context != null) {
            DeviceInfoCollector.getDeviceInfoJson(
                context,
                sdkVersion = sdkVersion,
                sdkName = event.sdkName
            )
        } else {
            ""
        }

        if (deviceInfoContent.isNotEmpty()) {
            root["device_info"] = Json.Raw("{${deviceInfoContent}}")
        }

        return Json.value(root)
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
