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
    implementation("com.otplesssdk:otp-sdk:<version>")
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
                // For SMS, senderAddress is the originating address (may be null depending on Play services/version).
                result.senderAddress?.let { println("SenderAddress=$it") }
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

If you need a custom OTP parsing configuration:

```kotlin
val sdk = OtpSdk.getInstance() ?: return
sdk.startListening(
    OtpConfig(
        channels = setOf(OtpChannel.SMS, OtpChannel.WHATSAPP),
    ),
    object : OtpCallback {
        override fun onResult(result: OtpResult) {
            // handle result
        }
    }
)
```

### 4) Stop Listening

```kotlin
sdk.stop()
```

## How it works

### SMS Retriever

The SDK uses Google Play Services SMS Retriever. Your verification SMS must include the app hash.
The receiver is declared in the library manifest and does not require SMS permissions.
If Google Play Services is unavailable, the SDK reports `SMS_PLAY_SERVICES_UNAVAILABLE`.

**Recommended SMS format (per Google SMS Retriever):**

```text
<#> OTP is 1234
E41V/JcH1a5
```

Notes:
- The **11-char hash should be the last line** (no extra text after it).
- Ensure the hash you send matches the build you're installing (debug vs release signing can change it).

You can print your 11-character app hash values using:

```kotlin
val hashes = OtpSdk.getAppHashes(context)
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
- WhatsApp handshake validity is 10 minutes by default in this SDK.
- The callback can be invoked multiple times (e.g., WhatsApp error then SMS success), and the SDK keeps listening on remaining active channels until they complete or you call `stop()`.
- OTP parsing extracts the first 6-digit OTP, otherwise the first 4-digit OTP, from the message.
- For SMS results, `senderAddress` is the SMS originating address and is only available when provided by Play Services.
