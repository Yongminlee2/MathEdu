import Foundation

/// 배지 정의 + 획득 판정 (순수 로직). 제목·설명은 **문자열 리소스 id** 다.
struct BadgeDef: Equatable {
    var id: String
    var emoji: String
    var titleRes: Int
    var descRes: Int

    init(_ id: String, _ emoji: String, _ titleRes: Int, _ descRes: Int) {
        self.id = id
        self.emoji = emoji
        self.titleRes = titleRes
        self.descRes = descRes
    }
}

/// 코틀린에서 이름 붙여 만드는 자리라 멤버 init 이름표를 그대로 쓴다
struct StatsSnapshot {
    var lessonsDone: Int
    var perfectCount: Int
    var xp: Int
    var streakBest: Int
    var placementDone: Bool
    var reviewCleared: Int
    var unitsCompleted: [String: Int] // trackId → 완료 유닛 수
    var skillLevels: [String: Int] = [:] // 영역 id → 레벨
    var goalsMet: Int = 0 // 일일 목표 달성 횟수
}

/// 얻을 수 있는 배지 목록. 수학 앱이라 수학으로 얻는 배지만 둔다.
/// (지운 배지 기록이 DB 에 남아 있어도 목록에 없으면 안 보일 뿐이다)
enum Badges {
    static let ALL: [BadgeDef] = [
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
    ]

    static func check(_ s: StatsSnapshot, _ already: Set<String>) -> [BadgeDef] {
        var earned: [BadgeDef] = []
        func give(_ id: String, _ cond: Bool) {
            if cond && !already.contains(id), let b = ALL.first(where: { $0.id == id }) { earned.append(b) }
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
        give("unit_master", s.unitsCompleted.values.contains { $0 >= 5 })
        give("goal_first", s.goalsMet >= 1)
        give("goal_10", s.goalsMet >= 10)
        give("m_calc_master", (s.skillLevels["m_calc"] ?? 0) >= 5)
        give("m_number_master", (s.skillLevels["m_number"] ?? 0) >= 5)
        give("m_shape_master", (s.skillLevels["m_shape"] ?? 0) >= 5)
        give("m_measure_master", (s.skillLevels["m_measure"] ?? 0) >= 5)
        give("m_data_master", (s.skillLevels["m_data"] ?? 0) >= 5)
        give("m_word_master", (s.skillLevels["m_word"] ?? 0) >= 5)
        give("m_all_rounder", Skills.MATH.allSatisfy { (s.skillLevels[$0.id] ?? 0) >= 3 })
        return earned
    }
}
