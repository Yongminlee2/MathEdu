import XCTest
@testable import PeepMath

/// 팩에 실제로 들어 있는 그림 명세(kind)를 앱이 전부 파싱할 수 있는지 검사한다.
/// (상수만 추가하고 KINDS 화이트리스트에 안 넣으면 fromJson 이 조용히 버린다)
final class VisualKindsTests: XCTestCase {

    /// MathVisual.kt 소스에서 KINDS 블록 안의 상수들을 실제 문자열 값으로 푼다
    private func whitelistedKinds() -> Set<String> {
        let src = RepoPaths.read("java/com/piyak/english/model/MathVisual.kt")
        // const val COORD3D = "coord3d" 꼴 전부 수집
        var constValues: [String: String] = [:]
        for g in TestSupport.findAll(#"const val (\w+) = "([a-z0-9_]+)""#, src) { constValues[g[1]] = g[2] }
        // 블록 안 주석에 괄호가 있어도 안전하게, 닫는 줄("        )")까지 통째로 잡는다
        let kindsBlock = TestSupport.find(#"val KINDS = setOf\((.*?)\n        \)"#, src, .dotMatchesLineSeparators)?[1] ?? ""
        return Set(TestSupport.findAll(#"\b([A-Z][A-Z0-9_]+)\b"#, kindsBlock).compactMap { constValues[$0[1]] })
    }

    func test_팩의_모든_그림_kind_를_앱이_파싱할_수_있다() {
        let whitelist = whitelistedKinds()
        XCTAssertTrue(whitelist.count >= 20, "화이트리스트 추출 실패 (\(whitelist.count)종)")
        // 아이폰판 화이트리스트도 안드로이드와 같아야 한다
        XCTAssertEqual(MathVisual.KINDS, whitelist, "MathVisual.swift 의 KINDS 가 MathVisual.kt 와 다르다")

        let files = (try? FileManager.default.contentsOfDirectory(atPath: TestSupport.packsDir().path)) ?? []
        var missing = Set<String>()
        for n in files where n.hasPrefix("math_") && n.hasSuffix(".json") {
            let text = TestSupport.readText(TestSupport.packsDir().appendingPathComponent(n))
            for m in TestSupport.findAll(#""kind"\s*:\s*"([a-z0-9_]+)""#, text) {
                if !whitelist.contains(m[1]) { missing.insert(m[1]) }
            }
        }
        XCTAssertTrue(
            missing.isEmpty,
            "팩에는 있는데 MathVisual.KINDS 에 없는 kind: \(missing.sorted()) — fromJson 이 조용히 버려서 그림이 안 나온다!"
        )
    }
}
