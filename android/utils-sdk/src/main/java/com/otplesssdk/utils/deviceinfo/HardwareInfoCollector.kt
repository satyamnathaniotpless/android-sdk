package com.otplesssdk.utils.deviceinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings

/**
 * Collects hardware-related information (device identity, RAM, storage).
 */
internal object HardwareInfoCollector {
    fun collect(context: Context): HardwareInfo {
        return HardwareInfo(
            // Device Hardware
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceBrand = Build.BRAND,
            deviceProduct = Build.PRODUCT,
            deviceId = getDeviceId(context),
            
            // Memory & Storage
            totalMemory = getTotalMemory(),
            totalRAM = getTotalRAM(context),
            storageAvailable = getStorageAvailable(),
            storageTotal = getStorageTotal()
        )
    }
    
    internal fun getDeviceId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getTotalMemory(): Long? {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.maxMemory()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getTotalRAM(context: Context): Long? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getStorageAvailable(): Long? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getStorageTotal(): Long? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.totalBytes
            } else {
                @Suppress("DEPRECATION")
                stat.blockCount.toLong() * stat.blockSize.toLong()
            }
        } catch (e: Exception) {
            null
        }
    }
}

internal data class HardwareInfo(
    // Device Hardware
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceBrand: String? = null,
    val deviceProduct: String? = null,
    val deviceId: String? = null,
    
    // Memory & Storage
    val totalMemory: Long? = null,
    val totalRAM: Long? = null,
    val storageAvailable: Long? = null,
    val storageTotal: Long? = null
)
