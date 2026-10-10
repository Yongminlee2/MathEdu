import Foundation

// sealed class Tok → 클래스 상속: `Formula.Tok` 밑에 `Formula.Tok.Txt` 같은 final class. 검사는 `t as? Formula.Tok.Frac`.
// 정규식은 자바와 같게 \d·\s 를 ASCII 로 풀어 쓰고, 위치는 코틀린처럼 UTF-16 단위로 센다.

/// 수식 텍스트 파서.
///
/// 팩의 prompt(`lim(n→∞) (3n + 5)/(4n + 2)`, `log_3 27`, `5^2`, `∫₀^3 3x^2 dx`, `√144`, `3/4`)를
/// 토큰 트리로 바꿔 FormulaView 가 진짜 수학 기호로 그린다.
/// 원칙: **어떤 입력에도 실패하지 않는다.** 못 알아보면 그냥 글자(Txt)로 남긴다.
enum Formula {

    class Tok {
        fileprivate init() {}
    }

    private static let SUB_DIGITS: [Character: Character] = [
        "₀": "0", "₁": "1", "₂": "2", "₃": "3", "₄": "4",
        "₅": "5", "₆": "6", "₇": "7", "₈": "8", "₉": "9",
    ]

    /// 수식으로 그릴 가치가 있는 prompt 인가 (아니면 기존 TextView 가 낫다)
    static func looksLikeMath(_ prompt: String) -> Bool {
        prompt.contains("lim(") || prompt.contains("∫") || prompt.contains("log_") ||
            prompt.contains("√") || containsMatch(#"\^[0-9]"#, prompt) ||
            containsMatch(#"[0-9]+/[0-9]+"#, prompt) ||
            containsMatch(#"\([^()]+\)/\([^()]+\)"#, prompt)
    }

    /// prompt 전체를 토큰으로. 실패해도 최소한 [Tok.Txt] 전체를 돌려준다
    static func parse(_ prompt: String) -> [Tok] {
        (try? parseChunk(prompt)) ?? [Tok.Txt(prompt)]
    }

    // ---------- 내부 ----------

    private struct ParseError: Error {}

    // 자바 정규식의 \s·\S (ASCII 공백만)
    private static let WS = #"[ \t\n\x0B\f\r]"#
    private static let NWS = #"[^ \t\n\x0B\f\r]"#

    private static func parseChunk(_ s: String) throws -> [Tok] {
        var out: [Tok] = []
        let u = Array(s.utf16)
        var i = 0
        var text: [UInt16] = []

        func flush() {
            if !text.isEmpty { out.append(Tok.Txt(String(decoding: text, as: UTF16.self))); text.removeAll() }
        }

        while i < u.count {
            let rest = s.substring(i)

            // lim(cond) body — body 는 한국어 경계(" 의", " 일 때" 등) 앞까지
            if rest.startsWith("lim(") {
                let close = s.indexOf(")", i + 4)
                if close > 0 {
                    let cond = s.substring(i + 4, close)
                    let bodyEnd = koreanBoundary(u, close + 1)
                    let body = s.substring(close + 1, bodyEnd).trim()
                    flush()
                    out.append(Tok.Lim(cond, try parseChunk(body)))
                    i = bodyEnd
                    continue
                }
            }

            // ∫₀^N body dx
            if u[i] == 0x222B { // '∫'
                if let m = find("^∫([₀₁₂₃₄₅₆₇₈₉]+)\\^(\(NWS)+)\(WS)+(.*?)\(WS)*d([a-z])", rest) {
                    let lo = String(m[1].map { SUB_DIGITS[$0] ?? $0 })
                    flush()
                    out.append(Tok.Integral(lo, m[2], try parseChunk(m[3] + " d" + m[4])))
                    i += m[0].length
                    continue
                }
            }

            // log_b
            if rest.startsWith("log_") {
                // 코틀린은 여기서 !! 로 죽고 parse 가 통째로 Txt 로 돌려준다
                guard let m = find("^log_(\(NWS)+)", rest) else { throw ParseError() }
                flush()
                out.append(Tok.Txt("log"))
                out.append(Tok.Sub(m[1]))
                i += m[0].length
                continue
            }

            // √144 · √(x+1)
            if u[i] == 0x221A { // '√'
                if let m = find(#"^√([0-9]+|\([^()]+\))"#, rest) {
                    let body = m[1].removeSurrounding("(", ")")
                    flush()
                    out.append(Tok.Sqrt(try parseChunk(body)))
                    i += m[0].length
                    continue
                }
            }

            // 아래 정규식은 모두 첫 글자가 정해져 있어 그 글자일 때만 돌린다 (결과는 같고 빠르다)
            let c = u[i]
            // (num)/(den) — 괄호 분수 (lim 본문 등)
            if c == 0x28, let m = find(#"^\(([^()]+)\)/\(([^()]+)\)"#, rest) {
                flush()
                out.append(Tok.Frac(try parseChunk(m[1]), try parseChunk(m[2])))
                i += m[0].length
                continue
            }
            // 3/4 — 숫자 분수 (날짜 같은 게 없는 콘텐츠라 안전)
            if c >= 0x30 && c <= 0x39, let n = find(#"^([0-9]+)/([0-9]+)"#, rest) {
                flush()
                out.append(Tok.Frac([Tok.Txt(n[1])], [Tok.Txt(n[2])]))
                i += n[0].length
                continue
            }
            // a^b — 지수
            if c == 0x5E, let p = find(#"^\^([0-9]+|\([^()]+\))"#, rest) {
                flush()
                out.append(Tok.Sup(p[1].removeSurrounding("(", ")")))
                i += p[0].length
                continue
            }
            text.append(u[i])
            i += 1
        }
        flush()
        return merge(out)
    }

    /// "lim 본문이 어디까지인가" — 한국어 조사·구두점 앞에서 끊는다
    private static func koreanBoundary(_ u: [UInt16], _ from: Int) -> Int {
        var j = from
        while j < u.count {
            let c = u[j]
            if (c >= 0xAC00 && c <= 0xD7A3) || c == 0x3F /* ? */ || c == 0x0A { return j }
            j += 1
        }
        return u.count
    }

    /// 이어진 Txt 를 합친다 (렌더 단순화)
    private static func merge(_ toks: [Tok]) -> [Tok] {
        var out: [Tok] = []
        for t in toks {
            if let a = t as? Tok.Txt, let last = out.last as? Tok.Txt {
                out[out.count - 1] = Tok.Txt(last.s + a.s)
            } else {
                out.append(t)
            }
        }
        return out
    }

    // MARK: - 정규식 도우미

    private static var regexCache: [String: NSRegularExpression] = [:]

    private static func regex(_ pattern: String) -> NSRegularExpression {
        if let r = regexCache[pattern] { return r }
        let r = try! NSRegularExpression(pattern: pattern)
        regexCache[pattern] = r
        return r
    }

    /// 코틀린 `Regex(p).find(s)?.groupValues` — 못 찾으면 nil, 안 걸린 묶음은 ""
    private static func find(_ pattern: String, _ s: String) -> [String]? {
        let ns = s as NSString
        guard let m = regex(pattern).firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return (0..<m.numberOfRanges).map {
            let r = m.range(at: $0)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
    }

    private static func containsMatch(_ pattern: String, _ s: String) -> Bool {
        regex(pattern).firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
    }
}

extension Formula.Tok {
    /// 그냥 글자 (한글 문장 포함)
    final class Txt: Formula.Tok {
        let s: String
        init(_ s: String) { self.s = s }
    }

    /// 직전 내용의 오른쪽 위 작은 글자 (지수)
    final class Sup: Formula.Tok {
        let s: String
        init(_ s: String) { self.s = s }
    }

    /// 직전 내용의 오른쪽 아래 작은 글자 (log 밑 등)
    final class Sub: Formula.Tok {
        let s: String
        init(_ s: String) { self.s = s }
    }

    /// 세로 분수
    final class Frac: Formula.Tok {
        let num: [Formula.Tok]
        let den: [Formula.Tok]
        init(_ num: [Formula.Tok], _ den: [Formula.Tok]) { self.num = num; self.den = den }
    }

    /// 근호 — 윗줄이 body 를 덮는다
    final class Sqrt: Formula.Tok {
        let body: [Formula.Tok]
        init(_ body: [Formula.Tok]) { self.body = body }
    }

    /// lim — 아래에 조건(n→∞)이 붙고 body 가 이어진다
    final class Lim: Formula.Tok {
        let cond: String
        let body: [Formula.Tok]
        init(_ cond: String, _ body: [Formula.Tok]) { self.cond = cond; self.body = body }
    }

    /// 정적분 — ∫ 위에 상한, 아래에 하한
    final class Integral: Formula.Tok {
        let lo: String
        let hi: String
        let body: [Formula.Tok]
        init(_ lo: String, _ hi: String, _ body: [Formula.Tok]) { self.lo = lo; self.hi = hi; self.body = body }
    }
}
