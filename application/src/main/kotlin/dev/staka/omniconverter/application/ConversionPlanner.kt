package dev.staka.omniconverter.application

import dev.staka.omniconverter.domain.engine.ConversionEngine
import dev.staka.omniconverter.domain.model.Format
import dev.staka.omniconverter.domain.port.EngineRegistry

data class PlanStep(val engine: ConversionEngine, val from: Format, val to: Format)

/** 変換経路。1 段か 2 段 */
data class ConversionPlan(val steps: List<PlanStep>)

/** エンジンが宣言した能力から変換経路を選ぶ。ADR-0003 */
class ConversionPlanner(private val registry: EngineRegistry) {

    private fun edgesFrom(from: Format): List<PlanStep> =
        registry.engines.flatMap { engine ->
            engine.capabilities.filter { it.from == from }.map { PlanStep(engine, it.from, it.to) }
        }

    /** 直接変換を優先し、無ければ 2 段までつなぐ。登録順が早いエンジンを優先する */
    fun plan(from: Format, to: Format): ConversionPlan? {
        if (from == to) return null
        val first = edgesFrom(from)
        first.firstOrNull { it.to == to }?.let { return ConversionPlan(listOf(it)) }
        for (a in first) {
            if (a.to == from) continue
            edgesFrom(a.to).firstOrNull { it.to == to }?.let { return ConversionPlan(listOf(a, it)) }
        }
        return null
    }

    /** [from] から到達できる形式 */
    fun targets(from: Format): Set<Format> {
        val direct = edgesFrom(from).map { it.to }.toSet()
        return (direct + direct.flatMap { mid -> edgesFrom(mid).map { it.to } }) - from
    }
}
