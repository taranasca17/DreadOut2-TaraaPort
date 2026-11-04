package id.taraaport.dreadout2.feature.analyzer.data

import android.content.Context
import android.net.Uri
import android.os.Build
import id.taraaport.dreadout2.feature.analyzer.model.GpuDriverPackage
import id.taraaport.dreadout2.feature.analyzer.nativebridge.VulkanNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class GpuDriverRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("taraaport_driver_prefs", Context.MODE_PRIVATE)
    private val driversRootDir = File(context.filesDir, "gpu_drivers")

    init {
        if (!driversRootDir.exists()) {
            driversRootDir.mkdirs()
        }
    }

    suspend fun importDriverZip(uri: Uri): Result<GpuDriverPackage> = withContext(Dispatchers.IO) {
        val tempId = "drv_" + UUID.randomUUID().toString().take(8)
        val targetDir = File(driversRootDir, tempId)
        targetDir.mkdirs()

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membaca file ZIP dari penyimpanan."))

            var foundMetaJson = false
            val extractedFiles = mutableListOf<File>()

            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // Cegah Zip Slip vulnerability & ratakan file ke dalam folder targetDir
                        val safeFileName = File(entry.name).name
                        val outFile = File(targetDir, safeFileName)
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedFiles.add(outFile)
                        if (safeFileName.equals("meta.json", ignoreCase = true)) {
                            foundMetaJson = true
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (extractedFiles.isEmpty()) {
                targetDir.deleteRecursively()
                return@withContext Result.failure(IllegalArgumentException("File ZIP kosong atau rusak."))
            }

            // Parsing meta.json (Format standar AdrenoTools / Turnip) atau fallback deteksi .so langsung
            val metaFile = File(targetDir, "meta.json")
            val name: String
            val author: String
            val version: String
            val libName: String
            val minApi: Int
            val desc: String

            if (foundMetaJson && metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                name = json.optString("name", "Custom Adreno Driver")
                author = json.optString("author", "Community")
                version = json.optString("driverVersion", json.optString("version", "1.0"))
                libName = json.optString("libraryName", "vulkan.adreno.so")
                minApi = json.optInt("minApi", 29)
                desc = json.optString("description", "Paket driver Vulkan kustom AdrenoTools")
            } else {
                // Jika tidak ada meta.json, cari file .so Vulkan secara langsung
                val firstSo = extractedFiles.firstOrNull { it.name.endsWith(".so", ignoreCase = true) }
                if (firstSo == null) {
                    targetDir.deleteRecursively()
                    return@withContext Result.failure(
                        IllegalArgumentException("Bukan paket GPU Driver yang valid (Tidak ditemukan meta.json maupun file .so).")
                    )
                }
                name = firstSo.nameWithoutExtension
                author = "Custom Import"
                version = "Unknown"
                libName = firstSo.name
                minApi = 29
                desc = "Diimpor tanpa meta.json (${firstSo.name})"
            }

            // Periksa syarat minimum Android SDK API
            if (Build.VERSION.SDK_INT < minApi) {
                targetDir.deleteRecursively()
                return@withContext Result.failure(
                    IllegalArgumentException("Driver ini membutuhkan minimal Android API $minApi, sedangkan perangkat Anda API ${Build.VERSION.SDK_INT}.")
                )
            }

            val soFile = File(targetDir, libName)
            if (!soFile.exists()) {
                targetDir.deleteRecursively()
                return@withContext Result.failure(
                    IllegalArgumentException("File pustaka utama '$libName' yang tertulis di meta.json tidak ditemukan di dalam ZIP.")
                )
            }

            // Uji keaslian binari ELF ARM64 & simbol ICD melalui JNI C++
            val (isVerified, verifyMsg) = VulkanNativeBridge.verifyDriverSoFile(soFile.absolutePath)
            if (!isVerified) {
                targetDir.deleteRecursively()
                return@withContext Result.failure(
                    IllegalArgumentException("Validasi Native gagal: $verifyMsg")
                )
            }

            // Simpan metadata terstandarisasi agar mudah dimuat ulang
            val savedMeta = JSONObject().apply {
                put("id", tempId)
                put("name", name)
                put("author", author)
                put("driverVersion", version)
                put("libraryName", libName)
                put("minApi", minApi)
                put("description", desc)
                put("extractedSoPath", soFile.absolutePath)
                put("isNativeVerified", true)
            }
            File(targetDir, "taraaport_driver_info.json").writeText(savedMeta.toString(2))

            val pkg = GpuDriverPackage(
                id = tempId,
                name = name,
                author = author,
                driverVersion = version,
                libraryName = libName,
                minApi = minApi,
                description = desc,
                extractedSoPath = soFile.absolutePath,
                isNativeVerified = true
            )
            Result.success(pkg)
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            Result.failure(e)
        }
    }

    fun getInstalledDrivers(): List<GpuDriverPackage> {
        val list = mutableListOf<GpuDriverPackage>()
        val folders = driversRootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (folder in folders) {
            val infoFile = File(folder, "taraaport_driver_info.json")
            if (infoFile.exists()) {
                try {
                    val json = JSONObject(infoFile.readText())
                    val soPath = json.optString("extractedSoPath", "")
                    if (File(soPath).exists()) {
                        list.add(
                            GpuDriverPackage(
                                id = json.getString("id"),
                                name = json.getString("name"),
                                author = json.getString("author"),
                                driverVersion = json.getString("driverVersion"),
                                libraryName = json.getString("libraryName"),
                                minApi = json.getInt("minApi"),
                                description = json.getString("description"),
                                extractedSoPath = soPath,
                                isNativeVerified = json.optBoolean("isNativeVerified", true)
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
        return list.sortedBy { it.name }
    }

    fun getActiveDriverId(): String? {
        val id = prefs.getString("active_driver_id", null)
        if (id != null && getInstalledDrivers().none { it.id == id }) {
            setActiveDriver(null)
            return null
        }
        return id
    }

    fun setActiveDriver(driver: GpuDriverPackage?) {
        prefs.edit().apply {
            if (driver == null) {
                remove("active_driver_id")
                remove("active_driver_so_path")
            } else {
                putString("active_driver_id", driver.id)
                putString("active_driver_so_path", driver.extractedSoPath)
            }
            apply()
        }
        syncDriverToConfigIni(driver)
    }

    fun deleteDriver(driverId: String) {
        if (getActiveDriverId() == driverId) {
            setActiveDriver(null)
        }
        val targetDir = File(driversRootDir, driverId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
    }

    private fun syncDriverToConfigIni(driver: GpuDriverPackage?) {
        val configFile = File(context.filesDir, "config/taraaport_config.ini")
        configFile.parentFile?.mkdirs()
        val existingLines = if (configFile.exists()) {
            configFile.readLines().filterNot {
                it.startsWith("custom_driver_enabled") ||
                it.startsWith("custom_driver_path") ||
                it.startsWith("[GpuDriver]")
            }
        } else {
            emptyList()
        }

        val updatedContent = buildString {
            existingLines.forEach { appendLine(it) }
            appendLine("[GpuDriver]")
            appendLine("custom_driver_enabled = ${if (driver != null) 1 else 0}")
            appendLine("custom_driver_path = ${driver?.extractedSoPath ?: "SYSTEM_DEFAULT"}")
        }
        configFile.writeText(updatedContent)
    }
}