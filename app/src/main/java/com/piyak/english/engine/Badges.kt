package com.piyak.english.engine

import com.piyak.english.R

/**
 * 배지 정의 + 획득 판정 (순수 로직).
 *
 * 제목·설명은 **문자열 리소스 id** 다 — 폰 언어를 따라가야 하므로.
 */
data class BadgeDef(val id: String, val emoji: String, val titleRes: Int, val descRes: Int)

data class StatsSnapshot(
    val lessonsDone: Int,
    val perfectCount: Int,
    val xp: Int,
    val streakBest: Int,
    val placementDone: Boolean,
    val reviewCleared: Int,
    val unitsCompleted: Map<String, Int>, // trackId → 완료 유닛 수
    val skillLevels: Map<String, Int> = emptyMap(), // 영역 id → 레벨
    val goalsMet: Int = 0, // 일일 목표 달성 횟수
)

/**
 * 얻을 수 있는 배지 목록.
 *
 * **수학 앱이므로 수학으로 얻는 배지만 둔다.** 앱을 둘로 나눌 때 영어 배지
 * (듣기·말하기·쓰기·문법·영어왕·두 과목 척척)가 그대로 남아 있어서,
 * 통계 화면에 **영영 얻을 수 없는 회색 배지**가 6개나 붙어 있었다.
 * 빈자리는 그동안 없던 수학 영역 배지(수감각·측정·자료)로 채웠다.
 *
 * 이미 지운 배지를 받아 둔 기록이 DB 에 남아 있어도, 목록에 없으면 안 보일 뿐 문제없다.
 */
object Badges {
    val ALL = listOf(
        BadgeDef("first_lesson", "🐣", R.string.bg_first_lesson, R.string.bg_first_lesson_d),
        BadgeDef("lessons_10", "📚", R.string.bg_lessons_10, R.string.bg_lessons_10_d),
        BadgeDef("lessons_50", "🎓", R.string.bg_lessons_50, R.string.bg_lessons_50_d),
        BadgeDef("lessons_200", "👑", R.string.bg_lessons_200, R.string.bg_lessons_200_d),
        BadgeDef("perfect_10", "💯", R.string.bg_perfect_10, R.string.bg_perfect_10_d),
        BadgeDef("streak_7", "🔥", R.string.bg_streak_7, R.string.bg_streak_7_d),
        BadgeDef("streak_30", "🌋", R.string.bg_streak_30, R.string.bg_streak_30_d),
        BadgeDef("xp_1000", "⭐", R.string.bg_xp_1000, R.string.bg_xp_1000_d),
        BadgeDef("xp_5000", "🌟", R.string.bg_xp_5000, R.string.bg_xp_5000_d),
        BadgeDef("placement", "🎯", R.string.bg_placement, R.string.bg_placement_d),
        BadgeDef("review_50", "💊", R.string.bg_review_50, R.string.bg_review_50_d),
        BadgeDef("unit_master", "🏆", R.string.bg_unit_master, R.string.bg_unit_master_d),
        BadgeDef("goal_first", "🎯", R.string.bg_goal_first, R.string.bg_goal_first_d),
        BadgeDef("goal_10", "🎪", R.string.bg_goal_10, R.string.bg_goal_10_d),
        // 수학 영역별
        BadgeDef("m_calc_master", "➕", R.string.bg_m_calc, R.string.bg_m_calc_d),
        BadgeDef("m_number_master", "🔢", R.string.bg_m_number, R.string.bg_m_number_d),
        BadgeDef("m_shape_master", "🔺", R.string.bg_m_shape, R.string.bg_m_shape_d),
        BadgeDef("m_measure_master", "📏", R.string.bg_m_measure, R.string.bg_m_measure_d),
        BadgeDef("m_data_master", "📊", R.string.bg_m_data, R.string.bg_m_data_d),
        BadgeDef("m_word_master", "🧩", R.string.bg_m_word, R.string.bg_m_word_d),
        BadgeDef("m_all_rounder", "🧮", R.string.bg_m_all, R.string.bg_m_all_d),
    )

    fun check(s: StatsSnapshot, already: Set<String>): List<BadgeDef> {
        val earned = ArrayList<BadgeDef>()
        fun give(id: String, cond: Boolean) {
            if (cond && id !in already) ALL.firstOrNull { it.id == id }?.let { earned.add(it) }
        }
        give("first_lesson", s.lessonsDone >= 1)
        give("lessons_10", s.lessonsDone >= 10)
        give("lessons_50", s.lessonsDone >= 50)
        give("lessons_200", s.lessonsDone >= 200)
        give("perfect_10", s.perfectCount >= 10)
        give("streak_7", s.streakBest >= 7)
        give("streak_30", s.streakBest >= 30)
        give("xp_1000", s.xp >= 1000)
        give("xp_5000", s.xp >= 5000)
        give("placement", s.placementDone)
        give("review_50", s.reviewCleared >= 50)
        give("unit_master", s.unitsCompleted.values.any { it >= 5 })
        give("goal_first", s.goalsMet >= 1)
        give("goal_10", s.goalsMet >= 10)
        give("m_calc_master", (s.skillLevels["m_calc"] ?: 0) >= 5)
        give("m_number_master", (s.skillLevels["m_number"] ?: 0) >= 5)
        give("m_shape_master", (s.skillLevels["m_shape"] ?: 0) >= 5)
        give("m_measure_master", (s.skillLevels["m_measure"] ?: 0) >= 5)
        give("m_data_master", (s.skillLevels["m_data"] ?: 0) >= 5)
        give("m_word_master", (s.skillLevels["m_word"] ?: 0) >= 5)
        give("m_all_rounder", Skills.MATH.all { (s.skillLevels[it.id] ?: 0) >= 3 })
        return earned
    }
}
