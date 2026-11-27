package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.model.FileRef
import dev.staka.omniconverter.domain.model.Formats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversionPlannerTest {
    private val pandoc = FakeEngine("pandoc", "md>html", "md>docx")
    private val mupdf = FakeEngine("mupdf", "html>pdf", "pdf>png")
    private val planner = ConversionPlanner(FakeRegistry(listOf(pandoc, mupdf)))

    private fun ids(from: String, to: String) =
        planner.plan(Formats.of(from), Formats.of(to))?.steps?.map { "${it.engine.id.value}:${it.from.id}>${it.to.id}" }

    @Test
    fun 直接変換できる経路を選ぶ() {
        assertEquals(listOf("pandoc:md>html"), ids("md", "html"))
    }

    @Test
    fun 直接変換が無ければ2段でつなぐ() {
        assertEquals(listOf("pandoc:md>html", "mupdf:html>pdf"), ids("md", "pdf"))
    }

    @Test
    fun 三段以上は探さない() {
        assertNull(ids("md", "png"))
    }

    @Test
    fun 先に登録したエンジンを優先する() {
        val a = FakeEngine("a", "png>jpg")
        val b = FakeEngine("b", "png>jpg")
        val p = ConversionPlanner(FakeRegistry(listOf(a, b)))
        assertEquals("a", p.plan(Formats.of("png"), Formats.of("jpg"))!!.steps.single().engine.id.value)
    }

    @Test
    fun 到達できる形式を列挙する() {
        assertEquals(setOf("html", "docx", "pdf"), planner.targets(Formats.of("md")).map { it.id }.toSet())
    }

    @Test
    fun 複数ファイルでは共通の変換先だけを出す() {
        val targets = GetAvailableTargets(planner)(listOf(FileRef("1", "a.md"), FileRef("2", "b.html")))
        assertEquals(listOf("pdf"), targets.map { it.id })
    }

    @Test
    fun 判定できないファイルがあれば変換先は無い() {
        assertEquals(emptyList(), GetAvailableTargets(planner)(listOf(FileRef("1", "a.md"), FileRef("2", "noext"))))
    }
}
