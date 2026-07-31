package com.piyak.english

import com.piyak.english.engine.Formula
import com.piyak.english.engine.Formula.Tok
import com.piyak.english.model.Subject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 수식 파서 검사.
 *
 * 파서는 팩의 평문 prompt 를 그대로 읽으므로, **팩 전체를 전수로 돌려**
 * 어떤 문제에서도 예외가 없고 주요 패턴(lim·log·∫·√·분수·지수)이
 * 실제로 수식 토큰으로 바뀌는지 확인한다.
 */
class FormulaTest {

    private fun packsDir(): File {
        val candidates = listOf(
            File("src/main/assets/packs"),
            File("app/src/main/assets/packs"),
        )
        return candidates.firstOrNull { it.isDirectory } ?: error("packs 디렉터리 없음")
    }

    private fun contains(toks: List<Tok>, pred: (Tok) -> Boolean): Boolean =
        toks.any {
            pred(it) || when (it) {
                is Tok.Frac -> contains(it.num, pred) || contains(it.den, pred)
                is Tok.Sqrt -> contains(it.body, pred)
                is Tok.Lim -> contains(it.body, pred)
                is Tok.Integral -> contains(it.body, pred)
                else -> false
            }
        }

    @Test fun everyPromptParsesWithoutCrashing() {
        val dir = packsDir()
        var total = 0
        var lim = 0; var integral = 0; var log = 0; var sqrt = 0; var frac = 0; var sup = 0
        for (tid in Subject.MATH.tracks + "math_placement") {
            val f = File(dir, "$tid.json")
            if (!f.isFile) continue
            val t = f.readText()
            val root = JSONObject(t)
            val prompts = ArrayList<String>()
            if (root.has("questions")) {
                val arr = root.getJSONArray("questions")
                for (i in 0 until arr.length()) prompts.add(arr.getJSONObject(i).getString("prompt"))
            } else {
                val units = root.getJSONArray("units")
                for (u in 0 until units.length()) {
                    val lessons = units.getJSONObject(u).getJSONArray("lessons")
                    for (l in 0 until lessons.length()) {
                        val qs = lessons.getJSONObject(l).getJSONArray("questions")
                        for (q in 0 until qs.length()) prompts.add(qs.getJSONObject(q).getString("prompt"))
                    }
                }
            }
            for (p in prompts) {
                val toks = Formula.parse(p)   // 예외가 나면 여기서 테스트가 죽는다
                assertTrue("$tid: 파스 결과가 비었다: $p", toks.isNotEmpty())
                total++
                if (contains(toks) { it is Tok.Lim }) lim++
                if (contains(toks) { it is Tok.Integral }) integral++
                if (contains(toks) { it is Tok.Sqrt }) sqrt++
                if (contains(toks) { it is Tok.Frac }) frac++
                if (contains(toks) { it is Tok.Sup }) sup++
                if (contains(toks) { it is Tok.Sub }) log++
            }
        }
        println("전수 $total · lim $lim · ∫ $integral · log(Sub) $log · √ $sqrt · 분수 $frac · 지수 $sup")
        assertTrue("전수가 너무 적다", total > 12000)
        assertTrue("lim 검출 없음", lim >= 50)
        assertTrue("∫ 검출 없음", integral >= 100)
        assertTrue("log 검출 없음", log >= 20)
        assertTrue("√ 검출 없음", sqrt >= 80)
        assertTrue("분수 검출 없음", frac >= 100)
        assertTrue("지수 검출 없음", sup >= 100)
    }

    @Test fun knownShapesParseCorrectly() {
        // lim — 조건이 아래로, 본문은 세로 분수
        val lim = Formula.parse("lim(n→∞) (3n + 5)/(4n + 2) 의 값은?")
        val l = lim.filterIsInstance<Tok.Lim>().single()
        assertEquals("n→∞", l.cond)
        assertTrue(l.body.any { it is Tok.Frac })
        // 뒤의 한국어는 평문으로 남는다
        assertTrue(lim.last() is Tok.Txt)

        // 정적분 — 하한 0, 상한 3
        val integ = Formula.parse("∫₀^3 3x^2 dx 의 값은?")
        val g = integ.filterIsInstance<Tok.Integral>().single()
        assertEquals("0", g.lo)
        assertEquals("3", g.hi)
        assertTrue("본문 지수가 위첨자로", contains(g.body) { it is Tok.Sup && it.s == "2" })

        // log 밑
        val log = Formula.parse("log_3 27 의 값은?")
        assertEquals("3", log.filterIsInstance<Tok.Sub>().single().s)

        // 근호
        val sq = Formula.parse("√512 = k√8 일 때 k는?")
        assertEquals(2, sq.filterIsInstance<Tok.Sqrt>().size)

        // 숫자 분수
        val fr = Formula.parse("1/4 + 2/4 = ?")
        assertEquals(2, fr.filterIsInstance<Tok.Frac>().size)
    }

    @Test fun plainKoreanIsNotMistakenForMath() {
        assertFalse(Formula.looksLikeMath("사과가 몇 개일까요?"))
        assertFalse(Formula.looksLikeMath("시계를 보고 몇 시인지 골라 보세요."))
        assertTrue(Formula.looksLikeMath("lim(n→∞) (3n + 5)/(4n + 2) 의 값은?"))
        assertTrue(Formula.looksLikeMath("√144 의 값은?"))
        assertTrue(Formula.looksLikeMath("1/4 + 2/4 = ?"))
        // 순수 한글 문장은 파스해도 통째로 Txt 하나
        val toks = Formula.parse("바늘을 끌어서 3시 30분을 만들어 보세요.")
        assertEquals(1, toks.size)
        assertTrue(toks[0] is Tok.Txt)
    }
}
