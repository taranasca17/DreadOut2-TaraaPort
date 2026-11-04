package id.taraaport.dreadout2.feature.analyzer.model

enum class CompatibilityTier(val label: String, val summary: String) {
    OPTIMAL(
        label = "Optimal",
        summary = "Perangkat memenuhi seluruh syarat ideal untuk menjalankan DreadOut 2 pada resolusi native dengan driver kustom."
    ),
    PLAYABLE(
        label = "Playable",
        summary = "Perangkat dapat menjalankan DreadOut 2 dengan lancar pada penyesuaian skala resolusi menengah."
    ),
    LIMITED(
        label = "Limited",
        summary = "Perangkat berada di batas minimum. Diperlukan resolusi rendah (0.5x) dan berpotensi mengalami glitch visual atau penurunan FPS."
    ),
    UNSUPPORTED(
        label = "Unsupported",
        summary = "Perangkat tidak memenuhi syarat minimum arsitektur, memori, atau Vulkan untuk memuat engine DreadOut 2."
    )
}

data class CompatibilityReport(
    val tier: CompatibilityTier,
    val score: Int,
    val reasons: List<String>,
    val warnings: List<String>,
    val blockers: List<String>
)