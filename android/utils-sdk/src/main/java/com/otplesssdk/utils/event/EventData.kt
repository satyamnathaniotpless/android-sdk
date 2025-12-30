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
     * Auto-incrementing event ID for the current session (starts at 1)
     */
    val eventId: Int = 1,
    
    /**
     * Persistent device ID (persists across app installs, based on GAID/device ID)
     */
    val deviceId: String? = null,
    
    /**
     * Event properties/metadata as key-value pairs
     */
    val properties: Map<String, Any?> = emptyMap(),
    
    /**
     * Optional user/session identifier
     */
    val userId: String? = null,
    
    /**
     * Optional session identifier
     */
    val sessionId: String? = null,
    
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
