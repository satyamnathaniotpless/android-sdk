package com.otplesssdk.utils.event

/**
 * Standard event data structure for sending events to the event database.
 */
data class EventData(
    /**
     * SDK name (e.g., "otp-sdk", "sna-sdk")
     */
    val sdkName: String,
    
    /**
     * Event name/type (e.g., "otp_received", "sna_authenticated")
     */
    val eventName: String,
    
    /**
     * Event timestamp in milliseconds (Unix epoch)
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Event ID for the current session.
     *
     * Nullable to avoid silently defaulting to a value that could create duplicate IDs.
     * `null` means "not set" (e.g., it will be assigned by the sender when dispatching the event).
     */
    val eventId: Int? = null,
    
    /**
     * Install ID (persists until app uninstall).
     */
    val inid: String? = null,
    
    /**
     * Event properties/metadata as key-value pairs
     */
    val properties: Map<String, Any?> = emptyMap(),
    
    /**
     * Optional user/session identifier
     */
    val userId: String? = null,
    
    /**
     * Session ID (persists for the current app process/session).
     */
    val tsid: String? = null,
    
    /**
     * Optional request identifier
     */
    val requestId: String? = null,
    
    /**
     * Optional ASID (Application Session ID)
     */
    val asid: String? = null,
    
    /**
     * Optional state parameter (passed in header)
     */
    val state: String? = null,
)
