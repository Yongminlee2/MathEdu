import Foundation

/// 과목. 트랙 목록·실력 영역·색을 과목별로 알려 준다.
enum Subject: String, KotlinEnum {
    case ENGLISH, MATH

    var id: String {
        switch self {
        case .ENGLISH: return "english"
        case .MATH: return "math"
        }
    }

    var emoji: String {
        switch self {
        case .ENGLISH: return "📘"
        case .MATH: return "🔢"
        }
    }

    /// 화면에 뜨는 이름·설명은 **문자열 리소스 id** 다
    var titleRes: Int {
        switch self {
        case .ENGLISH: return R.string.subj_english
        case .MATH: return R.string.subj_math
        }
    }

    var subtitleRes: Int {
        switch self {
        case .ENGLISH: return R.string.subj_english_sub
        case .MATH: return R.string.subj_math_sub
        }
    }

    var color: String {
        switch self {
        case .ENGLISH: return "#AEDCF5"
        case .MATH: return "#F7C6C7"
        }
    }

    /// 이 과목의 트랙 id 목록 (팩 파일명과 같다)
    var tracks: [String] {
        switch self {
        case .ENGLISH:
            return [
                "elem",
                "basic", "daily", "toeic", "toefl",
                "listening", "speaking", "writing", "grammar", "reading",
            ]
        case .MATH:
            return MathGrades.ALL.map { $0.trackId }
        }
    }

    static func of(_ id: String) -> Subject { entries.first { $0.id == id } ?? .ENGLISH }
}

/// 수학 학년 한 칸
struct MathGrade: Equatable {
    var trackId: String
    var emoji: String
    /// 학년 이름·단원 요약·묶음 이름은 **문자열 리소스 id** 다
    var titleRes: Int
    var subtitleRes: Int
    var stageRes: Int
    var color: String

    init(_ trackId: String, _ emoji: String, _ titleRes: Int, _ subtitleRes: Int, _ stageRes: Int, _ color: String) {
        self.trackId = trackId
        self.emoji = emoji
        self.titleRes = titleRes
        self.subtitleRes = subtitleRes
        self.stageRes = stageRes
        self.color = color
    }
}

enum MathGrades {
    static let STAGE_ELEM = R.string.stage_elem
    static let STAGE_MID = R.string.stage_mid
    static let STAGE_HIGH = R.string.stage_high

    static let ALL: [MathGrade] = [
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
    ]

    static let STAGES = [STAGE_ELEM, STAGE_MID, STAGE_HIGH]

    static func of(_ trackId: String) -> MathGrade? { ALL.first { $0.trackId == trackId } }

    static func byStage(_ stageRes: Int) -> [MathGrade] { ALL.filter { $0.stageRes == stageRes } }

    /// 배치고사 레벨(1~13) → 트랙
    static func forLevel(_ level: Int) -> MathGrade { ALL[(level - 1).coerceIn(0, ALL.count - 1)] }

    static func levelOf(_ trackId: String) -> Int { ALL.indexOfFirst { $0.trackId == trackId } + 1 }
}
