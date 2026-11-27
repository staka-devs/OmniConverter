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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

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
        val source = createDownload("$name.txt", "hello")
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(source)))

        compose.onNodeWithText(context.getString(R.string.pick_files)).performClick()
        compose.onNodeWithText(context.getString(R.string.file_count, 1)).assertExists()
        compose.onNodeWithText("MD").performClick()
        compose.onNodeWithText(context.getString(R.string.convert_to, "MD")).performClick()

        // 完了するとキューに出力ファイル名が出る
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasText("$name.md", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val output = findDownload("$name.md").also { created += it }
        assertEquals("HELLO", context.contentResolver.openInputStream(output)!!.use { it.readBytes().decodeToString() })
    }

    private fun createDownload(displayName: String, text: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
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
