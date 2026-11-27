package dev.staka.omniconverter

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.presentation.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 他のアプリから共有されたファイルが、変換画面に入り変換まで進むことを確かめる */
@RunWith(AndroidJUnit4::class)
class ShareFlowE2E {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "share-${System.nanoTime()}"
    private val created = mutableListOf<Uri>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        created.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun 共有されたファイルを変換する() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.txt")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val source = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!.also { created += it }
        context.contentResolver.openOutputStream(source)!!.use { it.write("shared".toByteArray()) }

        val intent = Intent(Intent.ACTION_SEND)
            .setClass(context, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, source)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scenario = ActivityScenario.launch(intent)

        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.file_count, 1))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("MD").performClick()
        compose.onNodeWithText(context.getString(R.string.convert_to, "MD")).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("$name.md", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$name.md"), null,
        )!!.use { c ->
            check(c.moveToFirst()) { "出力がありません" }
            created += Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
        }
    }
}
