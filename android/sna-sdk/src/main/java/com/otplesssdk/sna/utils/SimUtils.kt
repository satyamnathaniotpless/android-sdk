package com.otplesssdk.sna.utils

import android.content.Context
import android.telephony.TelephonyManager
import com.otplesssdk.sna.models.SimNetworkInfo
import com.otplesssdk.utils.logger.SdkLogger

/**
 * Utility class for accessing SIM and network information
 */
internal object SimUtils {
    private const val TAG = "SNASdk:SimUtils"

    private fun readOperatorString(tm: TelephonyManager?): String? {
        if (tm == null) return null

        val networkOperator = try {
            tm.networkOperator
        } catch (_: SecurityException) {
            null
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error reading networkOperator", e)
            null
        }

        if (!networkOperator.isNullOrBlank()) return networkOperator

        return try {
            tm.simOperator
        } catch (_: SecurityException) {
            null
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error reading simOperator", e)
            null
        }
    }

    private fun parseMccMnc(operator: String?): Pair<String, String>? {
        if (operator == null) return null
        val trimmed = operator.trim()
        if (trimmed.length !in 5..6) return null
        if (!trimmed.all { it.isDigit() }) return null

        val mcc = trimmed.substring(0, 3)
        val mnc = trimmed.substring(3)
        return Pair(mcc, mnc)
    }

    private fun readNetworkName(tm: TelephonyManager?): String? {
        if (tm == null) return null

        val networkName = try {
            tm.networkOperatorName
        } catch (_: SecurityException) {
            null
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error reading networkOperatorName", e)
            null
        }

        if (!networkName.isNullOrBlank()) return networkName

        val simName = try {
            tm.simOperatorName
        } catch (_: SecurityException) {
            null
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Error reading simOperatorName", e)
            null
        }

        return simName?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Get SIM and network information.
     *
     * Returns MCC/MNC for the default data SIM when available. MCC/MNC may be null
     * if there is no SIM, no registered network, or access is restricted on the
     * current Android version/device.
     *
     * @param context Android context
     * @return SimNetworkInfo containing MCC, MNC, and mobile data status
     */
    fun getSimNetworkInfo(context: Context): SimNetworkInfo {
        val tmForSim: TelephonyManager? = TelephonyHelper.getDefaultDataTelephonyManager(context)

        if (tmForSim == null) {
            SdkLogger.w(TAG, "TelephonyManager is not available")
        }
        
        // Extract MCC and MNC - Prioritize networkOperator (current network) over simOperator (SIM home network)
        // networkOperator reflects the actual network being used for mobile data
        // On Android 10+ (API 29+), we can read SIM operator without READ_PHONE_STATE permission
        // On Android 9 and below, READ_PHONE_STATE permission is required for networkOperator
        // For dual SIM: Returns MCC/MNC from the default data SIM (the one used for mobile data)
        val (mcc, mnc) = try {
            parseMccMnc(readOperatorString(tmForSim)) ?: Pair(null, null)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Error extracting MCC/MNC", e)
            Pair(null, null)
        }

        val networkName = readNetworkName(tmForSim)
        
        val isMobileDataEnabled = NetworkUtils.isMobileDataAvailable(context)
        
        return SimNetworkInfo(
            mcc = mcc,
            mnc = mnc,
            networkName = networkName,
            isMobileDataEnabled = isMobileDataEnabled
        )
    }
}
