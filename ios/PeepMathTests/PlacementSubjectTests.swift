import XCTest
@testable import PeepMath

/// 과목별 배치고사가 서로 섞이지 않는지 검증한다
final class PlacementSubjectTests: XCTestCase {

    func test_eachSubjectHasItsOwnLevelRange() {
        XCTAssertEqual(Placement.maxLevel(.ENGLISH), 10)
        XCTAssertEqual(Placement.maxLevel(.MATH), 13)
        // 수학은 13단계까지 올라갈 수 있어야 한다
        XCTAssertEqual(Placement.nextLevel(13, true, 13), 13)
        XCTAssertEqual(Placement.nextLevel(10, true, 10), 10)
        XCTAssertEqual(Placement.nextLevel(1, false, 13), 1)
    }

    func test_progressKeysAreSeparatePerSubject() {
        XCTAssertNotEqual(Placement.levelKey(.ENGLISH), Placement.levelKey(.MATH))
        XCTAssertNotEqual(Placement.doneKey(.ENGLISH), Placement.doneKey(.MATH))
        XCTAssertEqual(Placement.levelKey(.ENGLISH), "placement_level")
        XCTAssertEqual(Placement.levelKey(.MATH), "math_placement_level")
    }

    // 이름은 문자열 리소스라 여기서는 **레벨 → 학년 짝짓기**가 맞는지만 본다
    func test_levelsMapToTheRightGrade() {
        XCTAssertEqual(MathGrades.forLevel(1).titleRes, MathGrades.ALL.first!.titleRes)
        XCTAssertEqual(MathGrades.forLevel(13).titleRes, MathGrades.ALL.last!.titleRes)
        // 범위를 벗어난 레벨도 양 끝으로 안전하게 눌린다
        XCTAssertEqual(MathGrades.forLevel(0).titleRes, MathGrades.ALL.first!.titleRes)
        XCTAssertEqual(MathGrades.forLevel(99).titleRes, MathGrades.ALL.last!.titleRes)
    }

    /// 수학 전용 앱 — 영어 배치고사 팩이 섞여 들어오면 안 된다
    func test_onlyMathPlacementPackShips() throws {
        let math = try JSONObject(TestSupport.readText(TestSupport.pack("math_placement"))).getJSONArray("questions")
        XCTAssertTrue(math.length() > 0)
        XCTAssertTrue(!TestSupport.isFile(TestSupport.pack("placement")), "영어 배치고사 팩은 삐약영어에 있다")
        for i in 0..<math.length() {
            XCTAssertTrue((1...13).contains(math.getJSONObject(i).getInt("level")))
        }
    }

    /// 시작 화면이 팩을 전부 파싱하지 않도록 색인이 있어야 한다
    func test_packIndexCoversEveryTrack() throws {
        let f = TestSupport.pack("index")
        XCTAssertTrue(TestSupport.isFile(f), "index.json 이 없으면 시작 화면이 팩 전체를 파싱한다")
        let idx = try JSONObject(TestSupport.readText(f))
        for tid in ContentRepo.TRACK_IDS {
            XCTAssertTrue(idx.has(tid), "색인에 트랙 없음: \(tid)")
            let e = idx.getJSONObject(tid)
            XCTAssertTrue(e.getInt("lessons") > 0, "\(tid): 레슨 수가 0")
            XCTAssertTrue(e.getInt("questions") > 0, "\(tid): 문제 수가 0")
        }
        // 색인의 합이 실제 규모와 맞는지 (대략)
        let totalLessons = ContentRepo.TRACK_IDS.sumOf { idx.getJSONObject($0).getInt("lessons") }
        XCTAssertTrue(totalLessons > 1000, "전체 레슨 수가 이상함: \(totalLessons)")
    }
}
