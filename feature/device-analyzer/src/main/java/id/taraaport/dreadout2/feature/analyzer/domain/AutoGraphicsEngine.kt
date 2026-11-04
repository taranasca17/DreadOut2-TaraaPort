package id.taraaport.dreadout2.feature.analyzer.domain

import id.taraaport.dreadout2.feature.analyzer.model.AnisotropicLevel
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityReport
import id.taraaport.dreadout2.feature.analyzer.model.CompatibilityTier
import id.taraaport.dreadout2.feature.analyzer.model.DeviceSpec
import id.taraaport.dreadout2.feature.analyzer.model.GraphicsConfig
import id.taraaport.dreadout2.feature.analyzer.model.ResolutionScale

object AutoGraphicsEngine {

    fun generateRecommendedConfig(
        spec: DeviceSpec,
        report: CompatibilityReport
    ): GraphicsConfig {
        val isThermalHot = spec.batteryTempCelsius >= 41.0f

        return when (report.tier) {
            CompatibilityTier.OPTIMAL -> GraphicsConfig(
                resolutionScale = if (isThermalHot) ResolutionScale.RES_0_75X else ResolutionScale.RES_1_0X,
                targetFpsLimit = if (isThermalHot) 30 else 60,
                vsyncEnabled = true,
                asyncShaderCompilation = true,
                diskShaderCache = true,
                forceMaxGpuClocks = false,
                anisotropicFiltering = AnisotropicLevel.X8,
                isAutoRecommended = true
            )

            CompatibilityTier.PLAYABLE -> GraphicsConfig(
                resolutionScale = ResolutionScale.RES_0_75X,
                targetFpsLimit = 30,
                vsyncEnabled = true,
                asyncShaderCompilation = true,
                diskShaderCache = true,
                forceMaxGpuClocks = false,
                anisotropicFiltering = AnisotropicLevel.X4,
                isAutoRecommended = true
            )

            CompatibilityTier.LIMITED, CompatibilityTier.UNSUPPORTED -> GraphicsConfig(
                resolutionScale = ResolutionScale.RES_0_5X,
                targetFpsLimit = 30,
                vsyncEnabled = false,
                asyncShaderCompilation = true,
                diskShaderCache = true,
                forceMaxGpuClocks = spec.gpuInfo.supportsCustomTurnipDriver && !isThermalHot,
                anisotropicFiltering = AnisotropicLevel.OFF,
                isAutoRecommended = true
            )
        }
    }
}