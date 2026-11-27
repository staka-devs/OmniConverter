package dev.staka.omniconverter.engine.libarchive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.domain.model.ConversionResult
import dev.staka.omniconverter.domain.model.ConversionTask
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.infrastructure.process.BinaryLocator
import dev.staka.omniconverter.infrastructure.process.NativeProcessRunner
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.fail

/** 本物の bsdtar で zip を各形式へ詰め替え、中身の一覧が変わらないことを確かめる */
@RunWith(AndroidJUnit4::class)
class LibarchiveDeviceIT {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "archive-it").apply { mkdirs() }
    private val bsdtar = BinaryLocator(context).locate("bsdtar")
    private val engine = LibarchiveEngine({ bsdtar }, NativeProcessRunner())
    private val entries = listOf("readme.txt", "docs/日本語.md")

    private fun sampleZip(): File = File(dir, "sample.zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("中身 $name".toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun list(archive: File): List<String> = runBlocking {
        NativeProcessRunner().run(listOf(bsdtar.path, "-tf", archive.path), dir).log.lines()
            .filter { it.isNotBlank() && !it.endsWith("/") }.sorted()
    }

    @Test
    fun zipを各形式へ詰め替えても中身が変わらない() {
        val zip = sampleZip()
        for (to in listOf("tar", "tar.gz", "tar.xz", "tar.bz2", "tar.zst", "7z")) {
            val out = File(dir, "out.$to").apply { delete() }
            val result = runBlocking {
                engine.convert(ConversionTask(zip.toPath(), out.toPath(), Formats.of("zip"), Formats.of(to))) {}
            }
            if (result is ConversionResult.Failure) fail("zip → $to が失敗: ${result.log}")
            assertEquals(entries.sorted(), list(out), "zip → $to")
        }
    }
}
