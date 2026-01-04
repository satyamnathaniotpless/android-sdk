package com.otplesssdk.utils.deviceinfo

import com.otplesssdk.utils.json.Json

internal object DeviceInfoJsonBuilder {
    /**
     * Builds the `device_info` JSON payload content (without outer braces).
     *
     * Design goals:
     * - Best-effort: missing sections are omitted.
     * - No external JSON dependency: uses [Json] utility.
     * - Stable keys for backend parsing.
     */
    private fun MutableMap<String, Any?>.putIfNotNull(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    private fun content(map: Map<String, Any?>): String {
        return map.entries.joinToString(",") { (k, v) ->
            "${Json.quote(k)}:${Json.value(v)}"
        }
    }

    fun build(
        osInfo: OsInfo?,
        buildInfo: BuildInfo?,
        hardwareInfo: HardwareInfo?,
        networkInfo: NetworkInfo?,
        appInfo: AppInfo?,
        appPresenceInfo: AppPresenceInfo?,
        referrerInfo: ReferrerInfo?,
        systemInfo: SystemInfo?,
        identifiersInfo: IdentifiersInfo?,
        integrityInfo: IntegrityInfo?,
        sdkVersion: String?,
        sdkName: String?
    ): String {

        val root = linkedMapOf<String, Any?>()

        val os = linkedMapOf<String, Any?>(
            "platform" to "android"
        )
        osInfo?.let {
            os["version"] = it.version
            os["api_level"] = it.apiLevel
            os.putIfNotNull("codename", it.codename)
        }
        root["os_info"] = os

        hardwareInfo?.let { hw ->
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("manufacturer", hw.deviceManufacturer)
            m.putIfNotNull("model", hw.deviceModel)
            m.putIfNotNull("brand", hw.deviceBrand)
            m.putIfNotNull("product", hw.deviceProduct)
            m.putIfNotNull("total_memory", hw.totalMemory)
            m.putIfNotNull("total_ram", hw.totalRAM)
            m.putIfNotNull("storage_available", hw.storageAvailable)
            m.putIfNotNull("storage_total", hw.storageTotal)
            if (m.isNotEmpty()) root["hardware_info"] = m
        }

        identifiersInfo?.let { ids ->
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("gaid", ids.gaid)
            m.putIfNotNull("drm_id", ids.drmId)
            m.putIfNotNull("android_id", ids.androidId)
            if (m.isNotEmpty()) root["identifiers"] = m
        }

        appPresenceInfo?.let { p ->
            val m = linkedMapOf<String, Any?>()
            m["whatsapp"] = p.whatsapp
            m["whatsapp_business"] = p.whatsappBusiness
            m["whatsapp_any"] = p.whatsappAny
            m["telegram"] = p.telegram
            m["truecaller"] = p.truecaller
            m["viber"] = p.viber
            root["app_presence"] = m
        }

        integrityInfo?.token?.let { token ->
            val m = linkedMapOf<String, Any?>()
            m["token"] = token
            integrityInfo.tokenTimestampMs?.let { ts -> m["token_timestamp_ms"] = ts }
            if (m.isNotEmpty()) root["integrity_info"] = m
        }

        buildInfo?.let { b ->
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("build_id", b.buildId)
            m.putIfNotNull("build_type", b.buildType)
            m.putIfNotNull("build_tags", b.buildTags)
            m.putIfNotNull("build_host", b.buildHost)
            m.putIfNotNull("build_user", b.buildUser)
            if (m.isNotEmpty()) root["build_info"] = m
        }

        networkInfo?.let { n ->
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("type", n.networkType)
            m.putIfNotNull("is_connected", n.isConnected)
            m.putIfNotNull("has_internet_capability", n.hasInternetCapability)
            m.putIfNotNull("is_validated", n.isValidated)
            m.putIfNotNull("is_vpn_transport_active", n.isVpnTransportActive)
            m.putIfNotNull("has_sim", n.hasSim)
            m.putIfNotNull("phone_type", n.phoneType)
            m.putIfNotNull("is_cellular_data_enabled", n.isCellularDataEnabled)

            n.defaultData?.subscriptionId?.let { subId ->
                val d = linkedMapOf<String, Any?>("subscription_id" to subId)
                n.defaultData.simMccMnc?.let { d["sim_mcc_mnc"] = it }
                n.defaultData.networkMccMnc?.let { d["network_mcc_mnc"] = it }
                m["default_data"] = d
            }
            n.defaultCall?.subscriptionId?.let { subId ->
                val d = linkedMapOf<String, Any?>("subscription_id" to subId)
                n.defaultCall.simMccMnc?.let { d["sim_mcc_mnc"] = it }
                n.defaultCall.networkMccMnc?.let { d["network_mcc_mnc"] = it }
                m["default_call"] = d
            }

            if (m.isNotEmpty()) root["network_info"] = m
        }

        run {
            val m = linkedMapOf<String, Any?>()
            appInfo?.let { a ->
                m.putIfNotNull("version", a.appVersion)
                m.putIfNotNull("version_code", a.appVersionCode)
                m.putIfNotNull("package_name", a.appPackageName)
                m.putIfNotNull("target_sdk", a.appTargetSdk)
                m.putIfNotNull("min_sdk", a.appMinSdk)
                m.putIfNotNull("install_source", a.installSource)
                m.putIfNotNull("signature_hash", a.appSignatureHash)
                m.putIfNotNull("first_install_time", a.firstInstallTime)
                m.putIfNotNull("last_update_time", a.lastUpdateTime)
                m.putIfNotNull("is_instant_app", a.isInstantApp)
            }
            m.putIfNotNull("referrer", referrerInfo?.referrer)
            if (m.isNotEmpty()) root["app_info"] = m
        }

        run {
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("version", sdkVersion)
            m.putIfNotNull("name", sdkName)
            if (m.isNotEmpty()) root["sdk_info"] = m
        }

        systemInfo?.let { s ->
            val m = linkedMapOf<String, Any?>()
            m.putIfNotNull("timezone", s.timezone)
            m.putIfNotNull("locale", s.locale)
            m.putIfNotNull("is_emulator", s.isEmulator)
            if (m.isNotEmpty()) root["system_info"] = m
        }

        return content(root)
    }
}
