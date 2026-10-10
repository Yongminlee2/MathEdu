import XCTest
@testable import PeepMath

/// 수식 파서 검사. 팩 전체를 전수로 돌려 어떤 문제에서도 실패가 없고
/// 주요 패턴(lim·log·∫·√·분수·지수)이 실제로 수식 토큰으로 바뀌는지 확인한다.
final class FormulaTests: XCTestCase {

    typealias Tok = Formula.Tok

    private func contains(_ toks: [Tok], _ pred: (Tok) -> Bool) -> Bool {
        toks.contains { t in
            if pred(t) { return true }
            switch t {
            case let f as Tok.Frac: return contains(f.num, pred) || contains(f.den, pred)
            case let s as Tok.Sqrt: return contains(s.body, pred)
            case let l as Tok.Lim: return contains(l.body, pred)
            case let g as Tok.Integral: return contains(g.body, pred)
            default: return false
            }
        }
    }

    func test_everyPromptParsesWithoutCrashing() throws {
        var total = 0
        var lim = 0, integral = 0, log = 0, sqrt = 0, frac = 0, sup = 0
        for tid in Subject.MATH.tracks + ["math_placement"] {
            let f = TestSupport.pack(tid)
            if !TestSupport.isFile(f) { continue }
            let root = try JSONObject(TestSupport.readText(f))
            var prompts: [String] = []
            if root.has("questions") {
                let arr = root.getJSONArray("questions")
                for i in 0..<arr.length() { prompts.append(arr.getJSONObject(i).getString("prompt")) }
            } else {
                let units = root.getJSONArray("units")
                for u in 0..<units.length() {
                    let lessons = units.getJSONObject(u).getJSONArray("lessons")
                    for l in 0..<lessons.length() {
                        let qs = lessons.getJSONObject(l).getJSONArray("questions")
                        for q in 0..<qs.length() { prompts.append(qs.getJSONObject(q).getString("prompt")) }
                    }
                }
            }
            for p in prompts {
                let toks = Formula.parse(p)
                XCTAssertTrue(toks.isNotEmpty(), "\(tid): 파스 결과가 비었다: \(p)")
                total += 1
                if contains(toks, { $0 is Tok.Lim }) { lim += 1 }
                if contains(toks, { $0 is Tok.Integral }) { integral += 1 }
                if contains(toks, { $0 is Tok.Sqrt }) { sqrt += 1 }
                if contains(toks, { $0 is Tok.Frac }) { frac += 1 }
                if contains(toks, { $0 is Tok.Sup }) { sup += 1 }
                if contains(toks, { $0 is Tok.Sub }) { log += 1 }
            }
        }
        print("전수 \(total) · lim \(lim) · ∫ \(integral) · log(Sub) \(log) · √ \(sqrt) · 분수 \(frac) · 지수 \(sup)")
        XCTAssertTrue(total > 12000, "전수가 너무 적다")
        XCTAssertTrue(lim >= 50, "lim 검출 없음")
        XCTAssertTrue(integral >= 100, "∫ 검출 없음")
        XCTAssertTrue(log >= 20, "log 검출 없음")
        XCTAssertTrue(sqrt >= 80, "√ 검출 없음")
        XCTAssertTrue(frac >= 100, "분수 검출 없음")
        XCTAssertTrue(sup >= 100, "지수 검출 없음")
    }

    func test_knownShapesParseCorrectly() throws {
        // lim — 조건이 아래로, 본문은 세로 분수
        let lim = Formula.parse("lim(n→∞) (3n + 5)/(4n + 2) 의 값은?")
        let lims = lim.compactMap { $0 as? Tok.Lim }
        XCTAssertEqual(lims.count, 1)
        let l = try XCTUnwrap(lims.first)
        XCTAssertEqual(l.cond, "n→∞")
        XCTAssertTrue(l.body.contains { $0 is Tok.Frac })
        // 뒤의 한국어는 평문으로 남는다
        XCTAssertTrue(lim.last is Tok.Txt)

        // 정적분 — 하한 0, 상한 3
        let integ = Formula.parse("∫₀^3 3x^2 dx 의 값은?")
        let gs = integ.compactMap { $0 as? Tok.Integral }
        XCTAssertEqual(gs.count, 1)
        let g = try XCTUnwrap(gs.first)
        XCTAssertEqual(g.lo, "0")
        XCTAssertEqual(g.hi, "3")
        XCTAssertTrue(contains(g.body) { ($0 as? Tok.Sup)?.s == "2" }, "본문 지수가 위첨자로")

        // log 밑
        let log = Formula.parse("log_3 27 의 값은?")
        let subs = log.compactMap { $0 as? Tok.Sub }
        XCTAssertEqual(subs.count, 1)
        XCTAssertEqual(subs.first?.s, "3")

        // 근호
        let sq = Formula.parse("√512 = k√8 일 때 k는?")
        XCTAssertEqual(sq.compactMap { $0 as? Tok.Sqrt }.count, 2)

        // 숫자 분수
        let fr = Formula.parse("1/4 + 2/4 = ?")
        XCTAssertEqual(fr.compactMap { $0 as? Tok.Frac }.count, 2)
    }

    func test_plainKoreanIsNotMistakenForMath() {
        XCTAssertFalse(Formula.looksLikeMath("사과가 몇 개일까요?"))
        XCTAssertFalse(Formula.looksLikeMath("시계를 보고 몇 시인지 골라 보세요."))
        XCTAssertTrue(Formula.looksLikeMath("lim(n→∞) (3n + 5)/(4n + 2) 의 값은?"))
        XCTAssertTrue(Formula.looksLikeMath("√144 의 값은?"))
        XCTAssertTrue(Formula.looksLikeMath("1/4 + 2/4 = ?"))
        // 순수 한글 문장은 파스해도 통째로 Txt 하나
        let toks = Formula.parse("바늘을 끌어서 3시 30분을 만들어 보세요.")
        XCTAssertEqual(toks.count, 1)
        XCTAssertTrue(toks[0] is Tok.Txt)
    }
}
