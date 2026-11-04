package id.taraaport.dreadout2.feature.analyzer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.taraaport.dreadout2.feature.analyzer.model.AnisotropicLevel
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityTier
import id.taraaport.dreadout2.feature.analyzer.model.DriverManagerState
import id.taraaport.dreadout2.feature.analyzer.model.GpuDriverPackage
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import id.taraaport.dreadout2.feature.analyzer.model.ResolutionScale
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAnalyzerScreen(
    viewModel: DeviceAnalyzerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importGpuDriverFromUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaraaPort v0.3 — System, Graphics & Driver") }
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
                    AnalyzerAndGraphicsContent(
                        state = state,
                        onRefresh = { viewModel.refreshHardwareSpecs() },
                        onApplyAutoGraphics = { viewModel.applyAutoGraphics() },
                        onUpdateGraphics = { viewModel.updateCustomGraphics(it) },
                        onImportDriverClick = {
                            zipPickerLauncher.launch("*/*")
                        },
                        onSelectDriver = { viewModel.selectGpuDriver(it) },
                        onDeleteDriver = { viewModel.deleteGpuDriver(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzerAndGraphicsContent(
    state: AnalyzerUiState.Success,
    onRefresh: () -> Unit,
    onApplyAutoGraphics: () -> Unit,
    onUpdateGraphics: (GraphicsConfig) -> Unit,
    onImportDriverClick: () -> Unit,
    onSelectDriver: (GpuDriverPackage?) -> Unit,
    onDeleteDriver: (String) -> Unit
) {
    val spec = state.spec
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pindai Ulang Perangkat Keras & Status Termal")
        }

        // 1. Kartu Compatibility Analyzer
        CompatibilityCard(report = state.compatibility)

        // 2. Kartu GPU Driver Manager (TAHAP 3 - TEPAT DI BAWAH STATUS PLAYABLE)
        GpuDriverManagerCard(
            gpuName = spec.gpuInfo.rendererName,
            driverState = state.driverState,
            onImportClick = onImportDriverClick,
            onSelectDriver = onSelectDriver,
            onDeleteDriver = onDeleteDriver
        )

        // 3. Kartu Auto Graphics & Graphics Manager
        GraphicsManagerCard(
            activeConfig = state.activeConfig,
            configIniPath = state.configIniPath,
            onApplyAuto = onApplyAutoGraphics,
            onConfigChange = onUpdateGraphics
        )

        // 4. Kartu Detail Hardware
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

        SpecSectionCard(title = "Prosesor, Memori & Termal") {
            SpecRow("SoC / Board", spec.socModel)
            SpecRow("CPU Cores & Max Freq", "${spec.cpuCores} Cores (${spec.maxCpuFreqMhz} MHz)")
            SpecRow("Total / Tersedia RAM", "${spec.totalRamMb} MB / ${spec.availableRamMb} MB")
            SpecRow(
                "Penyimpanan Bebas",
                String.format(Locale.US, "%.2f GB / %.2f GB", spec.freeStorageGb, spec.totalStorageGb)
            )
            SpecRow("Status Termal & Suhu", "${spec.thermalStatusLabel} (${spec.batteryTempCelsius} °C)")
        }
    }
}

@Composable
private fun GpuDriverManagerCard(
    gpuName: String,
    driverState: DriverManagerState,
    onImportClick: () -> Unit,
    onSelectDriver: (GpuDriverPackage?) -> Unit,
    onDeleteDriver: (String) -> Unit
) {
    SpecSectionCard(title = "GPU Driver Manager (AdrenoTools / Turnip)") {
        Text(
            text = "Pilih file paket driver Turnip / Qualcomm (.zip) dari penyimpanan HP untuk menutup kekurangan ekstensi Vulkan pada $gpuName.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Impor Turnip Driver (.ZIP) dari Penyimpanan HP")
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = { onSelectDriver(null) },
            enabled = driverState.activeDriverId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset ke Driver Bawaan Sistem")
        }

        if (driverState.statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = driverState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (driverState.isErrorStatus) MaterialTheme.colorScheme.error else Color(0xFF81C784)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = driverState.activeDriverId == null,
                onClick = { onSelectDriver(null) }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Driver Bawaan Sistem (Default OEM)", fontWeight = FontWeight.SemiBold)
                Text(
                    "Menggunakan libvulkan.so bawaan pabrik ($gpuName)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        driverState.installedDrivers.forEach { pkg ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.8f)
                ) {
                    RadioButton(
                        selected = driverState.activeDriverId == pkg.id,
                        onClick = { onSelectDriver(pkg) }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${pkg.name} (${pkg.driverVersion})", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Oleh: ${pkg.author} | Lib: ${pkg.libraryName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TextButton(onClick = { onDeleteDriver(pkg.id) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CompatibilityCard(report: CompatibilityReport) {
    val badgeColor = when (report.tier) {
        CompatibilityTier.OPTIMAL -> Color(0xFF2E7D32)
        CompatibilityTier.PLAYABLE -> Color(0xFF1565C0)
        CompatibilityTier.LIMITED -> Color(0xFFEF6C00)
        CompatibilityTier.UNSUPPORTED -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status: ${report.tier.label.uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
                Text(
                    text = "Skor: ${report.score}/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = report.tier.summary, style = MaterialTheme.typography.bodyMedium)

            if (report.blockers.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Kendala Utama (Blocker):", fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                report.blockers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }

            if (report.warnings.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Catatan Peringatan:", fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                report.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }

            if (report.reasons.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Hasil Analisis Positif:", fontWeight = FontWeight.Bold)
                report.reasons.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GraphicsManagerCard(
    activeConfig: GraphicsConfig,
    configIniPath: String,
    onApplyAuto: () -> Unit,
    onConfigChange: (GraphicsConfig) -> Unit
) {
    SpecSectionCard(title = "Graphics Manager & Auto Graphics") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (activeConfig.isAutoRecommended) "Mode: Auto Recommended" else "Mode: Kustom Manual",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(onClick = onApplyAuto) {
                Text("Reset ke Auto Graphics")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Skala Resolusi Internal:", fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ResolutionScale.entries.forEach { scale ->
                FilterChip(
                    selected = activeConfig.resolutionScale == scale,
                    onClick = { onConfigChange(activeConfig.copy(resolutionScale = scale)) },
                    label = { Text(scale.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Batas Frame Rate (FPS):", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60).forEach { fps ->
                FilterChip(
                    selected = activeConfig.targetFpsLimit == fps,
                    onClick = { onConfigChange(activeConfig.copy(targetFpsLimit = fps)) },
                    label = { Text("$fps FPS") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Anisotropic Filtering:", fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnisotropicLevel.entries.forEach { level ->
                FilterChip(
                    selected = activeConfig.anisotropicFiltering == level,
                    onClick = { onConfigChange(activeConfig.copy(anisotropicFiltering = level)) },
                    label = { Text(level.label) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ToggleOptionRow(
            label = "VSync (Cegah Screen Tearing)",
            checked = activeConfig.vsyncEnabled,
            onCheckedChange = { onConfigChange(activeConfig.copy(vsyncEnabled = it)) }
        )
        ToggleOptionRow(
            label = "Asynchronous Shader Compilation",
            checked = activeConfig.asyncShaderCompilation,
            onCheckedChange = { onConfigChange(activeConfig.copy(asyncShaderCompilation = it)) }
        )
        ToggleOptionRow(
            label = "Simpan Disk Shader Cache",
            checked = activeConfig.diskShaderCache,
            onCheckedChange = { onConfigChange(activeConfig.copy(diskShaderCache = it)) }
        )
        ToggleOptionRow(
            label = "Force Maximum GPU Clocks (Adreno)",
            checked = activeConfig.forceMaxGpuClocks,
            onCheckedChange = { onConfigChange(activeConfig.copy(forceMaxGpuClocks = it)) }
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tersimpan otomatis di: $configIniPath",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.8f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
