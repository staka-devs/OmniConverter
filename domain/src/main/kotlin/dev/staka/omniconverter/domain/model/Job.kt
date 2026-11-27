package dev.staka.omniconverter.domain.model

/** 利用者の端末上のファイル。Android では SAF の URI を持つ */
data class FileRef(val uri: String, val name: String)

enum class JobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** 上級モードの生コマンド。引数中の {input} と {output} は実ファイルに置き換える */
data class RawCommand(val engine: EngineId, val args: List<String>, val outputExtension: String)

/** 一括変換の 1 件。[rawCommand] があれば [target] より優先する */
data class ConversionJob(
    val id: String,
    val source: FileRef,
    val target: Format?,
    val options: Map<String, String> = emptyMap(),
    val rawCommand: RawCommand? = null,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float? = null,
    val output: FileRef? = null,
    val error: String? = null,
) {
    init {
        require(target != null || rawCommand != null) { "変換先か生コマンドのどちらかが必要です" }
    }
}
