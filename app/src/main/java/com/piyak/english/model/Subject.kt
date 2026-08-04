package com.piyak.english.model

import com.piyak.english.R

/** 과목. 트랙 목록·실력 영역·색을 과목별로 알려 준다. */
enum class Subject(
    val id: String,
    val emoji: String,
    /** 화면에 뜨는 이름·설명은 **문자열 리소스 id** 다 */
    val titleRes: Int,
    val subtitleRes: Int,
    val color: String,
) {
    ENGLISH(
        "english", "📘", R.string.subj_english, R.string.subj_english_sub, "#AEDCF5"
    ),
    MATH(
        "math", "🔢", R.string.subj_math, R.string.subj_math_sub, "#F7C6C7"
    );

    /** 이 과목의 트랙 id 목록 (팩 파일명과 같다) */
    val tracks: List<String>
        get() = when (this) {
            ENGLISH -> listOf(
                "elem",
                "basic", "daily", "toeic", "toefl",
                "listening", "speaking", "writing", "grammar", "reading",
            )
            MATH -> MathGrades.ALL.map { it.trackId }
        }

    companion object {
        fun of(id: String): Subject = entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

/** 수학 학년 한 칸 */
data class MathGrade(
    val trackId: String,
    val emoji: String,
    /** 학년 이름·단원 요약·묶음 이름은 **문자열 리소스 id** 다 */
    val titleRes: Int,
    val subtitleRes: Int,
    val stageRes: Int,
    val color: String,
)

object MathGrades {
    val STAGE_ELEM = R.string.stage_elem
    val STAGE_MID = R.string.stage_mid
    val STAGE_HIGH = R.string.stage_high

    val ALL: List<MathGrade> = listOf(
        MathGrade("math_k", "🐣", R.string.gr_k, R.string.gr_k_sub, STAGE_ELEM, "#FFE0B2"),
        MathGrade("math_g1", "1️⃣", R.string.gr_g1, R.string.gr_g1_sub, STAGE_ELEM, "#FFECB3"),
        MathGrade("math_g2", "2️⃣", R.string.gr_g2, R.string.gr_g2_sub, STAGE_ELEM, "#F0F4C3"),
        MathGrade("math_g3", "3️⃣", R.string.gr_g3, R.string.gr_g3_sub, STAGE_ELEM, "#DCEDC8"),
        MathGrade("math_g4", "4️⃣", R.string.gr_g4, R.string.gr_g4_sub, STAGE_ELEM, "#C8E6C9"),
        MathGrade("math_g5", "5️⃣", R.string.gr_g5, R.string.gr_g5_sub, STAGE_ELEM, "#B2DFDB"),
        MathGrade("math_g6", "6️⃣", R.string.gr_g6, R.string.gr_g6_sub, STAGE_ELEM, "#B3E5FC"),
        MathGrade("math_m1", "🌱", R.string.gr_m1, R.string.gr_m1_sub, STAGE_MID, "#BBDEFB"),
        MathGrade("math_m2", "🌿", R.string.gr_m2, R.string.gr_m2_sub, STAGE_MID, "#C5CAE9"),
        MathGrade("math_m3", "🍀", R.string.gr_m3, R.string.gr_m3_sub, STAGE_MID, "#D1C4E9"),
        MathGrade("math_h1", "🌳", R.string.gr_h1, R.string.gr_h1_sub, STAGE_HIGH, "#E1BEE7"),
        MathGrade("math_h2", "🌲", R.string.gr_h2, R.string.gr_h2_sub, STAGE_HIGH, "#F8BBD0"),
        MathGrade("math_h3", "🎓", R.string.gr_h3, R.string.gr_h3_sub, STAGE_HIGH, "#FFCDD2"),
    )

    val STAGES = listOf(STAGE_ELEM, STAGE_MID, STAGE_HIGH)

    fun of(trackId: String): MathGrade? = ALL.firstOrNull { it.trackId == trackId }

    fun byStage(stageRes: Int): List<MathGrade> = ALL.filter { it.stageRes == stageRes }

    /** 배치고사 레벨(1~13) → 트랙 */
    fun forLevel(level: Int): MathGrade = ALL[(level - 1).coerceIn(0, ALL.size - 1)]

    fun levelOf(trackId: String): Int = ALL.indexOfFirst { it.trackId == trackId } + 1
}
