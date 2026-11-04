package id.taraaport.dreadout2.feature.analyzer.model

data class GpuDriverPackage(
    val id: String,
    val name: String,
    val author: String,
    val driverVersion: String,
    val libraryName: String,
    val minApi: Int,
    val description: String,
    val extractedSoPath: String,
    val isNativeVerified: Boolean
)

data class DriverManagerState(
    val isCustomDriverSupported: Boolean,
    val activeDriverId: String?, // null = Menggunakan Driver Bawaan Sistem (Default)
    val installedDrivers: List<GpuDriverPackage>,
    val statusMessage: String? = null,
    val isErrorStatus: Boolean = false
)