package id.taraaport.dreadout2.feature.analyzer.data

import android.content.Context
import id.taraaport.dreadout2.feature.analyzer.model.AnisotropicLevel
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import id.taraaport.dreadout2.feature.analyzer.model.ResolutionScale
import java.io.File

class GraphicsConfigRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("taraaport_graphics_prefs", Context.MODE_PRIVATE)
    private val configFile = File(context.filesDir, "config/taraaport_config.ini")

    fun saveConfig(config: GraphicsConfig) {
        prefs.edit().apply {
            putString("resolution_scale", config.resolutionScale.name)
            putInt("target_fps", config.targetFpsLimit)
            putBoolean("vsync", config.vsyncEnabled)
            putBoolean("async_shaders", config.asyncShaderCompilation)
            putBoolean("disk_cache", config.diskShaderCache)
            putBoolean("force_max_clocks", config.forceMaxGpuClocks)
            putString("anisotropic", config.anisotropicFiltering.name)
            putBoolean("is_auto", config.isAutoRecommended)
            apply()
        }

        // Tulis langsung ke file .ini untuk dikonsumsi C++ Emulator Core
        configFile.parentFile?.mkdirs()
        configFile.writeText(config.toIniPayload())
    }

    fun loadConfigOrDefault(fallback: GraphicsConfig): GraphicsConfig {
        if (!prefs.contains("resolution_scale")) {
            saveConfig(fallback)
            return fallback
        }

        val resName = prefs.getString("resolution_scale", fallback.resolutionScale.name)
        val anisoName = prefs.getString("anisotropic", fallback.anisotropicFiltering.name)

        return GraphicsConfig(
            resolutionScale = ResolutionScale.entries.find { it.name == resName } ?: fallback.resolutionScale,
            targetFpsLimit = prefs.getInt("target_fps", fallback.targetFpsLimit),
            vsyncEnabled = prefs.getBoolean("vsync", fallback.vsyncEnabled),
            asyncShaderCompilation = prefs.getBoolean("async_shaders", fallback.asyncShaderCompilation),
            diskShaderCache = prefs.getBoolean("disk_cache", fallback.diskShaderCache),
            forceMaxGpuClocks = prefs.getBoolean("force_max_clocks", fallback.forceMaxGpuClocks),
            anisotropicFiltering = AnisotropicLevel.entries.find { it.name == anisoName } ?: fallback.anisotropicFiltering,
            isAutoRecommended = prefs.getBoolean("is_auto", true)
        )
    }

    fun getIniFilePath(): String = configFile.absolutePath
}