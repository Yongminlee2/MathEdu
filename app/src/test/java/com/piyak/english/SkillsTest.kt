package com.piyak.english

import com.piyak.english.engine.DailyGoal
import com.piyak.english.engine.Ranks
import com.piyak.english.engine.SkillState
import com.piyak.english.engine.Skills
import com.piyak.english.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {

    private fun state(id: String, correct: Int, attempts: Int = correct) =
        SkillState(Skills.def(id), correct, attempts)

    @Test fun skillLevelThresholds() {
        assertEquals(0, Skills.levelFor(0))
        assertEquals(0, Skills.levelFor(9))
        assertEquals(1, Skills.levelFor(10))
        assertEquals(1, Skills.levelFor(29))
        assertEquals(2, Skills.levelFor(30))
        assertEquals(3, Skills.levelFor(60))
        assertEquals(10, Skills.levelFor(550))
        // 최대 레벨 상한
        assertEquals(10, Skills.levelFor(99999))
    }

    @Test fun skillProgressBetweenLevels() {
        // Lv1(10)~Lv2(30) 사이 20문제 구간의 절반 = 20정답
        assertEquals(0.5f, Skills.progressFor(20), 0.001f)
        assertEquals(0f, Skills.progressFor(10), 0.001f)
        assertEquals(1f, Skills.progressFor(550), 0.001f)
    }

    @Test fun skillStateDerivedValues() {
        val s = state("m_calc", correct = 30, attempts = 40)
        assertEquals(2, s.level)
        assertEquals(75, s.accuracy)
        assertEquals(30, s.nextLevelNeed) // Lv3 = 60정답 필요
        assertEquals(0, state("m_data", 0, 0).accuracy) // 0으로 나누지 않음
    }

    @Test fun overallIsAverageOfSkillLevels() {
        val states = listOf(
            state("m_calc", 60), // Lv3
            state("m_shape", 10),  // Lv1
            state("m_word", 30),   // Lv2
            state("m_number", 0),    // Lv0
            state("m_measure", 0),    // Lv0
            state("m_data", 0),      // Lv0
        )
        assertEquals(1f, Skills.overallLevel(states), 0.001f) // (3+1+2+0+0+0)/6
        assertEquals(0f, Skills.overallLevel(emptyList()), 0.001f)
    }

    @Test fun weakestPrefersUntouchedSkill() {
        val states = listOf(
            state("m_calc", 60),
            state("m_shape", 0),      // 한 번도 안 푼 영역이 가장 약하다
            state("m_word", 30),
        )
        assertEquals("m_shape", Skills.weakest(states)!!.def.id)
    }

    @Test fun ranksProgressWithOverallLevel() {
        // 이름은 이제 문자열 리소스라 안드로이드 없이는 못 읽는다.
        // **어느 칭호가 걸리는지**(순서)만 확인한다 — 그게 이 함수의 핵심이다.
        assertEquals(Ranks.ALL[0].titleRes, Ranks.of(0f).titleRes)
        assertEquals(Ranks.ALL[2].titleRes, Ranks.of(1.5f).titleRes)
        assertEquals(Ranks.ALL.last().titleRes, Ranks.of(10f).titleRes)
        // 다음 칭호와 진행률
        assertEquals(Ranks.ALL[1].titleRes, Ranks.next(0f)!!.titleRes)
        assertTrue(Ranks.next(10f) == null)
        assertEquals(0f, Ranks.progress(0f), 0.001f)
        assertEquals(1f, Ranks.progress(10f), 0.001f)
        assertTrue(Ranks.progress(0.35f) > 0.4f) // 0.7 구간의 절반쯤
    }

    @Test fun dailyGoalProgress() {
        assertEquals(0.5f, DailyGoal.progress(25, 50), 0.001f)
        assertEquals(1f, DailyGoal.progress(80, 50), 0.001f) // 초과해도 1을 넘지 않음
        assertTrue(DailyGoal.isDone(50, 50))
        assertFalse(DailyGoal.isDone(49, 50))
    }

    @Test fun questionSkillDefaults() {
        assertEquals("vocab", Question.Mcq("q1", "p", listOf("a", "b", "c", "d"), 0).skill)
        assertEquals("reading",
            Question.Mcq("q2", "p", listOf("a", "b", "c", "d"), 0, passage = "text").skill)
        assertEquals("listening", Question.ListenMcq("q3", "hi", "p", listOf("a", "b", "c", "d"), 0).skill)
        assertEquals("listening", Question.Dictation("q4", "hi", "hi").skill)
        assertEquals("writing", Question.Order("q5", "안녕", "hi there").skill)
        assertEquals("writing", Question.TypeTranslate("q6", "안녕", "hi").skill)
        assertEquals("speaking", Question.Speak("q7", "hi").skill)
    }

    @Test fun skillTagOverridesDefault() {
        // 문법 문제는 형태가 mcq 라서 팩의 skill 태그로만 구분된다
        val q = Question.Mcq("q8", "I ___ a student.", listOf("am", "is", "are", "be"), 0)
        assertEquals("vocab", q.skill)
        q.skillTag = "grammar"
        assertEquals("grammar", q.skill)
    }
}
