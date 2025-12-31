package com.otplesssdk.sna.utils

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * Shared helper to obtain the TelephonyManager for the default data SIM.
 */
internal object TelephonyHelper {

    /**
     * Returns TelephonyManager scoped to the default data subscription when possible.
     * Falls back to the default TelephonyManager.
     */
    fun getDefaultDataTelephonyManager(context: Context): TelephonyManager? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val defaultDataSubId = try {
                SubscriptionManager.getDefaultDataSubscriptionId()
            } catch (_: Exception) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }

            if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                try {
                    telephonyManager.createForSubscriptionId(defaultDataSubId)
                } catch (_: Exception) {
                    telephonyManager
                }
            } else {
                telephonyManager
            }
        } else {
            telephonyManager
        }
    }
}

