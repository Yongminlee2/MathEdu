import Foundation

/// 수학 답 채점. 아이가 쓰는 여러 표기를 같은 답으로 인정한다.
/// 0.75 = 3/4 = 6/8, 1,200 = 1200, "12 cm" = 12
enum MathGrader {

    /// 답을 수로 해석. 분수(3/4)·소수·쉼표·단위 문자를 다룬다. 실패하면 nil
    static func parse(_ raw: String) -> Double? {
        var s = raw.trim().lowercase()
            .replace(",", "")
            .replace(" ", "")
        if s.isEmpty { return nil }
        // 뒤에 붙은 단위 제거 (cm · kg · 개 · pcs · шт …) — 모든 문자 체계의 글자(isLetter)
        s = trimEndLetters(s)
        if s.isEmpty { return nil }
        // 대분수 1과2/3 형태는 다루지 않는다 (생성기가 만들지 않음)
        if s.contains("/") {
            let parts = s.split("/")
            if parts.count != 2 { return nil }
            guard let n = toDoubleOrNull(parts[0]) else { return nil }
            guard let d = toDoubleOrNull(parts[1]) else { return nil }
            return d == 0.0 ? nil : n / d
        }
        return toDoubleOrNull(s)
    }

    /// 수치가 같은지 (부동소수 오차 허용)
    static func sameNumber(_ a: String, _ b: String) -> Bool {
        guard let x = parse(a) else { return false }
        guard let y = parse(b) else { return false }
        return abs(x - y) < 1e-6
    }

    /// 식·문자 답 정규화: 공백 제거, 곱셈기호 통일, x·X 통일
    static func normalizeExpr(_ s: String) -> String {
        s.lowercase()
            .replace(" ", "")
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("＝", "=")
    }

    /// 숫자 입력 채점. 정답과 대안 답 중 하나와 수치가 같으면 정답.
    /// 수로 해석되지 않으면 식으로 보고 문자열 비교한다.
    static func grade(_ input: String, _ answer: String, _ alts: [String] = []) -> Bool {
        if input.isBlank() { return false }
        let candidates = [answer] + alts
        // 1) 수치 비교
        if parse(input) != nil {
            if candidates.contains(where: { sameNumber(input, $0) }) { return true }
        }
        // 2) 식 비교
        let inN = normalizeExpr(input)
        return candidates.contains { normalizeExpr($0) == inN }
    }

    /// 분수를 기약분수로 (해설 생성·표시용)
    static func reduce(_ n: Int, _ d: Int) -> (first: Int, second: Int) {
        if d == 0 { return (n, d) }
        let g = gcd(abs(n), abs(d)).coerceAtLeast(1)
        let sign = d < 0 ? -1 : 1
        return (sign * n / g, sign * d / g)
    }

    static func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? a : gcd(b, a % b) }

    static func lcm(_ a: Int, _ b: Int) -> Int { (a == 0 || b == 0) ? 0 : abs(a / gcd(a, b) * b) }

    // MARK: - 코틀린과 같게

    /// 코틀린 `trimEnd { it.isLetter() }` — UTF-16 한 단위씩 보므로 BMP 밖 글자는 건드리지 않는다
    private static func trimEndLetters(_ s: String) -> String {
        var scalars = Array(s.unicodeScalars)
        while let last = scalars.last, last.value <= 0xFFFF, isLetter(last) { scalars.removeLast() }
        var out = ""
        out.unicodeScalars.append(contentsOf: scalars)
        return out
    }

    private static func isLetter(_ v: Unicode.Scalar) -> Bool {
        switch v.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter: return true
        default: return false
        }
    }

    /// 코틀린 `toDoubleOrNull()` — 십진 표기만 받는다 (스위프트 Double() 은 0x10·inf 도 받아서 다르다)
    private static func toDoubleOrNull(_ s: String) -> Double? {
        let ok = s.range(of: #"^[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?$"#, options: .regularExpression) != nil
        return ok ? Double(s) : nil
    }
}
