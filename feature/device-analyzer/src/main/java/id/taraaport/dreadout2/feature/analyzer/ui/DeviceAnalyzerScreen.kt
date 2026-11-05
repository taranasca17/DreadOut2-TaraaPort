package id.taraaport.dreadout2.feature.analyzer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.taraaport.dreadout2.feature.analyzer.model.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAnalyzerScreen(viewModel: DeviceAnalyzerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val romPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.selectGameRomUri(uri)
    }

    val createSampleNspLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) viewModel.generateSampleNspFile(uri)
    }

    val driverZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.importGpuDriverFromUri(uri)
    }

    val prodKeysLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.importProdKeysFromUri(uri)
    }

    val importSaveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.importSaveDataZip(uri)
    }

    val exportSaveZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportSaveDataZip(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DreadOut 2 TaraaPort v0.5 — Launcher") }) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is AnalyzerUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is AnalyzerUiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.refreshHardwareSpecs() }) { Text("Coba Lagi") }
                    }
                }
                is AnalyzerUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. KARTU UTAMA: GAME LAUNCHER & ROM VALIDATOR (TAHAP 5)
                        GameLauncherCard(
                            launchState = state.launchState,
                            activeResolution = state.activeConfig.resolutionScale.label,
                            onSelectRomClick = { romPickerLauncher.launch("*/*") },
                            onCreateSampleNspClick = {
                                createSampleNspLauncher.launch("DreadOut2_0100626015258000_Test.nsp")
                            },
                            onLaunchGameClick = { viewModel.triggerBootValidation() }
                        )

                        // 2. Kartu Compatibility Analyzer
                        CompatibilityCard(report = state.compatibility)

                        // 3. Kartu Save Data & Prod.keys Manager
                        SaveAndKeysManagerCard(
                            saveState = state.saveState,
                            onImportKeysClick = { prodKeysLauncher.launch("*/*") },
                            onInitDummySaveClick = { viewModel.createSampleSaveData() },
                            onExportSaveClick = {
                                exportSaveZipLauncher.launch("DreadOut2_SaveBackup_${System.currentTimeMillis() / 1000}.zip")
                            },
                            onImportSaveClick = { importSaveZipLauncher.launch("*/*") }
                        )

                        // 4. Kartu GPU Driver Manager
                        GpuDriverManagerCard(
                            gpuName = state.spec.gpuInfo.rendererName,
                            driverState = state.driverState,
                            onImportClick = { driverZipLauncher.launch("*/*") },
                            onSelectDriver = { viewModel.selectGpuDriver(it) },
                            onDeleteDriver = { viewModel.deleteGpuDriver(it) }
                        )

                        // 5. Kartu Graphics Manager
                        GraphicsManagerCard(
                            activeConfig = state.activeConfig,
                            configIniPath = state.configIniPath,
                            onApplyAuto = { viewModel.applyAutoGraphics() },
                            onConfigChange = { viewModel.updateCustomGraphics(it) }
                        )

                        // 6. Kartu Detail Hardware
                        SpecSectionCard(title = "GPU & Vulkan (Native NDK)") {
                            SpecRow("Model GPU", state.spec.gpuInfo.rendererName)
                            SpecRow("Vendor ID", "0x${state.spec.gpuInfo.vendorId.toString(16).uppercase()}")
                            SpecRow("Versi API Vulkan", state.spec.gpuInfo.vulkanApiVersion)
                            SpecRow("Total Ekstensi Vulkan", "${state.spec.gpuInfo.totalVulkanExtensions} ekstensi")
                            SpecRow("Ekstensi Switch (UE4)", if (state.spec.gpuInfo.hasSwitchRequiredExtensions) "Lengkap" else "Tidak Lengkap")
                            SpecRow("Dukungan Driver Turnip", if (state.spec.gpuInfo.supportsCustomTurnipDriver) "Didukung (Adreno)" else "Terkunci Sistem")
                        }

                        SpecSectionCard(title = "Prosesor, Memori & Termal") {
                            SpecRow("SoC / Board", state.spec.socModel)
                            SpecRow("CPU Cores & Max Freq", "${state.spec.cpuCores} Cores (${state.spec.maxCpuFreqMhz} MHz)")
                            SpecRow("Total / Tersedia RAM", "${state.spec.totalRamMb} MB / ${state.spec.availableRamMb} MB")
                            SpecRow("Penyimpanan Bebas", String.format(Locale.US, "%.2f GB / %.2f GB", state.spec.freeStorageGb, state.spec.totalStorageGb))
                            SpecRow("Status Termal & Suhu", "${state.spec.thermalStatusLabel} (${state.spec.batteryTempCelsius} °C)")
                        }

                        OutlinedButton(onClick = { viewModel.refreshHardwareSpecs() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Pindai Ulang Seluruh Sistem")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameLauncherCard(
    launchState: LaunchReadinessState,
    activeResolution: String,
    onSelectRomClick: () -> Unit,
    onCreateSampleNspClick: () -> Unit,
    onLaunchGameClick: () -> Unit
) {
    val rom = launchState.romInfo
    SpecSectionCard(title = "Game Launcher & NSP/XCI Validator (Tahap 5)") {
        SpecRow("Berkas Game", rom.fileName)
        SpecRow("Format Kontainer", rom.format.label)
        SpecRow("Ukuran & Title ID", "${rom.fileSizeFormatted} | ID: ${rom.detectedTitleId}")
        SpecRow("Driver Vulkan Aktif", launchState.activeDriverLabel)
        SpecRow("Target Resolusi", activeResolution)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Daftar Periksa Pra-Peluncuran (Pre-Flight Check):", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        ChecklistItem("1. Perangkat Keras ARM64 & Vulkan 1.1+", launchState.isHardwareSupported)
        ChecklistItem("2. Kunci Dekripsi Switch (prod.keys)", launchState.isKeysReady)
        ChecklistItem("3. Berkas Game DreadOut 2 (NSP/XCI Valid)", launchState.isRomReady)

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelectRomClick, modifier = Modifier.weight(1f)) {
                Text("Pilih File NSP / XCI")
            }
            OutlinedButton(onClick = onCreateSampleNspClick, modifier = Modifier.weight(1f)) {
                Text("Buat Sampel NSP")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onLaunchGameClick,
            enabled = launchState.canLaunchGame,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (launchState.canLaunchGame) "LUNCURKAN DREADOUT 2 (SIAP BOOT)" else "LENGKAPI SYARAT DI ATAS UNTUK BOOT",
                fontWeight = FontWeight.Bold
            )
        }

        if (launchState.statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = launchState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (launchState.isErrorStatus) MaterialTheme.colorScheme.error else Color(0xFF81C784)
            )
        }
    }
}

