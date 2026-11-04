package id.taraaport.dreadout2.feature.analyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.taraaport.dreadout2.feature.analyzer.data.SystemHardwareCollector
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalyzerUiState {
    data object Loading : AnalyzerUiState
    data class Success(val spec: DeviceSpec) : AnalyzerUiState
    data class Error(val message: String) : AnalyzerUiState
}

class DeviceAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = SystemHardwareCollector(application.applicationContext)

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
                _uiState.value = AnalyzerUiState.Success(specs)
            } catch (e: Exception) {
                _uiState.value = AnalyzerUiState.Error(
                    e.localizedMessage ?: "Gagal memindai perangkat keras"
                )
            }
        }
    }
}