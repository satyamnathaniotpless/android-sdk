# SNA SDK (Android) — `sna-sdk`

SIM Network Authentication (SNA) SDK for Android. The SDK provides:

- **SIM + Network Info**: MCC, MNC, carrier/network name, and a conservative “mobile data enabled/available” signal.
- **SNA URL Call (Cellular)**: performs the SNA flow over **cellular data** (even if Wi‑Fi is connected) and returns:
  - final URL + final HTTP response (headers + capped body)
  - total time + key phase timings
  - per-hop redirect breakdown (status + timings)

This README documents **all public functionality** of the `sna-sdk` module.

---

## Installation

### As a published dependency (AAR)

```gradle
dependencies {
  implementation("com.otplesssdk:sna-sdk:<version>")
}
```

### As a local module

```gradle
dependencies {
  implementation(project(":sna-sdk"))
}
```

---

## Permissions

The library declares these permissions in its own manifest:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CHANGE_NETWORK_STATE` (used for `ConnectivityManager.requestNetwork()` in cellular acquisition)

Optional (host app choice, improves MCC/MNC reliability on some devices/OS versions):

- `android.permission.READ_PHONE_STATE` / `android.permission.READ_BASIC_PHONE_STATE` (Android 13+)

> Note: The SDK is best-effort and guards permission-protected reads with try/catch. If optional permissions are not granted, some fields may be `null`.

---

## Cleartext (HTTP) URLs

If your initial SNA URL is `http://…`, Android may block it unless you explicitly allow cleartext for that domain. Prefer `https://` when possible.

Example `network_security_config.xml` that allows cleartext only for specific domains:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">partnerapi.jio.com</domain>
    <domain includeSubdomains="true">api-csp.airtel.in</domain>
  </domain-config>
</network-security-config>
```

Reference it from your app manifest:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config" />
```

---

## Quick start

### 1) Initialize

Call once (Application is recommended):

```kotlin
SNASdk.initialize(applicationContext)
```

Optional: event tracking parameters (if you use the `utils-sdk` event sender):

```kotlin
SNASdk.initialize(
  context = applicationContext,
  appId = "<your-app-id>",
  eventEndpoint = "https://<your-endpoint>"
)
```

### 2) Enable logging (optional)

Logging is disabled by default. Enable only during debugging:

```kotlin
SNASdk.setLoggingEnabled(true)
```

---

## API usage

### Get SIM + Network info

```kotlin
val sdk = SNASdk.getInstance() ?: return
val info = sdk.getSimNetworkInfo()

println("MCC=${info.mcc} MNC=${info.mnc} Name=${info.networkName}")
println("MobileDataEnabled(best-effort)=${info.isMobileDataEnabled}")
```

Notes:
- `mcc/mnc/networkName` are **best-effort** and may be `null` (no SIM, not registered, access restricted).
- `isMobileDataEnabled` is conservative: if the SDK can’t reliably determine state, it returns `false`.

### Check mobile data availability

```kotlin
val sdk = SNASdk.getInstance() ?: return
val enabled = sdk.isMobileDataEnabled()
```

### Run SNA authentication (suspending)

This is the primary API. It is **suspending**, so the caller controls threading:

```kotlin
val sdk = SNASdk.getInstance() ?: return

val result = sdk.authenticate(
  url = "http://partnerapi.jio.com/v2/adv/smv?...",
  timeoutSeconds = 30,
)

when (result) {
  is SnaResult.Success -> {
    val r = result.response
    println("Success: ${r.httpCode} final=${r.finalUrl}")
    println("Total=${result.timings.totalMs}ms acquire=${result.timings.cellularAcquireMs}ms bind=${result.timings.processBindMs}ms")
    println("Body(truncated=${r.bodyTruncated})=${r.body}")
    result.redirects.forEach { hop ->
      println("Hop: ${hop.httpCode} ${hop.url} (${hop.durationMs}ms)")
    }
  }
  is SnaResult.Failure -> {
    println("Failure: ${result.reason} detail=${result.detail}")
    println("Total=${result.timings.totalMs}ms")
    result.response?.let { r ->
      println("Final response: ${r.httpCode} ${r.finalUrl}")
    }
  }
}
```

