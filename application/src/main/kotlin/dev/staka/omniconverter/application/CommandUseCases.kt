package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Preset
import dev.staka.omniconverter.domain.model.RawCommand
import dev.staka.omniconverter.domain.port.EngineRegistry
import dev.staka.omniconverter.domain.port.PresetRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 上級モードで選べるエンジン。登録順に並ぶ */
class ListEngines(private val registry: EngineRegistry) {
    operator fun invoke(): List<EngineId> = registry.engines.map { it.id }
}

sealed interface CommandError {
    data object Empty : CommandError
    data object UnclosedQuote : CommandError
    data object MissingInput : CommandError
    data object MissingOutput : CommandError
    data object InvalidExtension : CommandError
}

/** 入力欄の文字列を検証して生コマンドにする */
object CommandLine {
    /** シェルと同じく空白で区切る。引用符で囲めば空白を含められる。展開や変数は扱わない */
    fun split(text: String): List<String>? {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var inArg = false
        for (c in text) {
            when {
                quote != null && c == quote -> quote = null
                quote != null -> current.append(c)
                c == '"' || c == '\'' -> { quote = c; inArg = true }
                c.isWhitespace() -> if (inArg) { args += current.toString(); current.clear(); inArg = false }
                else -> { current.append(c); inArg = true }
            }
        }
        if (quote != null) return null
        if (inArg) args += current.toString()
        return args
    }

    fun build(engine: EngineId, text: String, extension: String): Result<RawCommand, CommandError> {
        val args = split(text) ?: return Result.Err(CommandError.UnclosedQuote)
        val ext = extension.trim().removePrefix(".").lowercase()
        return when {
            args.isEmpty() -> Result.Err(CommandError.Empty)
            args.none { "{input}" in it } -> Result.Err(CommandError.MissingInput)
            args.none { "{output}" in it } -> Result.Err(CommandError.MissingOutput)
            !EXTENSION.matches(ext) -> Result.Err(CommandError.InvalidExtension)
            else -> Result.Ok(RawCommand(engine, args, ext))
        }
    }

    /**
     * 保存したコマンドを入力欄に戻す。空白や引用符を含む引数は、含まない方の引用符で囲む。
     * shortcut: 両方の引用符を含む引数は戻せない。必要になったらエスケープ記法を split に足す
     */
    fun join(args: List<String>): String = args.joinToString(" ") { arg ->
        val needsQuote = arg.isEmpty() || arg.any { it.isWhitespace() || it == '"' || it == '\'' }
        when {
            !needsQuote -> arg
            '"' !in arg -> "\"$arg\""
            else -> "'$arg'"
        }
    }

    private val EXTENSION = Regex("[a-z0-9]+(\\.[a-z0-9]+)?")
}

sealed interface Result<out T, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>
}

class ObservePresets(private val repository: PresetRepository) {
    operator fun invoke(): Flow<List<Preset>> = repository.observeAll()
}

class SavePreset(private val repository: PresetRepository) {
    suspend operator fun invoke(name: String, command: RawCommand) =
        repository.save(Preset(UUID.randomUUID().toString(), name.trim().ifEmpty { command.engine.value }, command))
}

class DeletePreset(private val repository: PresetRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}
