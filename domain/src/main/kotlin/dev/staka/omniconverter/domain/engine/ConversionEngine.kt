package dev.staka.omniconverter.domain.engine

import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Progress
import java.nio.file.Path

/** すべての変換エンジンが実装する共通 API。ADR-0003 */
interface ConversionEngine {
    val id: EngineId
    val capabilities: Set<Capability>

    /** 呼び出し元のコルーチンがキャンセルされたら、実行中の処理も止める */
    suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult

    /** 上級モード用。[args] をそのままエンジンへ渡し、[output] を結果とする */
    suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit): ConversionResult
}
