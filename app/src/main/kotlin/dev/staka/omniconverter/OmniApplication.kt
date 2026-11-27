package dev.staka.omniconverter

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Configuration
import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.engine.ffmpeg.FfmpegEngine
import dev.staka.omniconverter.engine.imagemagick.ImageMagickEngine
import dev.staka.omniconverter.engine.libarchive.LibarchiveEngine
import dev.staka.omniconverter.engine.libheif.LibheifEngine
import dev.staka.omniconverter.engine.mupdf.MupdfEngine
import dev.staka.omniconverter.engine.pandoc.PandocEngine
import dev.staka.omniconverter.infrastructure.process.BinaryLocator
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.presentation.OmniApp
import dev.staka.omniconverter.presentation.convert.sharedFiles

open class OmniApplication : Application(), Configuration.Provider {
    val container by lazy { AppContainer(this, engines()) }

    /** 並び順がエンジンの優先順になる。E2E テストはここにテスト用エンジンを足す */
    protected open fun engines(): List<ConversionEngine> {
        val locator = BinaryLocator(this)
        val runner = NativeProcessRunner()
        return listOf(
            FfmpegEngine({ locator.locate("ffmpeg") }, runner),
            LibheifEngine(locator::locate, runner),
            ImageMagickEngine({ locator.locate("magick") }, runner),
            LibarchiveEngine({ locator.locate("bsdtar") }, runner),
            MupdfEngine({ locator.locate("mutool") }, runner),
            PandocEngine({ locator.locate("pandoc") }, runner),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()
}

class MainActivity : ComponentActivity() {
    /** 共有で受け取ったファイル。画面の作り直しで二重に受け取らないよう、初回の起動だけ読む */
    private var shared by mutableStateOf(emptyList<FileRef>())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shared = sharedFiles(intent)
        val useCases = (application as OmniApplication).container.useCases
        setContent { OmniApp(useCases, shared) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedFiles(intent).takeIf { it.isNotEmpty() }?.let { shared = it }
    }
}
