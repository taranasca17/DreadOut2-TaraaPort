package id.taraaport.dreadout2.feature.analyzer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import id.taraaport.dreadout2.feature.analyzer.model.GameRomInfo
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import id.taraaport.dreadout2.feature.analyzer.model.RomFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.Locale

class GameRomRepository(private val context: Context) {

    companion object {
        const val DREADOUT2_TITLE_ID = "0100626015258000"
    }

    private val prefs = context.getSharedPreferences("taraaport_rom_prefs", Context.MODE_PRIVATE)
    private val bootManifestFile = File(context.filesDir, "config/taraaport_boot_params.json")

    fun getSavedRomInfo(): GameRomInfo {
        val isLoaded = prefs.getBoolean("rom_loaded", false)
        if (!isLoaded) return GameRomInfo()

        val fmtName = prefs.getString("rom_format", RomFormat.UNKNOWN.name)
        return GameRomInfo(
            isLoaded = true,
            fileName = prefs.getString("rom_name", "DreadOut2.nsp") ?: "DreadOut2.nsp",
            format = RomFormat.entries.find { it.name == fmtName } ?: RomFormat.UNKNOWN,
            fileSizeFormatted = prefs.getString("rom_size", "0 MB") ?: "0 MB",
            detectedTitleId = prefs.getString("rom_title_id", DREADOUT2_TITLE_ID) ?: DREADOUT2_TITLE_ID,
            isDreadOut2Verified = prefs.getBoolean("rom_verified", false),
            uriString = prefs.getString("rom_uri", "") ?: ""
        )
    }

    suspend fun inspectAndRegisterRom(uri: Uri): Result<GameRomInfo> = withContext(Dispatchers.IO) {
        try {
            var displayName = "Unknown_Game.nsp"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membuka file ROM dari penyimpanan HP."))

            // Baca 64 KB pertama untuk memeriksa Magic Header PFS0 (NSP) atau HEAD (XCI) serta tabel Title ID
            val headerBuffer = ByteArray(65536)
            val bytesRead = inputStream.use { it.read(headerBuffer) }

            if (bytesRead < 4) {
                return@withContext Result.failure(IllegalArgumentException("File terlalu kecil atau kosong."))
            }

            // 1. Cek Magic Bytes NSP: 'P', 'F', 'S', '0' pada offset 0x00
            val isNspPfs0 = headerBuffer[0] == 'P'.code.toByte() &&
                            headerBuffer[1] == 'F'.code.toByte() &&
                            headerBuffer[2] == 'S'.code.toByte() &&
                            headerBuffer[3] == '0'.code.toByte()

            // 2. Cek Magic Bytes XCI: 'H', 'E', 'A', 'D' pada offset 0x100 (256)
            val isXciHead = bytesRead >= 260 &&
                            headerBuffer[256] == 'H'.code.toByte() &&
                            headerBuffer[257] == 'E'.code.toByte() &&
                            headerBuffer[258] == 'A'.code.toByte() &&
                            headerBuffer[259] == 'D'.code.toByte()

            val detectedFormat = when {
                isNspPfs0 -> RomFormat.NSP
                isXciHead -> RomFormat.XCI
                else -> return@withContext Result.failure(
                    IllegalArgumentException("Header binari tidak valid! File ini bukan paket NSP (PFS0) atau XCI (HEAD) Nintendo Switch.")
                )
            }

            // Pindai string ASCII di dalam tabel header PFS0/HEAD untuk mencari Title ID 16-karakter hex
            val headerAscii = String(headerBuffer, 0, bytesRead, Charsets.ISO_8859_1).uppercase(Locale.US)
            val hasDreadOutId = headerAscii.contains(DREADOUT2_TITLE_ID) ||
                                displayName.contains(DREADOUT2_TITLE_ID, ignoreCase = true) ||
                                displayName.contains("dreadout", ignoreCase = true)

            val hexRegex = Regex("0100[0-9A-F]{12}")
            val matchedId = when {
                headerAscii.contains(DREADOUT2_TITLE_ID) -> DREADOUT2_TITLE_ID
                else -> hexRegex.find(headerAscii)?.value ?: if (hasDreadOutId) DREADOUT2_TITLE_ID else "0100XXXXXXXXXXXX (Custom)"
            }

            val sizeMb = fileSize.toDouble() / (1024.0 * 1024.0)
            val sizeStr = if (sizeMb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", sizeMb / 1024.0)
            } else if (sizeMb >= 1.0) {
                String.format(Locale.US, "%.2f MB", sizeMb)
            } else {
                "$bytesRead Bytes (Header Sample)"
            }

            val info = GameRomInfo(
                isLoaded = true,
                fileName = displayName,
                format = detectedFormat,
                fileSizeFormatted = sizeStr,
                detectedTitleId = matchedId,
                isDreadOut2Verified = hasDreadOutId || matchedId == DREADOUT2_TITLE_ID,
                uriString = uri.toString()
            )

            prefs.edit().apply {
                putBoolean("rom_loaded", true)
                putString("rom_name", info.fileName)
                putString("rom_format", info.format.name)
                putString("rom_size", info.fileSizeFormatted)
                putString("rom_title_id", info.detectedTitleId)
                putBoolean("rom_verified", info.isDreadOut2Verified)
                putString("rom_uri", info.uriString)
                apply()
            }

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSampleDreadOutNsp(targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val out: OutputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membuat file sampel NSP."))

            out.use { stream ->
                // Tulis Magic Header resmi PFS0 (NSP) + String Table yang memuat Title ID DreadOut 2 (0100626015258000)
                val sampleHeader = "PFS0\u0001\u0000\u0000\u0000\u0020\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
                        "0100626015258000.tik\u00000100626015258000.cert\u0000DreadOut2_Switch_TaraaPort_SamplePayload"
                stream.write(sampleHeader.toByteArray(Charsets.ISO_8859_1))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateBootManifest(
        romInfo: GameRomInfo,
        graphicsConfig: GraphicsConfig,
        activeDriverSoPath: String?,
        prodKeysPath: String,
        configIniPath: String
    ): String {
        bootManifestFile.parentFile?.mkdirs()
        val json = JSONObject().apply {
            put("app_version", "0.5.0-alpha")
            put("timestamp", System.currentTimeMillis())
            put("title_id", romInfo.detectedTitleId)
            put("rom_file_name", romInfo.fileName)
            put("rom_format", romInfo.format.name)
            put("rom_uri", romInfo.uriString)
            put("prod_keys_path", prodKeysPath)
            put("graphics_ini_path", configIniPath)
            put("resolution_multiplier", graphicsConfig.resolutionScale.multiplier)
            put("target_fps", graphicsConfig.targetFpsLimit)
            put("custom_driver_so_path", activeDriverSoPath ?: "SYSTEM_LIBVULKAN")
        }
        bootManifestFile.writeText(json.toString(2))
        return bootManifestFile.absolutePath
    }
}