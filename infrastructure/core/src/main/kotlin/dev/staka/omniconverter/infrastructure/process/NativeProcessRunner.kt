package dev.staka.omniconverter.infrastructure.process

import dev.staka.omniconverter.domain.model.ConversionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.file.Path

data class ProcessResult(val exitCode: Int, val log: String)

/** 実行して、終了コードが 0 かつ出力が空でなければ成功とみなす。各エンジン共通の判定 */
suspend fun NativeProcessRunner.convert(
    command: List<String>,
    workDir: File,
    output: Path,
    environment: Map<String, String> = emptyMap(),
    onLine: (String) -> Unit = {},
): ConversionResult {
    val result = run(command, workDir, tempDirIn(workDir) + environment, onLine)
    return if (result.exitCode == 0 && output.toFile().length() > 0) {
        ConversionResult.Success(output, result.log)
    } else {
        ConversionResult.Failure(result.exitCode, result.log)
    }
}

/** Android には /tmp が無いので、作業ディレクトリを一時ディレクトリとして渡す */
private fun tempDirIn(workDir: File) = mapOf("TMPDIR" to workDir.path)

/** 同梱した実行ファイルを起動する。ADR-0004 */
class NativeProcessRunner(private val maxLogLines: Int = 500) {

    /**
     * 標準出力と標準エラーをまとめて 1 行ずつ [onLine] に渡す。
     * 読み取りはブロックするので別のコルーチンで行い、呼び出し元がキャンセルされたらプロセスを強制終了して読み取りを終わらせる。
     */
    suspend fun run(
        command: List<String>,
        workDir: File,
        environment: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): ProcessResult = coroutineScope {
        val process = start(command, workDir, environment)
        val output = async(Dispatchers.IO) { collectOutput(process, onLine) }
        try {
            output.await()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun start(command: List<String>, workDir: File, environment: Map<String, String>): Process =
        ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
            .also { it.outputStream.close() }

    /** 出力を読み切って末尾の [maxLogLines] 行を残す。強制終了でストリームが閉じられたら、失敗ではなくキャンセルとして伝える */
    private fun CoroutineScope.collectOutput(process: Process, onLine: (String) -> Unit): ProcessResult {
        val tail = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    tail.addKeepingLast(line, maxLogLines)
                    onLine(line)
                }
            }
        } catch (e: IOException) {
            ensureActive()
            throw e
        }
        return ProcessResult(process.waitFor(), tail.joinToString("\n"))
    }

    private fun ArrayDeque<String>.addKeepingLast(line: String, max: Int) {
        if (size == max) removeFirst()
        addLast(line)
    }
}
