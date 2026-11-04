package id.taraaport.dreadout2.feature.analyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.taraaport.dreadout2.feature.analyzer.data.GraphicsConfigRepository
import id.taraaport.dreadout2.feature.analyzer.data.SystemHardwareCollector
import id.taraaport.dreadout2.feature.analyzer.domain.AutoGraphicsEngine
import id.taraaport.dreadout2.feature.analyzer.domain.CompatibilityAnalyzer
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
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
        val configIniPath: String
    ) : AnalyzerUiState
    data class Error(val message: String) : AnalyzerUiState
}

class DeviceAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = SystemHardwareCollector(application.applicationContext)
    private val graphicsRepo = GraphicsConfigRepository(application.applicationContext)

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

                _uiState.value = AnalyzerUiState.Success(
                    spec = specs,
                    compatibility = report,
                    recommendedConfig = recommended,
                    activeConfig = active,
                    configIniPath = graphicsRepo.getIniFilePath()
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
}