package com.otplesssdk.utils.deviceinfo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.ProxyInfo
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.otplesssdk.utils.logger.SdkLogger

/**
 * Collects network and telephony-related information.
 *
 * Permissions (best-effort; missing permissions never throw):
 * - Optional (recommended): `android.permission.ACCESS_NETWORK_STATE` (normal)
 * - Optional (recommended for richer telephony/subscription fields): `android.permission.READ_PHONE_STATE`
 *   or `android.permission.READ_BASIC_PHONE_STATE` (Android 13+). If missing, those fields are omitted (null).
 */
internal object NetworkInfoCollector {
    private const val TAG = "NetworkInfoCollector"

    fun collect(
        context: Context,
        connectivityManager: ConnectivityManager?,
        telephonyManager: TelephonyManager?
    ): NetworkInfo {
        val (defaultDataSubId, defaultCallSubId) = getDefaultSubscriptionIds(context)
        SdkLogger.d(TAG, "Default subIds: data=$defaultDataSubId, call=$defaultCallSubId")
        val defaultDataTm = createTelephonyManagerForSubId(telephonyManager, defaultDataSubId)
        val defaultCallTm = createTelephonyManagerForSubId(telephonyManager, defaultCallSubId)

        // Prefer default-data subscription for the primary SIM/operator fields.
        val tmForCurrent = defaultDataTm ?: telephonyManager
        val hasTelephonyPermission = hasTelephonyReadPermission(context)

        return NetworkInfo(
            networkType = getNetworkType(connectivityManager),
            isConnected = getIsConnected(connectivityManager),
            hasInternetCapability = getHasInternetCapability(connectivityManager),
            isValidated = getIsValidated(connectivityManager),
            isMetered = getIsMetered(connectivityManager),
            networkOperator = getNetworkOperator(context, tmForCurrent, hasTelephonyPermission),
            networkOperatorName = getNetworkOperatorName(context, tmForCurrent, hasTelephonyPermission),
            simOperator = getSimOperator(context, tmForCurrent, hasTelephonyPermission),
            simOperatorName = getSimOperatorName(context, tmForCurrent, hasTelephonyPermission),
            simCountryIso = getSimCountryIso(context, tmForCurrent, hasTelephonyPermission),
            phoneType = getPhoneType(tmForCurrent),
            isRoaming = getIsRoaming(context, tmForCurrent, hasTelephonyPermission),
            networkGeneration = getNetworkGeneration(context, tmForCurrent, hasTelephonyPermission),
            isDataEnabled = getIsDataEnabled(context, tmForCurrent, hasTelephonyPermission),
            simSlotCount = getSimSlotCount(context),
            networkSubType = getNetworkSubType(connectivityManager, context),
            isVpnTransportActive = getIsVpnTransportActive(connectivityManager),
            dataNetworkType = getDataNetworkType(context, tmForCurrent, hasTelephonyPermission),
            carrierId = getCarrierId(context, tmForCurrent, hasTelephonyPermission),
            callState = getCallState(context, (defaultCallTm ?: tmForCurrent), hasTelephonyPermission),
            proxyHost = getProxyHost(connectivityManager),
            proxyPort = getProxyPort(connectivityManager),

            hasSim = getHasSim(context, tmForCurrent),
            subscriptions = getAllSubscriptions(context, telephonyManager),
            defaultData = defaultDataSubId?.let {
                DefaultSubscriptionInfo(
                    subscriptionId = it,
                    network = SubscriptionNetworkInfo(
                        networkOperator = getNetworkOperator(context, defaultDataTm, hasTelephonyPermission),
                        networkOperatorName = getNetworkOperatorName(context, defaultDataTm, hasTelephonyPermission),
                        networkCountryIso = getNetworkCountryIso(context, defaultDataTm, hasTelephonyPermission),
                        networkGeneration = getNetworkGeneration(context, defaultDataTm, hasTelephonyPermission),
                        dataNetworkType = getDataNetworkType(context, defaultDataTm, hasTelephonyPermission),
                        voiceNetworkType = getVoiceNetworkType(context, defaultDataTm, hasTelephonyPermission),
                        carrierId = getCarrierId(context, defaultDataTm, hasTelephonyPermission),
                        isRoaming = getIsRoaming(context, defaultDataTm, hasTelephonyPermission)
                    ),
                    sim = SubscriptionSimInfo(
                        simOperator = getSimOperator(context, defaultDataTm, hasTelephonyPermission),
                        simOperatorName = getSimOperatorName(context, defaultDataTm, hasTelephonyPermission),
                        simCountryIso = getSimCountryIso(context, defaultDataTm, hasTelephonyPermission),
                        isDataEnabled = getIsDataEnabled(context, defaultDataTm, hasTelephonyPermission)
                    )
                )
            },
            defaultCall = defaultCallSubId?.let {
                DefaultSubscriptionInfo(
                    subscriptionId = it,
                    network = SubscriptionNetworkInfo(
                        networkOperator = getNetworkOperator(context, defaultCallTm, hasTelephonyPermission),
                        networkOperatorName = getNetworkOperatorName(context, defaultCallTm, hasTelephonyPermission),
                        networkCountryIso = getNetworkCountryIso(context, defaultCallTm, hasTelephonyPermission),
                        networkGeneration = getNetworkGeneration(context, defaultCallTm, hasTelephonyPermission),
                        dataNetworkType = getDataNetworkType(context, defaultCallTm, hasTelephonyPermission),
                        voiceNetworkType = getVoiceNetworkType(context, defaultCallTm, hasTelephonyPermission),
                        carrierId = getCarrierId(context, defaultCallTm, hasTelephonyPermission),
                        isRoaming = getIsRoaming(context, defaultCallTm, hasTelephonyPermission)
                    ),
                    sim = SubscriptionSimInfo(
                        simOperator = getSimOperator(context, defaultCallTm, hasTelephonyPermission),
                        simOperatorName = getSimOperatorName(context, defaultCallTm, hasTelephonyPermission),
                        simCountryIso = getSimCountryIso(context, defaultCallTm, hasTelephonyPermission),
                        isDataEnabled = null
                    )
                )
            }
        )
    }

