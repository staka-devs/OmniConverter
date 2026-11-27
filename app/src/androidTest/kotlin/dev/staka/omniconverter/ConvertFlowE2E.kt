package dev.staka.omniconverter

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.presentation.R
import org.junit.After
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ファイル選択から変換、キューでの完了表示、出力ファイルの中身までを利用者の操作どおりに確かめる */
@RunWith(AndroidJUnit4::class)
class ConvertFlowE2E {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<Uri>()
    private val name = "e2e-${System.nanoTime()}"

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        created.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun ファイルを選んで変換し結果を受け取る() {
        val source = createDownload("$name.txt", "hello".toByteArray())
        val output = convertViaUi(source, "MD", "$name.md")
        assertEquals("HELLO", context.contentResolver.openInputStream(output)!!.use { it.readBytes().decodeToString() })
    }

    @Test
    fun 同梱したffmpegで動画から音声を取り出す() {
        val source = createDownload("$name.mp4", sampleVideo())
        val output = convertViaUi(source, "MP3", "$name.mp3")
        val head = context.contentResolver.openInputStream(output)!!.use { s -> ByteArray(3).also { s.read(it) } }
        // MP3 は ID3 タグかフレーム同期で始まる
        assertTrue(head.decodeToString() == "ID3" || head[0] == 0xFF.toByte(), "MP3 ではありません")
    }

    @Test
    fun 同梱したlibheifで写真をHEICにする() {
        val png = java.io.ByteArrayOutputStream().also { out ->
            android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.BLUE) }
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        val output = convertViaUi(createDownload("$name.png", png), "HEIC", "$name.heic")
        val head = context.contentResolver.openInputStream(output)!!.use { s -> ByteArray(12).also { s.read(it) } }
        // HEIF は ftyp ボックスで始まる
        assertEquals("ftyp", head.copyOfRange(4, 8).decodeToString())
    }

    /** 利用者と同じ操作で変換し、キューに完了が出たら出力ファイルを返す */
    private fun convertViaUi(source: Uri, target: String, outputName: String): Uri {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(source)))

        compose.onNodeWithText(context.getString(R.string.pick_files)).performClick()
        compose.onNodeWithText(context.getString(R.string.file_count, 1)).assertExists()
        compose.onNodeWithText(target).performClick()
        compose.onNodeWithText(context.getString(R.string.convert_to, target)).performClick()

        // 完了するとキューに出力ファイル名が出る
        compose.waitUntil(timeoutMillis = 120_000) {
            compose.onAllNodes(hasText(outputName, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        return findDownload(outputName).also { created += it }
    }

    /** 同梱した ffmpeg で 1 秒の動画を作る */
    private fun sampleVideo(): ByteArray {
        val out = File(context.cacheDir, "$name-sample.mp4")
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val process = ProcessBuilder(
            ffmpeg.path, "-y", "-f", "lavfi", "-i", "testsrc=duration=1:size=160x120:rate=10",
            "-f", "lavfi", "-i", "sine=duration=1", "-c:v", "libx264", "-c:a", "aac", "-shortest", out.path,
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { log }
        return out.readBytes().also { out.delete() }
    }

    private fun createDownload(displayName: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri.also { created += it }
    }

    private fun findDownload(displayName: String): Uri =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )!!.use { c ->
            check(c.moveToFirst()) { "出力が見つかりません: $displayName" }
            Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
        }
}
