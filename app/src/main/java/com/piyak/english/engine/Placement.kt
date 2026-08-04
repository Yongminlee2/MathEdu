package com.piyak.english.engine

/** 배치고사(레벨테스트) 적응형 사다리 — 순수 로직 */
object Placement {
    const val TOTAL = 25
    const val START_LEVEL = 3

    /** 영어는 10단계, 수학은 학년 13단계 */
    const val MAX_LEVEL_ENGLISH = 10
    const val MAX_LEVEL_MATH = 13

    fun maxLevel(subject: com.piyak.english.model.Subject): Int =
        if (subject == com.piyak.english.model.Subject.MATH) MAX_LEVEL_MATH else MAX_LEVEL_ENGLISH

    /**
     * 레벨 이름 = 학년 이름.
     *
     * 영어 레벨 이름표(초등 1~2학년 … 고급·토플)도 있었지만 수학 앱에서는
     * 아무도 부르지 않는 죽은 코드였다. 이름은 문자열 리소스라 Context 가 필요하다.
     */
    fun levelName(ctx: android.content.Context, level: Int): String =
        ctx.getString(com.piyak.english.model.MathGrades.forLevel(level).titleRes)

    /** 진행도 저장 키 (과목별로 따로 기억한다) */
    fun levelKey(subject: com.piyak.english.model.Subject): String =
        if (subject == com.piyak.english.model.Subject.MATH) "math_placement_level" else "placement_level"

    fun doneKey(subject: com.piyak.english.model.Subject): String =
        if (subject == com.piyak.english.model.Subject.MATH) "math_placement_done" else "placement_done"

    /** 맞으면 +1, 틀리면 -1 (과목의 최대 단계까지) */
    fun nextLevel(cur: Int, correct: Boolean, max: Int = MAX_LEVEL_ENGLISH): Int =
        (if (correct) cur + 1 else cur - 1).coerceIn(1, max)

    /** 최종 배치: 최근 10문항의 출제 레벨 중앙값 */
    fun placeLevel(history: List<Pair<Int, Boolean>>): Int {
        if (history.isEmpty()) return 1
        val recent = history.takeLast(10).map { it.first }.sorted()
        return recent[recent.size / 2]
    }
}
