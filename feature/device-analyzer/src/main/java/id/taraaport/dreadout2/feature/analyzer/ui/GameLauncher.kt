package id.taraaport.dreadout2.feature.analyzer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

object GameLauncher {
    /**
     * Membuka game NSP secara otomatis melalui emulator eksternal yang ada di HP
     * (Mendukung Sudachi, Citron, atau Yuzu)
     */
    fun launchGameIntent(context: Context, romPath: String): Boolean {
        val romFile = File(romPath)
        if (!romFile.exists()) return false

        val uri = Uri.fromFile(romFile)
        
        // Daftar paket emulator Switch populer di Android
        val supportedPackages = listOf(
            "org.sudachi.sudachi_emu",
            "org.citron.citron_emu",
            "org.yuzu.yuzu_emu"
        )

        for pkg in supportedPackages {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Coba paket emulator alternatif berikutnya jika tidak ditemukan
            }
        }

        // Fallback: Buka menggunakan pemilihan Intent umum di Android jika paket spesifik tidak ada
        try {
            val generalIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(generalIntent, "Pilih Emulator untuk Memainkan DreadOut 2"))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}