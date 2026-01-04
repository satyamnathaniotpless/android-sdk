# Utils SDK (Android)

`utils-sdk` is the shared foundation module used by Otpless SDKs (OTP, SNA, and upcoming Auth SDKs).

It provides:
- **Event sending** (`EventSender`)
- **Device info** collection (`DeviceInfoCollector`)
- **Stable IDs**: `inid` (install id) + `tsid` (session/process id) (`SessionIdManager`)
- **Networking utilities**: shared `OkHttpClient`, common headers, coroutine bridge, error classifier, and a minimal `ApiClient`
- **Shared logging** (`SdkLogger`)

---

## Add dependency

If you're building inside this repo:

```gradle
dependencies {
  implementation project(":utils-sdk")
}
```

If you consume a published AAR:

```gradle
dependencies {
  implementation("com.otplesssdk:utils-sdk:<version>")
}
```

---

## Permissions (declared by the library)

`utils-sdk` declares these in its manifest, so they are merged into the host app:
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `com.google.android.gms.permission.AD_ID` (helps GAID access on newer Android versions)

It also declares `<queries>` for app presence checks:
- WhatsApp, WhatsApp Business, Telegram, Truecaller, Viber

---

## Logging

Logging is **disabled by default**.

```kotlin
SdkLogger.setEnabled(true)
```

All SDKs share the same logger instance, so enabling it once enables logs across modules.

---

## IDs: `inid` and `tsid`

`SessionIdManager` provides:
- **`inid`**: install id (persists until uninstall)
- **`tsid`**: session id for the current app process (resets on app restart)

```kotlin
val inid = SessionIdManager.getInId(context)
val tsid = SessionIdManager.getTsId(context)
```

Both IDs are generated using `UuidUtil`.

---

## Common HTTP client + headers (recommended)

All HTTP calls created through `HttpClient` automatically attach **common `x-` headers** (when configured/available):
- `x-app-id`
- `x-sdk-name`
- `x-sdk-version`
- `x-inid`
- `x-tsid`
- `x-user_id`
- `x-asid`

### Configure once (usually via EventSender)

Most SDKs call `EventSender.initialize(...)` which in turn configures `HttpClient` automatically.
If you want to configure it directly:

```kotlin
HttpClient.configure(
  context = appContext,
  appId = "your-app-id",
  sdkName = "core-auth-sdk",
  sdkVersion = "1.2.3"
)
```

### Update SDK/user info later

```kotlin
HttpClient.setSdkInfo(sdkName = "core-auth-sdk", sdkVersion = "1.2.3")
HttpClient.setUserInfo(userId = "user-123", asid = "asid-xyz")
```

### Custom headers (always normalized to `x-`)

Custom header names are **forced to start with `x-`**. Per-request headers always win.

```kotlin
HttpClient.setCustomHeaders(
  mapOf(
    "tenant" to "india",     // becomes x-tenant
    "x-region" to "ap-south" // remains x-region
  )
)

HttpClient.putCustomHeader("trace-id", "abc") // adds x-trace-id
HttpClient.putCustomHeader("trace-id", null)  // removes x-trace-id
HttpClient.clearCustomHeaders()
```

---

## Minimal API client (`ApiClient`)

`ApiClient` is a very small wrapper over OkHttp:
- **single attempt** (no retries)
- coroutine-friendly (uses shared `Call.await()`)
- caps response body reading via `maxBodyBytes`
- returns a unified `ApiResult`

```kotlin
val request = Request.Builder()
  .url("https://example.com/api")
  .get()
  .build()

val result = ApiClient.execute(
  request = request,
  policy = ApiClient.NetworkPolicy(
    timeoutMs = 10_000,
    maxBodyBytes = 64 * 1024
  )
)
```

Common exceptions are classified via `NetworkExceptionClassifier`.

---

## Events (`EventSender`)

### Setup

```kotlin
EventSender.initialize(
  context = appContext,
  sdkName = "otp-sdk",
  sdkVersion = "1.0.0",
  appId = "your-app-id",
  eventEndpoint = "https://your-host/events"
)
```

### Core SDK (wrapper) pattern

If you have a **core SDK** that wraps OTP + SNA + Auth and you want all events to carry the core SDK identity,
set the global values **before** initializing sub-SDKs:

```kotlin
EventSender.sdkName = "core-auth-sdk"
EventSender.sdkVersion = "2.0.0"
EventSender.eventEndpoint = "https://your-host/events"

OtpSdk.initialize(appContext, appId = "your-app-id", eventEndpoint = EventSender.eventEndpoint)
SNASdk.initialize(appContext, appId = "your-app-id", eventEndpoint = EventSender.eventEndpoint)
```

Sub-SDKs will not override the already-set global values.

### Send an event

```kotlin
EventSender.sendEvent(
  eventName = "auth_started",
  properties = mapOf("flow" to "login"),
  requestId = "req-123",
  asid = "asid-xyz",
  userId = "user-123",
  state = "state-value"
)
```

Request notes:
- `EventSender` sends `POST` to `EventSender.eventEndpoint`
- `Content-Type: application/json; charset=utf-8`
- Adds `x-state` header when `state` is provided
- The shared `HttpClient` interceptor injects the common `x-*` headers (including `x-inid`, `x-tsid`, etc.)
- `userId` / `asid` provided in events are promoted to global HTTP headers via `HttpClient.setUserInfo(...)`

### Shutdown

```kotlin
EventSender.shutdown()
```

This stops accepting new events; in-flight work is allowed to finish.

---

## Device Info (`DeviceInfoCollector`)

`DeviceInfoCollector` returns a best-effort device snapshot as **JSON content** (without outer braces),
and refreshes missing/expired sections in the background.

### Warm-up at SDK init

```kotlin
DeviceInfoCollector.warmUp(
  context = appContext,
  sdkName = EventSender.sdkName,
  sdkVersion = EventSender.sdkVersion
)
```

### Get device_info JSON (for embedding)

```kotlin
val content = DeviceInfoCollector.getDeviceInfoJson(
  context = appContext,
  sdkName = EventSender.sdkName,
  sdkVersion = EventSender.sdkVersion
)
// content is something like:  "os_info":{...},"network_info":{...},...
```

### Public helpers

```kotlin
val network = DeviceInfoCollector.getNetworkInfo(appContext)
val identifiers = DeviceInfoCollector.getIdentifiersInfo(appContext) // includes androidId, gaid (best-effort)
val presence = DeviceInfoCollector.getAppPresenceInfo(appContext)
```

### Clear in-memory cache

```kotlin
DeviceInfoCollector.clearCache()
```

---

## Notes / Design principles

- **Best-effort and crash-safe**: collectors catch exceptions and return partial data instead of crashing.
- **No retries in ApiClient**: retry policy (if any) should live in the calling SDK/business layer.
- **Common headers**: centralized in `HttpClient` so all SDK network calls stay consistent.

