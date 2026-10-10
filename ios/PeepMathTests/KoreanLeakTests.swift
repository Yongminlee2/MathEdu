import XCTest
@testable import PeepMath

/// **한국어가 외국 폰으로 새어 나가지 않는지** 검사한다.
///
/// 한글이 든 문제문·해설은 반드시 번역 뼈대 키(tk/ek)를 갖고,
/// 그 키의 문자열이 기본 리소스(`values/strings.xml` = 영어)에 있어야 한다.
/// 새 문제를 추가했다면:
///   node tools/i18n/keyify.js --write && node tools/gen_math.js
///   node tools/i18n/gen_strings.js && node tools/i18n/verify.js
final class KoreanLeakTests: XCTestCase {

    private let hangul = "[가-힣]"

    /// values/strings.xml (기본 = 영어) 에 정의된 tpl_* 이름들
    private func englishTemplateKeys() -> Set<String> {
        let xml = RepoPaths.read("res/values/strings.xml")
        return Set(TestSupport.findAll(#"<string name="tpl_([0-9a-f]{8})">"#, xml).map { $0[1] })
    }

    private func mathQuestions() throws -> [JSONObject] {
        var out: [JSONObject] = []
        func walk(_ v: Any?) {
            switch v {
            case let a as JSONArray:
                for i in 0..<a.length() { walk(a.get(i)) }
            case let o as JSONObject:
                if o.optString("type") == "math" { out.append(o) }
                for k in o.keys() { walk(o.opt(k)) }
            default:
                break
            }
        }
        let dir = TestSupport.packsDir()
        let files = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        for n in files.sorted() where n.hasSuffix(".json") {
            walk(try JSONObject(TestSupport.readText(dir.appendingPathComponent(n))))
        }
        return out
    }

    func test_한글_문제와_해설에는_번역_키가_붙어_있다() throws {
        var bad: [String] = []
        for q in try mathQuestions() {
            let id = q.optString("id")
            let prompt = q.optString("prompt")
            if TestSupport.containsMatch(hangul, prompt) && q.optString("tk").isEmpty {
                bad.append("[\(id)] 문제문에 tk 없음: \(prompt.take(60))")
            }
            let explain = q.optString("explain")
            if TestSupport.containsMatch(hangul, explain) && q.optString("ek").isEmpty {
                bad.append("[\(id)] 해설에 ek 없음: \(explain.take(60))")
            }
        }
        XCTAssertTrue(
            bad.isEmpty,
            "번역 키 없는 문장 \(bad.count)건 — 이 문장은 어느 나라 폰에서도 한국어로 뜬다\n" +
                bad.take(10).joined(separator: "\n")
        )
    }

    func test_팩이_쓰는_번역_키는_모두_영어_리소스에_있다() throws {
        let english = englishTemplateKeys()
        var missing: [(String, String)] = []   // 넣은 순서를 지킨다 (LinkedHashMap)
        for q in try mathQuestions() {
            for field in ["tk", "ek"] {
                let key = q.optString(field)
                if key.isNotEmpty() && !english.contains(key) && !missing.contains(where: { $0.0 == key }) {
                    missing.append((key, q.optString("id")))
                }
            }
        }
        XCTAssertTrue(
            missing.isEmpty,
            "영어 리소스에 없는 뼈대 \(missing.count)종 — 영어가 없으면 다른 언어도 기댈 곳이 없다\n" +
                missing.take(10).map { "tpl_\($0.0) (예: \($0.1))" }.joined(separator: "\n")
        )
    }

    func test_기본_리소스는_영어이고_한국어는_values_ko_에_있다() {
        let def = RepoPaths.read("res/values/strings.xml")
        let ko = RepoPaths.read("res/values-ko/strings.xml")
        let appName = #"<string name="app_name">([^<]*)</string>"#
        let defName = TestSupport.find(appName, def)?[1] ?? ""
        let koName = TestSupport.find(appName, ko)?[1] ?? ""
        XCTAssertTrue(!TestSupport.containsMatch(hangul, defName), "기본 리소스(values/)의 app_name 이 한국어다: \(defName)")
        XCTAssertTrue(TestSupport.containsMatch(hangul, koName), "values-ko/ 의 app_name 이 한국어가 아니다: \(koName)")
    }
}
