package com.otplesssdk.utils.deviceinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

internal object HardwareInfoCollector {
    fun collect(context: Context): HardwareInfo {
        return HardwareInfo(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceBrand = Build.BRAND,
            deviceProduct = Build.PRODUCT,

            totalMemory = getTotalMemory(),
            totalRAM = getTotalRAM(context),
            storageAvailable = getStorageAvailable(),
            storageTotal = getStorageTotal()
        )
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
            stat.availableBytes
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getStorageTotal(): Long? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes
        } catch (e: Exception) {
            null
        }
    }
}

internal data class HardwareInfo(
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceBrand: String? = null,
    val deviceProduct: String? = null,

    val totalMemory: Long? = null,
    val totalRAM: Long? = null,
    val storageAvailable: Long? = null,
    val storageTotal: Long? = null
)
