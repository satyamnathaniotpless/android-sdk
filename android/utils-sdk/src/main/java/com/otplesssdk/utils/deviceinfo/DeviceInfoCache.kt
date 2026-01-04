package com.otplesssdk.utils.deviceinfo

import java.util.concurrent.ConcurrentHashMap

internal object DeviceInfoCache {
    /**
     * TTL-gated slot used by [DeviceInfoCollector].
     *
     * - [attemptElapsed] tracks the last time we *tried* to refresh (monotonic time).
     * - [successElapsed] tracks the last time we *successfully* refreshed (monotonic time).
     * - [value] holds the most recent successful value.
     *
     * Storing attempt+success separately avoids two common problems in SDKs:
     * - hammering a failing API every loop tick
     * - treating a failed attempt as a "fresh" success
     */
    internal class TtlSlot<T> {
        var value: T? = null

        var attemptElapsed: Long = 0
        var successElapsed: Long = 0
    }

    internal class Entry {
        // Non-TTL sections: computed once (or filled if missing) and treated as stable for the process.
        var osInfo: OsInfo? = null
        var buildInfo: BuildInfo? = null
        var systemInfo: SystemInfo? = null

        // TTL sections: can change over time; refreshed in background.
        val hardware = TtlSlot<HardwareInfo>()
        val network = TtlSlot<NetworkInfo>()
        val app = TtlSlot<AppInfo>()
        val appPresence = TtlSlot<AppPresenceInfo>()
        val referrer = TtlSlot<ReferrerInfo>()
        val identifiers = TtlSlot<IdentifiersInfo>()
        val integrity = TtlSlot<IntegrityInfo>()
    }

    internal data class TtlSnapshot<T>(
        val value: T?,
        val attemptElapsed: Long,
        val successElapsed: Long,
    )

    internal data class Snapshot(
        val osInfo: OsInfo?,
        val buildInfo: BuildInfo?,
        val systemInfo: SystemInfo?,
        val hardware: TtlSnapshot<HardwareInfo>,
        val network: TtlSnapshot<NetworkInfo>,
        val app: TtlSnapshot<AppInfo>,
        val appPresence: TtlSnapshot<AppPresenceInfo>,
        val referrer: TtlSnapshot<ReferrerInfo>,
        val identifiers: TtlSnapshot<IdentifiersInfo>,
        val integrity: TtlSnapshot<IntegrityInfo>,
    )

    private val map = ConcurrentHashMap<String, Entry>()

    /**
     * A "key" represents a unique SDK identity in the same app process.
     * We use `sdkName|sdkVersion` so multiple SDKs/versions can share the same module without mixing caches.
     */
    fun getOrCreate(key: String): Entry = map.getOrPut(key) { Entry() }

    fun keysSnapshot(): Set<String> {
        return map.keys.toSet()
    }

    fun snapshot(key: String): Snapshot {
        val entry = getOrCreate(key)
        synchronized(entry) {
            return Snapshot(
                osInfo = entry.osInfo,
                buildInfo = entry.buildInfo,
                systemInfo = entry.systemInfo,
                hardware = TtlSnapshot(
                    value = entry.hardware.value,
                    attemptElapsed = entry.hardware.attemptElapsed,
                    successElapsed = entry.hardware.successElapsed
                ),
                network = TtlSnapshot(
                    value = entry.network.value,
                    attemptElapsed = entry.network.attemptElapsed,
                    successElapsed = entry.network.successElapsed
                ),
                app = TtlSnapshot(
                    value = entry.app.value,
                    attemptElapsed = entry.app.attemptElapsed,
                    successElapsed = entry.app.successElapsed
                ),
                appPresence = TtlSnapshot(
                    value = entry.appPresence.value,
                    attemptElapsed = entry.appPresence.attemptElapsed,
                    successElapsed = entry.appPresence.successElapsed
                ),
                referrer = TtlSnapshot(
                    value = entry.referrer.value,
                    attemptElapsed = entry.referrer.attemptElapsed,
                    successElapsed = entry.referrer.successElapsed
                ),
                identifiers = TtlSnapshot(
                    value = entry.identifiers.value,
                    attemptElapsed = entry.identifiers.attemptElapsed,
                    successElapsed = entry.identifiers.successElapsed
                ),
                integrity = TtlSnapshot(
                    value = entry.integrity.value,
                    attemptElapsed = entry.integrity.attemptElapsed,
                    successElapsed = entry.integrity.successElapsed
                ),
            )
        }
    }

    fun update(key: String, updater: Entry.() -> Unit) {
        val entry = getOrCreate(key)
        synchronized(entry) {
            entry.updater()
        }
    }

    fun clear() {
        map.clear()
    }
}

