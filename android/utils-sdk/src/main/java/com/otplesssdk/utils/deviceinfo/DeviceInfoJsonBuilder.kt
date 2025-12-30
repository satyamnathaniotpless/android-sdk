package com.otplesssdk.utils.deviceinfo

/**
 * Builds device_info JSON (without outer braces) directly from collector outputs.
 * Returns the JSON content (without outer braces) that can be embedded in another JSON object.
 */
internal object DeviceInfoJsonBuilder {
    /**
     * Build JSON string from collector outputs.
     */
    fun build(
        osInfo: OsInfo?,
        buildInfo: BuildInfo?,
        hardwareInfo: HardwareInfo?,
        networkInfo: NetworkInfo?,
        appInfo: AppInfo?,
        systemInfo: SystemInfo?,
        identifiersInfo: IdentifiersInfo?,
        sdkVersion: String?,
        sdkName: String?
    ): String {
        
        val deviceParts = mutableListOf<String>()
        
        // OS Information
        val osParts = mutableListOf<String>()
        // Explicit platform identifier (backend uses this to distinguish Android vs iOS vs Web SDK payloads).
        osParts.add("\"platform\":\"android\"")
        osInfo?.version?.let { osParts.add("\"version\":${escapeJson(it)}") }
        osInfo?.apiLevel?.let { osParts.add("\"api_level\":$it") }
        osInfo?.codename?.let { osParts.add("\"codename\":${escapeJson(it)}") }
        if (osParts.isNotEmpty()) {
            deviceParts.add("\"os_info\":{${osParts.joinToString(",")}}")
        }
        
        // Hardware Information
        val hardwareParts = mutableListOf<String>()
        hardwareInfo?.deviceManufacturer?.let { hardwareParts.add("\"manufacturer\":${escapeJson(it)}") }
        hardwareInfo?.deviceModel?.let { hardwareParts.add("\"model\":${escapeJson(it)}") }
        hardwareInfo?.deviceBrand?.let { hardwareParts.add("\"brand\":${escapeJson(it)}") }
        hardwareInfo?.deviceProduct?.let { hardwareParts.add("\"product\":${escapeJson(it)}") }
        hardwareInfo?.totalMemory?.let { hardwareParts.add("\"total_memory\":$it") }
        hardwareInfo?.totalRAM?.let { hardwareParts.add("\"total_ram\":$it") }
        hardwareInfo?.storageAvailable?.let { hardwareParts.add("\"storage_available\":$it") }
        hardwareInfo?.storageTotal?.let { hardwareParts.add("\"storage_total\":$it") }
        if (hardwareParts.isNotEmpty()) {
            deviceParts.add("\"hardware_info\":{${hardwareParts.joinToString(",")}}")
        }
        
        // Identifiers
        val identifierParts = mutableListOf<String>()
        hardwareInfo?.deviceId?.let { identifierParts.add("\"device_id\":${escapeJson(it)}") }
        identifiersInfo?.gaid?.let { identifierParts.add("\"gaid\":${escapeJson(it)}") }
        identifiersInfo?.drmId?.let { identifierParts.add("\"drm_id\":${escapeJson(it)}") }
        if (identifierParts.isNotEmpty()) {
            deviceParts.add("\"identifiers\":{${identifierParts.joinToString(",")}}")
        }
        
        // Build Information
        val buildParts = mutableListOf<String>()
        buildInfo?.buildId?.let { buildParts.add("\"build_id\":${escapeJson(it)}") }
        buildInfo?.buildType?.let { buildParts.add("\"build_type\":${escapeJson(it)}") }
        buildInfo?.buildTags?.let { buildParts.add("\"build_tags\":${escapeJson(it)}") }
        buildInfo?.buildHost?.let { buildParts.add("\"build_host\":${escapeJson(it)}") }
        buildInfo?.buildUser?.let { buildParts.add("\"build_user\":${escapeJson(it)}") }
        if (buildParts.isNotEmpty()) {
            deviceParts.add("\"build_info\":{${buildParts.joinToString(",")}}")
        }
        
        // Network Information
        val networkParts = mutableListOf<String>()
        // Flat top-level network_info fields (as requested)
        networkInfo?.networkType?.let { networkParts.add("\"type\":${escapeJson(it)}") }
        networkInfo?.isConnected?.let { networkParts.add("\"is_connected\":$it") }
        networkInfo?.hasInternetCapability?.let { networkParts.add("\"has_internet_capability\":$it") }
        networkInfo?.isValidated?.let { networkParts.add("\"is_validated\":$it") }
        networkInfo?.isVpnTransportActive?.let { networkParts.add("\"is_vpn_transport_active\":$it") }
        networkInfo?.hasSim?.let { networkParts.add("\"has_sim\":$it") }
        networkInfo?.phoneType?.let { networkParts.add("\"phone_type\":${escapeJson(it)}") }
        networkInfo?.simSlotCount?.let { networkParts.add("\"sim_slot_count\":$it") }
        networkInfo?.dataNetworkType?.let { networkParts.add("\"data_network_type\":$it") }
        networkInfo?.callState?.let { networkParts.add("\"call_state\":$it") }

        networkInfo?.subscriptions?.takeIf { it.isNotEmpty() }?.let { subs ->
            val arr = subs.mapNotNull { s ->
                val parts = mutableListOf<String>()
                s.subscriptionId?.let { parts.add("\"subscription_id\":$it") }
                s.simSlotIndex?.let { parts.add("\"sim_slot_index\":$it") }
                s.isEmbedded?.let { parts.add("\"is_embedded\":$it") }
                s.dataRoamingEnabled?.let { parts.add("\"data_roaming_enabled\":$it") }
                s.isOpportunistic?.let { parts.add("\"is_opportunistic\":$it") }
                s.simState?.let { parts.add("\"sim_state\":${escapeJson(it)}") }

                s.network?.let { n ->
                    // Also expose key scalar fields at subscription top-level (as requested)
                    n.dataNetworkType?.let { parts.add("\"data_network_type\":$it") }
                    n.voiceNetworkType?.let { parts.add("\"voice_network_type\":$it") }
                    n.carrierId?.let { parts.add("\"carrier_id\":$it") }
                    n.isRoaming?.let { parts.add("\"is_roaming\":$it") }

                    val np = mutableListOf<String>()
                    n.networkOperator?.let { np.add("\"operator\":${escapeJson(it)}") }
                    n.networkOperatorName?.let { np.add("\"operator_name\":${escapeJson(it)}") }
                    n.networkCountryIso?.let { np.add("\"country_iso\":${escapeJson(it)}") }
                    if (np.isNotEmpty()) parts.add("\"network\":{${np.joinToString(",")}}")
                }

                s.sim?.let { si ->
                    // Also expose key scalar fields at subscription top-level (as requested)
                    si.isDataEnabled?.let { parts.add("\"is_data_enabled\":$it") }

                    val sp = mutableListOf<String>()
                    si.simOperator?.let { sp.add("\"operator\":${escapeJson(it)}") }
                    si.simOperatorName?.let { sp.add("\"operator_name\":${escapeJson(it)}") }
                    si.simCountryIso?.let { sp.add("\"country_iso\":${escapeJson(it)}") }
                    if (sp.isNotEmpty()) parts.add("\"sim\":{${sp.joinToString(",")}}")
                }

                if (parts.isEmpty()) null else "{${parts.joinToString(",")}}"
            }
            networkParts.add("\"subscriptions\":[${arr.joinToString(",")}]")
        }

        // Keep default_data as primary (host app may not grant phone permissions, so subscriptions[] might be missing)
        networkInfo?.defaultData?.let { defaultData ->
            val parts = mutableListOf<String>()
            defaultData.subscriptionId?.let { parts.add("\"subscription_id\":$it") }

            defaultData.network?.let { n ->
                // scalar fields at default_data top-level (as requested)
                n.dataNetworkType?.let { parts.add("\"data_network_type\":$it") }
                n.voiceNetworkType?.let { parts.add("\"voice_network_type\":$it") }
                n.carrierId?.let { parts.add("\"carrier_id\":$it") }
                n.isRoaming?.let { parts.add("\"is_roaming\":$it") }

                val np = mutableListOf<String>()
                n.networkOperator?.let { np.add("\"operator\":${escapeJson(it)}") }
                n.networkOperatorName?.let { np.add("\"operator_name\":${escapeJson(it)}") }
                n.networkCountryIso?.let { np.add("\"country_iso\":${escapeJson(it)}") }
                if (np.isNotEmpty()) parts.add("\"network\":{${np.joinToString(",")}}")
            }

            defaultData.sim?.let { si ->
                // scalar fields at default_data top-level (as requested)
                si.isDataEnabled?.let { parts.add("\"is_data_enabled\":$it") }

                val sp = mutableListOf<String>()
                si.simOperator?.let { sp.add("\"operator\":${escapeJson(it)}") }
                si.simOperatorName?.let { sp.add("\"operator_name\":${escapeJson(it)}") }
                si.simCountryIso?.let { sp.add("\"country_iso\":${escapeJson(it)}") }
                if (sp.isNotEmpty()) parts.add("\"sim\":{${sp.joinToString(",")}}")
            }

            if (parts.isNotEmpty()) {
                networkParts.add("\"default_data\":{${parts.joinToString(",")}}")
            }
        }

        // Strict: emit default_call only when platform provides a valid default voice subscription.
        networkInfo?.defaultCall?.let { dc ->
            val parts = mutableListOf<String>()
            dc.subscriptionId?.let { parts.add("\"subscription_id\":$it") }

            dc.network?.let { n ->
                // scalar fields at default_call top-level (as requested)
                n.dataNetworkType?.let { parts.add("\"data_network_type\":$it") }
                n.voiceNetworkType?.let { parts.add("\"voice_network_type\":$it") }
                n.carrierId?.let { parts.add("\"carrier_id\":$it") }
                n.isRoaming?.let { parts.add("\"is_roaming\":$it") }

                val np = mutableListOf<String>()
                n.networkOperator?.let { np.add("\"operator\":${escapeJson(it)}") }
                n.networkOperatorName?.let { np.add("\"operator_name\":${escapeJson(it)}") }
                n.networkCountryIso?.let { np.add("\"country_iso\":${escapeJson(it)}") }
                if (np.isNotEmpty()) parts.add("\"network\":{${np.joinToString(",")}}")
            }

            dc.sim?.let { si ->
                // scalar fields at default_call top-level (as requested)
                si.isDataEnabled?.let { parts.add("\"is_data_enabled\":$it") }

                val sp = mutableListOf<String>()
                si.simOperator?.let { sp.add("\"operator\":${escapeJson(it)}") }
                si.simOperatorName?.let { sp.add("\"operator_name\":${escapeJson(it)}") }
                si.simCountryIso?.let { sp.add("\"country_iso\":${escapeJson(it)}") }
                if (sp.isNotEmpty()) parts.add("\"sim\":{${sp.joinToString(",")}}")
            }

            if (parts.isNotEmpty()) {
                networkParts.add("\"default_call\":{${parts.joinToString(",")}}")
            }
        }
        if (networkParts.isNotEmpty()) {
            deviceParts.add("\"network_info\":{${networkParts.joinToString(",")}}")
        }
        
        // App Information
        val appParts = mutableListOf<String>()
        appInfo?.appVersion?.let { appParts.add("\"version\":${escapeJson(it)}") }
        appInfo?.appVersionCode?.let { appParts.add("\"version_code\":$it") }
        appInfo?.appPackageName?.let { appParts.add("\"package_name\":${escapeJson(it)}") }
        appInfo?.appTargetSdk?.let { appParts.add("\"target_sdk\":$it") }
        appInfo?.appMinSdk?.let { appParts.add("\"min_sdk\":$it") }
        appInfo?.installSource?.let { appParts.add("\"install_source\":${escapeJson(it)}") }
        appInfo?.referrer?.let { appParts.add("\"referrer\":${escapeJson(it)}") }
        appInfo?.appSignatureHash?.let { appParts.add("\"signature_hash\":${escapeJson(it)}") }
        appInfo?.signingCerts?.let { 
            appParts.add("\"signing_certs\":[${it.joinToString(",") { cert -> escapeJson(cert) }}]")
        }
        appInfo?.firstInstallTime?.let { appParts.add("\"first_install_time\":$it") }
        appInfo?.lastUpdateTime?.let { appParts.add("\"last_update_time\":$it") }
        appInfo?.isInstantApp?.let { appParts.add("\"is_instant_app\":$it") }
        if (appParts.isNotEmpty()) {
            deviceParts.add("\"app_info\":{${appParts.joinToString(",")}}")
        }
        
        // SDK Information
        val sdkParts = mutableListOf<String>()
        sdkVersion?.let { sdkParts.add("\"version\":${escapeJson(it)}") }
        sdkName?.let { sdkParts.add("\"name\":${escapeJson(it)}") }
        if (sdkParts.isNotEmpty()) {
            deviceParts.add("\"sdk_info\":{${sdkParts.joinToString(",")}}")
        }
        
        // System Information
        val systemParts = mutableListOf<String>()
        systemInfo?.timezone?.let { systemParts.add("\"timezone\":${escapeJson(it)}") }
        systemInfo?.locale?.let { systemParts.add("\"locale\":${escapeJson(it)}") }
        systemInfo?.isEmulator?.let { systemParts.add("\"is_emulator\":$it") }
        if (systemParts.isNotEmpty()) {
            deviceParts.add("\"system_info\":{${systemParts.joinToString(",")}}")
        }
        
        return deviceParts.joinToString(",")
    }

