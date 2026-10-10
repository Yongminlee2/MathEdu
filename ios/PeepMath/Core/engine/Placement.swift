import Foundation

/// 배치고사(레벨테스트) 적응형 사다리 — 순수 로직
enum Placement {
    static let TOTAL = 25
    static let START_LEVEL = 3

    /// 영어는 10단계, 수학은 학년 13단계
    static let MAX_LEVEL_ENGLISH = 10
    static let MAX_LEVEL_MATH = 13

    static func maxLevel(_ subject: Subject) -> Int {
        subject == .MATH ? MAX_LEVEL_MATH : MAX_LEVEL_ENGLISH
    }

    /// 레벨 이름 = 학년 이름 (문자열 리소스라 Context 가 필요하다)
    static func levelName(_ ctx: Context, _ level: Int) -> String {
        ctx.getString(MathGrades.forLevel(level).titleRes)
    }

    /// 진행도 저장 키 (과목별로 따로 기억한다)
    static func levelKey(_ subject: Subject) -> String {
        subject == .MATH ? "math_placement_level" : "placement_level"
    }

    static func doneKey(_ subject: Subject) -> String {
        subject == .MATH ? "math_placement_done" : "placement_done"
    }

    /// 맞으면 +1, 틀리면 -1 (과목의 최대 단계까지)
    static func nextLevel(_ cur: Int, _ correct: Bool, _ max: Int = MAX_LEVEL_ENGLISH) -> Int {
        (correct ? cur + 1 : cur - 1).coerceIn(1, max)
    }

    /// 최종 배치: 최근 10문항의 출제 레벨 중앙값
    static func placeLevel(_ history: [(first: Int, second: Bool)]) -> Int {
        if history.isEmpty { return 1 }
        let recent = history.suffix(10).map { $0.first }.sorted()
        return recent[recent.count / 2]
    }
}
