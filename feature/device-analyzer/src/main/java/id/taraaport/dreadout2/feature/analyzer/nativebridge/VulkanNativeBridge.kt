package id.taraaport.dreadout2.feature.analyzer.nativebridge

import android.view.Surface
import id.taraaport.dreadout2.feature.analyzer.model.GpuVulkanInfo

object VulkanNativeBridge {
    init { System.loadLibrary("taraaport_analyzer") }
    private external fun queryVulkanGpuInfoNative(): String
    private external fun verifyCustomDriverLibraryNative(soFilePath: String): String
    private external fun initializeEmulationBootNative(romFd: Int, driverPathStr: String, keysPathStr: String): String
    private external fun startSurfaceRenderLoopNative(surface: Surface, targetFps: Int)
    private external fun stopSurfaceRenderLoopNative()
    private external fun updateNativeRenderState(
        yaw: Float,
        pitch: Float,
        moveX: Float,
        moveY: Float,
        irisCam: Boolean,
        flashlight: Boolean,
        triggerBlitz: Boolean
    )
    private external fun getMeasuredFpsNative(): Int

    fun getGpuVulkanInfo(): GpuVulkanInfo {
        val raw = queryVulkanGpuInfoNative()
        val parts = raw.split("|")
        if (parts.size < 7) {
            return GpuVulkanInfo("Unknown", 0, 0L, "0.0.0", true, false, 0)
        }
        return GpuVulkanInfo(
            rendererName = parts[0],
            vendorId = parts[1].toIntOrNull() ?: 0,
            rawDriverVersion = parts[2].toLongOrNull() ?: 0L,
            vulkanApiVersion = parts[3],
            supportsCustomTurnipDriver = parts[4].toBooleanStrictOrNull() ?: true,
            hasSwitchRequiredExtensions = parts[5].toBooleanStrictOrNull() ?: false,
            totalVulkanExtensions = parts[6].toIntOrNull() ?: 0
        )
    }

    fun verifyDriverSoFile(soFilePath: String): Pair<Boolean, String> {
        val res = verifyCustomDriverLibraryNative(soFilePath)
        return Pair(res.startsWith("OK|"), res.substringAfter("|", "Tidak diketahui"))
    }

    fun startNativeBootSequence(romFd: Int, driverPath: String, keysPath: String): String {
        return initializeEmulationBootNative(romFd, driverPath, keysPath)
    }

    fun startRenderLoop(surface: Surface, targetFps: Int) {
        startSurfaceRenderLoopNative(surface, targetFps)
    }

    fun stopRenderLoop() {
        stopSurfaceRenderLoopNative()
    }

    fun syncInputToNativeRenderer(
        yaw: Float,
        pitch: Float,
        moveX: Float,
        moveY: Float,
        irisCam: Boolean,
        flashlight: Boolean,
        triggerBlitz: Boolean = false
    ) {
        updateNativeRenderState(yaw, pitch, moveX, moveY, irisCam, flashlight, triggerBlitz)
    }

    fun getMeasuredFps(): Int = getMeasuredFpsNative()
}