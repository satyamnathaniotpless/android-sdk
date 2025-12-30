package com.otplesssdk.sna.models

/**
 * Data class containing SIM and network information
 */
data class SimNetworkInfo(
    /**
     * Mobile Country Code (MCC) from the SIM card
     * Returns null if unavailable
     */
    val mcc: String?,
    
    /**
     * Mobile Network Code (MNC) from the SIM card
     * Returns null if unavailable
     */
    val mnc: String?,

    /**
     * Carrier / network display name for the default data subscription when available.
     *
     * This is best-effort and may be null if unavailable or access is restricted.
     */
    val networkName: String?,
    
    /**
     * Whether mobile data is currently enabled
     */
    val isMobileDataEnabled: Boolean
)
