# ProGuard rules for the OTP SDK module itself.
#
# Note: The library module does not enable shrinking by default, but these rules
# are kept for local builds/tests that may enable minification.
# Keep only what must remain stable and what is referenced from the manifest.

-keep class com.otplesssdk.otp.OtpSdk { *; }
-keep class com.otplesssdk.otp.callback.OtpCallback { *; }
-keep class com.otplesssdk.otp.models.** { *; }
-keep class com.otplesssdk.otp.receiver.** { *; }
