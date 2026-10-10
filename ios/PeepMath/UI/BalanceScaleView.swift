import UIKit

/// 양팔 저울로 일차방정식 `a·x + b = c` 를 푸는 판.
///
/// 왼쪽 접시에 x 상자 a개와 1짜리 추 b개, 오른쪽에 1짜리 추 c개.
/// 위 손잡이를 끌어 x 상자 하나의 무게를 바꾸면 저울이 실시간으로 기울고, 맞는 값에서 수평이 된다.
final class BalanceScaleView: View {

    private var coef = 3        // x 상자 개수 (a)
    private var leftConst = 2   // 왼쪽 추 (b)
    private var rightConst = 11 // 오른쪽 추 (c)
    private var maxX = 12

    /// 지금 정해 놓은 x 값
    private(set) var guess = 1

    var onChanged: ((Int) -> Void)?

    private let fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private let stroke: Paint = { let p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = .STROKE; return p }()
    private let text: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = .CENTER
        p.isFakeBoldText = true
        p.color = Color.parseColor("#4E342E")
        return p
    }()

    private var sliderLeft: Float = 0
    private var sliderRight: Float = 0
    private var sliderY: Float = 0

    /// 저울대 길이 (뷰 폭 대비)
    static let ARM_RATIO: Float = 0.30

    /// 접시 폭 (뷰 폭 대비). `ARM_RATIO + PAN_RATIO / 2 < 0.5` 여야 접시가 화면 밖으로 안 나간다.
    static let PAN_RATIO: Float = 0.28

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
    }
    required init?(coder: NSCoder) { fatalError() }

    func setEquation(_ coef: Int, _ leftConst: Int, _ rightConst: Int) {
        self.coef = coef.coerceAtLeast(1)
        self.leftConst = leftConst
        self.rightConst = rightConst
        let answer = (rightConst - leftConst) / self.coef
        // 정답이 눈금 한가운데쯤 오도록, 그리고 최소 8칸은 되도록
        maxX = (answer * 2).coerceAtLeast(8).coerceAtMost(20)
        guess = 1
        onChanged?(guess)
        invalidate()
    }

    private func leftWeight(_ x: Int) -> Int { coef * x + leftConst }
    private func rightWeight() -> Int { rightConst }

    /// 지금 값으로 저울이 수평인가
    func isBalanced() -> Bool { leftWeight(guess) == rightWeight() }

    func reset() {
        guess = 1
        onChanged?(guess)
        invalidate()
    }

    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        let w = Float(width)
        let h = Float(height)
        if w <= 0 { return }

        let cx = w / 2
        let pivotY = h * 0.52
        let armLen = w * BalanceScaleView.ARM_RATIO

        // 기울기 — 무게 차이에 비례하되 너무 눕지 않게 잘라 낸다
        let diff = leftWeight(guess) - rightWeight()
        let tilt = (Float(diff) * 2.2).coerceIn(-14, 14)
        let rad = Double(tilt) * Double.pi / 180

        // 받침대
        fill.color = Color.parseColor("#8D6E63")
        let base = Path()
        base.moveTo(cx, pivotY)
        base.lineTo(cx - w * 0.10, h * 0.80)
        base.lineTo(cx + w * 0.10, h * 0.80)
        base.close()
        canvas.drawPath(base, fill)
        canvas.drawRect(cx - w * 0.16, h * 0.80, cx + w * 0.16, h * 0.84, fill)

        // 저울대
        let lx = cx - armLen * Float(cos(rad))
        let ly = pivotY - armLen * Float(sin(rad))
        let rx = cx + armLen * Float(cos(rad))
        let ry = pivotY + armLen * Float(sin(rad))
        stroke.color = Color.parseColor("#5D4037")
        stroke.strokeWidth = dp(6)
        stroke.strokeCap = .ROUND
        canvas.drawLine(lx, ly, rx, ry, stroke)
        fill.color = Color.parseColor("#5D4037")
        canvas.drawCircle(cx, pivotY, dp(7), fill)

        // 접시 + 올려진 것들
        drawPan(canvas, lx, ly, w, left: true)
        drawPan(canvas, rx, ry, w, left: false)

        // 균형 표시
        text.textSize = dp(17)
        text.color = isBalanced() ? Color.parseColor("#2E7D32") : Color.parseColor("#8D6E63")
        canvas.drawText(
            isBalanced() ? context.getString(R.string.balance_ok)
                : (diff > 0 ? context.getString(R.string.balance_left) : context.getString(R.string.balance_right)),
            cx, h * 0.93, text
        )

        drawSlider(canvas, w, h)
    }

    private func drawPan(_ canvas: Canvas, _ x: Float, _ y: Float, _ w: Float, left: Bool) {
        let panW = w * BalanceScaleView.PAN_RATIO
        let panY = y + dp(30)
        stroke.color = Color.parseColor("#A1887F")
        stroke.strokeWidth = dp(2)
        canvas.drawLine(x, y, x, panY, stroke)
        fill.color = Color.parseColor("#D7CCC8")
        canvas.drawRoundRect(RectF(x - panW / 2, panY, x + panW / 2, panY + dp(8)), dp(4), dp(4), fill)

        // 접시 위에 놓인 것. x 상자가 5개까지 올 수 있어 상자 크기를 접시 폭에 맞춰 줄인다
        let slots: Float = left ? Float(coef) + (leftConst > 0 ? 2 : 0) : 2
        let box = min(dp(19), panW / (slots + 0.6))
        let step = box * 1.12
        let used: Float = left ? Float(coef) * step + (leftConst > 0 ? box * 2 : 0) : box * 2
        var bx = x - used / 2 + box * 0.5
        let by = panY - box * 0.75
        text.textSize = box * 0.62

        if left {
            for _ in 0..<coef {
                fill.color = Color.parseColor("#FF8A65")
                canvas.drawRoundRect(RectF(bx - box / 2, by - box / 2, bx + box / 2, by + box / 2), dp(4), dp(4), fill)
                text.color = Color.WHITE
                canvas.drawText("x", bx, by + text.textSize * 0.35, text)
                bx += step
            }
            drawWeights(canvas, leftConst, bx, by, box)
        } else {
            drawWeights(canvas, rightConst, bx, by, box)
        }
    }

    /// 1짜리 추 — 많으면 하나만 그리고 개수를 적는다
    private func drawWeights(_ canvas: Canvas, _ n: Int, _ startX: Float, _ y: Float, _ box: Float) {
        if n <= 0 { return }
        var bx = startX
        let show = n <= 5 ? n : 1
        for _ in 0..<show {
            fill.color = Color.parseColor("#FFD54F")
            canvas.drawCircle(bx, y, box * 0.42, fill)
            text.color = Color.parseColor("#5D4037")
            text.textSize = box * 0.5
            canvas.drawText("1", bx, y + text.textSize * 0.35, text)
            bx += box * 1.0
        }
        if n > 5 {
            text.color = Color.parseColor("#5D4037")
            text.textSize = box * 0.62
            canvas.drawText("×\(n)", bx + box * 0.35, y + text.textSize * 0.35, text)
        }
    }

    /// x 값을 정하는 손잡이
    private func drawSlider(_ canvas: Canvas, _ w: Float, _ h: Float) {
        sliderLeft = w * 0.12
        sliderRight = w * 0.88
        sliderY = h * 0.06

        stroke.color = Color.parseColor("#BCAAA4")
        stroke.strokeWidth = dp(4)
        canvas.drawLine(sliderLeft, sliderY, sliderRight, sliderY, stroke)
        // 눈금
        stroke.strokeWidth = dp(1.5)
        for i in 1...maxX {
            let x = sliderLeft + (sliderRight - sliderLeft) * Float(i - 1) / Float(maxX - 1)
            canvas.drawLine(x, sliderY - dp(5), x, sliderY + dp(5), stroke)
        }

        let kx = sliderLeft + (sliderRight - sliderLeft) * Float(guess - 1) / Float(maxX - 1)
        fill.color = Color.parseColor("#42A5F5")
        canvas.drawCircle(kx, sliderY, dp(15), fill)
        fill.color = Color.WHITE
        canvas.drawCircle(kx, sliderY, dp(6), fill)

        text.color = Color.parseColor("#1565C0")
        text.textSize = dp(18)
        canvas.drawText("x = \(guess)", kx, sliderY + dp(36), text)
    }

    override func onTouchEvent(_ event: MotionEvent) -> Bool {
        switch event.actionMasked {
        case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL: return true
        default: break
        }
        if abs(event.y - sliderY) > dp(46) { return false }
        if sliderRight <= sliderLeft { return false }
        parent?.requestDisallowInterceptTouchEvent(true)

        let t = ((event.x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0, 1)
        let v = 1 + (t * Float(maxX - 1)).roundToInt()
        if v != guess {
            guess = v
            onChanged?(v)
            invalidate()
        }
        return true
    }

    private func dp(_ v: Float) -> Float { v * context.resources.displayMetrics.density }
}
