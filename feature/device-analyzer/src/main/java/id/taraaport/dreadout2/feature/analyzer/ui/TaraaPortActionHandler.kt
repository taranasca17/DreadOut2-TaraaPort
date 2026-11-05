package id.taraaport.dreadout2.feature.analyzer.ui

import android.content.Context
import android.widget.Toast

object TaraaPortActionHandler {
    fun handleLaunchButton(context: Context, romPath: String) {
        if (romPath.isBlank()) {
            Toast.makeText(context, "File NSP DreadOut 2 belum dipilih!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Mempersiapkan atmosfer DreadOut 2...", Toast.LENGTH_SHORT).show()
        val success = GameLauncher.launchGameIntent(context, romPath)
        
        if (!success) {
            Toast.makeText(context, "Emulator Switch tidak ditemukan di perangkat!", Toast.LENGTH_LONG).show()
        }
    }
}