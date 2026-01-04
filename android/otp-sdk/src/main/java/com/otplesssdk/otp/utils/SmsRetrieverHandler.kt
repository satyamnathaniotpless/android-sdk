package com.otplesssdk.otp.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpErrorReason
import com.otplesssdk.utils.logger.SdkLogger

internal class SmsRetrieverHandler(
    private val store: OtpSessionStore,
    private val emitter: OtpResultEmitter
) {
    private companion object {
        private const val TAG = "SmsRetrieverHandler"
    }

    fun start(context: Context, session: OtpSession) {
        try {
            SdkLogger.d(TAG) { "start: channels=${session.config.channels}" }
            val availability = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) {
                SdkLogger.w(TAG) { "Google Play Services unavailable: code=$availability" }
                emitter.error(
                    session = session,
                    reason = OtpErrorReason.SMS_PLAY_SERVICES_UNAVAILABLE,
                    source = OtpChannel.SMS,
                    errorMessage = GoogleApiAvailability.getInstance().getErrorString(availability)
                )
                return
            }
            SmsRetriever.getClient(context).startSmsRetriever()
                .addOnSuccessListener {
                    SdkLogger.d(TAG, "SMS Retriever started")
                }
                .addOnFailureListener { exception ->
                    SdkLogger.w(TAG, "SMS Retriever failed to start", exception)
                    emitter.error(
                        session = session,
                        reason = OtpErrorReason.SMS_START_FAILED,
                        source = OtpChannel.SMS,
                        errorMessage = exception.message
                    )
                }
        } catch (exception: Exception) {
            SdkLogger.e(TAG, "SMS Retriever start exception", exception)
            emitter.error(
                session = session,
                reason = OtpErrorReason.SMS_START_FAILED,
                source = OtpChannel.SMS,
                errorMessage = exception.message
            )
        }
    }

    fun handleIntent(intent: Intent) {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return

        val extras = intent.extras ?: return
        val status = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(SmsRetriever.EXTRA_STATUS) as? Status
            }
        } catch (_: Throwable) {
            null
        } ?: return

        val currentSession = store.current()

        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                val senderAddress = extras.getString(SmsRetriever.EXTRA_SMS_ORIGINATING_ADDRESS)
                SdkLogger.d(TAG) {
                    "handleIntent: SUCCESS session=${currentSession != null} " +
                        "senderAddress=${senderAddress ?: "null"} messageLen=${message?.length ?: 0}"
                }
                if (message.isNullOrBlank()) {
                    currentSession?.let { s ->
                        if (s.config.channels.contains(OtpChannel.SMS)) {
                            emitter.error(
                                session = s,
                                reason = OtpErrorReason.SMS_OTP_NOT_FOUND,
                                source = OtpChannel.SMS
                            )
                        }
                    }
                    return
                }
                if (currentSession == null) return
                if (!currentSession.config.channels.contains(OtpChannel.SMS)) return
                if (currentSession.isCompleted(OtpChannel.SMS)) return

                val otp = OtpParser.extractOtp(message)
                if (otp == null) {
                    SdkLogger.d(TAG) { "handleIntent: OTP not found in message" }
                    emitter.error(
                        session = currentSession,
                        reason = OtpErrorReason.SMS_OTP_NOT_FOUND,
                        source = OtpChannel.SMS
                    )
                } else {
                    SdkLogger.d(TAG) { "handleIntent: OTP extracted (len=${otp.length})" }
                    emitter.success(
                        session = currentSession,
                        otp = otp,
                        source = OtpChannel.SMS,
                        senderAddress = senderAddress,
                        senderPackage = null
                    )
                }
            }

            CommonStatusCodes.TIMEOUT -> {
                SdkLogger.d(TAG) { "handleIntent: TIMEOUT session=${currentSession != null}" }
                currentSession?.let { s ->
                    if (s.config.channels.contains(OtpChannel.SMS) && !s.isCompleted(OtpChannel.SMS)) {
                        emitter.error(
                            session = s,
                            reason = OtpErrorReason.SMS_TIMEOUT,
                            source = OtpChannel.SMS
                        )
                    }
                }
            }

            else -> {
                SdkLogger.d(TAG) { "handleIntent: ERROR statusCode=${status.statusCode} session=${currentSession != null}" }
                currentSession?.let { s ->
                    if (s.config.channels.contains(OtpChannel.SMS) && !s.isCompleted(OtpChannel.SMS)) {
                        emitter.error(
                            session = s,
                            reason = OtpErrorReason.SMS_RETRIEVER_ERROR,
                            source = OtpChannel.SMS,
                            errorMessage = "statusCode=${status.statusCode}"
                        )
                    }
                }
            }
        }
    }
}

