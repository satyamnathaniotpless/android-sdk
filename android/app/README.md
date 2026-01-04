# Otpless SDK Test App

This is a manual **SDK test harness** for validating the end-to-end behavior of:

- `sna-sdk`
- `otp-sdk`
- `utils-sdk`

## Features

The app is organized into tabs:

### SNA

- **Get SIM/Network info** (`SNASdk.getSimNetworkInfo()`)
- **Check mobile data** (`SNASdk.isMobileDataEnabled()`)
- **Run SNA** (`SNASdk.authenticate(...)`) with URL + timeout
- **Run (blocking)** (`SNASdk.authenticateBlocking(...)`) to validate the Java/blocking wrapper behavior

### OTP

- Select channels: **SMS** / **WhatsApp**
- Start/Stop listener (`OtpSdk.startListening(...)` / `OtpSdk.stop()`)
- Print **SMS app hashes** (`OtpSdk.getAppHashes(...)`)
- Quick **WhatsApp installed** check (`OtpSdk.isWhatsAppInstalled()`)

> Note: receivers for SMS Retriever + WhatsApp OTP broadcasts are included via the `otp-sdk` library manifest.

### Device Info

- Request phone permissions (optional for richer MCC/MNC on some devices)
- Warm up device info collection (`DeviceInfoCollector.warmUp(...)`)
- Collect full **device_info JSON** (`DeviceInfoCollector.getDeviceInfoJson(...)`)
- Granular snapshots:
  - `DeviceInfoCollector.getNetworkInfo(...)`
  - `DeviceInfoCollector.getIdentifiersInfo(...)`
  - `DeviceInfoCollector.getAppPresenceInfo(...)`

### Utils

- **EventSender**: initialize + send custom events
- **HttpClient headers**: set custom `x-*` headers and verify effective headers on requests
- **ApiClient**: run requests via `ApiClient.execute(...)`
- Captures and displays **effective request headers** after `HttpClient` header injection

## Building and Running

### Prerequisites

- Android device or emulator connected via USB
- USB debugging enabled
- Android SDK installed

### Build and Install

From the `android` directory:

```bash
# Build the app
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug

# Or run directly (builds and installs)
./gradlew :app:installDebug && adb shell am start -n com.sna.sdk.testapp/.MainActivity
```

### Using Android Studio

1. Open the `android` folder in Android Studio
2. Wait for Gradle sync to complete
3. Select the `app` run configuration
4. Click Run or press Shift+F10

## Testing on Device

### Before Testing

1. **Grant Permissions**: The app will request `READ_PHONE_STATE` permission on first launch. Grant it to test SIM info features.

2. **Enable Mobile Data**: Make sure mobile data is enabled on your device for full testing.

3. **Disable WiFi** (optional): To verify cellular-only enforcement, disable WiFi before testing authentication.

### Test Scenarios

1. **SIM Info Test**: Click "Get SIM Info" to see MCC, MNC, and mobile data status
2. **Mobile Data Test**: Toggle mobile data on/off and click "Check Mobile Data Status" to verify detection
3. **Authentication Test**: 
   - Enter a test URL (e.g., `http://httpbin.org/redirect-to?url=https://httpbin.org/get`)
   - Click "Test SNA Authentication"
   - Wait for result (success or failure with reason)

## Notes

- The app uses local modules via:
  - `implementation project(':sna-sdk')`
  - `implementation project(':otp-sdk')`
  - `implementation project(':utils-sdk')`
- All network operations run in the background
- Results are displayed in the in-app log views (copyable)

