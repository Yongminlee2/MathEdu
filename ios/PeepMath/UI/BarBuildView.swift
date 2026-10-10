import UIKit

/// 표를 보고 막대를 직접 끌어 올려 그래프를 완성하는 판.
/// 한 번 세워 보면 막대 높이가 곧 수라는 걸 몸으로 알게 된다.
final class BarBuildView: View {

    private var labels: [String] = []
    private var target: [Int] = []
    private var current: [Int] = []
    private var maxV = 5

    private let fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private let stroke: Paint = { let p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = .STROKE; return p }()
    private let text: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = .CENTER
        p.color = Color.parseColor("#4E342E")
        return p
    }()

    private let palette = [
        Color.parseColor("#FF8A65"), Color.parseColor("#4DB6AC"),
        Color.parseColor("#BA68C8"), Color.parseColor("#FFD54F"),
        Color.parseColor("#7986CB"), Color.parseColor("#AED581"),
    ]

    private var plotLeft: Float = 0
    private var plotRight: Float = 0
    private var plotTop: Float = 0
    private var plotBottom: Float = 0

    var onChanged: (([Int]) -> Void)?
    var onBarMoved: (() -> Void)?

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
    }
    required init?(coder: NSCoder) { fatalError() }

    func setTarget(_ labels: [String], _ values: [Int]) {
        self.labels = labels
        self.target = values
        maxV = ((values.max() ?? 5) + 1).coerceAtLeast(5)
        current.removeAll()
        for _ in 0..<values.count { current.append(0) }
        onChanged?(current)
        invalidate()
    }

    func values() -> [Int] { current }

    func isCorrect() -> Bool { current.count == target.count && current == target }

    /// 아직 높이가 안 맞는 막대 수
    func wrongBars() -> Int { current.indices.filter { current[$0] != target.getOrNull($0) }.count }

    func reset() {
        for i in current.indices { current[i] = 0 }
        onChanged?(current)
        invalidate()
    }

    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        let w = Float(width)
        let h = Float(height)
        if w <= 0 || current.isEmpty { return }

        plotLeft = dp(34)
        plotRight = w - dp(12)
        plotTop = dp(14)
        plotBottom = h - dp(34)

        stroke.color = Color.parseColor("#8D6E63")
        stroke.strokeWidth = dp(2.5)
        canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, stroke)
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, stroke)

        // 가로 눈금선 — 높이를 눈으로 셀 수 있어야 한다
        text.textSize = dp(11)
        text.isFakeBoldText = false
        stroke.strokeWidth = dp(1)
        stroke.color = Color.parseColor("#33795548")
        for i in 1...maxV {
            let y = plotBottom - (plotBottom - plotTop) * Float(i) / Float(maxV)
            canvas.drawLine(plotLeft, y, plotRight, y, stroke)
            text.color = Color.parseColor("#8D6E63")
            text.textAlign = .RIGHT
            canvas.drawText("\(i)", plotLeft - dp(6), y + text.textSize * 0.35, text)
            text.textAlign = .CENTER
        }

        let slot = (plotRight - plotLeft) / Float(current.count)
        let barW = slot * 0.56
        for i in current.indices {
            let cx = plotLeft + slot * (Float(i) + 0.5)
            let bh = (plotBottom - plotTop) * Float(current[i]) / Float(maxV)
            if current[i] > 0 {
                fill.color = palette[i % palette.count]
                canvas.drawRoundRect(RectF(cx - barW / 2, plotBottom - bh, cx + barW / 2, plotBottom), dp(5), dp(5), fill)
                // 막대 꼭대기의 손잡이 — 여기를 잡아 올린다
                fill.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(cx, plotBottom - bh, dp(5), fill)
            } else {
                stroke.color = Color.parseColor("#BCAAA4")
                stroke.strokeWidth = dp(2)
                stroke.pathEffect = DashPathEffect([dp(6), dp(5)], 0)
                canvas.drawRoundRect(RectF(cx - barW / 2, plotBottom - dp(10), cx + barW / 2, plotBottom), dp(5), dp(5), stroke)
                stroke.pathEffect = nil
            }

            text.isFakeBoldText = true
            // 막대가 5개면 칸이 좁아진다 — 이름이 옆 칸을 침범하지 않게 칸 폭에 맞춘다
            text.textSize = min(dp(13), slot * 0.19)
            text.color = current[i] == target.getOrNull(i) ? Color.parseColor("#2E7D32") : Color.parseColor("#8D6E63")
            canvas.drawText("\(labels.getOrNull(i) ?? "") \(current[i])", cx, plotBottom + dp(20), text)
        }
    }

    override func onTouchEvent(_ event: MotionEvent) -> Bool {
        switch event.actionMasked {
        case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL: return true
        default: break
        }
        if current.isEmpty || plotRight <= plotLeft { return false }
        if event.x < plotLeft - dp(20) || event.y > plotBottom + dp(30) { return false }
        parent?.requestDisallowInterceptTouchEvent(true)

        let slot = (plotRight - plotLeft) / Float(current.count)
        let i = ((event.x - plotLeft) / slot).toInt().coerceIn(0, current.count - 1)
        // 손가락 높이가 곧 막대 높이 (칸에 딱 붙는다)
        let t = ((plotBottom - event.y) / (plotBottom - plotTop)).coerceIn(0, 1)
        let v = (t * Float(maxV)).roundToInt().coerceIn(0, maxV)
        if v != current[i] {
            current[i] = v
            onBarMoved?()
            onChanged?(current)
            invalidate()
        }
        return true
    }

    private func dp(_ v: Float) -> Float { v * context.resources.displayMetrics.density }
}
