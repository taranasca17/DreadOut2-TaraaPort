package id.taraaport.dreadout2.feature.analyzer.ui

import android.content.Context
import android.widget.Toast

object TaraaPortActionHandler {
    fun handleLaunchButton(context: Context, romPath: String) {
        if (romPath.isBlank()) {
            Toast.makeText(context, "File NSP belum dipilih!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Mempersiapkan peluncur...", Toast.LENGTH_SHORT).show()
        val success = GameLauncher.launchGameIntent(context, romPath)
        
        if (!success) {
            Toast.makeText(context, "Emulator Switch tidak ditemukan!", Toast.LENGTH_LONG).show()
        }
    }
}