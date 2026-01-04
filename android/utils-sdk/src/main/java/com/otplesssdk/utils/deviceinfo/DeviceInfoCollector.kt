package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.content.pm.PackageInfo
import com.otplesssdk.utils.concurrency.Once
import com.otplesssdk.utils.concurrency.SingleFlight
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.logger.SdkLogger
import com.otplesssdk.utils.safe.Safe
import com.otplesssdk.utils.time.Clock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DeviceInfoCollector {
    private const val TAG = "DeviceInfoCollector"

    /**
     * Device-info lifecycle (high level):
     *
     * - Call [warmUp] at SDK init to start background refresh and prime caches.
     * - Call [getDeviceInfoJson] when you need the payload; it returns immediately from cache and
     *   schedules background refresh for missing/expired sections.
     *
     * Caching:
     * - In-memory cache lives in [DeviceInfoCache], keyed by `(sdkName|sdkVersion)`.
     * - Each TTL-gated section stores:
     *   - last attempt time (monotonic): prevents hammering
     *   - last success time (monotonic): TTL freshness
     *
     * Threading:
     * - No public API here should crash or block the main thread.
     * - Potentially blocking collectors are wrapped with timeouts.
     */
    private const val NETWORK_TTL_MS = 60 * 1000L
    private const val APPINFO_TTL_MS = 10 * 60 * 1000L
    private const val APP_PRESENCE_TTL_MS = 10 * 60 * 1000L
    private const val HARDWARE_TTL_MS = 10 * 60 * 1000L
    private const val REFERRER_TTL_MS = 24 * 60 * 60 * 1000L
    private const val IDENTIFIERS_TTL_MS = 30 * 60 * 1000L
    private const val INTEGRITY_TTL_MS = 10 * 60 * 1000L

    private const val REFRESH_LOOP_INTERVAL_MS = 30 * 1000L

    private const val NETWORK_TIMEOUT_MS = 1000L
    private const val HARDWARE_TIMEOUT_MS = 1500L
    private const val APPINFO_TIMEOUT_MS = 2000L
    private const val IDENTIFIERS_TIMEOUT_MS = 3000L
    private const val APP_PRESENCE_TIMEOUT_MS = 1000L

    private val refreshJob = SupervisorJob()
    private val refreshScope = SdkCoroutineScope.createIOScope(parentJob = refreshJob)

    // Ensures exactly one refresh loop per process.
    private val refreshLoopOnce = Once()

    // Stored as application context to avoid leaking Activities.
    @Volatile
    private var loopAppContext: Context? = null

    private fun collectDeviceInfoJson(context: Context, sdkVersion: String? = null, sdkName: String? = null): String {
        val appContext = context.applicationContext
        val key = cacheKey(sdkVersion, sdkName)

        // Start/ensure periodic background refresh (idempotent).
        ensureBackgroundRefresh(appContext)

        // Fill minimal basics synchronously only if missing (cheap and fast).
        val snapshotBefore = DeviceInfoCache.snapshot(key)
        if (snapshotBefore.osInfo == null || snapshotBefore.buildInfo == null || snapshotBefore.systemInfo == null) {
            DeviceInfoCache.update(key) {
                if (osInfo == null) osInfo = Safe.tryOrNull(TAG, "os_info") { OsInfoCollector.collect() }
                if (buildInfo == null) buildInfo = Safe.tryOrNull(TAG, "build_info") { BuildInfoCollector.collect() }
                if (systemInfo == null) systemInfo = Safe.tryOrNull(TAG, "system_info") { SystemInfoCollector.collect() }
            }
        }

        // Trigger a background refresh pass for missing/expired sections (single-flight per key).
        refreshMissingAsync(appContext, key)

        val snapshot = DeviceInfoCache.snapshot(key)

        val deviceInfoJson = try {
            DeviceInfoJsonBuilder.build(
                osInfo = snapshot.osInfo,
                buildInfo = snapshot.buildInfo,
                hardwareInfo = snapshot.hardware.value,
                networkInfo = snapshot.network.value,
                appInfo = snapshot.app.value,
                appPresenceInfo = snapshot.appPresence.value,
                referrerInfo = snapshot.referrer.value,
                systemInfo = snapshot.systemInfo,
                identifiersInfo = snapshot.identifiers.value,
                integrityInfo = snapshot.integrity.value,
                sdkVersion = sdkVersion,
                sdkName = sdkName
            )
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error building device info JSON: ${e.javaClass.name}: ${e.message}")
            ""
        }

        return deviceInfoJson
    }

    /**
     * Warm up device-info collection.
     *
     * **Threading**: safe to call on the main thread (work is scheduled on a background scope).
     *
     * **Behavior**:
     * - Starts the background refresh loop (only once per process).
     * - Kicks off a background refresh for the given `(sdkName, sdkVersion)` cache key.
     * - Starts Install Referrer collection (best-effort).
     *
     * **Caching / TTL**:
     * - Does not block to wait for data.
     * - Populates in-memory cache progressively; later calls to [getDeviceInfoJson] return richer data.
     *
     * **Failure**:
     * - Never throws; failures are best-effort and will not crash the host app.
     */
    @JvmStatic
    fun warmUp(context: Context, sdkVersion: String? = null, sdkName: String? = null) {
        try {
            val appContext = context.applicationContext
            val key = cacheKey(sdkVersion, sdkName)

            // Referrer is async; starting early improves chance it is available for later payloads.
            ReferrerCollector.start(appContext)

            ensureBackgroundRefresh(appContext)

            refreshMissingAsync(appContext, key)
        } catch (e: Exception) {
            SdkLogger.w(TAG, "DeviceInfo warmUp failed: ${e.javaClass.name}: ${e.message}")
        }
    }

    /**
     * Best-effort network snapshot.
     *
     * Threading: safe to call on any thread.
     */
    @JvmStatic
    fun getNetworkInfo(context: Context): NetworkInfo {
        val appContext = context.applicationContext
        val telephonyManager = try {
            appContext.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        } catch (_: Exception) {
            null
        }
        val connectivityManager = try {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        } catch (_: Exception) {
            null
        }
        return NetworkInfoCollector.collect(connectivityManager, telephonyManager)
    }

    @JvmStatic
    fun getIdentifiersInfo(context: Context): IdentifiersInfo {
        return IdentifiersCollector.collect(context.applicationContext)
    }

    @JvmStatic
    fun getAppPresenceInfo(context: Context): AppPresenceInfo {
        return AppPresenceCollector.collect(context.applicationContext)
    }

    private fun refreshMissingAsync(
        context: Context,
        key: String
    ) {
        val flightKey = "deviceinfo_refresh:$key"
        // Prevent duplicate refresh passes for the same key running concurrently.
        if (!SingleFlight.enter(flightKey)) return

        refreshScope.launch {
            try {
                refreshAllSectionsOnce(context, key)
            } finally {
                SingleFlight.leave(flightKey)
            }
        }
    }

    private suspend fun refreshAllSectionsOnce(context: Context, key: String) {
        val appContext = context.applicationContext
        // TTL comparisons must use a monotonic clock to avoid wall-clock jumps.
        val nowElapsed = Clock.elapsedRealtime()

        val packageInfo = getPackageInfo(appContext)
        val telephonyManager = try {
            appContext.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        } catch (_: Exception) {
            null
        }
        val connectivityManager = try {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        } catch (_: Exception) {
            null
        }

        val snapshot = DeviceInfoCache.snapshot(key)

        // Non-TTL basics: fill once if missing (no need to refresh frequently).
        DeviceInfoCache.update(key) {
            if (osInfo == null) osInfo = Safe.tryOrNull(TAG, "os_info") { OsInfoCollector.collect() }
            if (buildInfo == null) buildInfo = Safe.tryOrNull(TAG, "build_info") { BuildInfoCollector.collect() }
            if (systemInfo == null) systemInfo = Safe.tryOrNull(TAG, "system_info") { SystemInfoCollector.collect() }
        }

        // TTL-gated sections:
        // - sync sections: collect -> update cache (attempt/success tracked)
        // - async sections: trigger fetch on TTL cadence, but poll cached value frequently when missing
        coroutineScope {
            launchSyncSection(
                key = key,
                nowElapsed = nowElapsed,
                name = "hardware_info",
                ttlMs = HARDWARE_TTL_MS,
                timeoutMs = HARDWARE_TIMEOUT_MS,
                snapshot = snapshot.hardware,
                slot = { hardware },
            ) { Safe.tryOrNull(TAG, "hardware_info") { HardwareInfoCollector.collect(appContext) } }

            launchSyncSection(
                key = key,
                nowElapsed = nowElapsed,
                name = "app_info",
                ttlMs = APPINFO_TTL_MS,
                timeoutMs = APPINFO_TIMEOUT_MS,
                snapshot = snapshot.app,
                slot = { app },
            ) { Safe.tryOrNull(TAG, "app_info") { AppInfoCollector.collect(appContext, packageInfo) } }

            launchSyncSection(
                key = key,
                nowElapsed = nowElapsed,
                name = "app_presence",
                ttlMs = APP_PRESENCE_TTL_MS,
                timeoutMs = APP_PRESENCE_TIMEOUT_MS,
                snapshot = snapshot.appPresence,
                slot = { appPresence },
            ) { Safe.tryOrNull(TAG, "app_presence") { AppPresenceCollector.collect(appContext) } }

            launchSyncSection(
                key = key,
                nowElapsed = nowElapsed,
                name = "network_info",
                ttlMs = NETWORK_TTL_MS,
                timeoutMs = NETWORK_TIMEOUT_MS,
                snapshot = snapshot.network,
                slot = { network },
            ) {
                Safe.tryOrNull(TAG, "network_info") {
                    NetworkInfoCollector.collect(connectivityManager, telephonyManager)
                }
            }

            launchSyncSection(
                key = key,
                nowElapsed = nowElapsed,
                name = "identifiers",
                ttlMs = IDENTIFIERS_TTL_MS,
                timeoutMs = IDENTIFIERS_TIMEOUT_MS,
                snapshot = snapshot.identifiers,
                slot = { identifiers },
            ) { Safe.tryOrNull(TAG, "identifiers") { IdentifiersCollector.collect(appContext) } }

            launchAsyncSection(
                key = key,
                nowElapsed = nowElapsed,
                ttlMs = REFERRER_TTL_MS,
                snapshot = snapshot.referrer,
                slot = { referrer },
                trigger = { ReferrerCollector.start(appContext) },
                readCached = { Safe.tryOrNull(TAG, "referrer_info") { ReferrerCollector.getCached() } }
            )

            launchAsyncSection(
                key = key,
                nowElapsed = nowElapsed,
                ttlMs = INTEGRITY_TTL_MS,
                snapshot = snapshot.integrity,
                slot = { integrity },
                trigger = { IntegrityCollector.refreshAsync(appContext) },
                readCached = { Safe.tryOrNull(TAG, "integrity_info") { IntegrityCollector.getCached() } }
            )
        }
    }

    private fun <T> kotlinx.coroutines.CoroutineScope.launchSyncSection(
        key: String,
        nowElapsed: Long,
        name: String,
        ttlMs: Long,
        timeoutMs: Long,
        snapshot: DeviceInfoCache.TtlSnapshot<T>,
        slot: DeviceInfoCache.Entry.() -> DeviceInfoCache.TtlSlot<T>,
        collect: suspend () -> T?,
    ) {
        if (!shouldAttempt(snapshot, ttlMs, nowElapsed)) return
        launch {
            // Timeout prevents rare OEM/IPC stalls from hanging our refresh coroutine forever.
            val v = Safe.withTimeoutOrNull(TAG, name, timeoutMs) { collect() }
            updateTtlSection(
                key = key,
                nowElapsed = nowElapsed,
                ttlMs = ttlMs,
                newValue = v,
                slot = slot
            )
        }
    }

    private fun <T> kotlinx.coroutines.CoroutineScope.launchAsyncSection(
        key: String,
        nowElapsed: Long,
        ttlMs: Long,
        snapshot: DeviceInfoCache.TtlSnapshot<T>,
        slot: DeviceInfoCache.Entry.() -> DeviceInfoCache.TtlSlot<T>,
        trigger: () -> Unit,
        readCached: () -> T?,
    ) {
        val shouldTrigger = shouldAttempt(snapshot, ttlMs, nowElapsed)
        val shouldPoll = snapshot.value == null
        if (!shouldTrigger && !shouldPoll) return
        launch {
            if (shouldTrigger) {
                trigger()
                // Mark the attempt even if the async value isn't available yet.
                markAttempt(key = key, nowElapsed = nowElapsed, slot = slot)
            }
            val v = readCached()
            updateIfPresent(key = key, nowElapsed = nowElapsed, newValue = v, slot = slot)
        }
    }

    private fun isExpired(lastElapsed: Long, ttlMs: Long, nowElapsed: Long): Boolean {
        if (lastElapsed <= 0L) return true
        return nowElapsed - lastElapsed > ttlMs
    }

    private fun <T> shouldAttempt(
        s: DeviceInfoCache.TtlSnapshot<T>,
        ttlMs: Long,
        nowElapsed: Long
    ): Boolean {
        val fresh = s.value != null && !isExpired(s.successElapsed, ttlMs, nowElapsed)
        if (fresh) return false
        return s.attemptElapsed == 0L || isExpired(s.attemptElapsed, ttlMs, nowElapsed)
    }

    private inline fun <T> updateTtlSection(
        key: String,
        nowElapsed: Long,
        ttlMs: Long,
        newValue: T?,
        crossinline slot: DeviceInfoCache.Entry.() -> DeviceInfoCache.TtlSlot<T>,
    ) {
        DeviceInfoCache.update(key) {
            val s = slot()
            val attempt = s.attemptElapsed
            val success = s.successElapsed

            // Prevent an older refresh pass from overwriting a newer one.
            if (attempt > nowElapsed || success > nowElapsed) return@update

            val current = s.value
            if (current != null && !isExpired(success, ttlMs, nowElapsed)) return@update

            s.attemptElapsed = nowElapsed

            if (newValue != null) {
                s.value = newValue
                s.successElapsed = nowElapsed
            }
        }
    }

    private inline fun <T> markAttempt(
        key: String,
        nowElapsed: Long,
        crossinline slot: DeviceInfoCache.Entry.() -> DeviceInfoCache.TtlSlot<T>,
    ) {
        DeviceInfoCache.update(key) {
            val s = slot()
            if (s.attemptElapsed > nowElapsed) return@update
            s.attemptElapsed = nowElapsed
        }
    }

    private inline fun <T> updateIfPresent(
        key: String,
        nowElapsed: Long,
        newValue: T?,
        crossinline slot: DeviceInfoCache.Entry.() -> DeviceInfoCache.TtlSlot<T>,
    ) {
        if (newValue == null) return
        DeviceInfoCache.update(key) {
            val s = slot()
            if (s.successElapsed > nowElapsed) return@update
            s.value = newValue
            s.successElapsed = nowElapsed
        }
    }

    private fun ensureBackgroundRefresh(appContext: Context) {
        loopAppContext = appContext.applicationContext

        if (!refreshLoopOnce.tryStart()) return

        refreshScope.launch {
            while (refreshJob.isActive) {
                val ctx = loopAppContext
                if (ctx != null) {
                    // Usually only 1 key exists, but iterate safely over a snapshot.
                    for (key in DeviceInfoCache.keysSnapshot()) {
                        try {
                            refreshMissingAsync(ctx, key)
                        } catch (_: Exception) {
                        }
                    }
                }
                delay(REFRESH_LOOP_INTERVAL_MS)
            }
        }
    }

    private fun cacheKey(sdkVersion: String?, sdkName: String?): String {
        return "${sdkName.orEmpty()}|${sdkVersion.orEmpty()}"
    }
    
    /**
     * Returns `device_info` JSON content (without outer braces), suitable for embedding into a larger JSON.
     *
     * **Threading**:
     * - Safe to call on the main thread.
     * - Returns immediately with whatever is already cached.
     * - Schedules background work to refresh missing/expired sections.
     *
     * **Snapshot semantics**:
     * - Best-effort: fields may be missing if not yet collected or unavailable on the device.
     * - Richer later: subsequent calls typically return more fields as background refresh completes.
     *
     * **Dependencies / availability** (best-effort):
     * - Install Referrer requires Play Store; collected asynchronously.
     * - Play Integrity requires Play services and app eligibility; collected asynchronously.
     * - GAID collection is best-effort and may be unavailable depending on device/Play services/user settings.
     *
     * **Failure**:
     * - Never throws; returns empty string on unexpected errors.
     */
    fun getDeviceInfoJson(
        context: Context,
        sdkVersion: String? = null,
        sdkName: String? = null
    ): String {
        return try {
            collectDeviceInfoJson(context, sdkVersion, sdkName)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Failed to get device info JSON", e)
            ""
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(context: Context): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        DeviceInfoCache.clear()
    }
}
