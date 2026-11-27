package dev.staka.omniconverter

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Capability
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.EngineId
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.Progress
import java.nio.file.Files
import java.nio.file.Path

class E2ETestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, E2EApplication::class.java.name, context)
}

/** 実エンジンの無い変換経路を、テスト用エンジンで端から端まで通す */
class E2EApplication : OmniApplication() {
    override fun engines(): List<ConversionEngine> = listOf(UpperCaseEngine())
}

class UpperCaseEngine : ConversionEngine {
    override val id = EngineId("upper")
    override val capabilities = setOf(Capability(Formats.of("txt"), Formats.of("md")))

    override suspend fun convert(task: ConversionTask, onProgress: (Progress) -> Unit): ConversionResult {
        Files.writeString(task.output, Files.readString(task.input).uppercase())
        return ConversionResult.Success(task.output, "")
    }

    override suspend fun runRaw(args: List<String>, workDir: Path, output: Path, onProgress: (Progress) -> Unit) =
        ConversionResult.Failure(null, "unsupported")
}
