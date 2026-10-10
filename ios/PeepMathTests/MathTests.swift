import XCTest
@testable import PeepMath

final class MathTests: XCTestCase {

    // ---------------- 채점기 ----------------

    func test_parsesNumbersFractionsAndUnits() {
        XCTAssertEqual(MathGrader.parse("7")!, 7.0, accuracy: 1e-9)
        XCTAssertEqual(MathGrader.parse("3/4")!, 0.75, accuracy: 1e-9)
        XCTAssertEqual(MathGrader.parse("1,200")!, 1200.0, accuracy: 1e-9)
        XCTAssertEqual(MathGrader.parse("12 cm")!, 12.0, accuracy: 1e-9)
        XCTAssertEqual(MathGrader.parse("-5")!, -5.0, accuracy: 1e-9)
        XCTAssertNil(MathGrader.parse(""))
        XCTAssertNil(MathGrader.parse("3/0"))
    }

    func test_fractionAndDecimalAreTheSameAnswer() {
        XCTAssertTrue(MathGrader.grade("3/4", "0.75"))
        XCTAssertTrue(MathGrader.grade("0.75", "3/4"))
        XCTAssertTrue(MathGrader.grade("6/8", "3/4"))
        XCTAssertFalse(MathGrader.grade("2/4", "3/4"))
    }

    func test_unitsAndSpacesAreForgiven() {
        XCTAssertTrue(MathGrader.grade(" 12 ", "12"))
        XCTAssertTrue(MathGrader.grade("12cm", "12"))
        XCTAssertTrue(MathGrader.grade("1,000", "1000"))
        XCTAssertFalse(MathGrader.grade("", "12"))
    }

    func test_expressionAnswersAreNormalized() {
        XCTAssertTrue(MathGrader.grade("(x+2)(x+3)", "(x+2)(x+3)"))
        XCTAssertTrue(MathGrader.grade("(x + 2)(x + 3)", "(x+2)(x+3)"))
        XCTAssertTrue(MathGrader.grade("(x+3)(x+2)", "(x+2)(x+3)", ["(x+3)(x+2)"]))
        XCTAssertFalse(MathGrader.grade("(x+1)(x+3)", "(x+2)(x+3)"))
    }

    func test_reduceAndGcd() {
        let r1 = MathGrader.reduce(6, 8)
        XCTAssertTrue(r1 == (3, 4), "\(r1)")
        let r2 = MathGrader.reduce(5, 15)
        XCTAssertTrue(r2 == (1, 3), "\(r2)")
        XCTAssertEqual(MathGrader.gcd(12, 18), 6)
        XCTAssertEqual(MathGrader.lcm(12, 18), 36)
    }

    // ---------------- 과목·학년 구조 ----------------

    func test_subjectsHaveDistinctTracks() {
        let eng = Subject.ENGLISH.tracks
        let math = Subject.MATH.tracks
        XCTAssertTrue(eng.isNotEmpty() && math.isNotEmpty())
        XCTAssertTrue(Set(eng).intersection(math).isEmpty, "과목 간 트랙이 겹치면 안 됨")
        XCTAssertEqual(math.count, 13)
        // 삐약수학은 수학 트랙만 싣는다
        XCTAssertEqual(math.count, ContentRepo.TRACK_IDS.count)
    }

    func test_mathGradesAreOrderedAndComplete() {
        XCTAssertEqual(MathGrades.ALL.count, 13)
        // 배치고사 레벨 1~13 이 학년과 1:1로 대응해야 한다
        for lv in 1...13 {
            let g = MathGrades.forLevel(lv)
            XCTAssertEqual(MathGrades.levelOf(g.trackId), lv)
        }
        // 범위를 벗어난 레벨도 안전하게 잘린다
        XCTAssertEqual(MathGrades.forLevel(0), MathGrades.ALL.first!)
        XCTAssertEqual(MathGrades.forLevel(99), MathGrades.ALL.last!)
        XCTAssertEqual(MathGrades.STAGES.count, 3)
        XCTAssertEqual(MathGrades.STAGES.sumOf { MathGrades.byStage($0).count }, 13)
    }

    /// 수학 앱에는 수학 영역만 있어야 한다 (영어 영역이 딸려 오면 통계·배지가 오염된다)
    func test_onlyMathSkillsRemain() {
        let math = Set(Skills.MATH.map { $0.id })
        XCTAssertEqual(math.count, 6)
        XCTAssertTrue(math.allSatisfy { $0.startsWith("m_") }, "수학 영역 id 는 m_ 로 시작해야 함")
        XCTAssertEqual(Skills.ALL, Skills.MATH, "ALL 은 이제 MATH 와 같아야 함")
        XCTAssertEqual(Skills.forSubject(.MATH), Skills.MATH)
    }

