package com.otplesssdk.otp.models

/**
 * Enumerates the possible reasons an OTP retrieval attempt can fail in the OTP SDK.
 *
 * This is provided to SDK consumers as a stable, machine-readable error classification to help:
 * - Show the right user-facing message (e.g. prompt to install WhatsApp, retry, or fallback to manual entry)
 * - Decide whether retrying is useful (timeouts vs. permanent environment issues)
 * - Implement fallbacks (e.g. switch from auto-read to manual OTP entry)
 *
 * Typically, you receive an [OtpErrorReason] via an OTP callback/result object and should handle it by:
 * - Guiding the user to correct missing prerequisites (e.g. install/enable services)
 * - Retrying when it is transient (e.g. timeouts)
 * - Falling back to manual OTP entry when auto-retrieval cannot complete.
 */
enum class OtpErrorReason {
    /**
     * The SDK could not start SMS Retriever listening.
     *
     * Likely causes: missing/disabled Google Play services, invalid configuration, or OS restrictions.
     * Suggested handling: prompt the user to update/enable Play services, then retry; otherwise fallback to manual OTP entry.
     */
    SMS_START_FAILED,

    /**
     * An SMS OTP was not received within the expected time window.
     *
     * Likely causes: delayed carrier delivery, user is out of coverage, or the OTP SMS was never sent.
     * Suggested handling: allow retry/resend OTP; provide manual entry as a fallback.
     */
    SMS_TIMEOUT,

    /**
     * An SMS message was received, but an OTP could not be extracted from it.
     *
     * Likely causes: message format does not match expected OTP patterns or the SMS content is not the OTP message.
     * Suggested handling: fallback to manual entry and/or adjust server-side SMS template to a supported format.
     */
    SMS_OTP_NOT_FOUND,

    /**
     * SMS Retriever returned an error while attempting to retrieve the SMS.
     *
     * Likely causes: Play services/internal API error, user/device restrictions, or transient Google Play services issues.
     * Suggested handling: retry once; if it persists, fallback to manual OTP entry.
     */
    SMS_RETRIEVER_ERROR,

    /**
     * Google Play services is unavailable on the device, so SMS Retriever cannot be used.
     *
     * Likely causes: device has no Play services (AOSP devices), Play services missing/disabled/outdated.
     * Suggested handling: fallback to manual OTP entry (and optionally show guidance to install/update Play services when applicable).
     */
    SMS_PLAY_SERVICES_UNAVAILABLE,

    /**
     * WhatsApp is not installed (or not available) on the device for WhatsApp-based OTP retrieval.
     *
     * Likely causes: WhatsApp app not installed or disabled.
     * Suggested handling: prompt the user to install/enable WhatsApp or switch to another OTP channel/manual entry.
     */
    WHATSAPP_NOT_INSTALLED,

    /**
     * The SDK could not complete the initial WhatsApp handshake/setup required for OTP retrieval.
     *
     * Likely causes: missing permissions/receiver configuration, unsupported device/WhatsApp environment, or network issues.
     * Suggested handling: verify integration/manifest configuration, retry once, then fallback to manual entry or SMS.
     */
    WHATSAPP_HANDSHAKE_FAILED,

    /**
     * A WhatsApp OTP was not received within the expected time window.
     *
     * Likely causes: delayed delivery, user is offline, or the OTP message was never sent.
     * Suggested handling: allow retry/resend OTP; provide manual entry as a fallback.
     */
    WHATSAPP_TIMEOUT,

    /**
     * A WhatsApp message was successfully received, but **no OTP could be extracted from its content**.
     *
     * This reason is used only for **content-level issues** where delivery succeeded but the message text is not
     * extractable as an OTP, e.g. wrong template text, OTP missing from the message, or an OTP format/pattern mismatch.
     *
     * Suggested handling: fallback to manual entry and/or adjust the server-side WhatsApp template to a supported format.
     *
     * Example mapping: "message received but no OTP present" -> [WHATSAPP_OTP_NOT_FOUND]
     */
    WHATSAPP_OTP_NOT_FOUND,

    /**
     * A WhatsApp OTP retrieval **technical failure** occurred.
     *
     * This reason is used for failures in the WhatsApp OTP pipeline due to **technical/receiver/parsing** problems or
     * internal SDK failures, e.g. channel/permission/configuration issues, receiver parsing exceptions, SDK processing
     * errors, or unexpected internal network/timeouts while handling the flow.
     *
     * Suggested handling: switch channels (e.g. SMS), log/report the error for diagnosis, and retry if appropriate.
     *
     * Example mapping: "receiver parse exception" -> [WHATSAPP_OTP_ERROR]
     */
    WHATSAPP_OTP_ERROR,
}
