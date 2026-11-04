package id.taraaport.dreadout2.feature.analyzer.domain

import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityTier
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec

object CompatibilityAnalyzer {

    private const val MIN_REQUIRED_STORAGE_GB = 6.0
    private const val MIN_RAM_LIMITED_MB = 5400L   // Kelas 6 GB RAM
    private const val MIN_RAM_PLAYABLE_MB = 7200L  // Kelas 8 GB RAM
    private const val MIN_RAM_OPTIMAL_MB = 11000L  // Kelas 12 GB+ RAM

    fun evaluate(spec: DeviceSpec): CompatibilityReport {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val blockers = mutableListOf<String>()
        var score = 0

        // 1. Pemeriksaan Blocker Mutlak (Arsitektur, Vulkan, RAM, Storage)
        val hasArm64 = spec.supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
        if (!hasArm64) {
            blockers.add("CPU tidak mendukung instruksi 64-bit (arm64-v8a) yang wajib untuk NCE.")
        } else {
            reasons.add("Arsitektur arm64-v8a didukung (Native Code Execution aktif).")
            score += 20
        }

        val vulkanSupported = isVulkanAtLeast11(spec.gpuInfo.vulkanApiVersion)
        if (!vulkanSupported) {
            blockers.add("Versi Vulkan (${spec.gpuInfo.vulkanApiVersion}) di bawah batas minimum Vulkan 1.1.")
        } else {
            reasons.add("API Vulkan ${spec.gpuInfo.vulkanApiVersion} terdeteksi dan siap digunakan.")
            score += 20
        }

        if (spec.totalRamMb < MIN_RAM_LIMITED_MB) {
            blockers.add("Total RAM (${spec.totalRamMb} MB) di bawah batas minimum 6 GB untuk memuat aset Unreal Engine 4.")
        }

        if (spec.freeStorageGb < MIN_REQUIRED_STORAGE_GB) {
            warnings.add(
                "Ruang penyimpanan bebas (${String.format("%.1f", spec.freeStorageGb)} GB) kurang dari 6 GB untuk game & shader cache."
            )
        }

        // Jika terdapat Blocker mutlak, langsung klasifikasikan sebagai UNSUPPORTED
        if (blockers.isNotEmpty()) {
            return CompatibilityReport(
                tier = CompatibilityTier.UNSUPPORTED,
                score = score.coerceAtMost(25),
                reasons = reasons,
                warnings = warnings,
                blockers = blockers
            )
        }

        // 2. Penilaian Kapasitas RAM
        when {
            spec.totalRamMb >= MIN_RAM_OPTIMAL_MB -> {
                score += 25
                reasons.add("Kapasitas RAM sangat lega (${spec.totalRamMb} MB), aman dari penutupan paksa LMKD.")
            }
            spec.totalRamMb >= MIN_RAM_PLAYABLE_MB -> {
                score += 18
                reasons.add("Kapasitas RAM mencukupi (${spec.totalRamMb} MB / kelas 8 GB).")
            }
            else -> {
                score += 8
                warnings.add("RAM terbatas (${spec.totalRamMb} MB). Tutup semua aplikasi latar belakang sebelum bermain.")
            }
        }

        // 3. Penilaian GPU, Driver Turnip, & Ekstensi Vulkan Switch
        val gpuName = spec.gpuInfo.rendererName
        val isHighEndAdreno = isHighTierAdreno(gpuName)
        val isMidRangeAdreno = isMidTierAdreno(gpuName)

        if (spec.gpuInfo.supportsCustomTurnipDriver) {
            reasons.add("GPU Qualcomm Adreno mendukung penggunaan custom GPU driver (Mesa Turnip).")
            score += if (isHighEndAdreno) 25 else if (isMidRangeAdreno) 18 else 10
        } else {
            warnings.add("GPU Non-Adreno ($gpuName) tidak mendukung driver Turnip; bergantung penuh pada driver bawaan pabrik.")
            score += 8
        }

        if (spec.gpuInfo.hasSwitchRequiredExtensions) {
            reasons.add("Ekstensi Vulkan kritikal (Transform Feedback & Custom Border Color) tersedia.")
            score += 10
        } else {
            warnings.add("Ekstensi Vulkan Switch tidak lengkap pada driver sistem saat ini. Disarankan memasang driver Turnip (jika Adreno).")
        }

        // 4. Penilaian Kondisi Termal Saat Ini
        if (spec.batteryTempCelsius >= 42.0f) {
            warnings.add("Suhu perangkat saat ini cukup tinggi (${spec.batteryTempCelsius} °C), berisiko memicu thermal throttling.")
        }

        // 5. Penentuan Tier Akhir
        val finalTier = when {
            score >= 85 && spec.totalRamMb >= MIN_RAM_OPTIMAL_MB && spec.gpuInfo.supportsCustomTurnipDriver -> {
                CompatibilityTier.OPTIMAL
            }
            score >= 65 && spec.totalRamMb >= MIN_RAM_PLAYABLE_MB -> {
                CompatibilityTier.PLAYABLE
            }
            else -> {
                CompatibilityTier.LIMITED
            }
        }

        return CompatibilityReport(
            tier = finalTier,
            score = score.coerceIn(0, 100),
            reasons = reasons,
            warnings = warnings,
            blockers = blockers
        )
    }

    private fun isVulkanAtLeast11(versionStr: String): Boolean {
        val parts = versionStr.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major > 1 || (major == 1 && minor >= 1)
    }

    private fun isHighTierAdreno(renderer: String): Boolean {
        val highPatterns = listOf("730", "740", "750", "830", "660", "650")
        return highPatterns.any { renderer.contains(it, ignoreCase = true) }
    }

    private fun isMidTierAdreno(renderer: String): Boolean {
        val midPatterns = listOf("710", "720", "725", "642", "640", "630")
        return midPatterns.any { renderer.contains(it, ignoreCase = true) }
    }
}