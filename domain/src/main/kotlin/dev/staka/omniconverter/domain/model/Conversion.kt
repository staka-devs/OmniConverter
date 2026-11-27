package dev.staka.omniconverter.domain.model

import java.nio.file.Path

@JvmInline
value class EngineId(val value: String)

/** エンジンが受け付ける追加指定。[choices] が空なら自由入力 */
data class OptionSpec(
    val key: String,
    val label: String,
    val default: String,
    val choices: List<String> = emptyList(),
)

/** エンジンが [from] から [to] へ変換できることの宣言 */
data class Capability(
    val from: Format,
    val to: Format,
    val options: List<OptionSpec> = emptyList(),
)

/** 作業領域にあるファイルに対する 1 回の変換 */
data class ConversionTask(
    val input: Path,
    val output: Path,
    val from: Format,
    val to: Format,
    val options: Map<String, String> = emptyMap(),
)

/** [fraction] が null のときは進み具合が不明 */
data class Progress(val fraction: Float?, val message: String? = null)

sealed interface ConversionResult {
    val log: String

    data class Success(val output: Path, override val log: String) : ConversionResult
    data class Failure(val exitCode: Int?, override val log: String) : ConversionResult
}
