package id.taraaport.dreadout2.feature.analyzer.nativebridge

import id.taraaport.dreadout2.feature.analyzer.model.GpuVulkanInfo

object VulkanNativeBridge {
    init {
        System.loadLibrary("taraaport_analyzer")
    }

    private external fun queryVulkanGpuInfoNative(): String
    private external fun verifyCustomDriverLibraryNative(soFilePath: String): String

    fun getGpuVulkanInfo(): GpuVulkanInfo {
        val raw = queryVulkanGpuInfoNative()
        val parts = raw.split("|")
        if (parts.size < 7) {
            return GpuVulkanInfo(
                rendererName = "Unknown / Error",
                vendorId = 0,
                rawDriverVersion = 0L,
                vulkanApiVersion = "0.0.0",
                supportsCustomTurnipDriver = false,
                hasSwitchRequiredExtensions = false,
                totalVulkanExtensions = 0
            )
        }
        return GpuVulkanInfo(
            rendererName = parts[0],
            vendorId = parts[1].toIntOrNull() ?: 0,
            rawDriverVersion = parts[2].toLongOrNull() ?: 0L,
            vulkanApiVersion = parts[3],
            supportsCustomTurnipDriver = parts[4].toBooleanStrictOrNull() ?: false,
            hasSwitchRequiredExtensions = parts[5].toBooleanStrictOrNull() ?: false,
            totalVulkanExtensions = parts[6].toIntOrNull() ?: 0
        )
    }

    fun verifyDriverSoFile(soFilePath: String): Pair<Boolean, String> {
        val res = verifyCustomDriverLibraryNative(soFilePath)
        val isOk = res.startsWith("OK|")
        val msg = res.substringAfter("|", "Tidak diketahui")
        return Pair(isOk, msg)
    }
}