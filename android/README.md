# SNA SDK (Android)

SIM Network Authentication (SNA) SDK for Android. Designed for embedding in large apps with a minimal API surface, strong defaults, and enterprise-friendly diagnostics.

## What You Get

- **SIM + Network Info**: MCC, MNC, carrier/network name, and a conservative “mobile data enabled” signal
- **SNA URL Call (Cellular)**: performs the SNA flow over **cellular data** (even if Wi‑Fi is connected) and returns:
  - final URL + final HTTP response (headers + capped body)
  - total time + key phase timings
  - per-hop redirect breakdown (status + timings)

## Quick Start

### 1) Add the SDK

**If you consume a published AAR**

```gradle
dependencies {
    implementation("com.sna:sna-sdk:<version>")
}
```

**If you’re integrating as a local module**

```gradle
dependencies {
    implementation(project(":sna-sdk"))
}
```

### 2) Permissions

The SDK declares the required permissions in its library manifest; in most cases you don’t need to add anything. Some enterprise apps prefer declaring explicitly—use:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

Optional (host app choice, improves MCC/MNC reliability on some older devices):

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" android:maxSdkVersion="28" />
```

### 3) Cleartext (HTTP) SNA URLs

If your initial SNA URL is `http://…`, Android may block it unless you explicitly allow cleartext for that domain. Prefer `https://` when possible.

Example `network_security_config.xml` that allows cleartext only for a specific domain:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">partnerapi.jio.com</domain>
  </domain-config>
</network-security-config>
```

And reference it in your app manifest:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config" />
```

### 4) Initialize

Call once (Application is recommended):

```kotlin
SNASdk.initialize(applicationContext)
```

### 5) Enable logging (optional)

Logging is **disabled by default** (including errors). Enable only during debugging:

```kotlin
SNASdk.setLoggingEnabled(true)
```

## Usage

### Get SIM + Network Info

```kotlin
val sdk = SNASdk.getInstance() ?: return
val info = sdk.getSimNetworkInfo()

println("MCC=${info.mcc} MNC=${info.mnc} Name=${info.networkName}")
println("MobileDataEnabled(conservative)=${info.isMobileDataEnabled}")
```

Notes:
- `mcc/mnc/networkName` are **best-effort** and may be `null` (no SIM, not registered, access restricted).
- `isMobileDataEnabled()` is **conservative**: if the SDK can’t reliably determine the state, it returns `false`.

### Run SNA Authentication (Cellular + Trace)

```kotlin
val sdk = SNASdk.getInstance() ?: return

sdk.authenticate(
    url = "http://partnerapi.jio.com/v2/adv/smv?...",
    timeoutSeconds = 30,
    callback = object : SnaCallback {
        override fun onResult(result: SnaResult) {
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
        }
    }
)
```

### Java usage

```java
SNASdk sdk = SNASdk.getInstance();
if (sdk == null) return;

sdk.authenticate("http://partnerapi.jio.com/v2/adv/smv?...", new SnaCallback() {
  @Override public void onResult(SnaResult result) {
    if (result instanceof SnaResult.Success) {
      SnaResult.Success s = (SnaResult.Success) result;
      System.out.println(s.getResponse().getFinalUrl());
    }
  }
});
```

## Enterprise Notes (Important)

### Cellular enforcement model

On API 23+ the SDK acquires a cellular `Network` and then performs a temporary **process-wide** bind to that network for the duration of the SNA call. This is the most reliable approach on real devices when Wi‑Fi is connected.

Implications:
- While the bind is active, **other concurrent network traffic** in the app process may also route over cellular.
- The SDK serializes SNA calls internally to minimize risk, but the host app should avoid running SNA in parallel with other route-sensitive network operations if this matters.

### Data handling & privacy

- Redirect trace and the final URL can include sensitive query params (tokens/IDs). Treat `SnaFinalResponse.finalUrl` as **sensitive**.
- The SDK returns the final response body (capped to `MAX_FINAL_RESPONSE_BYTES`) and marks `bodyTruncated` when truncated. Do not log this in production.

## Troubleshooting

- `MOBILE_DATA_UNAVAILABLE`: no usable cellular network acquired within the acquisition timeout. Check SIM/data state and permissions.
- `NETWORK_ERROR` + `DNS_FAILED`: DNS resolution failed over cellular (may be carrier/Private DNS related).
- `NETWORK_ERROR` + `CLEARTEXT_NOT_PERMITTED`: initial URL is `http://` and cleartext is blocked; add `network_security_config` or use `https://`.
- `REDIRECT_FAILURE` + `TOO_MANY_REDIRECTS`: OkHttp hit its internal follow-up limit (redirect loop/too many hops).
- `TIMEOUT`: overall operation exceeded `timeoutSeconds`.

## API Reference

### `SNASdk`

- `initialize(context)`
- `getInstance()`
- `setLoggingEnabled(enabled)`
- `getSimNetworkInfo(): SimNetworkInfo`
- `isMobileDataEnabled(): Boolean`
- `authenticate(url, callback)`
- `authenticate(url, timeoutSeconds, callback)`

### Models

- `SimNetworkInfo(mcc, mnc, networkName, isMobileDataEnabled)`
- `SnaResult.Success(response, timings, redirects)`
- `SnaResult.Failure(reason, detail, timings, response?, redirects)`
- `SnaFinalResponse(finalUrl, httpCode, headers, body, bodyTruncated, contentType)`
- `SnaTimings(totalMs, cellularAcquireMs?, processBindMs?)`
- `SnaRedirectHop(url, httpCode?, durationMs, dnsMs?, connectMs?, tlsMs?)`

## OTP SDK

See `otp-sdk/README.md` for the SMS Retriever + WhatsApp OTP auto-read SDK.
