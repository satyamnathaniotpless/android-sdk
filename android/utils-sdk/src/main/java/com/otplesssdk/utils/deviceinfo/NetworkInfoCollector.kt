package com.otplesssdk.utils.deviceinfo

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

internal object NetworkInfoCollector {
    /**
     * Network + telephony snapshot.
     *
     * Why this is defensive:
     * - Android network APIs differ by SDK level (NetworkCapabilities vs deprecated activeNetworkInfo).
     * - Telephony fields can be unavailable or throw on some OEMs; this must never crash an SDK.
     */
    fun collect(
        connectivityManager: ConnectivityManager?,
        telephonyManager: TelephonyManager?
    ): NetworkInfo {
        val defaultDataSubId = getDefaultDataSubscriptionIdOrNull()
        val defaultCallSubId = getDefaultCallSubscriptionIdOrNull()
        val simOperator = getSimOperatorMccMnc(telephonyManager)
        val networkOperator = getNetworkOperatorMccMnc(telephonyManager)
        val cellularEnabled = getIsCellularDataEnabled(telephonyManager)

        return NetworkInfo(
            networkType = getNetworkType(connectivityManager),
            isConnected = getIsConnected(connectivityManager),
            hasInternetCapability = getHasInternetCapability(connectivityManager),
            isValidated = getIsValidated(connectivityManager),
            isVpnTransportActive = getIsVpnTransportActive(connectivityManager),
            hasSim = getHasSim(telephonyManager),
            phoneType = getPhoneType(telephonyManager),
            isCellularDataEnabled = cellularEnabled,
            defaultData = defaultDataSubId?.let {
                DefaultSubscriptionInfo(
                    subscriptionId = it,
                    simMccMnc = simOperator,
                    networkMccMnc = networkOperator
                )
            },
            defaultCall = defaultCallSubId?.let {
                DefaultSubscriptionInfo(
                    subscriptionId = it,
                    simMccMnc = simOperator,
                    networkMccMnc = networkOperator
                )
            }
        )
    }

    private fun getDefaultDataSubscriptionIdOrNull(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        return try {
            SubscriptionManager.getDefaultDataSubscriptionId()
                .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (_: Exception) {
            null
        }
    }

    private fun getDefaultCallSubscriptionIdOrNull(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        return try {
            SubscriptionManager.getDefaultVoiceSubscriptionId()
                .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (_: Exception) {
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
                @Suppress("DEPRECATION")
                when (networkInfo?.type) {
                    @Suppress("DEPRECATION")
                    ConnectivityManager.TYPE_WIFI -> "WiFi"
                    @Suppress("DEPRECATION")
                    ConnectivityManager.TYPE_MOBILE -> "Cellular"
                    @Suppress("DEPRECATION")
                    ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                    @Suppress("DEPRECATION")
                    ConnectivityManager.TYPE_BLUETOOTH -> "Bluetooth"
                    @Suppress("DEPRECATION")
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

    private fun getHasSim(telephonyManager: TelephonyManager?): Boolean? {
        return try {
            val state = telephonyManager?.simState ?: return null
            when (state) {
                TelephonyManager.SIM_STATE_READY -> true
                TelephonyManager.SIM_STATE_ABSENT -> false
                else -> null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getSimOperatorMccMnc(telephonyManager: TelephonyManager?): String? {
        return try {
            telephonyManager?.simOperator?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getNetworkOperatorMccMnc(telephonyManager: TelephonyManager?): String? {
        return try {
            telephonyManager?.networkOperator?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getIsCellularDataEnabled(telephonyManager: TelephonyManager?): Boolean? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager?.isDataEnabled
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}

data class NetworkInfo(
    val networkType: String? = null,
    val isConnected: Boolean? = null,
    val hasInternetCapability: Boolean? = null,
    val isValidated: Boolean? = null,
    val isVpnTransportActive: Boolean? = null,
    val hasSim: Boolean? = null,
    val phoneType: String? = null,
    val isCellularDataEnabled: Boolean? = null,
    val defaultData: DefaultSubscriptionInfo? = null,
    val defaultCall: DefaultSubscriptionInfo? = null,
)

data class DefaultSubscriptionInfo(
    val subscriptionId: Int? = null,
    val simMccMnc: String? = null,
    val networkMccMnc: String? = null,
)
