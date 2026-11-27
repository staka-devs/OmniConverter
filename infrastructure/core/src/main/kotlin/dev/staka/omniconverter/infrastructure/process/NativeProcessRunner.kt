package dev.staka.omniconverter.infrastructure.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

data class ProcessResult(val exitCode: Int, val log: String)

/** 同梱した実行ファイルを起動する。呼び出し元がキャンセルされたらプロセスを強制終了する。ADR-0004 */
class NativeProcessRunner(private val maxLogLines: Int = 500) {

    suspend fun run(
        command: List<String>,
        workDir: File,
        environment: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): ProcessResult = coroutineScope {
        val process = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
        process.outputStream.close()

        // 読み取りはブロックするので別コルーチンに分け、キャンセル時はプロセスを殺して読み取りを終わらせる
        val reader = async(Dispatchers.IO) {
            val log = ArrayDeque<String>()
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (log.size == maxLogLines) log.removeFirst()
                        log.addLast(line)
                        onLine(line)
                    }
                }
            } catch (e: IOException) {
                // 強制終了でストリームが閉じられた場合は、失敗ではなくキャンセルとして伝える
                ensureActive()
                throw e
            }
            ProcessResult(process.waitFor(), log.joinToString("\n"))
        }
        try {
            reader.await()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
