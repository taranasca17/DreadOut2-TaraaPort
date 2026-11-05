package id.taraaport.dreadout2.feature.analyzer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.taraaport.dreadout2.feature.analyzer.data.GameRomRepository
import id.taraaport.dreadout2.feature.analyzer.data.GpuDriverRepository
import id.taraaport.dreadout2.feature.analyzer.data.GraphicsConfigRepository
import id.taraaport.dreadout2.feature.analyzer.data.SaveAndKeysRepository
import id.taraaport.dreadout2.feature.analyzer.data.SystemHardwareCollector
import id.taraaport.dreadout2.feature.analyzer.domain.AutoGraphicsEngine
import id.taraaport.dreadout2.feature.analyzer.domain.CompatibilityAnalyzer
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityTier
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import id.taraaport.dreadout2.feature.analyzer.model.DriverManagerState
import id.taraaport.dreadout2.feature.analyzer.model.GpuDriverPackage
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import id.taraaport.dreadout2.feature.analyzer.model.LaunchReadinessState
import id.taraaport.dreadout2.feature.analyzer.model.SaveManagerUiState
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
        val driverState: DriverManagerState,
        val saveState: SaveManagerUiState,
        val launchState: LaunchReadinessState
    ) : AnalyzerUiState
    data class Error(val message: String) : AnalyzerUiState
}

class DeviceAnalyzerViewModel(application: Application) : AndroidViewModel(application) {
    private val collector = SystemHardwareCollector(application.applicationContext)
    private val graphicsRepo = GraphicsConfigRepository(application.applicationContext)
    private val driverRepo = GpuDriverRepository(application.applicationContext)
    private val saveRepo = SaveAndKeysRepository(application.applicationContext)
    private val romRepo = GameRomRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<AnalyzerUiState>(AnalyzerUiState.Loading)
    val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