#### Background usage (recommended)

Use the suspend API from a background coroutine (WorkManager, services, repository layer, etc.):

```kotlin
val result = withContext(Dispatchers.IO) {
  sdk.authenticate(url, timeoutSeconds = 30)
}
```

> Note: internally, the SDK executes network work on `Dispatchers.IO`, but using your own `Dispatchers.IO` context keeps your calling code consistent and avoids any accidental main-thread work in your app layer.

### Java usage (blocking)

For Java / non-coroutine callers, use the blocking wrapper.

> IMPORTANT: This **blocks** the calling thread. Do not call this on the main thread.

```java
SNASdk sdk = SNASdk.getInstance();
if (sdk == null) return;

SnaResult result = sdk.authenticateBlocking(
  "http://partnerapi.jio.com/v2/adv/smv?...",
  30L
);
System.out.println(result.toString());
```

---

## Cellular enforcement model (important)

On API 23+ the SDK:

1. Acquires a cellular `Network` via `ConnectivityManager.requestNetwork()`
2. Performs a temporary **process-wide bind** to that network for the duration of the SNA call
3. Unbinds in a `finally` block

Implications:
- While the bind is active, **other concurrent network traffic** in the same app process may also route over cellular.
- The SDK **serializes SNA calls internally** to reduce this risk, but the host app should avoid running SNA in parallel with route-sensitive network operations if this matters.

---

## Data handling & privacy

- The final URL can include sensitive query parameters (tokens/IDs). Treat `SnaFinalResponse.finalUrl` as sensitive.
- The SDK returns the final response body (capped to `MAX_FINAL_RESPONSE_BYTES`) and marks `bodyTruncated` when truncated.
  - Do not log response bodies in production.

---

## Troubleshooting

### Failure reasons

`SnaResult.Failure.reason` is one of:
- `INVALID_URL`
- `MOBILE_DATA_UNAVAILABLE`
- `TIMEOUT`
- `REDIRECT_FAILURE`
- `NETWORK_ERROR`
- `UNKNOWN_ERROR`

### Common `detail` values

Depending on the failure:
- `NETWORK_ERROR` + `DNS_FAILED`: DNS resolution failed (often carrier / Private DNS / routing).
- `NETWORK_ERROR` + `CLEARTEXT_NOT_PERMITTED`: URL is `http://` and cleartext is blocked (configure network security or use `https://`).
- `REDIRECT_FAILURE` + `TOO_MANY_REDIRECTS`: OkHttp hit its follow-up limit (redirect loop / too many hops).
- `TIMEOUT` + `Timed out after <N>s`: overall coroutine timeout hit.

---

## API reference

### `SNASdk`

- `initialize(context, appId?, eventEndpoint?)`
- `getInstance()`
- `setLoggingEnabled(enabled)`
- `getSimNetworkInfo(): SimNetworkInfo`
- `isMobileDataEnabled(): Boolean`
- `authenticate(url, timeoutSeconds = DEFAULT): SnaResult` *(suspend)*
- `authenticateBlocking(url, timeoutSeconds = DEFAULT): SnaResult`

### Models

- `SimNetworkInfo(mcc, mnc, networkName, isMobileDataEnabled)`
- `SnaResult.Success(response, timings, redirects)`
- `SnaResult.Failure(reason, detail, timings, response?, redirects)`
- `SnaFinalResponse(finalUrl, httpCode, headers, body, bodyTruncated, contentType)`
- `SnaTimings(totalMs, cellularAcquireMs?, processBindMs?)`
- `SnaRedirectHop(url, httpCode?, durationMs, dnsMs?, connectMs?, tlsMs?)`

