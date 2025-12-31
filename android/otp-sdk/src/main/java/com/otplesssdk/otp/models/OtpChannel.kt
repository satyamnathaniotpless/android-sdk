package com.otplesssdk.otp.models

/**
 * OTP delivery/transport options supported by the SDK.
 *
 * This enum indicates which channel the SDK should use (or has used) to obtain the OTP on Android.
 */
enum class OtpChannel {
    /**
     * OTP delivered via SMS.
     *
     * On Android, this may be auto-retrieved/parsed using platform capabilities (e.g., SMS Retriever)
     * depending on device support and app configuration.
     */
    SMS,

    /**
     * OTP delivered via WhatsApp.
     *
     * On Android, retrieval relies on WhatsApp message delivery and any applicable app/device handling
     * configured by the SDK integration.
     */
    WHATSAPP
}