    // ---------------- 콘텐츠 ----------------

    func test_everyMathTrackHasEnoughQuestions() throws {
        var grand = 0
        for g in MathGrades.ALL {
            let f = TestSupport.pack(g.trackId)
            XCTAssertTrue(TestSupport.isFile(f), "팩 없음: \(g.trackId)")
            let t = try XCTUnwrap(try TestSupport.track(g.trackId))
            let qs = t.units.flatMap { u in u.lessons.flatMap { $0.questions } }
            XCTAssertTrue(qs.count >= 300, "\(g.trackId): 문제가 너무 적음 (\(qs.count))")
            grand += qs.count
        }
        print("수학 총 문제 수: \(grand)")
        XCTAssertTrue(grand >= 8000, "수학 전체 문제 수 부족: \(grand)")
    }

    func test_mathQuestionsAreWellFormed() throws {
        var ids = Set<String>()
        var skillCount: [String: Int] = [:]
        for g in MathGrades.ALL {
            guard let t = try TestSupport.track(g.trackId) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                XCTAssertTrue(q is Question.Math, "\(g.trackId): 수학 팩에 수학 문제가 아닌 게 있음 (\(q.id))")
                guard let m = q as? Question.Math else { continue }
                XCTAssertTrue(ids.insert(m.id).inserted, "id 중복 \(m.id)")
                XCTAssertTrue(m.prompt.isNotBlank(), "\(m.id): 문제 문구 없음")
                XCTAssertTrue(!m.explain.isNullOrBlank(), "\(m.id): 해설 없음")
                switch m.input {
                case "choice":
                    XCTAssertEqual(m.choices.count, 4, "\(m.id): 선택지 4개 아님")
                    XCTAssertEqual(Set(m.choices).count, 4, "\(m.id): 선택지 중복")
                    XCTAssertTrue(m.choices.indices.contains(m.answerIndex), "\(m.id): 정답 인덱스 범위")
                default:
                    XCTAssertTrue(m.answer.isNotBlank(), "\(m.id): 답 없음")
                }
                if let v = m.visual {
                    XCTAssertTrue(MathVisual.KINDS.contains(v.kind), "\(m.id): 알 수 없는 그림 \(v.kind)")
                }
                skillCount[m.skill, default: 0] += 1
            } } }
        }
        print("수학 영역별 문제 수: \(skillCount)")
        // 여섯 영역 모두 Lv.3(누적 정답 60) 을 찍을 만큼은 있어야 대시보드가 의미를 갖는다
        for s in Skills.MATH {
            let n = skillCount[s.id] ?? 0
            XCTAssertTrue(n >= 70, "수학 영역 '\(s.id)' 문제가 너무 적음: \(n)")
        }
    }

    /// 숫자 입력 문제의 정답이 실제로 채점기를 통과하는지 — 생성기 오타를 잡는다
    func test_mathAnswersPassTheirOwnGrader() throws {
        var checked = 0
        for g in MathGrades.ALL {
            guard let t = try TestSupport.track(g.trackId) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                guard let m = q as? Question.Math else { continue }
                if m.input == "choice" { continue }
                XCTAssertTrue(
                    MathGrader.grade(m.answer, m.answer, m.alts),
                    "\(m.id): 정답 '\(m.answer)' 이 채점기를 통과하지 못함"
                )
                checked += 1
            } } }
        }
        XCTAssertTrue(checked > 1000, "검사한 문제가 없음")
    }

    func test_mathPlacementLaddersAcrossGrades() throws {
        let f = TestSupport.pack("math_placement")
        XCTAssertTrue(TestSupport.isFile(f), "math_placement.json 없음")
        let arr = try JSONObject(TestSupport.readText(f)).getJSONArray("questions")
        var perLevel: [Int: Int] = [:]
        for i in 0..<arr.length() {
            let o = arr.getJSONObject(i)
            let lv = o.getInt("level")
            XCTAssertTrue((1...13).contains(lv), "레벨 범위(1~13)를 벗어남: \(lv)")
            _ = try Question.fromJson(o) // 파싱되는지
            perLevel[lv, default: 0] += 1
        }
        // 학년마다 문제가 있어야 적응형 사다리가 작동한다
        for lv in 1...13 {
            XCTAssertTrue((perLevel[lv] ?? 0) >= 3, "배치고사에 레벨 \(lv) 문제가 없음")
        }
    }
}
