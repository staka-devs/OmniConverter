package dev.staka.omniconverter.presentation

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.staka.omniconverter.domain.model.ConversionJob
import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import dev.staka.omniconverter.domain.model.JobStatus
import dev.staka.omniconverter.presentation.convert.ConvertScreen
import dev.staka.omniconverter.presentation.convert.ConvertUiState
import dev.staka.omniconverter.presentation.queue.QueueScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun ファイル未選択ではファイル選択だけを出す() {
        compose.setContent { ConvertScreen(ConvertUiState(), {}, {}, {}, {}) }
        compose.onNodeWithText(str(R.string.pick_files)).assertExists()
        compose.onNodeWithText(str(R.string.tab_convert)).assertDoesNotExist()
    }

    @Test
    fun 形式を選ぶまで変換ボタンは押せない() {
        val files = listOf(FileRef("u", "a.png"))
        val targets = listOf(Formats.of("jpg"), Formats.of("webp"), Formats.of("pdf"))
        var picked: String? = null
        compose.setContent { ConvertScreen(ConvertUiState(files, targets), {}, { picked = it.id }, {}, {}) }

        compose.onNodeWithText(str(R.string.file_count, 1)).assertExists()
        compose.onNodeWithText(str(R.string.category_image)).assertExists()
        compose.onNodeWithText(str(R.string.tab_convert)).assertIsNotEnabled()
        compose.onNodeWithText("WEBP").performClick()
        assertEquals("webp", picked)
    }

    @Test
    fun 選んだ形式が変換ボタンに出る() {
        val state = ConvertUiState(listOf(FileRef("u", "a.png")), listOf(Formats.of("jpg")), Formats.of("jpg"))
        var converted = false
        compose.setContent { ConvertScreen(state, {}, {}, {}, { converted = true }) }
        compose.onNodeWithText(str(R.string.convert_to, "JPG")).assertIsEnabled().performClick()
        assertEquals(true, converted)
    }

    @Test
    fun キューは状態ごとの結果を出す() {
        val src = FileRef("u", "movie.mp4")
        val jobs = listOf(
            ConversionJob("1", src, Formats.of("mp3"), status = JobStatus.RUNNING, progress = 0.42f),
            ConversionJob("2", src, Formats.of("mp3"), status = JobStatus.SUCCEEDED, output = FileRef("o", "movie.mp3")),
            ConversionJob("3", src, Formats.of("mp3"), status = JobStatus.FAILED, error = "log\nInvalid data found"),
        )
        var cancelled: String? = null
        compose.setContent { QueueScreen(jobs, { cancelled = it }, {}) }

        compose.onNodeWithText("42%", substring = true).assertExists()
        compose.onNodeWithText("movie.mp3", substring = true).assertExists()
        compose.onNodeWithText("Invalid data found", substring = true).assertExists()
        compose.onNodeWithContentDescription(str(R.string.cancel)).performClick()
        assertEquals("1", cancelled)
    }

    @Test
    fun キューが空なら空であることだけを出す() {
        compose.setContent { QueueScreen(emptyList(), {}, {}) }
        compose.onNodeWithText(str(R.string.queue_empty)).assertExists()
        compose.onNodeWithContentDescription(str(R.string.clear_finished)).assertIsNotEnabled()
    }

    private fun commandScreen(
        state: dev.staka.omniconverter.presentation.command.CommandUiState,
        presets: List<dev.staka.omniconverter.domain.model.Preset> = emptyList(),
        onApply: (dev.staka.omniconverter.domain.model.Preset) -> Unit = {},
        onDelete: (dev.staka.omniconverter.domain.model.Preset) -> Unit = {},
    ) = compose.setContent {
        dev.staka.omniconverter.presentation.command.CommandScreen(state, presets, {}, {}, {}, {}, onApply, {}, onDelete, {})
    }

    @Test
    fun コマンドの誤りを入力欄の下に出す() {
        val engine = dev.staka.omniconverter.domain.model.EngineId("ffmpeg")
        commandScreen(
            dev.staka.omniconverter.presentation.command.CommandUiState(
                engines = listOf(engine), engine = engine, text = "-i x",
                error = dev.staka.omniconverter.application.CommandError.MissingInput,
            ),
        )
        compose.onNodeWithText(str(R.string.error_input)).assertExists()
        // ファイルを選ぶまでは実行できない
        compose.onNodeWithText(str(R.string.run)).assertIsNotEnabled()
    }

    @Test
    fun プリセットを押すと呼び出し削除アイコンで消す() {
        val engine = dev.staka.omniconverter.domain.model.EngineId("ffmpeg")
        val preset = dev.staka.omniconverter.domain.model.Preset(
            "p", "音声だけ", dev.staka.omniconverter.domain.model.RawCommand(engine, listOf("{input}", "{output}"), "mp3"),
        )
        var applied: String? = null
        var deleted: String? = null
        commandScreen(
            dev.staka.omniconverter.presentation.command.CommandUiState(engines = listOf(engine), engine = engine),
            listOf(preset), { applied = it.id }, { deleted = it.id },
        )
        compose.onNodeWithText("音声だけ").performClick()
        compose.onNodeWithContentDescription(str(R.string.delete_preset, "音声だけ")).performClick()
        assertEquals("p", applied)
        assertEquals("p", deleted)
    }
}
