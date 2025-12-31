# SNA SDK Test App

This is a simple test application to manually test the SNA SDK on a real device.

## Features

The test app provides a simple UI to test:

1. **Get SIM Info** - Displays MCC, MNC, and mobile data status
2. **Check Mobile Data Status** - Shows current mobile data state
3. **Test SNA Authentication** - Enter a URL and test authentication with redirect handling

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

- The app uses the SDK module directly via `implementation project(':android')`
- All network operations run in the background
- Results are displayed in the text area at the top
- The app automatically scrolls to show the latest results

