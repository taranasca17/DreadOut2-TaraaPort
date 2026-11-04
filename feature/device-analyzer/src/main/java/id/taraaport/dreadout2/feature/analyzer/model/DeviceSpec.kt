package id.taraaport.dreadout2.feature.analyzer.model

data class GpuVulkanInfo(
    val rendererName: String,
    val vendorId: Int,
    val rawDriverVersion: Long,
    val vulkanApiVersion: String,
    val supportsCustomTurnipDriver: Boolean,
    val hasSwitchRequiredExtensions: Boolean,
    val totalVulkanExtensions: Int
)

data class DeviceSpec(
    val socModel: String,
    val cpuHardware: String,
    val cpuCores: Int,
    val maxCpuFreqMhz: Long,
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val androidVersion: String,
    val sdkInt: Int,
    val thermalStatusLabel: String,
    val batteryTempCelsius: Float,
    val gpuInfo: GpuVulkanInfo
)