    private fun getAllSubscriptions(context: Context, telephonyManager: TelephonyManager?): List<NetworkSubscription>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        if (!hasTelephonyReadPermission(context)) return null
        val sm = try {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        } catch (_: Exception) {
            null
        } ?: return null

        val list: List<SubscriptionInfo> = try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyReadPermission(context) above.
            sm.activeSubscriptionInfoList ?: return emptyList()
        } catch (e: SecurityException) {
            SdkLogger.d(TAG, "activeSubscriptionInfoList blocked: ${e.javaClass.name}: ${e.message}")
            return null
        } catch (e: Exception) {
            SdkLogger.d(TAG, "activeSubscriptionInfoList failed: ${e.javaClass.name}: ${e.message}")
            return null
        }

        val hasReadPerm = hasTelephonyReadPermission(context)
        return list.mapNotNull { si ->
            val subId = si.subscriptionId
            val tm = createTelephonyManagerForSubId(telephonyManager, subId)
            val simState = safeInt { tm?.simState }
            NetworkSubscription(
                subscriptionId = subId,
                simSlotIndex = safeInt { si.simSlotIndex },
                isEmbedded = safeBoolean { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) si.isEmbedded else null },
                displayName = safeString { si.displayName?.toString() },
                carrierName = safeString { si.carrierName?.toString() },
                countryIso = safeString { si.countryIso },
                mcc = safeInt { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) si.mcc else null },
                mnc = safeInt { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) si.mnc else null },
                dataRoamingEnabled = safeBoolean { getDataRoamingEnabled(si) },
                isOpportunistic = safeBoolean { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) si.isOpportunistic else null },
                groupUuid = safeString { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) si.groupUuid?.toString() else null },
                simState = simState?.let { simStateToString(it) },
                network = SubscriptionNetworkInfo(
                    networkOperator = getNetworkOperator(context, tm, hasReadPerm),
                    networkOperatorName = getNetworkOperatorName(context, tm, hasReadPerm),
                    networkCountryIso = getNetworkCountryIso(context, tm, hasReadPerm),
                    networkGeneration = getNetworkGeneration(context, tm, hasReadPerm),
                    dataNetworkType = getDataNetworkType(context, tm, hasReadPerm),
                    voiceNetworkType = getVoiceNetworkType(context, tm, hasReadPerm),
                    carrierId = getCarrierId(context, tm, hasReadPerm),
                    isRoaming = getIsRoaming(context, tm, hasReadPerm)
                ),
                sim = SubscriptionSimInfo(
                    simOperator = getSimOperator(context, tm, hasReadPerm),
                    simOperatorName = getSimOperatorName(context, tm, hasReadPerm),
                    simCountryIso = getSimCountryIso(context, tm, hasReadPerm),
                    isDataEnabled = getIsDataEnabled(context, tm, hasReadPerm)
                )
            )
        }
    }

    private fun getDataRoamingEnabled(subscriptionInfo: SubscriptionInfo): Boolean? {
        return try {
            val roaming = subscriptionInfo.dataRoaming
            when (roaming) {
                SubscriptionManager.DATA_ROAMING_ENABLE -> true
                SubscriptionManager.DATA_ROAMING_DISABLE -> false
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun simStateToString(state: Int): String {
        return when (state) {
            TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
            else -> "UNKNOWN"
        }
    }

    private inline fun safeString(block: () -> String?): String? {
        return try {
            block()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private inline fun safeBoolean(block: () -> Boolean?): Boolean? {
        return try {
            block()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private inline fun safeInt(block: () -> Int?): Int? {
        return try {
            block()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getHasSim(context: Context, telephonyManager: TelephonyManager?): Boolean? {
        // Prefer TelephonyManager SIM state (doesn't require listing subscriptions).
        try {
            val state = telephonyManager?.simState
            if (state != null) {
                return when (state) {
                    TelephonyManager.SIM_STATE_READY -> true
                    TelephonyManager.SIM_STATE_ABSENT -> false
                    else -> null
                }
            }
        } catch (_: SecurityException) {
            // fall through
        } catch (_: Exception) {
            // fall through
        }

        // Fallback: try SubscriptionManager active list (may be empty without permission on some OEMs).
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
            if (!hasTelephonyReadPermission(context)) return null
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return null
            @Suppress("MissingPermission") // Guarded by hasTelephonyReadPermission(context) above.
            val list = sm.activeSubscriptionInfoList
            list != null && list.isNotEmpty()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getDefaultSubscriptionIds(context: Context): Pair<Int?, Int?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null to null

        val rawDefaultData = try {
            SubscriptionManager.getDefaultDataSubscriptionId()
        } catch (e: Exception) {
            SdkLogger.d(TAG, "getDefaultDataSubscriptionId failed: ${e.javaClass.name}: ${e.message}")
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        val rawDefaultVoice = try {
            SubscriptionManager.getDefaultVoiceSubscriptionId()
        } catch (e: Exception) {
            SdkLogger.d(TAG, "getDefaultVoiceSubscriptionId failed: ${e.javaClass.name}: ${e.message}")
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        val rawDefaultSms = try {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } catch (e: Exception) {
            SdkLogger.d(TAG, "getDefaultSmsSubscriptionId failed: ${e.javaClass.name}: ${e.message}")
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        val activeSubIds = try {
            if (!hasTelephonyReadPermission(context)) {
                null
            } else {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                @Suppress("MissingPermission") // Guarded by hasTelephonyReadPermission(context) above.
                sm?.activeSubscriptionInfoList
                    ?.mapNotNull { it?.subscriptionId }
                    ?.distinct()
            }
        } catch (e: SecurityException) {
            SdkLogger.d(TAG, "activeSubscriptionInfoList blocked: ${e.javaClass.name}: ${e.message}")
            null
        } catch (e: Exception) {
            SdkLogger.d(TAG, "activeSubscriptionInfoList failed: ${e.javaClass.name}: ${e.message}")
            null
        }

        SdkLogger.d(
            TAG,
            "Default subId raw values: data=$rawDefaultData voice=$rawDefaultVoice sms=$rawDefaultSms invalid=${SubscriptionManager.INVALID_SUBSCRIPTION_ID} activeSubIds=${activeSubIds ?: "n/a"}"
        )

        val defaultData = rawDefaultData.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        val defaultCall = rawDefaultVoice.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        return defaultData to defaultCall
    }

    private fun createTelephonyManagerForSubId(telephonyManager: TelephonyManager?, subId: Int?): TelephonyManager? {
        if (telephonyManager == null) return null
        if (subId == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.createForSubscriptionId(subId).also {
                    SdkLogger.d(TAG, "createForSubscriptionId($subId) -> ${if (it != null) "ok" else "null"}")
                }
            } else {
                telephonyManager
            }
        } catch (e: SecurityException) {
            SdkLogger.d(TAG, "createForSubscriptionId($subId) blocked: ${e.javaClass.name}: ${e.message}")
            null
        } catch (e: Exception) {
            SdkLogger.d(TAG, "createForSubscriptionId($subId) failed: ${e.javaClass.name}: ${e.message}")
            null
        }
    }
    
    private fun getNetworkType(connectivityManager: ConnectivityManager?): String? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return null
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Unknown"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "WiFi"
                    ConnectivityManager.TYPE_MOBILE -> "Cellular"
                    ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                    ConnectivityManager.TYPE_BLUETOOTH -> "Bluetooth"
                    ConnectivityManager.TYPE_VPN -> "VPN"
                    else -> "Unknown"
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getIsConnected(connectivityManager: ConnectivityManager?): Boolean? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.activeNetwork != null
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo?.isConnected
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getHasInternetCapability(connectivityManager: ConnectivityManager?): Boolean? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return null
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getIsValidated(connectivityManager: ConnectivityManager?): Boolean? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return null
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getIsMetered(connectivityManager: ConnectivityManager?): Boolean? {
        if (connectivityManager == null) return null
        return try {
            connectivityManager.isActiveNetworkMetered
        } catch (_: Exception) {
            null
        }
    }
    
    private fun getPhoneType(telephonyManager: TelephonyManager?): String? {
        if (telephonyManager == null) return null
        return try {
            when (telephonyManager.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                TelephonyManager.PHONE_TYPE_NONE -> "None"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getNetworkGeneration(telephonyManager: TelephonyManager?): String? {
        // Deprecated: prefer getNetworkGeneration(context, telephonyManager, hasPermission)
        return null
    }
    
    private fun getNetworkGeneration(context: Context, telephonyManager: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        val type = getDataNetworkType(context, telephonyManager, hasTelephonyPermission) ?: return null
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> null
        }
    }
    
    private fun getIsDataEnabled(context: Context, telephonyManager: TelephonyManager?, hasTelephonyPermission: Boolean): Boolean? {
        if (!hasTelephonyPermission) return null
        if (telephonyManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.isDataEnabled
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: Exception) {
            null
        }
    }
    
    private fun getSimSlotCount(context: Context): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                if (subscriptionManager != null) {
                    try {
                        if (!hasTelephonyReadPermission(context)) return null
                        @Suppress("MissingPermission") // Guarded by hasTelephonyReadPermission(context) above.
                        val count = subscriptionManager.activeSubscriptionInfoCount
                        count.takeIf { it > 0 }
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getNetworkSubType(connectivityManager: ConnectivityManager?, context: Context): String? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return null
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
                
                // Get transport-specific subtype
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    telephonyManager?.let { tm ->
                        val type = getDataNetworkType(context, tm, hasTelephonyReadPermission(context)) ?: return@let null
                        when (type) {
                            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                            TelephonyManager.NETWORK_TYPE_NR -> "NR"
                            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
                            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                            else -> null
                        }
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getIsVpnTransportActive(connectivityManager: ConnectivityManager?): Boolean? {
        if (connectivityManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return null
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getDataNetworkType(context: Context, telephonyManager: TelephonyManager?, hasTelephonyPermission: Boolean): Int? {
        if (!hasTelephonyPermission) return null
        if (telephonyManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.dataNetworkType
            } else {
                @Suppress("DEPRECATION", "MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.networkType
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getVoiceNetworkType(context: Context, telephonyManager: TelephonyManager?, hasTelephonyPermission: Boolean): Int? {
        if (!hasTelephonyPermission) return null
        if (telephonyManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.voiceNetworkType
            } else {
                @Suppress("DEPRECATION", "MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.networkType
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: Exception) {
            null
        }
    }
    
    private fun getCarrierId(context: Context, telephonyManager: TelephonyManager?, hasTelephonyPermission: Boolean): Int? {
        if (!hasTelephonyPermission) return null
        if (telephonyManager == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
                telephonyManager.simCarrierId
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: Exception) {
            null
        }
    }
    
    private fun getProxyHost(connectivityManager: ConnectivityManager?): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                // Use reflection to access defaultProxy (available on API 23+)
                try {
                    val defaultProxyMethod = ConnectivityManager::class.java.getMethod("getDefaultProxy")
                    val proxyInfo = defaultProxyMethod.invoke(connectivityManager) as? ProxyInfo
                    proxyInfo?.host
                } catch (e: Exception) {
                    // Fallback to system property
                    System.getProperty("http.proxyHost")?.takeIf { it.isNotEmpty() }
                }
            } else {
                // Legacy proxy detection
                System.getProperty("http.proxyHost")?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getProxyPort(connectivityManager: ConnectivityManager?): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                // Use reflection to access defaultProxy (available on API 23+)
                try {
                    val defaultProxyMethod = ConnectivityManager::class.java.getMethod("getDefaultProxy")
                    val proxyInfo = defaultProxyMethod.invoke(connectivityManager) as? ProxyInfo
                    proxyInfo?.port?.takeIf { it > 0 }
                } catch (e: Exception) {
                    // Fallback to system property
                    System.getProperty("http.proxyPort")?.toIntOrNull()?.takeIf { it > 0 }
                }
            } else {
                // Legacy proxy detection
                System.getProperty("http.proxyPort")?.toIntOrNull()?.takeIf { it > 0 }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun hasTelephonyReadPermission(context: Context): Boolean {
        // READ_BASIC_PHONE_STATE exists on Android 13+; using it when available allows collecting
        // basic telephony fields without requiring the broader READ_PHONE_STATE grant.
        val hasReadPhoneState =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasReadBasicPhoneState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        return hasReadPhoneState || hasReadBasicPhoneState
    }

    private fun getNetworkOperator(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.networkOperator
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getNetworkOperatorName(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.networkOperatorName
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getNetworkCountryIso(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.networkCountryIso
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getSimOperator(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.simOperator
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getSimOperatorName(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.simOperatorName
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getSimCountryIso(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): String? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.simCountryIso
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getIsRoaming(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): Boolean? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.isNetworkRoaming
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getCallState(context: Context, tm: TelephonyManager?, hasTelephonyPermission: Boolean): Int? {
        if (!hasTelephonyPermission) return null
        return try {
            @Suppress("MissingPermission") // Guarded by hasTelephonyPermission and try/catch.
            tm?.callState
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}

internal data class NetworkInfo(
    val networkType: String? = null,
    val isConnected: Boolean? = null,
    val hasInternetCapability: Boolean? = null,
    val isValidated: Boolean? = null,
    val isMetered: Boolean? = null,
    val networkOperator: String? = null,
    val networkOperatorName: String? = null,
    val simOperator: String? = null,
    val simOperatorName: String? = null,
    val simCountryIso: String? = null,
    val phoneType: String? = null,
    val isRoaming: Boolean? = null,
    val networkGeneration: String? = null,
    val isDataEnabled: Boolean? = null,
    val simSlotCount: Int? = null,
    val networkSubType: String? = null,
    val isVpnTransportActive: Boolean? = null,
    val dataNetworkType: Int? = null,
    val carrierId: Int? = null,
    val callState: Int? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,

    // SIM presence + defaults (dual-SIM support)
    val hasSim: Boolean? = null,
    val subscriptions: List<NetworkSubscription>? = null,
    val defaultData: DefaultSubscriptionInfo? = null,
    val defaultCall: DefaultSubscriptionInfo? = null
)

internal data class NetworkSubscription(
    val subscriptionId: Int? = null,
    val simSlotIndex: Int? = null,
    val isEmbedded: Boolean? = null, // eSIM if true (when available)
    val displayName: String? = null,
    val carrierName: String? = null,
    val countryIso: String? = null,
    val mcc: Int? = null,
    val mnc: Int? = null,
    val dataRoamingEnabled: Boolean? = null,
    val isOpportunistic: Boolean? = null,
    val groupUuid: String? = null,
    val simState: String? = null,
    val network: SubscriptionNetworkInfo? = null,
    val sim: SubscriptionSimInfo? = null
)

internal data class DefaultSubscriptionInfo(
    val subscriptionId: Int? = null,
    val network: SubscriptionNetworkInfo? = null,
    val sim: SubscriptionSimInfo? = null
)

internal data class SubscriptionNetworkInfo(
    val networkOperator: String? = null,
    val networkOperatorName: String? = null,
    val networkCountryIso: String? = null,
    val networkGeneration: String? = null,
    val dataNetworkType: Int? = null,
    val voiceNetworkType: Int? = null,
    val carrierId: Int? = null,
    val isRoaming: Boolean? = null
)

internal data class SubscriptionSimInfo(
    val simOperator: String? = null,
    val simOperatorName: String? = null,
    val simCountryIso: String? = null,
    val isDataEnabled: Boolean? = null
)

