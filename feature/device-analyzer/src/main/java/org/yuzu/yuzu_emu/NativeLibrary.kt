package org.yuzu.yuzu_emu

import android.content.Context
import android.view.Surface
import androidx.annotation.Keep
import java.io.File

@Keep
object NativeLibrary {
    var isEngineLoaded = false
        private set

    init {
        try {
            System.loadLibrary("c++_shared")
        } catch (_: UnsatisfiedLinkError) {}
        try {
            System.loadLibrary("adrenotools")
        } catch (_: UnsatisfiedLinkError) {}
        try {
            System.loadLibrary("yuzu-android")
            isEngineLoaded = true
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun initializeEnvironment(context: Context, keysFile: File?): String {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dirs = listOf("nand", "sdmc", "keys", "config", "shader", "log", "gpu_drivers")
        dirs.forEach { File(baseDir, it).mkdirs() }

        val targetKeys = File(baseDir, "keys/prod.keys")
        if (keysFile != null && keysFile.exists()) {
            keysFile.copyTo(targetKeys, overwrite = true)
        }

        return baseDir.absolutePath
    }

    // Tanda tangan JNI yang cocok persis dengan libyuzu-android.so
    @JvmStatic external fun setAppDirectory(directory: String)
    @JvmStatic external fun initializeSystem()
    @JvmStatic external fun initializeGpuDriver(path: String)
    @JvmStatic external fun initializeEmptyUserDirectory()
    @JvmStatic external fun reloadKeys(): Boolean
    @JvmStatic external fun areKeysPresent(): Boolean
    @JvmStatic external fun surfaceChanged(surface: Surface)
    @JvmStatic external fun surfaceDestroyed()
    @JvmStatic external fun run(path: String, programIndex: Int, frontendInitiated: Boolean)
    @JvmStatic external fun pauseEmulation()
    @JvmStatic external fun unpauseEmulation()
    @JvmStatic external fun stopEmulation()
    @JvmStatic external fun isRunning(): Boolean

    @Keep @JvmStatic fun exitEmulationActivity(resultCode: Int) {}
    @Keep @JvmStatic fun onEmulationStarted() {}
    @Keep @JvmStatic fun onEmulationStopped(status: Int) {}
    @Keep @JvmStatic fun updateProgress(name: String, written: Long, total: Long) {}
}