@Composable
private fun ChecklistItem(label: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (isReady) "✓ SIAP" else "✗ BELUM",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isReady) Color(0xFF81C784) else Color(0xFFFFB74D)
        )
    }
}

@Composable
private fun SaveAndKeysManagerCard(
    saveState: SaveManagerUiState,
    onImportKeysClick: () -> Unit,
    onInitDummySaveClick: () -> Unit,
    onExportSaveClick: () -> Unit,
    onImportSaveClick: () -> Unit
) {
    val saveInfo = saveState.saveInfo
    val keysInfo = saveState.keysInfo

    SpecSectionCard(title = "Save Data & Prod.keys Manager (Tahap 4)") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.65f)) {
                Text("Status Kunci Dekripsi (prod.keys):", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (keysInfo.isInstalled) "Terpasang (${keysInfo.validKeyCount} kunci valid)" else "Belum terpasang (Wajib untuk memuat NSP/XCI)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keysInfo.isInstalled) Color(0xFF81C784) else Color(0xFFFFB74D)
                )
            }
            OutlinedButton(onClick = onImportKeysClick) {
                Text("Impor Keys")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Save Data DreadOut 2 (ID: ${saveInfo.titleId})", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        SpecRow("Status Progres", if (saveInfo.hasSaveData) "${saveInfo.totalFilesCount} berkas (${saveInfo.totalSizeBytes} B)" else "Kosong")
        SpecRow("Terakhir Diubah", saveInfo.lastModifiedFormatted)

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportSaveClick, enabled = saveInfo.hasSaveData, modifier = Modifier.weight(1f)) {
                Text("Backup Save (.ZIP)")
            }
            Button(onClick = onImportSaveClick, modifier = Modifier.weight(1f)) {
                Text("Restore Save (.ZIP)")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(onClick = onInitDummySaveClick, modifier = Modifier.fillMaxWidth()) {
            Text("Inisialisasi Slot Save Awal (Untuk Tes Backup)")
        }

        if (saveState.statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = saveState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (saveState.isErrorStatus) MaterialTheme.colorScheme.error else Color(0xFF81C784)
            )
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
        Text("Pilih file paket driver Turnip (.zip) dari penyimpanan HP untuk dipasang pada $gpuName.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
            Text("+ Impor Turnip Driver (.ZIP) dari Penyimpanan HP")
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = { onSelectDriver(null) }, enabled = driverState.activeDriverId != null, modifier = Modifier.fillMaxWidth()) {
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = driverState.activeDriverId == null, onClick = { onSelectDriver(null) })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Driver Bawaan Sistem (Default OEM)", fontWeight = FontWeight.SemiBold)
                Text("Menggunakan libvulkan.so bawaan pabrik ($gpuName)", style = MaterialTheme.typography.bodySmall)
            }
        }
        driverState.installedDrivers.forEach { pkg ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.8f)) {
                    RadioButton(selected = driverState.activeDriverId == pkg.id, onClick = { onSelectDriver(pkg) })
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${pkg.name} (${pkg.driverVersion})", fontWeight = FontWeight.SemiBold)
                        Text("Oleh: ${pkg.author} | Lib: ${pkg.libraryName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { onDeleteDriver(pkg.id) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
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
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ${report.tier.label.uppercase()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = badgeColor)
                Text("Skor: ${report.score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = report.tier.summary, style = MaterialTheme.typography.bodyMedium)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (activeConfig.isAutoRecommended) "Mode: Auto Recommended" else "Mode: Kustom Manual", fontWeight = FontWeight.SemiBold)
            Button(onClick = onApplyAuto) { Text("Reset ke Auto Graphics") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Skala Resolusi Internal:", fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ResolutionScale.entries.forEach { scale ->
                FilterChip(selected = activeConfig.resolutionScale == scale, onClick = { onConfigChange(activeConfig.copy(resolutionScale = scale)) }, label = { Text(scale.label) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Batas Frame Rate (FPS):", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60).forEach { fps ->
                FilterChip(selected = activeConfig.targetFpsLimit == fps, onClick = { onConfigChange(activeConfig.copy(targetFpsLimit = fps)) }, label = { Text("$fps FPS") })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Anisotropic Filtering:", fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnisotropicLevel.entries.forEach { level ->
                FilterChip(selected = activeConfig.anisotropicFiltering == level, onClick = { onConfigChange(activeConfig.copy(anisotropicFiltering = level)) }, label = { Text(level.label) })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ToggleOptionRow("VSync (Cegah Screen Tearing)", activeConfig.vsyncEnabled) { onConfigChange(activeConfig.copy(vsyncEnabled = it)) }
        ToggleOptionRow("Asynchronous Shader Compilation", activeConfig.asyncShaderCompilation) { onConfigChange(activeConfig.copy(asyncShaderCompilation = it)) }
        ToggleOptionRow("Simpan Disk Shader Cache", activeConfig.diskShaderCache) { onConfigChange(activeConfig.copy(diskShaderCache = it)) }
        ToggleOptionRow("Force Maximum GPU Clocks (Adreno)", activeConfig.forceMaxGpuClocks) { onConfigChange(activeConfig.copy(forceMaxGpuClocks = it)) }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Tersimpan otomatis di: $configIniPath", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ToggleOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.8f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SpecSectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}