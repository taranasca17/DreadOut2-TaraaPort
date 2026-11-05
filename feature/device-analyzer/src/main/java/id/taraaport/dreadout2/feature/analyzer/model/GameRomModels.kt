package id.taraaport.dreadout2.feature.analyzer.model

enum class RomFormat(val label: String) {
    NSP("Nintendo Submission Package (NSP / PFS0)"),
    XCI("NX Card Image (XCI / HEAD)"),
    UNKNOWN("Format Tidak Dikenali")
}

data class GameRomInfo(
    val isLoaded: Boolean = false,
    val fileName: String = "Belum dipilih",
    val format: RomFormat = RomFormat.UNKNOWN,
    val fileSizeFormatted: String = "0 MB",
    val detectedTitleId: String = "-",
    val isDreadOut2Verified: Boolean = false,
    val uriString: String = ""
)

data class LaunchReadinessState(
    val romInfo: GameRomInfo = GameRomInfo(),
    val isHardwareSupported: Boolean = false,
    val isKeysReady: Boolean = false,
    val isRomReady: Boolean = false,
    val activeDriverLabel: String = "System Default",
    val bootManifestPath: String? = null,
    val statusMessage: String? = null,
    val isErrorStatus: Boolean = false
) {
    val canLaunchGame: Boolean
        get() = isHardwareSupported && isKeysReady && isRomReady
}