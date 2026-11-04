package id.taraaport.dreadout2.feature.analyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAnalyzerScreen(
    viewModel: DeviceAnalyzerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DreadOut 2 TaraaPort — Device Analyzer") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is AnalyzerUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is AnalyzerUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.refreshHardwareSpecs() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
                is AnalyzerUiState.Success -> {
                    DeviceSpecContent(
                        spec = state.spec,
                        onRefresh = { viewModel.refreshHardwareSpecs() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSpecContent(
    spec: DeviceSpec,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pindai Ulang Perangkat Keras & Status Termal")
        }

        SpecSectionCard(title = "GPU & Vulkan (Native NDK)") {
            SpecRow("Model GPU", spec.gpuInfo.rendererName)
            SpecRow("Vendor ID", "0x${spec.gpuInfo.vendorId.toString(16).uppercase()}")
            SpecRow("Versi API Vulkan", spec.gpuInfo.vulkanApiVersion)
            SpecRow("Total Ekstensi Vulkan", "${spec.gpuInfo.totalVulkanExtensions} ekstensi")
            SpecRow(
                "Ekstensi Switch (UE4)",
                if (spec.gpuInfo.hasSwitchRequiredExtensions) "Lengkap" else "Tidak Lengkap"
            )
            SpecRow(
                "Dukungan Driver Turnip",
                if (spec.gpuInfo.supportsCustomTurnipDriver) "Didukung (Adreno)" else "Terkunci Sistem (Non-Adreno)"
            )
        }

        SpecSectionCard(title = "Prosesor (CPU & ABI)") {
            SpecRow("SoC / Board", spec.socModel)
            SpecRow("Hardware Info", spec.cpuHardware)
            SpecRow("Jumlah Core", "${spec.cpuCores} Cores")
            SpecRow(
                "Frekuensi Maksimum",
                if (spec.maxCpuFreqMhz > 0) "${spec.maxCpuFreqMhz} MHz" else "Tidak Terbaca"
            )
            SpecRow("Instruksi ABI", spec.supportedAbis.joinToString(", "))
        }

        SpecSectionCard(title = "Memori (RAM) & Penyimpanan") {
            SpecRow("Total RAM", "${spec.totalRamMb} MB")
            SpecRow("RAM Tersedia Saat Ini", "${spec.availableRamMb} MB")
            SpecRow(
                "Penyimpanan Internal Bebas",
                String.format(Locale.US, "%.2f GB / %.2f GB", spec.freeStorageGb, spec.totalStorageGb)
            )
        }

        SpecSectionCard(title = "Sistem Operasi & Termal") {
            SpecRow("Versi Android", "Android ${spec.androidVersion} (API ${spec.sdkInt})")
            SpecRow("Status Termal Sistem", spec.thermalStatusLabel)
            SpecRow("Suhu Baterai", "${spec.batteryTempCelsius} °C")
        }
    }
}

@Composable
private fun SpecSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f)
        )
    }
}