package id.taraaport.dreadout2.feature.analyzer.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import id.taraaport.dreadout2.feature.analyzer.nativebridge.VulkanNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemHardwareCollector(private val context: Context) {

    suspend fun collectFullSpecs(): DeviceSpec = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorageGb = (statFs.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        val freeStorageGb = (statFs.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            Build.BOARD
        }

        DeviceSpec(
            socModel = socModel.ifBlank { Build.HARDWARE },
            cpuHardware = readCpuHardwareName(),
            cpuCores = cpuCores,
            maxCpuFreqMhz = readMaxCpuFrequencyMhz(cpuCores),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            freeStorageGb = freeStorageGb,
            totalStorageGb = totalStorageGb,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            thermalStatusLabel = readCurrentThermalStatus(),
            batteryTempCelsius = readBatteryTemperatureCelsius(),
            gpuInfo = VulkanNativeBridge.getGpuVulkanInfo()
        )
    }

    private fun readCpuHardwareName(): String {
        return try {
            val lines = File("/proc/cpuinfo").readLines()
            val hardwareLine = lines.firstOrNull {
                it.startsWith("Hardware", ignoreCase = true) ||
                it.startsWith("model name", ignoreCase = true)
            }
            hardwareLine?.substringAfter(":")?.trim() ?: Build.HARDWARE
        } catch (e: Exception) {
            Build.HARDWARE
        }
    }

    private fun readMaxCpuFrequencyMhz(coreCount: Int): Long {
        var maxKhz = 0L
        for (i in 0 until coreCount) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (freqFile.exists() && freqFile.canRead()) {
                val khz = freqFile.readText().trim().toLongOrNull() ?: 0L
                if (khz > maxKhz) maxKhz = khz
            }
        }
        return if (maxKhz > 0) maxKhz / 1000 else 0L
    }

    fun readCurrentThermalStatus(): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal (None)"
            PowerManager.THERMAL_STATUS_LIGHT -> "Hangat (Light Throttling)"
            PowerManager.THERMAL_STATUS_MODERATE -> "Sedang (Moderate Throttling)"
            PowerManager.THERMAL_STATUS_SEVERE -> "Panas (Severe Throttling)"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Kritis (Critical)"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Darurat (Emergency)"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Tidak Diketahui"
        }
    }

    private fun readBatteryTemperatureCelsius(): Float {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return tempTenths / 10.0f
    }
}