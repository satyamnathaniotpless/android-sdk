# Consumer ProGuard rules for library users
# This file is automatically included in the AAR

# Keep SDK public API
-keep class com.otplesssdk.sna.SNASdk { *; }
-keep interface com.otplesssdk.sna.callback.** { *; }
-keep class com.otplesssdk.sna.models.** { *; }

# Keep SDK classes (moved from proguard-rules.pro so consumers control shrinking)
-keep class com.otplesssdk.sna.** { *; }
