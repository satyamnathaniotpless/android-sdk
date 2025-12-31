# OTP SDK (Android)

OTP auto-read for SMS Retriever and WhatsApp zero-tap authentication templates.

## Requirements

- minSdk 21+
- compileSdk 28+ (this SDK uses compileSdk 34)

## Quick Start

### 1) Add the SDK

If you consume a published AAR:

```gradle
dependencies {
    implementation("com.sna:otp-sdk:<version>")
}
```

If you integrate as a local module:

```gradle
dependencies {
    implementation(project(":otp-sdk"))
}
```

### 2) Initialize

Call once (Application is recommended):

```kotlin
OtpSdk.initialize(applicationContext)
```

### 3) Start Listening

```kotlin
val sdk = OtpSdk.getInstance() ?: return
val channels = setOf(OtpChannel.SMS, OtpChannel.WHATSAPP)

sdk.startListening(channels, object : OtpCallback {
    override fun onResult(result: OtpResult) {
        when (result) {
            is OtpResult.Success -> {
                println("OTP (${result.source}) = ${result.otp}")
                result.senderId?.let { println("SenderId=$it") }
            }
            is OtpResult.Error -> {
                println("Error (${result.source}) = ${result.reason}")
                result.errorKey?.let { println("Key=$it") }
                result.errorMessage?.let { println("Message=$it") }
            }
        }
    }
})
```

### 4) Stop Listening

```kotlin
sdk.stop()
```

Or without keeping the instance:

```kotlin
OtpSdk.stop()
```

## How it works

### SMS Retriever

The SDK uses Google Play Services SMS Retriever. Your verification SMS must include the app hash.
The receiver is declared in the library manifest and does not require SMS permissions.
If Google Play Services is unavailable, the SDK reports `SMS_PLAY_SERVICES_UNAVAILABLE`.

You can print your 11-character app hash values using:

```kotlin
val hashes = OtpSdk.getInstance()?.getAppHashes().orEmpty()
hashes.forEach { println("SMS hash (${it.packageName}): ${it.hash}") }
```

Or without initializing:

```kotlin
val hashes = OtpSdk.getAppHashes(context)
```

If you only need the first hash:

```kotlin
val hash = OtpSdk.getAppHashes(context).firstOrNull()?.hash
```

### WhatsApp zero-tap

The SDK sends the handshake intent (`com.whatsapp.otp.OTP_REQUESTED`) on `startListening`.
Your server should send the WhatsApp authentication template after the handshake.
When eligible, WhatsApp broadcasts `com.whatsapp.otp.OTP_RETRIEVED` to deliver the code.

The SDK also listens for WhatsApp error broadcasts (`com.whatsapp.otp.OTP_ERROR`) and surfaces them
as `OtpResult.Error` with `errorKey` and `errorMessage`.

## Manifest snippets (for reference)

These are already included in the library manifest:

```xml
<receiver
    android:name=".receiver.SmsRetrieverReceiver"
    android:exported="true"
    android:permission="com.google.android.gms.auth.api.phone.permission.SEND">
    <intent-filter>
        <action android:name="com.google.android.gms.auth.api.phone.SMS_RETRIEVED" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.WhatsAppOtpReceiver"
    android:exported="true"
    android:permission="com.whatsapp.permission.BROADCAST">
    <intent-filter>
        <action android:name="com.whatsapp.otp.OTP_RETRIEVED" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.WhatsAppOtpErrorReceiver"
    android:exported="true"
    android:permission="com.whatsapp.permission.BROADCAST">
    <intent-filter>
        <action android:name="com.whatsapp.otp.OTP_ERROR" />
    </intent-filter>
</receiver>
```

Package visibility for WhatsApp is also declared:

```xml
<queries>
    <package android:name="com.whatsapp" />
    <package android:name="com.whatsapp.w4b" />
</queries>
```

## Notes and limitations

- SMS Retriever times out after 5 minutes; you will receive `SMS_TIMEOUT`.
- WhatsApp handshake validity is 10 minutes by default in this SDK; use `whatsAppTimeoutMs` to adjust.
- The callback can be invoked multiple times (e.g., WhatsApp error then SMS success), and the SDK keeps listening on remaining active channels until they complete or you call `stop()`.
- OTP parsing defaults to a digit-only 6-digit code with common keyword matching.
- `senderId` is only available for SMS when provided by Play Services.
