package id.taraaport.dreadout2.feature.analyzer.model

enum class ResolutionScale(val multiplier: Float, val label: String) {
    RES_0_5X(0.5f, "0.5x (360p - Performa Maksimal)"),
    RES_0_75X(0.75f, "0.75x (540p - Seimbang)"),
    RES_1_0X(1.0f, "1.0x (720p - Native Switch Handheld)"),
    RES_1_5X(1.5f, "1.5x (1080p - Docked HD)")
}

enum class AnisotropicLevel(val value: Int, val label: String) {
    OFF(0, "Mati (Default)"),
    X2(2, "2x"),
    X4(4, "4x"),
    X8(8, "8x"),
    X16(16, "16x")
}

data class GraphicsConfig(
    val resolutionScale: ResolutionScale,
    val targetFpsLimit: Int,
    val vsyncEnabled: Boolean,
    val asyncShaderCompilation: Boolean,
    val diskShaderCache: Boolean,
    val forceMaxGpuClocks: Boolean,
    val anisotropicFiltering: AnisotropicLevel,
    val isAutoRecommended: Boolean
) {
    fun toIniPayload(): String = buildString {
        appendLine("[Renderer]")
        appendLine("backend = 1") // 1 = Vulkan
        appendLine("resolution_setup = ${resolutionScale.name}")
        appendLine("scaling_multiplier = ${resolutionScale.multiplier}")
        appendLine("speed_limit = ${if (targetFpsLimit == 60) 200 else 100}")
        appendLine("target_fps = $targetFpsLimit")
        appendLine("use_vsync = ${if (vsyncEnabled) 1 else 0}")
        appendLine("use_asynchronous_shaders = ${if (asyncShaderCompilation) 1 else 0}")
        appendLine("use_disk_shader_cache = ${if (diskShaderCache) 1 else 0}")
        appendLine("force_max_clock = ${if (forceMaxGpuClocks) 1 else 0}")
        appendLine("max_anisotropy = ${anisotropicFiltering.value}")
    }
}