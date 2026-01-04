# OTP SDK consumer ProGuard rules
#
# Keep only what must remain stable for SDK consumers and Android manifest components.
# Let R8 shrink/obfuscate internal implementation details.

# Public entrypoint API
-keep class com.otplesssdk.otp.OtpSdk { *; }

# Public callback + models used in consumer code
-keep class com.otplesssdk.otp.callback.OtpCallback { *; }
-keep class com.otplesssdk.otp.models.** { *; }

# Manifest-registered receivers (must remain for runtime delivery)
-keep class com.otplesssdk.otp.receiver.** { *; }
