package com.depromeet.team2.architecture

import com.depromeet.team2.Team2ServerApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModule
import org.springframework.modulith.core.ApplicationModules
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DomainRegistryTest {
    private val modules = ApplicationModules.of(Team2ServerApplication::class.java)

    @Suppress("UNCHECKED_CAST")
    private val registry: Map<String, Domain> =
        run {
            val root = File(REGISTRY).inputStream().use { Yaml().load<Map<String, Any?>>(it) }
            val domains = root["domains"] as? Map<String, Map<String, Any?>> ?: fail("$REGISTRY 에 domains 가 없습니다")
            domains.mapValues { (_, value) ->
                Domain(
                    description = value["description"] as? String ?: "",
                    dependsOn = (value["dependsOn"] as? List<String>).orEmpty().toSet(),
                )
            }
        }

    private val actualDependencies: Map<String, Set<String>> =
        modules.associate { module ->
            module.name() to
                module
                    .getDirectDependencies(modules)
                    .uniqueModules()
                    .map { it.name() }
                    .filter { it != GLOBAL && it != module.name() }
                    .toList()
                    .toSortedSet()
        }

    @Test
    fun `코드의 도메인은 모두 레지스트리에 등록돼 있다`() {
        val unregistered = actualDependencies.keys - registry.keys
        assertTrue(
            unregistered.isEmpty(),
            "docs/domains.yaml 에 등록되지 않은 도메인: $unregistered",
        )
    }

    @Test
    fun `레지스트리의 dependsOn 은 등록된 도메인만 가리킨다`() {
        val unknown =
            registry.flatMap { (name, domain) ->
                (domain.dependsOn - registry.keys).map { "$name → $it" }
            }
        assertTrue(unknown.isEmpty(), "docs/domains.yaml 에 없는 도메인을 dependsOn 에 적었습니다: $unknown")
    }

    @Test
    fun `코드의 도메인 간 의존은 레지스트리에 허용된 것만 있다`() {
        val violations =
            actualDependencies.flatMap { (name, deps) ->
                val allowed = registry[name]?.dependsOn.orEmpty()
                (deps - allowed).map { "$name → $it" }
            }
        assertTrue(
            violations.isEmpty(),
            "docs/domains.yaml 의 dependsOn 에 없는 의존: $violations\n" +
                "의도한 의존이면 dependsOn 에 추가하고, 아니면 코드를 고쳐 주세요.",
        )
    }

    @Test
    fun `영향 범위 문서가 최신이다`() {
        val names = (registry.keys + actualDependencies.keys).filter { it != GLOBAL }.sorted()
        val effective: Map<String, Set<String>> =
            names.associateWith { actualDependencies[it] ?: registry[it]?.dependsOn.orEmpty().toSortedSet() }
        val dependents: Map<String, Set<String>> =
            names.associateWith { target ->
                effective.filterValues { target in it }.keys.toSortedSet()
            }

        fun transitiveDependents(start: String): Set<String> {
            val visited = sortedSetOf<String>()
            val queue = ArrayDeque(dependents[start].orEmpty())
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (visited.add(next)) queue.addAll(dependents[next].orEmpty())
            }
            return visited
        }

        fun Set<String>.show() = if (isEmpty()) "-" else joinToString(", ") { "`$it`" }

        val expected =
            buildString {
                appendLine("# 도메인 영향 범위")
                appendLine()
                appendLine("> 자동 생성 문서입니다. 직접 고치지 말고 `docs/domains.yaml` · 코드를 바꾼 뒤 `./gradlew test` 로 다시 만듭니다.")
                appendLine(">")
                appendLine("> `global` 은 모든 도메인이 의존하므로 표 · 그래프에서 뺐습니다. `global` 을 바꾸면 전 도메인이 영향을 받습니다.")
                appendLine()
                appendLine("**기능 기획 시**: 바꿀 도메인의 \"영향 범위\" 칸에 있는 도메인도 함께 검토합니다.")
                appendLine()
                appendLine("| 도메인 | 설명 | 의존하는 도메인 | 나를 직접 의존 | 영향 범위 (바꾸면 영향받는 도메인) |")
                appendLine("|---|---|---|---|---|")
                names.forEach { name ->
                    val description = registry[name]?.description ?: "(레지스트리 미등록)"
                    val status = if (name in actualDependencies) "" else " _(기획 중 · 코드 없음)_"
                    val deps = effective.getValue(name)
                    appendLine(
                        "| `$name`$status | $description | ${deps.show()} | ${dependents[name].orEmpty().show()} | " +
                            "${transitiveDependents(name).show()} |",
                    )
                }
                if (names.isEmpty()) appendLine("| - | 아직 도메인이 없습니다 | - | - | - |")
                appendLine()
                appendLine("## 의존 그래프")
                appendLine()
                appendLine("```mermaid")
                appendLine("graph LR")
                names.forEach { name ->
                    val arrow = if (name in actualDependencies) "-->" else "-.->"
                    val deps = effective.getValue(name)
                    if (deps.isEmpty()) appendLine("    $name") else deps.forEach { appendLine("    $name $arrow $it") }
                }
                appendLine("```")
                appendLine()
                appendLine("점선(`-.->`)은 아직 코드가 없는 기획 중 도메인의 예정된 의존입니다.")
            }

        val file = File(IMPACT_DOC)
        val current = if (file.exists()) file.readText() else ""
        if (current == expected) return

        if (System.getenv("CI") == "true") {
            assertEquals(expected, current, "$IMPACT_DOC 가 최신이 아닙니다. 로컬에서 ./gradlew test 후 커밋해 주세요.")
        }
        file.writeText(expected)
    }

    private fun ApplicationModule.name() = identifier.toString()

    private data class Domain(
        val description: String,
        val dependsOn: Set<String>,
    )

    companion object {
        private const val GLOBAL = "global"
        private const val REGISTRY = "docs/domains.yaml"
        private const val IMPACT_DOC = "docs/domain-impact.md"
    }
}
