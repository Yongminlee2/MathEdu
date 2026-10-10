import XCTest
@testable import PeepMath

/// 모든 문제팩 JSON 을 파싱해 데이터 무결성을 검증한다 (실기기 없이).
final class ContentPackTests: XCTestCase {

    private func validateQuestion(_ q: Question, _ ids: inout Set<String>, _ where_: String) {
        XCTAssertTrue(ids.insert(q.id).inserted, "\(where_): id 중복 \(q.id)")
        switch q {
        case let q as Question.Mcq:
            XCTAssertTrue(q.choices.count == 4, "\(where_)/\(q.id): 선택지 4개")
            XCTAssertTrue(q.choices.indices.contains(q.answer), "\(where_)/\(q.id): 정답 범위")
            XCTAssertTrue(Set(q.choices).count == 4, "\(where_)/\(q.id): 선택지 중복")
            XCTAssertTrue(q.prompt.isNotBlank(), "\(where_)/\(q.id): 프롬프트 비어있음")
        case let q as Question.ListenMcq:
            XCTAssertTrue(q.choices.count == 4, "\(where_)/\(q.id): 선택지 4개")
            XCTAssertTrue(q.choices.indices.contains(q.answer), "\(where_)/\(q.id): 정답 범위")
            XCTAssertTrue(Set(q.choices).count == 4, "\(where_)/\(q.id): 선택지 중복")
            XCTAssertTrue(q.tts.isNotBlank(), "\(where_)/\(q.id): tts 비어있음")
        case let q as Question.Dictation:
            XCTAssertTrue(q.tts.isNotBlank(), "\(where_)/\(q.id): tts")
            XCTAssertTrue(q.answer.isNotBlank(), "\(where_)/\(q.id): answer")
        case let q as Question.Order:
            XCTAssertTrue(q.tokens.count >= 2, "\(where_)/\(q.id): 토큰 2개 이상")
            XCTAssertTrue(q.ko.isNotBlank(), "\(where_)/\(q.id): ko")
        case let q as Question.TypeTranslate:
            XCTAssertTrue(q.answer.isNotBlank(), "\(where_)/\(q.id): answer")
            XCTAssertTrue(q.ko.isNotBlank(), "\(where_)/\(q.id): ko")
        case is Question.Math:
            break   // 수학 전용 앱 — 수학 문제가 정상 (MathTests 가 따로 검증한다)
        case let q as Question.Match:
            XCTAssertTrue(q.pairs.count == 5, "\(where_)/\(q.id): 5쌍")
            XCTAssertTrue(Set(q.pairs.map { $0.first }).count == 5, "\(where_)/\(q.id): 왼쪽 중복")
            XCTAssertTrue(Set(q.pairs.map { $0.second }).count == 5, "\(where_)/\(q.id): 오른쪽 중복")
        case let q as Question.Speak:
            XCTAssertTrue(q.en.isNotBlank(), "\(where_)/\(q.id): en")
        case let q as Question.ListenDialog:
            // 토익 대화는 2줄+, 토플 강의는 1인 독백(1줄)도 허용
            XCTAssertTrue(q.lines.isNotEmpty(), "\(where_)/\(q.id): 대사 없음")
            XCTAssertTrue(q.lines.allSatisfy { $0.second.isNotBlank() }, "\(where_)/\(q.id): 빈 대사")
            XCTAssertTrue(q.choices.count == 4, "\(where_)/\(q.id): 선택지 4개")
            XCTAssertTrue(q.choices.indices.contains(q.answer), "\(where_)/\(q.id): 정답 범위")
        default:
            XCTFail("\(where_)/\(q.id): 모르는 문제 종류")
        }
    }

    func test_allTracksParseAndValidate() throws {
        var ids = Set<String>()
        var total = 0
        for tid in Subject.MATH.tracks {
            XCTAssertTrue(TestSupport.isFile(TestSupport.pack(tid)), "팩 없음: \(tid)")
            guard let t = try TestSupport.track(tid) else { continue }
            XCTAssertTrue(t.units.isNotEmpty(), "\(tid): 유닛 없음")
            for u in t.units {
                XCTAssertTrue(u.lessons.isNotEmpty(), "\(tid)/\(u.id): 레슨 없음")
                for l in u.lessons {
                    XCTAssertTrue(l.questions.isNotEmpty(), "\(tid)/\(u.id)/\(l.id): 문제 없음")
                    for q in l.questions {
                        validateQuestion(q, &ids, "\(tid)/\(u.id)/\(l.id)")
                        total += 1
                    }
                }
            }
        }
        print("총 문제 수(트랙): \(total)")
        XCTAssertTrue(total >= 5000, "문제가 너무 적음: \(total)")
    }

    func test_everyQuestionHasKnownSkill() throws {
        let valid = Set(Skills.MATH.map { $0.id })
        // 영어 앱에서 쓰던 영역 id — 팩에 섞여 들어오면 영어 콘텐츠가 딸려 온 것이다
        let engSkills: Set<String> = ["listening", "speaking", "writing", "grammar", "reading", "vocab"]
        var counts: [String: Int] = [:]
        for tid in Subject.MATH.tracks {
            guard let t = try TestSupport.track(tid) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                XCTAssertTrue(valid.contains(q.skill), "\(tid)/\(q.id): 알 수 없는 영역 '\(q.skill)'")
                XCTAssertTrue(!engSkills.contains(q.skill), "\(tid)/\(q.id): 수학 팩에 영어 영역이 섞임")
                counts[q.skill, default: 0] += 1
            } } }
        }
        print("영역별 문제 수: \(counts)")
        // 여섯 영역 모두 Lv.3(누적 정답 60) 이상 올릴 만큼은 있어야 배지·종합 레벨이 달성 가능하다
        for s in valid {
            let n = counts[s] ?? 0
            XCTAssertTrue(n >= 70, "영역 '\(s)' 문제가 너무 적어 Lv.3 달성 불가: \(n)")
        }
    }

    func test_placementPoolValid() throws {
        let f = TestSupport.pack("math_placement")
        XCTAssertTrue(TestSupport.isFile(f), "math_placement.json 없음")
        let arr = try JSONObject(TestSupport.readText(f)).getJSONArray("questions")
        var perLevel: [Int: Int] = [:]
        var ids = Set<String>()
        for i in 0..<arr.length() {
            let o = arr.getJSONObject(i)
            let lv = o.getInt("level")
            XCTAssertTrue((1...13).contains(lv), "레벨 범위")
            let q = try Question.fromJson(o)
            // 수학 배치고사는 빠르게 넘겨야 해서 선다형이거나 숫자 답이어야 한다
            XCTAssertTrue(q is Question.Math, "배치고사 문제 타입")
            validateQuestion(q, &ids, "placement")
            perLevel[lv, default: 0] += 1
        }
        for lv in 1...13 {
            XCTAssertTrue((perLevel[lv] ?? 0) >= 4, "레벨 \(lv) 문제 4개 이상 필요 (현재 \(perLevel[lv] ?? 0))")
        }
    }

    func test_orderTilesReconstructable() throws {
        // order 문제: 정답 문장이 타일만으로 만들어지는지 확인
        for tid in Subject.MATH.tracks {
            guard let t = try TestSupport.track(tid) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                guard let q = q as? Question.Order else { continue }
                let bank = q.tokens + q.extras
                for tok in q.tokens where bank.filter({ $0 == tok }).count < q.tokens.filter({ $0 == tok }).count {
                    XCTFail("\(q.id): 타일 부족 '\(tok)'")
                }
            } } }
        }
    }
}
