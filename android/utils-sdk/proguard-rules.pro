# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep public API surfaces (safe for minified AARs)
-keep class com.otplesssdk.utils.logger.SdkLogger { *; }
-keep class com.otplesssdk.utils.singleton.** { *; }
-keep class com.otplesssdk.utils.coroutines.** { *; }
-keep class com.otplesssdk.utils.event.EventSender { *; }
-keep class com.otplesssdk.utils.event.EventData { *; }
-keep class com.otplesssdk.utils.deviceinfo.DeviceInfoCollector { *; }
-keep class com.otplesssdk.utils.deviceinfo.DeviceInfoJsonBuilder { *; }
-keep class com.otplesssdk.utils.ids.SessionIdManager { *; }

# Strip debug logs (optional): if you want smaller + faster release builds.
# NOTE: This removes calls but keeps class/methods.
-assumenosideeffects class com.otplesssdk.utils.logger.SdkLogger {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

