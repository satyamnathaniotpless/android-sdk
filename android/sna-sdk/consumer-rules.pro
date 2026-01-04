# Consumer ProGuard rules for library users
# This file is automatically included in the AAR

# Keep SDK public API (only).
# Avoid broad keep rules (e.g. `com.otplesssdk.sna.**`) so consuming apps can still shrink/optimize internals.
-keep class com.otplesssdk.sna.SNASdk { *; }
-keep class com.otplesssdk.sna.models.** { *; }