    init { refreshHardwareSpecs() }

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
                val activeDriverObj = installedDrivers.find { it.id == activeDriverId }
                val keysInfo = saveRepo.getProdKeysInfo()
                val romInfo = romRepo.getSavedRomInfo()

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
                    ),
                    saveState = SaveManagerUiState(
                        saveInfo = saveRepo.getSaveDataInfo(),
                        keysInfo = keysInfo
                    ),
                    launchState = LaunchReadinessState(
                        romInfo = romInfo,
                        isHardwareSupported = report.tier != CompatibilityTier.UNSUPPORTED,
                        isKeysReady = keysInfo.isInstalled,
                        isRomReady = romInfo.isLoaded,
                        activeDriverLabel = activeDriverObj?.let { "${it.name} (${it.driverVersion})" } ?: "Driver Bawaan Sistem (OEM)"
                    )
                )
            } catch (e: Exception) {
                _uiState.value = AnalyzerUiState.Error(e.localizedMessage ?: "Gagal memindai perangkat keras")
            }
        }
    }

    fun selectGameRomUri(uri: Uri) {
        viewModelScope.launch {
            val result = romRepo.inspectAndRegisterRom(uri)
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            result.fold(
                onSuccess = { info ->
                    _uiState.value = current.copy(
                        launchState = current.launchState.copy(
                            romInfo = info,
                            isRomReady = info.isLoaded,
                            statusMessage = "Header ${info.format.name} terverifikasi! Title ID: ${info.detectedTitleId}",
                            isErrorStatus = false
                        )
                    )
                },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        launchState = current.launchState.copy(
                            statusMessage = err.localizedMessage ?: "File ROM tidak valid.",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    fun generateSampleNspFile(targetUri: Uri) {
        viewModelScope.launch {
            val res = romRepo.createSampleDreadOutNsp(targetUri)
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            res.fold(
                onSuccess = { selectGameRomUri(targetUri) },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        launchState = current.launchState.copy(
                            statusMessage = err.localizedMessage ?: "Gagal membuat sampel NSP.",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    fun triggerBootValidation(): String? {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return null
        val activeDriver = current.driverState.installedDrivers.find { it.id == current.driverState.activeDriverId }
        val manifestPath = romRepo.generateBootManifest(
            romInfo = current.launchState.romInfo,
            graphicsConfig = current.activeConfig,
            activeDriverSoPath = activeDriver?.extractedSoPath,
            prodKeysPath = current.saveState.keysInfo.keyFilePath,
            configIniPath = current.configIniPath
        )
        _uiState.value = current.copy(
            launchState = current.launchState.copy(
                bootManifestPath = manifestPath,
                statusMessage = "Meluncurkan Layar Emulasi DreadOut 2...",
                isErrorStatus = false
            )
        )
        return manifestPath
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
        viewModelScope.launch {
            updateDriverMessage("Mengekstrak ZIP & menginspeksi header ELF64 ARM64...", false)
            val result = driverRepo.importDriverZip(uri)
            result.fold(
                onSuccess = { newPkg ->
                    driverRepo.setActiveDriver(newPkg)
                    val latest = _uiState.value as? AnalyzerUiState.Success ?: return@fold
                    _uiState.value = latest.copy(
                        driverState = latest.driverState.copy(
                            activeDriverId = newPkg.id,
                            installedDrivers = driverRepo.getInstalledDrivers(),
                            statusMessage = "Driver '${newPkg.name}' (${newPkg.driverVersion}) aktif!",
                            isErrorStatus = false
                        ),
                        launchState = latest.launchState.copy(
                            activeDriverLabel = "${newPkg.name} (${newPkg.driverVersion})"
                        )
                    )
                },
                onFailure = { err -> updateDriverMessage(err.localizedMessage ?: "Gagal impor driver.", true) }
            )
        }
    }

    fun selectGpuDriver(driver: GpuDriverPackage?) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        driverRepo.setActiveDriver(driver)
        _uiState.value = current.copy(
            driverState = current.driverState.copy(
                activeDriverId = driver?.id,
                statusMessage = if (driver == null) "Dikembalikan ke Driver Bawaan Sistem." else "Driver diubah ke '${driver.name}'.",
                isErrorStatus = false
            ),
            launchState = current.launchState.copy(
                activeDriverLabel = driver?.let { "${it.name} (${it.driverVersion})" } ?: "Driver Bawaan Sistem (OEM)"
            )
        )
    }

    fun deleteGpuDriver(driverId: String) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        driverRepo.deleteDriver(driverId)
        val installed = driverRepo.getInstalledDrivers()
        val activeId = driverRepo.getActiveDriverId()
        val activeObj = installed.find { it.id == activeId }
        _uiState.value = current.copy(
            driverState = current.driverState.copy(
                activeDriverId = activeId,
                installedDrivers = installed,
                statusMessage = "Paket driver dihapus.",
                isErrorStatus = false
            ),
            launchState = current.launchState.copy(
                activeDriverLabel = activeObj?.let { "${it.name} (${it.driverVersion})" } ?: "Driver Bawaan Sistem (OEM)"
            )
        )
    }

    fun importProdKeysFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = saveRepo.importProdKeys(uri)
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            result.fold(
                onSuccess = { info ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            keysInfo = info,
                            statusMessage = "Berhasil memvalidasi & memasang prod.keys (${info.validKeyCount} kunci terdeteksi)!",
                            isErrorStatus = false
                        ),
                        launchState = current.launchState.copy(isKeysReady = info.isInstalled)
                    )
                },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            statusMessage = err.localizedMessage ?: "Gagal mengimpor prod.keys",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    fun createSampleSaveData() {
        viewModelScope.launch {
            val result = saveRepo.generateDummySaveSlot()
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            result.fold(
                onSuccess = { info ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            saveInfo = info,
                            statusMessage = "Slot Save Data awal DreadOut 2 berhasil diinisialisasi!",
                            isErrorStatus = false
                        )
                    )
                },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            statusMessage = err.localizedMessage ?: "Gagal membuat Save Data",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    fun exportSaveDataZip(targetUri: Uri) {
        viewModelScope.launch {
            val result = saveRepo.exportSaveDataToZip(targetUri)
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            result.fold(
                onSuccess = { count ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            statusMessage = "Berhasil mem-backup & mengekspor $count berkas Save Data ke ZIP!",
                            isErrorStatus = false
                        )
                    )
                },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            statusMessage = err.localizedMessage ?: "Gagal mengekspor Save Data",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    fun importSaveDataZip(sourceUri: Uri) {
        viewModelScope.launch {
            val result = saveRepo.importSaveDataFromZip(sourceUri)
            val current = _uiState.value as? AnalyzerUiState.Success ?: return@launch
            result.fold(
                onSuccess = { info ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            saveInfo = info,
                            statusMessage = "Save Data berhasil di-restore (${info.totalFilesCount} berkas dipulihkan)!",
                            isErrorStatus = false
                        )
                    )
                },
                onFailure = { err ->
                    _uiState.value = current.copy(
                        saveState = current.saveState.copy(
                            statusMessage = err.localizedMessage ?: "Gagal mengimpor Save Data",
                            isErrorStatus = true
                        )
                    )
                }
            )
        }
    }

    private fun updateDriverMessage(msg: String, isError: Boolean) {
        val current = _uiState.value as? AnalyzerUiState.Success ?: return
        _uiState.value = current.copy(driverState = current.driverState.copy(statusMessage = msg, isErrorStatus = isError))
    }
}