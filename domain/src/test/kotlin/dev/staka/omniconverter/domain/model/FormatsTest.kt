package dev.staka.omniconverter.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FormatsTest {
    @Test
    fun 拡張子から形式を判定する() {
        assertEquals("jpg", Formats.detect("IMG_0001.JPEG")?.id)
        assertEquals("heic", Formats.detect("photo.heif")?.id)
    }

    @Test
    fun 複合拡張子を優先する() {
        assertEquals("tar.gz", Formats.detect("backup.tar.gz")?.id)
        assertEquals("tar", Formats.detect("backup.tar")?.id)
    }

    @Test
    fun 未知の拡張子はnull() {
        assertNull(Formats.detect("readme"))
    }

    @Test
    fun 形式IDと拡張子は重複しない() {
        assertEquals(Formats.all.size, Formats.all.map { it.id }.toSet().size)
        assertEquals(Formats.all.sumOf { it.extensions.size }, Formats.all.flatMap { it.extensions }.toSet().size)
    }

    @Test
    fun ジョブは変換先か生コマンドが必要() {
        assertFailsWith<IllegalArgumentException> { ConversionJob("1", FileRef("u", "a.png"), target = null) }
    }
}
