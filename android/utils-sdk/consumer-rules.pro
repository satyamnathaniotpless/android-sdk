# Consumer ProGuard rules for library users
# This file is automatically included in the AAR

# Keep SDK public API
-keep class com.otplesssdk.utils.logger.SdkLogger { *; }
-keep class com.otplesssdk.utils.singleton.SdkSingleton { *; }
-keep class com.otplesssdk.utils.coroutines.SdkCoroutineScope { *; }
-keep class com.otplesssdk.utils.event.EventSender { *; }
-keep class com.otplesssdk.utils.event.EventData { *; }
-keep class com.otplesssdk.utils.event.DeviceInfo { *; }

