package com.piyak.english

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 팩에 실제로 들어 있는 그림 명세(kind)를 앱이 전부 파싱할 수 있는지 검사한다.
 *
 * v1.22 사고 재발 방지: 좌표·도형 도해(coord3d/coord2d/geom)를 상수로만 추가하고
 * MathVisual.KINDS 화이트리스트에 안 넣어서, fromJson 이 조용히 null 을 돌려주고
 * 화면에는 병아리 폴백만 나왔다 — 팩·상수·화이트리스트가 어긋나면 여기서 잡는다.
 */
class VisualKindsTest {

    /** MathVisual.kt 소스에서 KINDS 블록 안의 상수들을 실제 문자열 값으로 푼다 */
    private fun whitelistedKinds(): Set<String> {
        val src = File("src/main/java/com/piyak/english/model/MathVisual.kt").readText()
        // const val COORD3D = "coord3d" 꼴 전부 수집
        val constValues = Regex("const val (\\w+) = \"([a-z0-9_]+)\"")
            .findAll(src).associate { it.groupValues[1] to it.groupValues[2] }
        // 블록 안 주석에 괄호가 있어도 안전하게, 닫는 줄("        )")까지 통째로 잡는다
        val kindsBlock = Regex("val KINDS = setOf\\((.*?)\\n        \\)", RegexOption.DOT_MATCHES_ALL)
            .find(src)?.groupValues?.get(1) ?: ""
        return Regex("\\b([A-Z][A-Z0-9_]+)\\b").findAll(kindsBlock)
            .mapNotNull { constValues[it.groupValues[1]] }
            .toSet()
    }

    @Test
    fun `팩의 모든 그림 kind 를 앱이 파싱할 수 있다`() {
        val whitelist = whitelistedKinds()
        assertTrue("화이트리스트 추출 실패 (${whitelist.size}종)", whitelist.size >= 20)

        val packsDir = File("src/main/assets/packs")
        val kindRe = Regex("\"kind\"\\s*:\\s*\"([a-z0-9_]+)\"")
        val missing = sortedSetOf<String>()
        for (f in packsDir.listFiles { _, n -> n.startsWith("math_") && n.endsWith(".json") } ?: arrayOf()) {
            for (m in kindRe.findAll(f.readText())) {
                val kind = m.groupValues[1]
                if (kind !in whitelist) missing.add(kind)
            }
        }
        assertTrue(
            "팩에는 있는데 MathVisual.KINDS 에 없는 kind: $missing — fromJson 이 조용히 버려서 그림이 안 나온다!",
            missing.isEmpty()
        )
    }
}
