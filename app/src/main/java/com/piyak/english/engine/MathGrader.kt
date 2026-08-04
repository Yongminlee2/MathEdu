package com.piyak.english.engine

import kotlin.math.abs

/**
 * 수학 답 채점. 아이가 쓰는 여러 표기를 같은 답으로 인정한다.
 * 0.75 = 3/4 = 6/8, 1,200 = 1200, "12 cm" = 12
 */
object MathGrader {

    /** 답을 수로 해석. 분수(3/4)·소수·쉼표·단위 문자를 다룬다. 실패하면 null */
    fun parse(raw: String): Double? {
        var s = raw.trim().lowercase()
            .replace(",", "")
            .replace(" ", "")
        if (s.isEmpty()) return null
        // 뒤에 붙은 단위 제거 (cm · kg · 개 · pcs · шт …)
        //
        // isLetter() 는 **모든 문자 체계의 글자**에 참이다 — 한글·가나·한자·키릴·타이까지.
        // 예전에는 한국어 단위를 따로 나열했는데, 그건 isLetter() 와 겹치는 데다
        // 다른 언어의 단위는 못 걷어냈다. 기호 단위(° % 등)는 답에 안 쓰이므로 그대로 둔다.
        s = s.trimEnd { it.isLetter() }
        if (s.isEmpty()) return null
        // 대분수 1과2/3 형태는 다루지 않는다 (생성기가 만들지 않음)
        return if (s.contains("/")) {
            val parts = s.split("/")
            if (parts.size != 2) return null
            val n = parts[0].toDoubleOrNull() ?: return null
            val d = parts[1].toDoubleOrNull() ?: return null
            if (d == 0.0) null else n / d
        } else s.toDoubleOrNull()
    }

    /** 수치가 같은지 (부동소수 오차 허용) */
    fun sameNumber(a: String, b: String): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return false
        return abs(x - y) < 1e-6
    }

    /** 식·문자 답 정규화: 공백 제거, 곱셈기호 통일, x·X 통일 */
    fun normalizeExpr(s: String): String =
        s.lowercase()
            .replace(" ", "")
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("＝", "=")

    /**
     * 숫자 입력 채점. 정답과 대안 답 중 하나와 수치가 같으면 정답.
     * 수로 해석되지 않으면 식으로 보고 문자열 비교한다.
     */
    fun grade(input: String, answer: String, alts: List<String> = emptyList()): Boolean {
        if (input.isBlank()) return false
        val candidates = listOf(answer) + alts
        // 1) 수치 비교
        if (parse(input) != null) {
            if (candidates.any { sameNumber(input, it) }) return true
        }
        // 2) 식 비교
        val inN = normalizeExpr(input)
        return candidates.any { normalizeExpr(it) == inN }
    }

    /** 분수를 기약분수로 (해설 생성·표시용) */
    fun reduce(n: Int, d: Int): Pair<Int, Int> {
        if (d == 0) return n to d
        val g = gcd(abs(n), abs(d)).coerceAtLeast(1)
        val sign = if (d < 0) -1 else 1
        return (sign * n / g) to (sign * d / g)
    }

    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    fun lcm(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else abs(a / gcd(a, b) * b)
}
