package id.taraaport.dreadout2.feature.analyzer.data

import android.content.Context
import android.net.Uri
import id.taraaport.dreadout2.feature.analyzer.model.ProdKeysInfo
import id.taraaport.dreadout2.feature.analyzer.model.SaveDataInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveAndKeysRepository(private val context: Context) {

    companion object {
        const val DREADOUT2_TITLE_ID = "0100626015258000"
    }

    // Struktur standar NAND emulator Switch (Yuzu/Sudachi compatible)
    private val saveRootDir = File(context.filesDir, "nand/user/save/0000000000000000/0000000000000001/$DREADOUT2_TITLE_ID")
    private val backupTempDir = File(context.cacheDir, "save_rollback_temp")
    private val keysDir = File(context.filesDir, "keys")
    private val prodKeysFile = File(keysDir, "prod.keys")

    init {
        saveRootDir.mkdirs()
        keysDir.mkdirs()
    }

    fun getSaveDataInfo(): SaveDataInfo {
        val allFiles = saveRootDir.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = allFiles.sumOf { it.length() }
        val latestMod = allFiles.maxOfOrNull { it.lastModified() } ?: 0L

        val dateStr = if (latestMod > 0L) {
            SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(latestMod))
        } else {
            "Belum ada data tersimpan"
        }

        return SaveDataInfo(
            titleId = DREADOUT2_TITLE_ID,
            hasSaveData = allFiles.isNotEmpty(),
            totalFilesCount = allFiles.size,
            totalSizeBytes = totalBytes,
            lastModifiedFormatted = dateStr,
            internalSavePath = saveRootDir.absolutePath
        )
    }

    fun getProdKeysInfo(): ProdKeysInfo {
        if (!prodKeysFile.exists()) {
            return ProdKeysInfo(false, 0, prodKeysFile.absolutePath)
        }
        val lines = prodKeysFile.readLines()
        val validCount = lines.count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")
        }
        return ProdKeysInfo(
            isInstalled = validCount >= 2,
            validKeyCount = validCount,
            keyFilePath = prodKeysFile.absolutePath
        )
    }

    suspend fun importProdKeys(uri: Uri): Result<ProdKeysInfo> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membaca file prod.keys dari penyimpanan."))

            val lines = content.lines()
            val keyPairs = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }

            val hasHeaderKey = keyPairs.any { it.startsWith("header_key") }
            val hasAppKey = keyPairs.any { it.startsWith("key_area_key_application") || it.startsWith("titlekek") }

            if (!hasHeaderKey && !hasAppKey) {
                return@withContext Result.failure(
                    IllegalArgumentException("File bukan prod.keys Switch yang valid (Tidak ditemukan header_key atau key_area_key_application).")
                )
            }

            prodKeysFile.writeText(content)
            Result.success(getProdKeysInfo())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateDummySaveSlot(): Result<SaveDataInfo> = withContext(Dispatchers.IO) {
        try {
            val ue4SaveDir = File(saveRootDir, "Saved/SaveGames").apply { mkdirs() }
            val slotFile = File(ue4SaveDir, "DreadOut2_Slot0.sav")
            val configLog = File(saveRootDir, "Saved/Config/Switch/GameUserSettings.ini").apply { parentFile?.mkdirs() }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            slotFile.writeText("GVAS_UE4_SAVEDATA_HEADER|DreadOut2_TaraaPort|Chapter=1|LastSaved=$timestamp")
            configLog.writeText("[/Script/DreadOut2.DO2GameUserSettings]\nSubtitlesEnabled=True\nGamma=2.2\n")

            Result.success(getSaveDataInfo())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSaveDataToZip(targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val files = saveRootDir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Folder Save Data masih kosong. Tidak ada progres untuk diekspor."))
        }

        try {
            val outStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membuat file tujuan di penyimpanan HP."))

            ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                // Tambahkan metadata penanda integritas DreadOut 2 TaraaPort
                val metaEntry = ZipEntry("taraaport_save_meta.ini")
                zos.putNextEntry(metaEntry)
                val metaText = "title_id=$DREADOUT2_TITLE_ID\ngame=DreadOut 2\nexported_at=${System.currentTimeMillis()}\n"
                zos.write(metaText.toByteArray())
                zos.closeEntry()

                for (file in files) {
                    val relativePath = file.relativeTo(saveRootDir).path.replace("\\", "/")
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            Result.success(files.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSaveDataFromZip(sourceUri: Uri): Result<SaveDataInfo> = withContext(Dispatchers.IO) {
        // 1. Cadangkan save data lama ke folder rollback sementara untuk berjaga-jaga jika ZIP rusak
        backupTempDir.deleteRecursively()
        backupTempDir.mkdirs()
        if (saveRootDir.exists()) {
            saveRootDir.copyRecursively(backupTempDir, overwrite = true)
        }

        val stagingDir = File(context.cacheDir, "save_staging_extract").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal membuka file ZIP Save Data."))

            var extractedCount = 0
            ZipInputStream(BufferedInputStream(inStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val cleanName = entry.name.replace("\\", "/")
                    // Cegah Zip Slip Attack
                    if (cleanName.contains("..")) {
                        throw SecurityException("Terdeteksi jalur file berbahaya di dalam arsip ZIP.")
                    }

                    if (!entry.isDirectory && cleanName != "taraaport_save_meta.ini") {
                        val destFile = File(stagingDir, cleanName)
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (extractedCount == 0) {
                stagingDir.deleteRecursively()
                return@withContext Result.failure(IllegalArgumentException("File ZIP kosong atau tidak berisi berkas Save Data."))
            }

            // 2. Tolak jika pengguna salah memilih file ZIP Driver GPU di menu Save Data
            val hasSoFiles = stagingDir.walkTopDown().any { it.extension.equals("so", ignoreCase = true) }
            if (hasSoFiles) {
                stagingDir.deleteRecursively()
                return@withContext Result.failure(
                    IllegalArgumentException("Ini adalah paket GPU Driver (.so), bukan arsip Save Data permainan!")
                )
            }

            // 3. Jika lolos validasi, timpa folder save utama dengan hasil ekstraksi
            saveRootDir.deleteRecursively()
            saveRootDir.mkdirs()
            stagingDir.copyRecursively(saveRootDir, overwrite = true)
            stagingDir.deleteRecursively()
            backupTempDir.deleteRecursively()

            Result.success(getSaveDataInfo())
        } catch (e: Exception) {
            // Rollback otomatis ke save data sebelum impor jika terjadi kegagalan
            saveRootDir.deleteRecursively()
            saveRootDir.mkdirs()
            if (backupTempDir.exists()) {
                backupTempDir.copyRecursively(saveRootDir, overwrite = true)
                backupTempDir.deleteRecursively()
            }
            stagingDir.deleteRecursively()
            Result.failure(e)
        }
    }
}