package com.piyak.english.engine

/**
 * 수식 텍스트 파서.
 *
 * 팩의 prompt 는 생성기가 만든 **규칙적인 평문**이다 — `lim(n→∞) (3n + 5)/(4n + 2)`,
 * `log_3 27`, `5^2`, `∫₀^3 3x^2 dx`, `√144`, `3/4` 같은 꼴. 이걸 토큰 트리로 바꿔
 * FormulaView 가 진짜 수학 기호(세로 분수·아래첨자·근호 윗줄·적분 상하한)로 그린다.
 *
 * 원칙: **어떤 입력에도 예외를 던지지 않는다.** 패턴을 못 알아보면 그냥 글자(Txt)로
 * 남기고, 화면은 평문으로라도 반드시 나온다. 팩은 재생성하지 않는다.
 */
object Formula {

    sealed class Tok {
        /** 그냥 글자 (한글 문장 포함) */
        data class Txt(val s: String) : Tok()

        /** 직전 내용의 오른쪽 위 작은 글자 (지수) */
        data class Sup(val s: String) : Tok()

        /** 직전 내용의 오른쪽 아래 작은 글자 (log 밑 등) */
        data class Sub(val s: String) : Tok()

        /** 세로 분수 */
        data class Frac(val num: List<Tok>, val den: List<Tok>) : Tok()

        /** 근호 — 윗줄이 body 를 덮는다 */
        data class Sqrt(val body: List<Tok>) : Tok()

        /** lim — 아래에 조건(n→∞)이 붙고 body 가 이어진다 */
        data class Lim(val cond: String, val body: List<Tok>) : Tok()

        /** 정적분 — ∫ 위에 상한, 아래에 하한 */
        data class Integral(val lo: String, val hi: String, val body: List<Tok>) : Tok()
    }

    private val SUB_DIGITS = mapOf(
        '₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4',
        '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8', '₉' to '9',
    )

    /** 수식으로 그릴 가치가 있는 prompt 인가 (아니면 기존 TextView 가 낫다) */
    fun looksLikeMath(prompt: String): Boolean =
        prompt.contains("lim(") || prompt.contains('∫') || prompt.contains("log_") ||
            prompt.contains('√') || Regex("""\^\d""").containsMatchIn(prompt) ||
            Regex("""\d+/\d+""").containsMatchIn(prompt) ||
            Regex("""\([^()]+\)/\([^()]+\)""").containsMatchIn(prompt)

    /** prompt 전체를 토큰으로. 실패해도 최소한 [Tok.Txt] 전체를 돌려준다 */
    fun parse(prompt: String): List<Tok> = runCatching { parseChunk(prompt) }
        .getOrElse { listOf(Tok.Txt(prompt)) }

    // ---------- 내부 ----------

    private fun parseChunk(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        val text = StringBuilder()

        fun flush() {
            if (text.isNotEmpty()) { out.add(Tok.Txt(text.toString())); text.clear() }
        }

        while (i < s.length) {
            val rest = s.substring(i)

            // lim(cond) body — body 는 한국어 경계(" 의", " 일 때" 등) 앞까지
            if (rest.startsWith("lim(")) {
                val close = s.indexOf(')', i + 4)
                if (close > 0) {
                    val cond = s.substring(i + 4, close)
                    val bodyEnd = koreanBoundary(s, close + 1)
                    val body = s.substring(close + 1, bodyEnd).trim()
                    flush()
                    out.add(Tok.Lim(cond, parseChunk(body)))
                    i = bodyEnd
                    continue
                }
            }

            // ∫₀^N body dx
            if (s[i] == '∫') {
                val m = Regex("""^∫([₀₁₂₃₄₅₆₇₈₉]+)\^(\S+)\s+(.*?)\s*d([a-z])""")
                    .find(rest)
                if (m != null) {
                    val lo = m.groupValues[1].map { SUB_DIGITS[it] ?: it }.joinToString("")
                    flush()
                    out.add(
                        Tok.Integral(
                            lo, m.groupValues[2],
                            parseChunk(m.groupValues[3] + " d" + m.groupValues[4])
                        )
                    )
                    i += m.value.length
                    continue
                }
            }

            // log_b
            if (rest.startsWith("log_")) {
                val m = Regex("""^log_(\S+)""").find(rest)!!
                flush()
                out.add(Tok.Txt("log"))
                out.add(Tok.Sub(m.groupValues[1]))
                i += m.value.length
                continue
            }

            // √144 · √(x+1)
            if (s[i] == '√') {
                val m = Regex("""^√(\d+|\([^()]+\))""").find(rest)
                if (m != null) {
                    val body = m.groupValues[1].removeSurrounding("(", ")")
                    flush()
                    out.add(Tok.Sqrt(parseChunk(body)))
                    i += m.value.length
                    continue
                }
            }

            // (num)/(den) — 괄호 분수 (lim 본문 등)
            run {
                val m = Regex("""^\(([^()]+)\)/\(([^()]+)\)""").find(rest)
                if (m != null) {
                    flush()
                    out.add(Tok.Frac(parseChunk(m.groupValues[1]), parseChunk(m.groupValues[2])))
                    i += m.value.length
                    return@run
                }
                // 3/4 — 숫자 분수 (날짜 같은 게 없는 콘텐츠라 안전)
                val n = Regex("""^(\d+)/(\d+)""").find(rest)
                if (n != null) {
                    flush()
                    out.add(Tok.Frac(listOf(Tok.Txt(n.groupValues[1])), listOf(Tok.Txt(n.groupValues[2]))))
                    i += n.value.length
                    return@run
                }
                // a^b — 지수
                val p = Regex("""^\^(\d+|\([^()]+\))""").find(rest)
                if (p != null) {
                    flush()
                    out.add(Tok.Sup(p.groupValues[1].removeSurrounding("(", ")")))
                    i += p.value.length
                    return@run
                }
                text.append(s[i])
                i++
            }
        }
        flush()
        return merge(out)
    }

    /** "lim 본문이 어디까지인가" — 한국어 조사·구두점 앞에서 끊는다 */
    private fun koreanBoundary(s: String, from: Int): Int {
        for (j in from until s.length) {
            val c = s[j]
            if (c in '가'..'힣' || c == '?' || c == '\n') return j
        }
        return s.length
    }

    /** 이어진 Txt 를 합친다 (렌더 단순화) */
    private fun merge(toks: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>()
        for (t in toks) {
            val last = out.lastOrNull()
            if (t is Tok.Txt && last is Tok.Txt) {
                out[out.size - 1] = Tok.Txt(last.s + t.s)
            } else out.add(t)
        }
        return out
    }
}
