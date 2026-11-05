package id.taraaport.dreadout2.feature.analyzer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

object GameLauncher {
    fun launchGameIntent(context: Context, romPath: String): Boolean {
        val romFile = File(romPath)
        if (!romFile.exists()) {
            Toast.makeText(context, "Error: File ROM tidak ditemukan di path: $romPath", Toast.LENGTH_LONG).show()
            return false
        }

        val uri = Uri.fromFile(romFile)
        val supportedPackages = listOf(
            "org.sudachi.sudachi_emu",
            "org.citron.citron_emu",
            "org.yuzu.yuzu_emu"
        )

        for (pkg in supportedPackages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Berhasil memicu emulator: $pkg", Toast.LENGTH_SHORT).show()
                return true
            } catch (e: Exception) {
                // Lanjut coba paket berikutnya
            }
        }

        // Coba buka dengan chooser umum
        try {
            val generalIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(generalIntent, "Pilih Emulator Switch"))
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal total: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }

        return false
    }
}