    fun buildMinimal(
        sdkVersion: String?,
        sdkName: String?,
        osVersion: String?,
        osApiLevel: Int?,
        deviceManufacturer: String?,
        deviceModel: String?,
        deviceBrand: String?,
        deviceProduct: String?
    ): String {
        val deviceParts = mutableListOf<String>()
        val osParts = mutableListOf<String>()
        osParts.add("\"platform\":\"android\"")
        osVersion?.let { osParts.add("\"version\":${escapeJson(it)}") }
        osApiLevel?.let { osParts.add("\"api_level\":$it") }
        if (osParts.isNotEmpty()) deviceParts.add("\"os_info\":{${osParts.joinToString(",")}}")

        val hwParts = mutableListOf<String>()
        deviceManufacturer?.let { hwParts.add("\"manufacturer\":${escapeJson(it)}") }
        deviceModel?.let { hwParts.add("\"model\":${escapeJson(it)}") }
        deviceBrand?.let { hwParts.add("\"brand\":${escapeJson(it)}") }
        deviceProduct?.let { hwParts.add("\"product\":${escapeJson(it)}") }
        if (hwParts.isNotEmpty()) deviceParts.add("\"hardware_info\":{${hwParts.joinToString(",")}}")

        val sdkParts = mutableListOf<String>()
        sdkVersion?.let { sdkParts.add("\"version\":${escapeJson(it)}") }
        sdkName?.let { sdkParts.add("\"name\":${escapeJson(it)}") }
        if (sdkParts.isNotEmpty()) deviceParts.add("\"sdk_info\":{${sdkParts.joinToString(",")}}")

        return deviceParts.joinToString(",")
    }
    
    /**
     * Escape JSON string value. Handles all special characters including control characters.
     */
    fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 10)
        sb.append('"')
        for (i in value.indices) {
            val ch = value[i]
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f") // Form feed
                else -> {
                    // Escape control characters (U+0000 to U+001F)
                    if (ch < ' ') {
                        sb.append("\\u")
                        sb.append(String.format("%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
