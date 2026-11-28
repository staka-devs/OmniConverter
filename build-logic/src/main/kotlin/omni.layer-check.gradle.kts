plugins {
    base
}

/** 各層が依存してよいプロジェクトの接頭辞。:app は合成ルートなので制約しない */
val allowed: Map<String, List<String>> = mapOf(
    ":domain" to emptyList(),
    ":application" to listOf(":domain"),
    ":infrastructure:core" to listOf(":domain", ":application"),
    ":infrastructure:engine-" to listOf(":domain", ":application", ":infrastructure:core"),
    ":presentation" to listOf(":domain", ":application"),
)

val edges = objects.listProperty<String>()
gradle.projectsEvaluated {
    subprojects.forEach { p ->
        p.configurations.forEach { c ->
            c.dependencies.withType<ProjectDependency>().filter { it.path != p.path }.forEach { d ->
                edges.add("${p.path}->${d.path}")
            }
        }
    }
}

val checkLayerDependencies by tasks.registering {
    group = "verification"
    description = "Clean Architecture の依存方向を検査する"
    val edgeList = edges
    val rules = allowed
    doLast {
        val violations = edgeList.get().distinct().mapNotNull { edge ->
            val (from, to) = edge.split("->")
            val rule = rules.entries.firstOrNull { (prefix, _) -> from == prefix || (prefix.endsWith("-") && from.startsWith(prefix)) }?.value
                ?: return@mapNotNull null
            if (rule.any { to == it }) null else edge
        }
        check(violations.isEmpty()) { "依存方向の違反があります:\n" + violations.joinToString("\n") }
    }
}

tasks.named("check") { dependsOn(checkLayerDependencies) }
