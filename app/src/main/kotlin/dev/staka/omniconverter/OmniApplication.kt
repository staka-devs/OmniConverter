package dev.staka.omniconverter

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.Configuration
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.engine.ffmpeg.FfmpegEngine
import dev.staka.omniconverter.infrastructure.process.BinaryLocator
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import dev.staka.omniconverter.presentation.OmniApp

open class OmniApplication : Application(), Configuration.Provider {
    val container by lazy { AppContainer(this, engines()) }

    /** 並び順がエンジンの優先順になる。E2E テストはここにテスト用エンジンを足す */
    protected open fun engines(): List<ConversionEngine> {
        val locator = BinaryLocator(this)
        val runner = NativeProcessRunner()
        return listOf(
            FfmpegEngine({ locator.locate("ffmpeg") }, runner),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val useCases = (application as OmniApplication).container.useCases
        setContent { OmniApp(useCases) }
    }
}
