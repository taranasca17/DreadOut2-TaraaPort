package id.taraaport.dreadout2.feature.analyzer.model

data class SaveDataInfo(
    val titleId: String = "0100626015258000",
    val hasSaveData: Boolean,
    val totalFilesCount: Int,
    val totalSizeBytes: Long,
    val lastModifiedFormatted: String,
    val internalSavePath: String
)

data class ProdKeysInfo(
    val isInstalled: Boolean,
    val validKeyCount: Int,
    val keyFilePath: String
)

data class SaveManagerUiState(
    val saveInfo: SaveDataInfo,
    val keysInfo: ProdKeysInfo,
    val statusMessage: String? = null,
    val isErrorStatus: Boolean = false
)