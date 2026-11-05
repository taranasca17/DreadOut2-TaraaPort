package id.taraaport.dreadout2.feature.analyzer.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.yuzu.yuzu_emu.NativeLibrary

@Composable
fun YuzuGameSurface(
    romPath: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        // Tidak ada aksi khusus, tunggu perubahan ukuran/permukaan
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        try {
                            if (NativeLibrary.isEngineLoaded) {
                                // 1. Kirim surface layar Vulkan ke core C++
                                NativeLibrary.surfaceChanged(holder.surface)
                                
                                // 2. Jalankan ROM DreadOut 2 (.nsp)
                                if (romPath.isNotBlank()) {
                                    NativeLibrary.run(romPath, 0, true)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        try {
                            NativeLibrary.surfaceDestroyed()
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    )
}