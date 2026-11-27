package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.EngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandLineTest {
    private val ffmpeg = EngineId("ffmpeg")

    @Test
    fun 空白で区切り引用符の中は一つの引数にする() {
        assertEquals(
            listOf("-i", "{input}", "-vf", "scale=640:-2, fps=30", "-metadata", "title=", "{output}"),
            CommandLine.split("""  -i {input} -vf "scale=640:-2, fps=30"  -metadata title='' {output} """),
        )
        assertEquals(listOf("a\"b"), CommandLine.split("""'a"b'"""))
    }

    @Test
    fun 閉じていない引用符はエラー() {
        assertNull(CommandLine.split("-i \"{input}"))
        assertEquals(Result.Err(CommandError.UnclosedQuote), CommandLine.build(ffmpeg, "-i '{input}", "mp3"))
    }

    @Test
    fun 入力と出力の置き場所が必要() {
        assertEquals(Result.Err(CommandError.Empty), CommandLine.build(ffmpeg, "   ", "mp3"))
        assertEquals(Result.Err(CommandError.MissingInput), CommandLine.build(ffmpeg, "-i x {output}", "mp3"))
        assertEquals(Result.Err(CommandError.MissingOutput), CommandLine.build(ffmpeg, "-i {input} out.mp3", "mp3"))
    }

    @Test
    fun 拡張子は英数字だけを受け付ける() {
        assertEquals(Result.Err(CommandError.InvalidExtension), CommandLine.build(ffmpeg, "{input} {output}", "../x"))
        assertEquals(Result.Err(CommandError.InvalidExtension), CommandLine.build(ffmpeg, "{input} {output}", ""))
        val ok = CommandLine.build(ffmpeg, "-i {input} {output}", ".TAR.GZ") as Result.Ok
        assertEquals("tar.gz", ok.value.outputExtension)
    }

    @Test
    fun 入力欄に戻しても同じ引数になる() {
        val args = listOf("-i", "{input}", "-vf", "scale=640:-2, fps=30", "", "a\"b", "it's", "{output}")
        assertEquals(args, CommandLine.split(CommandLine.join(args)))
    }
}
