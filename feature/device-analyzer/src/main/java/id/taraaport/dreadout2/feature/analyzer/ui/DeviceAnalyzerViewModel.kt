package id.taraaport.dreadout2.feature.analyzer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.taraaport.dreadout2.feature.analyzer.data.GpuDriverRepository
import id.taraaport.dreadout2.feature.analyzer.data.GraphicsConfigRepository
import id.taraaport.dreadout2.feature.analyzer.data.SystemHardwareCollector
import id.taraaport.dreadout2.feature.analyzer.domain.AutoGraphicsEngine
import id.taraaport.dreadout2.feature.analyzer.domain.CompatibilityAnalyzer
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import id.taraaport.dreadout2.feature.analyzer.model.DriverManagerState
import id.taraaport.dreadout2.feature.analyzer.model.GpuDriverPackage
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalyzerUiState {
    data object Loading : AnalyzerUiState
    data class Success(
        val spec: DeviceSpec,
        val compatibility: CompatibilityReport,
        val recommendedConfig: GraphicsConfig,
        val activeConfig: GraphicsConfig,
        val configIniPath: String,
        val driverState: DriverManagerState
    ) : AnalyzerUiState
    data class Error(val message: String) : AnalyzerUiState
}

class DeviceAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = SystemHardwareCollector(application.applicationContext)
    private val graphicsRepo = GraphicsConfigRepository(application.applicationContext)
    private val driverRepo = GpuDriverRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<AnalyzerUiState>(AnalyzerUiState.Loading)
    val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

    init {
        refreshHardwareSpecs()
    }

    fun refreshHardwareSpecs() {
        viewModelScope.launch {
            _uiState.value = AnalyzerUiState.Loading
            try {
                val specs = collector.collectFullSpecs()
                val report = CompatibilityAnalyzer.evaluate(specs)
                val recommended = AutoGraphicsEngine.generateRecommendedConfig(specs, report)
                val active = graphicsRepo.loadConfigOrDefault(recommended)

                val installedDrivers = driverRepo.getInstalledDrivers()
                val activeDriverId = driverRepo.getActiveDriverId()

                _uiState.value = AnalyzerUiState.Success(
                    spec = specs,
                    compatibility = report,
                    recommendedConfig = recommended,
                    activeConfig = active,
                    configIniPath = graphicsRepo.getIniFilePath(),
                    driverState = DriverManagerState(
                        isCustomDriverSupported = specs.gpuInfo.supportsCustomTurnipDriver,
                        activeDriverId = activeDriverId,
                        installedDrivers = installedDrivers
                    )
                )
            } catch (e: Exception) {
                _uiState.value = AnalyzerUiState.Error(
                    e.localizedMessage ?: "Gagal memindai perangkat keras"
                )
            }
        }
    }

    fun applyAutoGraphics() {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        graphicsRepo.saveConfig(current.recommendedConfig)
        _uiState.value = current.copy(activeConfig = current.recommendedConfig)
    }

    fun updateCustomGraphics(updated: GraphicsConfig) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        val customConfig = updated.copy(isAutoRecommended = false)
        graphicsRepo.saveConfig(customConfig)
        _uiState.value = current.copy(activeConfig = customConfig)
    }

    fun importGpuDriverFromUri(uri: Uri) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        if (!current.driverState.isCustomDriverSupported) {
            updateDriverMessage("Perangkat ini menggunakan GPU Non-Adreno dan tidak mendukung custom driver.", true)
            return
        }

        viewModelScope.launch {
            updateDriverMessage("Memeriksa struktur ZIP & memvalidasi simbol Vulkan ARM64...", false)
            val result = driverRepo.importDriverZip(uri)
            result.fold(
                onSuccess = { newPkg ->
                    driverRepo.setActiveDriver(newPkg)
                    val refreshedList = driverRepo.getInstalledDrivers()
                    val latest = _uiState.value as? AnalyzerUiState.Success ?: return@fold
                    _uiState.value = latest.copy(
                        driverState = latest.driverState.copy(
                            activeDriverId = newPkg.id,
                            installedDrivers = refreshedList,
                            statusMessage = "Driver '${newPkg.name}' (${newPkg.driverVersion}) berhasil diverifikasi & diaktifkan!",
                            isErrorStatus = false
                        )
                    )
                },
                onFailure = { err ->
                    updateDriverMessage(err.localizedMessage ?: "Gagal mengimpor paket driver.", true)
                }
            )
        }
    }

    fun selectGpuDriver(driver: GpuDriverPackage?) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        driverRepo.setActiveDriver(driver)
        val msg = if (driver == null) {
            "Dikembalikan ke Driver Bawaan Sistem (Default OEM)."
        } else {
            "Driver aktif diubah ke '${driver.name}' (${driver.driverVersion})."
        }
        _uiState.value = current.copy(
            driverState = current.driverState.copy(
                activeDriverId = driver?.id,
                statusMessage = msg,
                isErrorStatus = false
            )
        )
    }

    fun deleteGpuDriver(driverId: String) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        driverRepo.deleteDriver(driverId)
        val refreshedList = driverRepo.getInstalledDrivers()
        val activeId = driverRepo.getActiveDriverId()
        _uiState.value = current.copy(
            driverState = current.driverState.copy(
                activeDriverId = activeId,
                installedDrivers = refreshedList,
                statusMessage = "Paket driver berhasil dihapus.",
                isErrorStatus = false
            )
        )
    }

    private fun updateDriverMessage(msg: String, isError: Boolean) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        _uiState.value = current.copy(
            driverState = current.driverState.copy(
                statusMessage = msg,
                isErrorStatus = isError
            )
        )
    }
}