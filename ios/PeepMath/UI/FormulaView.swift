import UIKit

/// Formula 토큰을 진짜 수학 기호처럼 그린다 — 세로 분수, log 아래첨자,
/// lim 아래 조건, 근호 윗줄, ∫ 상·하한. 한글 문장 토큰은 그대로 글자로 흘린다.
/// 긴 문장은 토큰(원자 상자) 단위로 줄바꿈한다. 분수·근호·lim·∫ 는 쪼개지 않는다.
final class FormulaView: View {

    private typealias Tok = Formula.Tok

    private var toks: [Formula.Tok] = []

    private lazy var main: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#3E2723")
        p.textSize = sp(24)
        p.isFakeBoldText = true
        return p
    }()
    private lazy var script: Paint = {
        let p = Paint(main); p.textSize = main.textSize * 0.58; return p
    }()
    private lazy var rule: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#3E2723")
        p.strokeWidth = dp(1.8)
        p.strokeCap = .ROUND
        return p
    }()

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
    }
    required init?(coder: NSCoder) { fatalError() }

    func setFormula(_ prompt: String) {
        toks = Formula.parse(prompt)
        requestLayout()
        invalidate()
    }

    // ---------- 상자 측정 ----------
    /// 토큰 하나의 렌더 상자: 폭, 기준선 위 높이(ascent), 기준선 아래 깊이(descent)
    private struct Box { let w: Float; let asc: Float; let desc: Float }

    private func textBox(_ p: Paint, _ s: String) -> Box {
        let fm = p.fontMetrics
        return Box(w: p.measureText(s), asc: -fm.ascent, desc: fm.descent)
    }

    private func measure(_ t: Tok) -> Box {
        if let t = t as? Tok.Txt { return textBox(main, t.s) }
        if let t = t as? Tok.Sup {
            let it = textBox(script, t.s)
            return Box(w: it.w + dp(1), asc: it.asc + main.textSize * 0.42, desc: 0)
        }
        if let t = t as? Tok.Sub {
            let it = textBox(script, t.s)
            return Box(w: it.w + dp(1), asc: 0, desc: it.desc + main.textSize * 0.28)
        }
        if let t = t as? Tok.Frac {
            let n = measureRow(t.num); let d = measureRow(t.den)
            let w = max(n.w, d.w) + dp(10)
            // 가로줄이 기준선 살짝 위 — 위로 분자 전체, 아래로 분모 전체
            return Box(w: w, asc: n.asc + n.desc + dp(7), desc: d.asc + d.desc + dp(5))
        }
        if let t = t as? Tok.Sqrt {
            let b = measureRow(t.body)
            return Box(w: b.w + main.textSize * 0.62 + dp(4), asc: b.asc + dp(6), desc: b.desc)
        }
        if let t = t as? Tok.Lim {
            let limW = main.measureText("lim")
            let condW = script.measureText(t.cond)
            let head = max(limW, condW)
            let b = measureRow(t.body)
            return Box(w: head + dp(6) + b.w, asc: max(textBox(main, "lim").asc, b.asc),
                       desc: max(script.textSize + dp(3), b.desc))
        }
        if let t = t as? Tok.Integral {
            let glyph = main.textSize * 1.45
            let boundW = max(script.measureText(t.hi), script.measureText(t.lo))
            let b = measureRow(t.body)
            return Box(w: main.measureText("∫") * (glyph / main.textSize) + boundW + dp(6) + b.w,
                       asc: max(glyph * 0.72, b.asc), desc: max(glyph * 0.38, b.desc))
        }
        return Box(w: 0, asc: 0, desc: 0)
    }

    private func measureRow(_ row: [Tok]) -> Box {
        var w: Float = 0; var asc: Float = 0; var desc: Float = 0
        for t in row {
            let b = measure(t)
            w += b.w
            asc = max(asc, b.asc)
            desc = max(desc, b.desc)
        }
        return Box(w: w, asc: asc, desc: desc)
    }

    // ---------- 줄바꿈 ----------
    /// Txt 는 단어 단위로 쪼개고 나머지는 원자 상자로 취급해 greedy 줄바꿈
    private func flatten() -> [Tok] {
        var out: [Tok] = []
        for t in toks {
            if let txt = t as? Tok.Txt {
                // 공백을 유지한 채 단어 단위로
                var word = ""
                for c in txt.s {
                    word.append(c)
                    if c == " " { out.append(Tok.Txt(word)); word = "" }
                }
                if !word.isEmpty { out.append(Tok.Txt(word)) }
            } else { out.append(t) }
        }
        return out
    }

    private func buildLines(_ maxW: Float) -> [[Tok]] {
        var lines: [[Tok]] = []
        var line: [Tok] = []
        var w: Float = 0
        for t in flatten() {
            let bw = measure(t).w
            if w + bw > maxW && !line.isEmpty {
                lines.append(line); line = []; w = 0
            }
            line.append(t); w += bw
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }

    override func onMeasure(_ widthMeasureSpec: Int, _ heightMeasureSpec: Int) {
        let w = MeasureSpec.getSize(widthMeasureSpec)
        let lines = buildLines(Float(w) - dp(8))
        var h = dp(8)
        for line in lines {
            let b = measureRow(line)
            h += b.asc + b.desc + dp(6)
        }
        setMeasuredDimension(w, h.toInt().coerceAtLeast(dp(40).toInt()))
    }

    // ---------- 그리기 ----------
    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        var y = dp(4)
        for line in buildLines(Float(width) - dp(8)) {
            let b = measureRow(line)
            let base = y + b.asc
            var x = dp(4)
            for t in line { x = drawTok(canvas, t, x, base) }
            y += b.asc + b.desc + dp(6)
        }
    }

    /// 토큰 하나를 (x, 기준선 base) 에 그리고 다음 x 를 돌려준다
    private func drawTok(_ canvas: Canvas, _ t: Tok, _ x: Float, _ base: Float) -> Float {
        if let t = t as? Tok.Txt {
            canvas.drawText(t.s, x, base, main)
            return x + main.measureText(t.s)
        }
        if let t = t as? Tok.Sup {
            canvas.drawText(t.s, x + dp(1), base - main.textSize * 0.42, script)
            return x + script.measureText(t.s) + dp(1)
        }
        if let t = t as? Tok.Sub {
            canvas.drawText(t.s, x + dp(1), base + main.textSize * 0.28, script)
            return x + script.measureText(t.s) + dp(1)
        }
        if let t = t as? Tok.Frac {
            let n = measureRow(t.num); let d = measureRow(t.den)
            let w = max(n.w, d.w) + dp(10)
            let mid = base - main.textSize * 0.30   // 가로줄 위치
            // 분자 (가로줄 위에)
            var nx = x + (w - n.w) / 2
            for tt in t.num { nx = drawTok(canvas, tt, nx, mid - dp(5) - n.desc) }
            // 가로줄
            canvas.drawLine(x + dp(2), mid, x + w - dp(2), mid, rule)
            // 분모
            var dx = x + (w - d.w) / 2
            for tt in t.den { dx = drawTok(canvas, tt, dx, mid + dp(5) + d.asc) }
            return x + w
        }
        if let t = t as? Tok.Sqrt {
            let b = measureRow(t.body)
            let hook = main.textSize * 0.62
            let top = base - b.asc - dp(4)
            // 근호: 체크 모양 + 윗줄
            canvas.drawLine(x, base - b.asc * 0.35, x + hook * 0.35, base + b.desc, rule)
            canvas.drawLine(x + hook * 0.35, base + b.desc, x + hook, top, rule)
            canvas.drawLine(x + hook, top, x + hook + b.w + dp(4), top, rule)
            var bx = x + hook + dp(2)
            for tt in t.body { bx = drawTok(canvas, tt, bx, base) }
            return x + hook + b.w + dp(6)
        }
        if let t = t as? Tok.Lim {
            let limW = main.measureText("lim")
            let condW = script.measureText(t.cond)
            let head = max(limW, condW)
            canvas.drawText("lim", x + (head - limW) / 2, base, main)
            canvas.drawText(t.cond, x + (head - condW) / 2, base + script.textSize + dp(2), script)
            var bx = x + head + dp(6)
            for tt in t.body { bx = drawTok(canvas, tt, bx, base) }
            return bx
        }
        if let t = t as? Tok.Integral {
            let big = Paint(main)
            big.textSize = main.textSize * 1.45
            big.isFakeBoldText = false
            let gw = big.measureText("∫")
            canvas.drawText("∫", x, base + big.textSize * 0.18, big)
            let boundX = x + gw + dp(1)
            canvas.drawText(t.hi, boundX, base - main.textSize * 0.62, script)
            canvas.drawText(t.lo, boundX, base + main.textSize * 0.34, script)
            let boundW = max(script.measureText(t.hi), script.measureText(t.lo))
            var bx = boundX + boundW + dp(5)
            for tt in t.body { bx = drawTok(canvas, tt, bx, base) }
            return bx
        }
        return x
    }

    private func dp(_ v: Float) -> Float { v * context.resources.displayMetrics.density }
    private func sp(_ v: Float) -> Float { v * context.resources.displayMetrics.scaledDensity }
}
