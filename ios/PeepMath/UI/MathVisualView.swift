import UIKit

/// 수학 문제의 그림을 그린다. 이미지 파일 없이 이모지와 Canvas 도형만 쓴다.
/// 새 그림 종류는 MathVisual 에 kind 를 추가하고 여기 draw 분기만 늘리면 된다.
final class MathVisualView: View {

    /// 각도 그림의 꼭짓점 가로 위치와 변 길이 (뷰 폭 대비). CX - LEN >= 0, CX + LEN <= 1 이어야 한다.
    static let ANGLE_CX_RATIO: Float = 0.5
    static let ANGLE_LEN_RATIO: Float = 0.44

    /// 원(시계·분수) 밑에 붙는 두 줄을 위해 비워 두는 세로 공간(dp). 아래 두 상수보다 커야 한다.
    static let LABEL_BLOCK_DP: Float = 54

    /// 첫째 줄(값) 과 둘째 줄(안내) 의 원 아래 위치(dp)
    static let LABEL_LINE1_DP: Float = 22
    static let LABEL_LINE2_DP: Float = 42

    private let palette = [
        "#FF8A80", "#FFD54F", "#80CBC4", "#81D4FA", "#B39DDB", "#A5D6A7",
    ].map { Color.parseColor($0) }

    private let fill: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = .FILL
        return p
    }()
    private let stroke: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = .STROKE
        p.strokeCap = .ROUND
        p.strokeJoin = .ROUND
        p.color = Color.parseColor("#5D4037")
        return p
    }()
    private let text: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = .CENTER
        p.color = Color.parseColor("#4E342E")
        return p
    }()
    private let emojiPaint: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = .CENTER
        return p
    }()

    var visual: MathVisual? = nil {
        didSet {
            let v = visual
            counted.removeAll()
            painted.removeAll()
            itemOffsets.removeAll()
            dragIndex = -1
            setHour = 12; setMinute = 0
            setAngle = 0
            markValue = v?.p ?? 0.0
            draggingClock = false
            requestLayout()
            invalidate()
        }
    }

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
    }
    required init?(coder: NSCoder) { fatalError() }

    // ---------- 손가락으로 짚어 세기 ----------
    // 그림의 사물을 하나씩 터치하면 번호가 붙는다 (손가락으로 짚으며 세기).
    private var itemCenters: [PointF] = []
    private var itemEmojis: [String] = []
    /// 짚은 순서 그대로 (코틀린 LinkedHashSet)
    private var counted: [Int] = []

    /// 아이가 끌어 옮긴 거리. 하나라도 옮기면 배열의 행·열 안내선을 지운다.
    private var itemOffsets: [Int: PointF] = [:]
    private var dragIndex = -1
    private var dragMoved = false
    private var dragStartX: Float = 0
    private var dragStartY: Float = 0

    /// 셀 수 있는 그림인지 (이모지·배열 계열)
    var countable: Bool {
        guard let k = visual?.kind else { return false }
        return [MathVisual.EMOJI, MathVisual.EMOJI_OP, MathVisual.ARRAY].contains(k)
    }

    /// 손으로 옮길 수 있는 그림인지 — 셀 수 있는 그림은 모두 옮길 수도 있다
    var movable: Bool { countable }

    /// 지금까지 짚은 개수가 바뀔 때
    var onCountChanged: ((Int) -> Void)? = nil

    var countedSoFar: Int { counted.count }

    func clearCount() {
        counted.removeAll()
        itemOffsets.removeAll()      // 옮겨 놓은 것도 처음 자리로
        onCountChanged?(0)
        invalidate()
    }

    // ---------- 시계 바늘 돌리기 ----------
    // 짧은 바늘 근처를 잡으면 시침이, 그 밖을 잡으면 분침이 손가락을 따라온다.
    private var clockCx: Float = 0
    private var clockCy: Float = 0
    private var clockR: Float = 0
    private var draggingHour = false
    private var draggingClock = false

    private(set) var setHour = 12
    private(set) var setMinute = 0

    /// 맞춰 놓은 시각이 바뀔 때 (시, 분)
    var onClockChanged: ((Int, Int) -> Void)? = nil

    func resetClock() {
        setHour = 12; setMinute = 0
        onClockChanged?(setHour, setMinute)
        invalidate()
    }

    private func handleClockTouch(_ event: MotionEvent) -> Bool {
        let dx = event.x - clockCx
        let dy = event.y - clockCy
        let dist = hypot(dx, dy)
        switch event.actionMasked {
        case MotionEvent.ACTION_DOWN:
            if dist > clockR * 1.05 { return false }
            // 안쪽을 잡으면 시침, 바깥을 잡으면 분침
            draggingHour = dist < clockR * 0.55
            draggingClock = true
            parent?.requestDisallowInterceptTouchEvent(true)
        case MotionEvent.ACTION_MOVE:
            if !draggingClock { return false }
        default:
            draggingClock = false; return true
        }
        // 12시 방향이 0도가 되도록 회전
        var deg = toDegrees(atan2(Double(dy), Double(dx))) + 90.0
        if deg < 0 { deg += 360.0 }
        if draggingHour {
            let h = mathRound(deg / 30.0) % 12
            setHour = h == 0 ? 12 : h
        } else {
            // 5분 단위로 딱 붙게
            setMinute = (mathRound(deg / 6.0) / 5 * 5) % 60
        }
        onClockChanged?(setHour, setMinute)
        invalidate()
        return true
    }

    override func onTouchEvent(_ event: MotionEvent) -> Bool {
        switch visual?.kind {
        case MathVisual.CLOCK_SET?: return handleClockTouch(event)
        case MathVisual.FRACTION_PAINT?: return handlePaintTouch(event)
        case MathVisual.NUMBER_LINE_DRAG?: return handleLineTouch(event)
        case MathVisual.ANGLE_SET?: return handleAngleTouch(event)
        default: break
        }
        if !countable { return false }

        switch event.actionMasked {
        case MotionEvent.ACTION_DOWN:
            let hit = nearestItem(event.x, event.y)
            if hit < 0 { return false }
            dragIndex = hit
            dragMoved = false
            dragStartX = event.x
            dragStartY = event.y
            if movable { parent?.requestDisallowInterceptTouchEvent(true) }
            return true
        case MotionEvent.ACTION_MOVE:
            if dragIndex < 0 || !movable { return false }
            let dx = event.x - dragStartX
            let dy = event.y - dragStartY
            // 손가락이 조금 흔들린 것까지 끌기로 보면 세기가 안 된다
            if !dragMoved && hypot(dx, dy) < Float(dp(10)) { return true }
            dragMoved = true
            let base: PointF
            if let b = itemOffsets[dragIndex] { base = b } else {
                base = PointF(0, 0)
                itemOffsets[dragIndex] = base
            }
            base.x += dx
            base.y += dy
            dragStartX = event.x
            dragStartY = event.y
            invalidate()
            return true
        case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL:
            let hit = dragIndex
            let moved = dragMoved
            dragIndex = -1
            dragMoved = false
            if hit < 0 { return false }
            // 끌지 않고 톡 눌렀으면 "세기" — 다시 누르면 취소
            if !moved {
                if let k = counted.firstIndex(of: hit) { counted.remove(at: k) } else { counted.append(hit) }
                onCountChanged?(counted.count)
            }
            invalidate()
            return true
        default:
            break
        }
        return false
    }

    /// 손가락에 가장 가까운 이모지 (너무 멀면 -1)
    private func nearestItem(_ x: Float, _ y: Float) -> Int {
        var best = -1
        var bestD = Float.greatestFiniteMagnitude
        for i in 0..<itemCenters.count {
            let c = itemCenters[i]
            let d = hypot(x - c.x, y - c.y)
            if d < bestD { bestD = d; best = i }
        }
        return best >= 0 && bestD <= itemSpacing * 0.6 ? best : -1
    }

    /// 항목 사이 간격 (터치 판정 범위 계산용)
    private var itemSpacing: Float = 0

    /// 이모지 기준점과 글자 기준선 사이 거리 (그림 종류마다 다르다)
    private var itemCenterDy: Float = 0

    /// 짚은 순서대로 번호를 그린다
    private func drawCountMarks(_ canvas: Canvas) {
        if counted.isEmpty { return }
        let r = itemSpacing * 0.20
        text.textSize = r * 1.5
        text.isFakeBoldText = true
        for (order, idx) in counted.enumerated() {
            guard let c = itemCenters.getOrNull(idx) else { continue }
            fill.color = Color.parseColor("#66BB6A")
            canvas.drawCircle(c.x + itemSpacing * 0.30, c.y - itemSpacing * 0.30, r, fill)
            text.color = Color.WHITE
            canvas.drawText(
                "\(order + 1)",
                c.x + itemSpacing * 0.30,
                c.y - itemSpacing * 0.30 + text.textSize * 0.35, text
            )
        }
        text.color = Color.parseColor("#4E342E")
    }

    override func onMeasure(_ widthMeasureSpec: Int, _ heightMeasureSpec: Int) {
        let w = MeasureSpec.getSize(widthMeasureSpec)
        let wf = Float(w)
        let h: Int
        if let v = visual {
            switch v.kind {
            case MathVisual.EMOJI:
                let perRow = v.a > 10 ? 5 : 5
                let rows = (v.a + perRow - 1) / perRow
                h = (wf * 0.18 * Float(rows)).toInt().coerceAtLeast(dp(90))
            case MathVisual.EMOJI_OP: h = (wf * 0.42).toInt()
            case MathVisual.ARRAY: h = (wf * 0.13 * Float(v.a) + Float(dp(30))).toInt().coerceAtLeast(dp(110))
            case MathVisual.SHAPES, MathVisual.COMPARE: h = (wf * 0.38).toInt()
            case MathVisual.CLOCK: h = (wf * 0.62).toInt()
            // 밑에 붙는 두 줄까지 들어갈 높이
            case MathVisual.CLOCK_SET: h = (wf * 0.85).toInt()
            case MathVisual.GROUP: h = 0          // GroupDragView 가 따로 그린다
            case MathVisual.FRACTION: h = (wf * 0.40).toInt()
            case MathVisual.FRACTION_PAINT: h = (wf * 0.70).toInt()
            case MathVisual.SHAPE_SORT: h = 0     // GroupDragView 가 따로 그린다
            case MathVisual.NUMBER_LINE: h = dp(96)
            case MathVisual.NUMBER_LINE_DRAG: h = dp(150)      // 손잡이·값 표시 자리
            case MathVisual.ANGLE_SET: h = (wf * 0.68).toInt()
            // 전용 뷰가 따로 그린다
            case MathVisual.BALANCE, MathVisual.BAR_BUILD, MathVisual.GATHER: h = 0
            case MathVisual.BAR_GRAPH: h = (wf * 0.50).toInt()
            case MathVisual.COORD3D, MathVisual.COORD2D: h = (wf * 0.72).toInt()
            case MathVisual.GEOM:
                switch v.op {
                case "ineq": h = (wf * 0.30).toInt()
                case "dice": h = (wf * 0.34).toInt()
                default: h = (wf * 0.56).toInt()
                }
            case MathVisual.ANGLE: h = (wf * 0.50).toInt()
            default: h = dp(100)
            }
        } else {
            h = 0
        }
        setMeasuredDimension(w, h)
    }

    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        guard let v = visual else { return }
        let w = Float(width)
        let h = Float(height)
        itemCenters.removeAll()
        itemEmojis.removeAll()
        switch v.kind {
        case MathVisual.EMOJI: drawEmojiGrid(canvas, v.emoji, v.a, w, h)
        case MathVisual.COORD3D: drawCoord3d(canvas, v, w, h)
        case MathVisual.COORD2D:
            switch v.op {
            case "tangent", "area", "poly": drawCurveOp(canvas, v, w, h)
            case "exp", "log", "seqlim": drawFuncMisc(canvas, v, w, h)
            default: drawCoord2d(canvas, v, w, h)
            }
        case MathVisual.GEOM: drawGeom(canvas, v, w, h)
        case MathVisual.EMOJI_OP: drawEmojiOp(canvas, v, w, h)
        case MathVisual.ARRAY: drawArray(canvas, v, w, h)
        case MathVisual.SHAPES: drawShapes(canvas, v, w, h)
        case MathVisual.CLOCK, MathVisual.CLOCK_SET: drawClock(canvas, v, w, h)
        case MathVisual.FRACTION, MathVisual.FRACTION_PAINT: drawFraction(canvas, v, w, h)
        case MathVisual.NUMBER_LINE, MathVisual.NUMBER_LINE_DRAG: drawNumberLine(canvas, v, w, h)
        case MathVisual.BAR_GRAPH: drawBarGraph(canvas, v, w, h)
        case MathVisual.ANGLE, MathVisual.ANGLE_SET: drawAngle(canvas, v, w, h)
        case MathVisual.COMPARE: drawCompare(canvas, v, w, h)
        default: break
        }
        drawDraggedOnTop(canvas)
        drawCountMarks(canvas)
    }

    /// 이모지 하나를 그리고 터치 판정 자리를 등록한다. 끌어 옮긴 만큼(itemOffsets) 옮겨 그린다.
    private func placeEmoji(_ canvas: Canvas, _ emoji: String, _ x: Float, _ y: Float, _ centerDy: Float) {
        let i = itemCenters.count
        let off = itemOffsets[i]
        let ox = x + (off?.x ?? 0)
        let oy = y + (off?.y ?? 0)
        itemCenters.append(PointF(ox, oy - centerDy))
        itemEmojis.append(emoji)
        itemCenterDy = centerDy
        // 지금 끌고 있는 것은 맨 위에 크게 다시 그린다
        if i != dragIndex { drawItem(canvas, emoji, ox, oy - centerDy, emojiPaint.textSize) }
    }

    /// 끌고 있는 이모지를 가장 위에 (다른 것에 가리지 않게)
    private func drawDraggedOnTop(_ canvas: Canvas) {
        let i = dragIndex
        if i < 0 || i >= itemCenters.count { return }
        let c = itemCenters[i]
        drawItem(canvas, itemEmojis[i], c.x, c.y, emojiPaint.textSize * 1.2)
    }

    // ---------- 이모지 → 일러스트 ----------

    /// 세고 끄는 사물: 그림 사전 일러스트가 있으면 이모지 대신 그린다 (목록은 EmojiArt)
    private func artFor(_ emoji: String) -> Drawable? {
        EmojiArt.of(context, emoji)
    }

    /// (cx, cy) 를 중심으로 일러스트 또는 이모지 하나를 그린다. ts = 이모지 글자 크기 기준
    private func drawItem(_ canvas: Canvas, _ emoji: String, _ cx: Float, _ cy: Float, _ ts: Float) {
        if let d = artFor(emoji) {
            let half = (ts * 0.62).toInt()
            let hf = Float(half)
            d.setBounds((cx - hf).toInt(), (cy - hf).toInt(), (cx + hf).toInt(), (cy + hf).toInt())
            d.draw(canvas)
        } else {
            let base = emojiPaint.textSize
            emojiPaint.textSize = ts
            canvas.drawText(emoji, cx, cy + ts * 0.35, emojiPaint)
            emojiPaint.textSize = base
        }
    }

    // ---------- 이모지 세기 ----------
    private func drawEmojiGrid(_ canvas: Canvas, _ emoji: String, _ n: Int, _ w: Float, _ h: Float) {
        if n <= 0 { return }
        let perRow = min(5, n)
        let rows = (n + perRow - 1) / perRow
        let cell = min(w / (Float(perRow) + 0.6), h / (Float(rows) + 0.3))
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.78
        let startX = (w - cell * Float(perRow)) / 2 + cell / 2
        let startY = (h - cell * Float(rows)) / 2 + cell * 0.78
        for r in 0..<rows {
            let cols = min(perRow, n - r * perRow)
            let rowX = startX + Float(perRow - cols) * cell / 2
            for c in 0..<cols {
                placeEmoji(canvas, emoji, rowX + Float(c) * cell, startY + Float(r) * cell, cell * 0.28)
            }
        }
    }

    // ---------- 🍎🍎 + 🍎 ----------
    private func drawEmojiOp(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let total = v.a + v.bb
        let cell = min(w / (Float(total) + 3.2), h * 0.42)
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.85
        text.textSize = cell * 0.9
        text.isFakeBoldText = true
        let gap = cell * 0.9
        let totalW = Float(v.a) * cell + gap + Float(v.bb) * cell
        var x = (w - totalW) / 2 + cell / 2
        let y = h / 2 + cell * 0.3
        for _ in 0..<max(0, v.a) {
            placeEmoji(canvas, v.emoji, x, y, cell * 0.3)
            x += cell
        }
        canvas.drawText(v.op == "-" ? "－" : "＋", x + gap / 2 - cell / 2, y, text)
        x += gap
        for _ in 0..<max(0, v.bb) {
            placeEmoji(canvas, v.emoji, x, y, cell * 0.3)
            x += cell
        }
    }

    // ---------- 곱셈 배열 ----------
    private func drawArray(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let rows = v.a.coerceAtLeast(1)
        let cols = v.bb.coerceAtLeast(1)
        let cell = min((w - Float(dp(40))) / Float(cols), (h - Float(dp(30))) / Float(rows))
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.72
        let startX = (w - cell * Float(cols)) / 2 + cell / 2
        let startY = (h - cell * Float(rows)) / 2 + cell * 0.75
        for r in 0..<rows { for c in 0..<cols {
            placeEmoji(canvas, v.emoji, startX + Float(c) * cell, startY + Float(r) * cell, cell * 0.26)
        } }

        // 행·열 안내선 — 하나라도 옮기고 나면 더 이상 맞지 않으므로 지운다
        if !itemOffsets.isEmpty { return }
        stroke.strokeWidth = dp(1.5)
        stroke.color = Color.parseColor("#33795548")
        let left = (w - cell * Float(cols)) / 2
        let top = (h - cell * Float(rows)) / 2
        for r in 1..<rows {
            canvas.drawLine(left, top + Float(r) * cell, left + cell * Float(cols), top + Float(r) * cell, stroke)
        }
        for c in 1..<cols {
            canvas.drawLine(left + Float(c) * cell, top, left + Float(c) * cell, top + cell * Float(rows), stroke)
        }
        stroke.color = Color.parseColor("#5D4037")
    }

    // ---------- 도형 ----------
    private func drawShapes(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let n = v.labels.count.coerceAtLeast(1)
        let cellW = w / Float(n)
        let r = min(cellW * 0.32, h * 0.34)
        text.textSize = r * 0.42
        text.isFakeBoldText = false
        for i in 0..<v.labels.count {
            let cx = cellW * (Float(i) + 0.5)
            let cy = h * 0.45
            fill.color = palette[i % palette.count]
            // 어떤 도형을 그릴지는 번역 전 이름으로 고른다
            drawShape(canvas, v.labelsKo.getOrElse(i) { _ in v.labels[i] }, cx, cy, r)
            canvas.drawText("\(i + 1)", cx, h * 0.93, text)
        }
    }

    private func drawShape(_ canvas: Canvas, _ name: String, _ cx: Float, _ cy: Float, _ r: Float) {
        stroke.strokeWidth = dp(3.0)
        switch name {
        case "원":
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, stroke)
        case "타원":
            let rect = RectF(cx - r * 1.25, cy - r * 0.75, cx + r * 1.25, cy + r * 0.75)
            canvas.drawOval(rect, fill); canvas.drawOval(rect, stroke)
        case "사각형", "정사각형":
            let rect = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawRect(rect, fill); canvas.drawRect(rect, stroke)
        case "직사각형":
            let rect = RectF(cx - r * 1.3, cy - r * 0.75, cx + r * 1.3, cy + r * 0.75)
            canvas.drawRect(rect, fill); canvas.drawRect(rect, stroke)
        default:
            let sides: Int
            switch name {
            case "삼각형": sides = 3
            case "오각형": sides = 5
            case "육각형": sides = 6
            case "팔각형": sides = 8
            default: sides = 3
            }
            let path = polygonPath(cx, cy, r, sides)
            canvas.drawPath(path, fill); canvas.drawPath(path, stroke)
        }
    }

    private func polygonPath(_ cx: Float, _ cy: Float, _ r: Float, _ sides: Int) -> Path {
        let path = Path()
        for i in 0..<sides {
            let a = toRadians(-90.0 + 360.0 * Double(i) / Double(sides))
            let x = cx + r * cos(a).toFloat()
            let y = cy + r * sin(a).toFloat()
            if i == 0 { path.moveTo(x, y) } else { path.lineTo(x, y) }
        }
        path.close()
        return path
    }

    // ---------- 시계 ----------
    private func drawClock(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let setMode = v.kind == MathVisual.CLOCK_SET
        let cx = w / 2
        // 바늘 돌리기는 시계 밑에 두 줄이 붙는다 — 그만큼 자리를 비워 둔다
        let r = setMode ? min(w * 0.42, (h - dp(Self.LABEL_BLOCK_DP)) * 0.5)
            : min(w, h) * 0.42
        let cy = setMode ? r + Float(dp(6)) : h / 2
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, r, fill)
        stroke.strokeWidth = dp(4.0)
        stroke.color = Color.parseColor("#FFB300")
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.parseColor("#5D4037")

        text.textSize = r * 0.20
        text.isFakeBoldText = true
        for i in 1...12 {
            let a = toRadians(-90.0 + 30.0 * Double(i))
            let tx = cx + r * 0.80 * cos(a).toFloat()
            let ty = cy + r * 0.80 * sin(a).toFloat() + text.textSize * 0.35
            canvas.drawText("\(i)", tx, ty, text)
        }
        // 분 눈금
        stroke.strokeWidth = dp(1.5)
        for i in 0..<60 {
            if i % 5 == 0 { continue }
            let a = toRadians(-90.0 + 6.0 * Double(i))
            canvas.drawLine(
                cx + r * 0.93 * cos(a).toFloat(), cy + r * 0.93 * sin(a).toFloat(),
                cx + r * 0.99 * cos(a).toFloat(), cy + r * 0.99 * sin(a).toFloat(), stroke
            )
        }

        // 마지막으로 그린 판 위치 — 바늘을 끌 때 각도 계산에 쓴다
        clockCx = cx; clockCy = cy; clockR = r

        let hour = v.kind == MathVisual.CLOCK_SET ? Double(setHour) : v.p
        let minute = v.kind == MathVisual.CLOCK_SET ? Double(setMinute) : v.q
        // 시침 (분에 따라 조금씩 이동)
        let ha = toRadians(-90.0 + 30.0 * hour.truncatingRemainder(dividingBy: 12) + 0.5 * minute)
        stroke.strokeWidth = dp(6.0)
        stroke.color = Color.parseColor("#FF7043")
        canvas.drawLine(
            cx, cy,
            cx + r * 0.48 * cos(ha).toFloat(), cy + r * 0.48 * sin(ha).toFloat(), stroke
        )
        // 분침
        let ma = toRadians(-90.0 + 6.0 * minute)
        stroke.strokeWidth = dp(4.0)
        stroke.color = Color.parseColor("#42A5F5")
        canvas.drawLine(
            cx, cy,
            cx + r * 0.72 * cos(ma).toFloat(), cy + r * 0.72 * sin(ma).toFloat(), stroke
        )
        fill.color = Color.parseColor("#5D4037")
        canvas.drawCircle(cx, cy, dp(5.0), fill)
        stroke.color = Color.parseColor("#5D4037")

        if setMode {
            // 지금 맞춰 놓은 시각을 숫자로도 보여 준다
            text.textSize = dp(19.0)
            text.color = Color.parseColor("#4E342E")
            canvas.drawText(context.getString(R.string.time_hm, setHour, setMinute), cx, cy + r + dp(Self.LABEL_LINE1_DP), text)
            text.textSize = dp(13.0)
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText(context.getString(R.string.vis_clock_hint), cx, cy + r + dp(Self.LABEL_LINE2_DP), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 분수 (원을 나눈 그림) ----------
    private func drawFraction(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let paintMode = v.kind == MathVisual.FRACTION_PAINT
        let denom = v.q.toInt().coerceAtLeast(1)
        let numer = v.p.toInt().coerceIn(0, denom)
        let cx = w / 2
        // 색칠 모드는 원 밑에 두 줄이 붙는다 — 자리를 먼저 빼 놓고 원 크기를 정한다
        let r = paintMode ? min(w * 0.40, (h - dp(Self.LABEL_BLOCK_DP)) * 0.5)
            : min(w, h) * 0.40
        let cy = paintMode ? r + Float(dp(6)) : h / 2
        let rect = RectF(cx - r, cy - r, cx + r, cy + r)
        let sweep = 360 / Float(denom)

        // 색칠 모드에서는 아이가 칠한 칸이, 아니면 문제가 정해 준 칸이 색칠된다
        func filled(_ i: Int) -> Bool { paintMode ? painted.contains(i) : i < numer }

        for i in 0..<denom {
            fill.color = filled(i) ? Color.parseColor("#FF8A65") : Color.parseColor("#FFF3E0")
            canvas.drawArc(rect, -90 + sweep * Float(i), sweep, true, fill)
        }
        stroke.strokeWidth = dp(3.0)
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.strokeWidth = dp(2.0)
        for i in 0..<denom {
            let a = toRadians(-90.0 + Double(sweep * Float(i)))
            canvas.drawLine(cx, cy, cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), stroke)
        }

        if paintMode {
            pieCx = cx; pieCy = cy; pieR = r; pieSlices = denom
            text.textSize = dp(18.0)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
            canvas.drawText(
                context.getString(R.string.vis_painted, painted.count, denom), cx, cy + r + dp(Self.LABEL_LINE1_DP), text
            )
            text.textSize = dp(13.0)
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText(context.getString(R.string.vis_paint_hint), cx, cy + r + dp(Self.LABEL_LINE2_DP), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 분수 색칠하기 ----------
    // "3/8 만큼 색칠해 보세요" — 조각을 눌러 칠한다.
    private var pieCx: Float = 0
    private var pieCy: Float = 0
    private var pieR: Float = 0
    private var pieSlices = 1
    /// 칠한 칸 (코틀린 LinkedHashSet)
    private var painted: [Int] = []

    var paintedCount: Int { painted.count }

    /// 칠한 칸 수가 바뀔 때
    var onPaintChanged: ((Int) -> Void)? = nil

    func clearPaint() {
        painted.removeAll()
        onPaintChanged?(0)
        invalidate()
    }

    private func handlePaintTouch(_ event: MotionEvent) -> Bool {
        if event.actionMasked != MotionEvent.ACTION_DOWN { return false }
        let dx = event.x - pieCx
        let dy = event.y - pieCy
        if hypot(dx, dy) > pieR { return false }
        var deg = toDegrees(atan2(Double(dy), Double(dx))) + 90.0
        if deg < 0 { deg += 360.0 }
        let idx = (deg / (360.0 / Double(pieSlices))).toInt().coerceIn(0, pieSlices - 1)
        // 다시 누르면 지운다
        if let k = painted.firstIndex(of: idx) { painted.remove(at: k) } else { painted.append(idx) }
        onPaintChanged?(painted.count)
        invalidate()
        return true
    }

    // ---------- 수직선 ----------
    private func drawNumberLine(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let lo = v.p
        let hi = v.q
        if hi <= lo { return }
        let left = Float(dp(28))
        let right = w - Float(dp(28))
        let y = h * 0.55
        stroke.strokeWidth = dp(3.0)
        canvas.drawLine(left, y, right, y, stroke)
        // 화살촉
        canvas.drawLine(right, y, right - Float(dp(10)), y - Float(dp(6)), stroke)
        canvas.drawLine(right, y, right - Float(dp(10)), y + Float(dp(6)), stroke)

        // 구간 수가 지정돼 있으면 그대로 (소수·음수 수직선은 눈금 간격이 1이 아니다)
        let steps = v.a > 0 ? v.a.coerceAtMost(20) : (hi - lo).toInt().coerceIn(1, 20)
        let dragMode = v.kind == MathVisual.NUMBER_LINE_DRAG
        text.textSize = dragMode ? dp(13.0) : h * 0.20
        text.isFakeBoldText = false
        // 눈금이 촘촘하면 숫자는 띄엄띄엄 적는다 (20칸이면 네 칸 걸러야 읽힌다)
        let labelEvery: Int
        if steps > 12 { labelEvery = 4 } else if steps > 6 { labelEvery = 2 } else { labelEvery = 1 }
        for i in 0...steps {
            let x = left + (right - left) * Float(i) / Float(steps)
            canvas.drawLine(x, y - Float(dp(8)), x, y + Float(dp(8)), stroke)
            if i % labelEvery == 0 {
                let labelY = y + h * (v.kind == MathVisual.NUMBER_LINE_DRAG ? 0.26 : 0.36)
                canvas.drawText(fmt(lo + (hi - lo) * Double(i) / Double(steps)), x, labelY, text)
            }
        }
        // 표시할 값
        fill.color = Color.parseColor("#FF7043")
        for value in v.values {
            let x = left + (right - left) * ((value - lo) / (hi - lo)).toFloat()
            canvas.drawCircle(x, y, dp(9.0), fill)
        }

        if v.kind == MathVisual.NUMBER_LINE_DRAG {
            lineLeft = left; lineRight = right; lineY = y
            let x = left + (right - left) * ((markValue - lo) / (hi - lo)).toFloat()
            // 끌 수 있다는 걸 알 수 있게 큼직한 손잡이로
            fill.color = Color.parseColor("#42A5F5")
            canvas.drawCircle(x, y, dp(16.0), fill)
            fill.color = Color.WHITE
            canvas.drawCircle(x, y, dp(7.0), fill)

            text.textSize = h * 0.20
            text.isFakeBoldText = true
            text.color = Color.parseColor("#1565C0")
            canvas.drawText(fmt(markValue), x, y - Float(dp(24)), text)
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 수직선 위의 점 끌기 ----------
    // 수직선 위 어디쯤인지 직접 짚어 본다. 눈금에 딱 붙는다.
    private var lineLeft: Float = 0
    private var lineRight: Float = 0
    private var lineY: Float = 0
    private var markValue = 0.0

    /// 지금 짚은 값
    var markedValue: Double { markValue }

    var onMarkChanged: ((Double) -> Void)? = nil

    func resetMark() {
        markValue = visual?.p ?? 0.0
        onMarkChanged?(markValue)
        invalidate()
    }

    private func handleLineTouch(_ event: MotionEvent) -> Bool {
        guard let v = visual else { return false }
        if event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL { return true }
        if lineRight <= lineLeft { return false }
        // 선에서 너무 멀면 무시 (연습장 필기와 헷갈리지 않게)
        if abs(event.y - lineY) > Float(dp(56)) { return false }
        parent?.requestDisallowInterceptTouchEvent(true)

        let t = ((event.x - lineLeft) / (lineRight - lineLeft)).coerceIn(0, 1)
        let steps = v.a.coerceAtLeast(1)
        let raw = v.p + (v.q - v.p) * Double(t)
        // 눈금에 딱 붙인다
        let stepSize = (v.q - v.p) / Double(steps)
        let snapped = v.p + Double(mathRound((raw - v.p) / stepSize)) * stepSize
        let rounded = Double(mathRound(snapped * 1000.0)) / 1000.0
        if rounded != markValue {
            markValue = rounded
            onMarkChanged?(markValue)
            invalidate()
        }
        return true
    }

    // ---------- 막대그래프 ----------
    private func drawBarGraph(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        if v.values.isEmpty { return }
        let maxV = (v.values.max() ?? 1.0).coerceAtLeast(1.0)
        let gridSteps = 4

        // 눈금 글자 크기를 먼저 정하고, 가장 긴 눈금값을 재서 왼쪽 여백을 잡는다
        text.textSize = (h * 0.085).coerceIn(dp(11.0), dp(15.0))
        text.isFakeBoldText = false
        let gridLabels = (1...gridSteps).map { fmt(maxV * Double($0) / Double(gridSteps)) }
        let labelW = gridLabels.map { text.measureText($0) }.max()!
        let left = labelW + Float(dp(14))
        let bottom = h - Float(dp(30))
        let top = Float(dp(14))
        let right = w - Float(dp(14))
        stroke.strokeWidth = dp(2.5)
        canvas.drawLine(left, top, left, bottom, stroke)
        canvas.drawLine(left, bottom, right, bottom, stroke)

        // 눈금 — 축 왼쪽에 오른쪽 정렬
        text.textAlign = .RIGHT
        for (i, label) in gridLabels.enumerated() {
            let yy = bottom - (bottom - top) * Float(i + 1) / Float(gridSteps)
            canvas.drawText(label, left - Float(dp(6)), yy + text.textSize * 0.35, text)
        }
        text.textAlign = .CENTER

        let n = v.values.count
        let slot = (right - left) / Float(n)
        let barW = slot * 0.55
        for i in 0..<v.values.count {
            let bh = (Double(bottom - top) * (v.values[i] / maxV)).toFloat()
            let cx = left + slot * (Float(i) + 0.5)
            fill.color = palette[i % palette.count]
            let rect = RectF(cx - barW / 2, bottom - bh, cx + barW / 2, bottom)
            canvas.drawRoundRect(rect, dp(6.0), dp(6.0), fill)
            if let it = v.labels.getOrNull(i) {
                let base = text.textSize
                if text.measureText(it) > slot * 0.92 {
                    text.textSize = base * (slot * 0.92 / text.measureText(it))
                }
                canvas.drawText(it, cx, bottom + base * 1.4, text)
                text.textSize = base
            }
        }
    }

    // ---------- 각도 ----------
    private func drawAngle(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let setMode = v.kind == MathVisual.ANGLE_SET
        let deg = setMode ? Double(setAngle) : v.p
        // 꼭짓점을 가운데에 둔다 (둔각일 때 변이 화면 밖으로 나가지 않게)
        let cx = w * Self.ANGLE_CX_RATIO
        let cy = h * 0.78
        let len = min(w * Self.ANGLE_LEN_RATIO, cy - Float(dp(14)))
        angleCx = cx; angleCy = cy; angleLen = len

        stroke.strokeWidth = dp(4.0)
        canvas.drawLine(cx, cy, cx + len, cy, stroke)
        let a = toRadians(-deg)
        if setMode { stroke.color = Color.parseColor("#42A5F5") }
        canvas.drawLine(cx, cy, cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(), stroke)
        stroke.color = Color.parseColor("#5D4037")
        // 각 표시 호
        stroke.strokeWidth = dp(2.5)
        stroke.color = Color.parseColor("#FF7043")
        let r = len * 0.28
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 0, -deg.toFloat(), false, stroke)
        stroke.color = Color.parseColor("#5D4037")
        fill.color = Color.parseColor("#5D4037")
        canvas.drawCircle(cx, cy, dp(4.0), fill)

        if setMode {
            // 끝점에 손잡이를 그려서 "여기를 잡아 돌린다"를 알려 준다
            fill.color = Color.parseColor("#42A5F5")
            canvas.drawCircle(
                cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(),
                dp(14.0), fill
            )
            fill.color = Color.WHITE
            canvas.drawCircle(
                cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(),
                dp(6.0), fill
            )
            text.textSize = len * 0.20
            text.isFakeBoldText = true
            text.color = Color.parseColor("#1565C0")
            canvas.drawText("\(setAngle)°", cx + len * 0.42, cy - len * 0.16, text)
            text.textSize = len * 0.13
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText(context.getString(R.string.vis_angle_hint), w * 0.5, h - Float(dp(8)), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 각도 만들기 ----------
    // 각도를 재는 대신 직접 만들어 본다. 5° 단위로 붙는다.
    private var angleCx: Float = 0
    private var angleCy: Float = 0
    private var angleLen: Float = 0

    private(set) var setAngle = 0

    var onAngleChanged: ((Int) -> Void)? = nil

    func resetAngle() {
        setAngle = 0
        onAngleChanged?(0)
        invalidate()
    }

    private func handleAngleTouch(_ event: MotionEvent) -> Bool {
        if event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL { return true }
        let dx = event.x - angleCx
        let dy = event.y - angleCy
        if hypot(dx, dy) > angleLen * 1.35 { return false }
        parent?.requestDisallowInterceptTouchEvent(true)
        // 위로 열리는 각만 다룬다 (0°~180°)
        var deg = toDegrees(atan2(-Double(dy), Double(dx)))
        if deg < 0 { deg = 0.0 }
        let snapped = (mathRound(deg / 5.0) * 5).coerceIn(0, 180)
        if snapped != setAngle {
            setAngle = snapped
            onAngleChanged?(snapped)
            invalidate()
        }
        return true
    }

    // ---------- 두 그룹 비교 ----------
    private func drawCompare(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let halfW = w / 2
        drawEmojiGridIn(canvas, v.emoji, v.a, 0, 0, halfW - Float(dp(8)), h)
        drawEmojiGridIn(canvas, v.labels.getOrNull(0) ?? v.emoji, v.bb, halfW + Float(dp(8)), 0, halfW - Float(dp(8)), h)
        stroke.strokeWidth = dp(2.0)
        stroke.color = Color.parseColor("#33795548")
        canvas.drawLine(halfW, Float(dp(8)), halfW, h - Float(dp(8)), stroke)
        stroke.color = Color.parseColor("#5D4037")
    }

    private func drawEmojiGridIn(
        _ canvas: Canvas, _ emoji: String, _ n: Int, _ x0: Float, _ y0: Float, _ w: Float, _ h: Float
    ) {
        if n <= 0 { return }
        let perRow = min(3, n)
        let rows = (n + perRow - 1) / perRow
        let cell = min(w / (Float(perRow) + 0.4), h / (Float(rows) + 0.4))
        emojiPaint.textSize = cell * 0.76
        let startX = x0 + (w - cell * Float(perRow)) / 2 + cell / 2
        let startY = y0 + (h - cell * Float(rows)) / 2 + cell * 0.78
        var left = n
        for r in 0..<rows {
            let cols = min(perRow, left)
            for c in 0..<cols {
                canvas.drawText(emoji, startX + Float(c) * cell, startY + Float(r) * cell, emojiPaint)
            }
            left -= cols
        }
    }

    private func fmt(_ d: Double) -> String {
        d == Double(d.toInt()) ? d.toInt().toString() : "%.1f".format(d)
    }

    private func dp(_ v: Int) -> Int { (Float(v) * context.resources.displayMetrics.density).toInt() }

    // ---------- 좌표 도해 (교과서 그림처럼, 문제 속 숫자와 정확히 일치) ----------

    /// 공간좌표: 오른손 좌표계 축 3개 + 점 P(x,y,z) 까지 안내 직육면체
    private func drawCoord3d(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let x = (v.values.getOrNull(0) ?? 0.0).toFloat()
        let y = (v.values.getOrNull(1) ?? 0.0).toFloat()
        let z = (v.values.getOrNull(2) ?? 0.0).toFloat()
        let m = max(abs(x), abs(y), abs(z), 1)
        let cx = w * 0.46
        let cy = h * 0.55
        let u = min(w, h) * 0.30 / m
        // 투영: y = 오른쪽, z = 위, x = 왼쪽 아래로 비스듬히 (오른손 좌표계)
        func pt(_ px: Float, _ py: Float, _ pz: Float) -> PointF {
            PointF(
                cx + py * u - px * u * 0.55,
                cy - pz * u + px * u * 0.38
            )
        }
        let axis = newPaint {
            $0.color = Color.parseColor("#6D4C41"); $0.strokeWidth = dp(2.0); $0.style = .STROKE
        }
        let dash = newPaint {
            $0.color = Color.parseColor("#B39F8C"); $0.strokeWidth = dp(1.5); $0.style = .STROKE
            $0.pathEffect = DashPathEffect([dp(5.0), dp(4.0)], 0)
        }
        let lbl = newPaint {
            $0.color = Color.parseColor("#4E342E"); $0.textSize = dp(14.0); $0.textAlign = .CENTER
            $0.isFakeBoldText = true
            $0.setShadowLayer(dp(3.0), 0, 0, Color.WHITE)
        }
        func line(_ a: PointF, _ b: PointF, _ p: Paint) { canvas.drawLine(a.x, a.y, b.x, b.y, p) }
        let o = pt(0, 0, 0)
        let ax = m + 1.3
        let xe = pt(ax, 0, 0); let ye = pt(0, ax, 0); let ze = pt(0, 0, ax)
        line(o, xe, axis); line(o, ye, axis); line(o, ze, axis)
        canvas.drawText("x", xe.x - dp(9.0), xe.y + dp(11.0), lbl)
        canvas.drawText("y", ye.x + dp(10.0), ye.y + dp(4.0), lbl)
        canvas.drawText("z", ze.x, ze.y - dp(7.0), lbl)
        canvas.drawText("O", o.x - dp(10.0), o.y + dp(13.0), lbl)
        // 점까지 가는 직육면체 모서리 (점선)
        let pX = pt(x, 0, 0); let pY = pt(0, y, 0); let pZ = pt(0, 0, z)
        let pXY = pt(x, y, 0); let pXZ = pt(x, 0, z); let pYZ = pt(0, y, z)
        let pp = pt(x, y, z)
        line(pX, pXY, dash); line(pY, pXY, dash); line(pXY, pp, dash)
        line(pZ, pXZ, dash); line(pZ, pYZ, dash); line(pXZ, pp, dash); line(pYZ, pp, dash)
        line(pX, pXZ, dash); line(pY, pYZ, dash)
        let dot = newPaint { $0.color = Color.parseColor("#FF7043") }
        canvas.drawCircle(pp.x, pp.y, dp(5.0), dot)
        lbl.textAlign = .LEFT
        canvas.drawText("P(\(fmtC(x)), \(fmtC(y)), \(fmtC(z)))", pp.x + dp(8.0), pp.y - dp(7.0), lbl)
    }

    /// 좌표평면: 벡터 화살표 2개 / 포물선과 꼭짓점 / 타원
    private func drawCoord2d(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let cx = w / 2; let cy = h / 2
        var m: Float = 1
        switch v.op {
        case "vec": for i in 0..<4 { m = max(m, abs(vAt(v, i))) }
        case "parab": m = max(abs(v.p.toFloat()), abs(v.q.toFloat()) * 0.6, 3)
        case "ellipse": m = max(v.p.toFloat(), v.q.toFloat())
        case "line":
            m = max(abs(vAt(v, 1)) + 1.5, 4)
            if v.values.count >= 3 {
                m = max(
                    m, abs(vAt(v, 2)) + 1.5,
                    abs(vAt(v, 0) * vAt(v, 2) + vAt(v, 1)) + 1.5
                )
            }
        case "line2": m = max(
            abs(vAt(v, 4)), abs(vAt(v, 5)),
            abs(vAt(v, 1)), abs(vAt(v, 3)), 4
        )
        case "seg": for i in 0..<4 { m = max(m, abs(vAt(v, i))) }
        case "circ": m = max(
            abs(vAt(v, 0)) + vAt(v, 2),
            abs(vAt(v, 1)) + vAt(v, 2), 3
        )
        case "hyper": m = max(sqrt(abs(vAt(v, 0))) + 2, 5)
        default: break
        }
        m += 1.5
        let u = min(w, h) * 0.44 / m
        func X(_ x: Float) -> Float { cx + x * u }
        func Y(_ y: Float) -> Float { cy - y * u }
        let grid = newPaint {
            $0.color = Color.parseColor("#EADFCE"); $0.strokeWidth = dp(1.0)
        }
        let axis = newPaint {
            $0.color = Color.parseColor("#8D6E63"); $0.strokeWidth = dp(2.0)
        }
        let lbl = newPaint {
            $0.color = Color.parseColor("#4E342E"); $0.textSize = dp(14.0); $0.textAlign = .CENTER
            $0.isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            $0.setShadowLayer(dp(3.0), 0, 0, Color.WHITE)
        }
        // 눈금 (너무 촘촘하지 않게 정수 간격을 고른다)
        let step = ceil(m / 6).toInt().coerceAtLeast(1)
        var g = step
        while Float(g) < m {
            canvas.drawLine(X(Float(g)), Y(-m), X(Float(g)), Y(m), grid)
            canvas.drawLine(X(-Float(g)), Y(-m), X(-Float(g)), Y(m), grid)
            canvas.drawLine(X(-m), Y(Float(g)), X(m), Y(Float(g)), grid)
            canvas.drawLine(X(-m), Y(-Float(g)), X(m), Y(-Float(g)), grid)
            g += step
        }
        canvas.drawLine(X(-m), cy, X(m), cy, axis)
        canvas.drawLine(cx, Y(-m), cx, Y(m), axis)
        canvas.drawText("x", X(m) - dp(9.0), cy - dp(8.0), lbl)
        canvas.drawText("y", cx + dp(11.0), Y(m) + dp(13.0), lbl)
        canvas.drawText("O", cx - dp(8.0), cy + dp(13.0), lbl)

        switch v.op {
        case "vec":
            let colors = ["#FF7043", "#42A5F5"]
            for k in 0...1 {
                let vx = vAt(v, k * 2); let vy = vAt(v, k * 2 + 1)
                let p = newPaint {
                    $0.color = Color.parseColor(colors[k]); $0.strokeWidth = dp(3.0)
                }
                drawArrow(canvas, cx, cy, X(vx), Y(vy), p)
                lbl.color = p.color
                canvas.drawText(
                    "(\(fmtC(vx)), \(fmtC(vy)))",
                    X(vx), Y(vy) + (vy >= 0 ? -dp(9.0) : dp(16.0)), lbl
                )
            }
            lbl.color = Color.parseColor("#4E342E")
        case "parab":
            let a = Float(v.a); let p0 = v.p.toFloat(); let q0 = v.q.toFloat()
            let path = Path()
            var first = true
            var xx = -m
            while xx <= m {
                let yy = a * (xx - p0) * (xx - p0) + q0
                if yy >= -m && yy <= m {
                    if first { path.moveTo(X(xx), Y(yy)); first = false }
                    else { path.lineTo(X(xx), Y(yy)) }
                } else { first = true }
                xx += 0.05
            }
            let curve = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
            }
            canvas.drawPath(path, curve)
            let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            canvas.drawCircle(X(p0), Y(q0), dp(5.0), dot)
            canvas.drawText("(\(fmtC(p0)), \(fmtC(q0)))", X(p0), Y(q0) + dp(18.0), lbl)
        case "line2":
            // 연립방정식: 두 직선과 교점
            let colors = ["#FF7043", "#42A5F5"]
            for k in 0...1 {
                let sl = vAt(v, k * 2); let ic = vAt(v, k * 2 + 1)
                let p = newPaint {
                    $0.color = Color.parseColor(colors[k]); $0.strokeWidth = dp(3.0)
                }
                var xs = -m; var xe = m
                if sl != 0 {
                    let cands = [-m, m, (m - ic) / sl, (-m - ic) / sl].sorted()
                    xs = cands[1]; xe = cands[2]
                }
                canvas.drawLine(X(xs), Y(sl * xs + ic), X(xe), Y(sl * xe + ic), p)
            }
            let px = vAt(v, 4); let py = vAt(v, 5)
            canvas.drawCircle(X(px), Y(py), dp(5.0), newPaint {
                $0.color = Color.parseColor("#5C6BC0")
            })
            canvas.drawText("(\(fmtC(px)), \(fmtC(py)))", X(px), Y(py) - dp(9.0), lbl)
        case "seg":
            // 두 점 사이 거리 — 선분과 점선 직각변
            let x1 = vAt(v, 0); let y1 = vAt(v, 1); let x2 = vAt(v, 2); let y2 = vAt(v, 3)
            let dashP = newPaint {
                $0.color = Color.parseColor("#B39F8C"); $0.strokeWidth = dp(1.5); $0.style = .STROKE
                $0.pathEffect = DashPathEffect([dp(5.0), dp(4.0)], 0)
            }
            canvas.drawLine(X(x1), Y(y1), X(x2), Y(y1), dashP)
            canvas.drawLine(X(x2), Y(y1), X(x2), Y(y2), dashP)
            canvas.drawLine(X(x1), Y(y1), X(x2), Y(y2), newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0)
            })
            let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            canvas.drawCircle(X(x1), Y(y1), dp(5.0), dot)
            canvas.drawCircle(X(x2), Y(y2), dp(5.0), dot)
            canvas.drawText("(\(fmtC(x1)), \(fmtC(y1)))", X(x1), Y(y1) + dp(17.0), lbl)
            canvas.drawText("(\(fmtC(x2)), \(fmtC(y2)))", X(x2), Y(y2) - dp(9.0), lbl)
        case "circ":
            // 좌표평면 위의 원 (중심 (a,b), 반지름 r)
            let a = vAt(v, 0); let b0 = vAt(v, 1); let r = vAt(v, 2)
            let ring = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
            }
            canvas.drawCircle(X(a), Y(b0), r * u, ring)
            let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            canvas.drawCircle(X(a), Y(b0), dp(4.0), dot)
            canvas.drawLine(X(a), Y(b0), X(a + r), Y(b0), newPaint {
                $0.color = Color.parseColor("#8D6E63"); $0.strokeWidth = dp(2.0)
            })
            canvas.drawText("(\(fmtC(a)), \(fmtC(b0)))", X(a), Y(b0) + dp(17.0), lbl)
            canvas.drawText("r", X(a + r / 2), Y(b0) - dp(7.0), lbl)
        case "hyper":
            // 반비례 y = k/x — 쌍곡선 두 가지
            let k = vAt(v, 0)
            let curve = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
            }
            for sign: Float in [1, -1] {
                let path = Path()
                var first = true
                var x: Float = 0.25
                while x <= m {
                    let yy = k / (x * sign)
                    if abs(yy) <= m {
                        if first { path.moveTo(X(x * sign), Y(yy)); first = false }
                        else { path.lineTo(X(x * sign), Y(yy)) }
                    } else { first = true }
                    x += m / 90
                }
                canvas.drawPath(path, curve)
            }
        case "line":
            // y = ax + b 직선과 y절편 — 교과서 일차함수 그림
            let a = vAt(v, 0); let b0 = vAt(v, 1)
            let p = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0)
            }
            // 화면(±m) 안에 들어오는 구간만 긋는다
            func yOf(_ x: Float) -> Float { a * x + b0 }
            var xs = -m; var xe = m
            if a != 0 {
                let cands = [-m, m, (m - b0) / a, (-m - b0) / a].sorted()
                xs = cands[1]; xe = cands[2]
            }
            canvas.drawLine(X(xs), Y(yOf(xs)), X(xe), Y(yOf(xe)), p)
            let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            canvas.drawCircle(X(0), Y(b0), dp(5.0), dot)
            canvas.drawText("(0, \(fmtC(b0)))", X(0) + dp(34.0), Y(b0) - dp(6.0), lbl)
            if v.values.count >= 3 {
                // x → k 로 다가가는 점 (함수의 극한)
                let k = vAt(v, 2); let yk = a * k + b0
                let guide = newPaint {
                    $0.color = Color.parseColor("#B39F8C"); $0.strokeWidth = dp(1.5); $0.style = .STROKE
                    $0.pathEffect = DashPathEffect([dp(5.0), dp(4.0)], 0)
                }
                canvas.drawLine(X(k), Y(0), X(k), Y(yk), guide)
                canvas.drawLine(X(0), Y(yk), X(k), Y(yk), guide)
                canvas.drawCircle(X(k), Y(yk), dp(5.0), newPaint {
                    $0.color = Color.parseColor("#FF7043")
                })
                canvas.drawText("x=\(fmtC(k))", X(k), Y(0) + dp(15.0), lbl)
            }
        case "ellipse":
            let a = v.p.toFloat(); let b = v.q.toFloat()
            let oval = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
            }
            canvas.drawOval(X(-a), Y(b), X(a), Y(-b), oval)
            let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            canvas.drawCircle(X(a), cy, dp(4.0), dot)
            canvas.drawCircle(X(-a), cy, dp(4.0), dot)
            canvas.drawText(fmtC(a), X(a), cy + dp(15.0), lbl)
            canvas.drawText(fmtC(-a), X(-a), cy + dp(15.0), lbl)
            canvas.drawText(fmtC(b), cx + dp(13.0), Y(b) + dp(4.0), lbl)
        default: break
        }
    }

    private func vAt(_ v: MathVisual, _ i: Int) -> Float { (v.values.getOrNull(i) ?? 0.0).toFloat() }

    private func fmtC(_ f: Float) -> String {
        // 코틀린 Float.toString 과 같은 가장 짧은 표기
        f == floor(f) ? f.toInt().toString() : "\(f)"
    }

    /// 화살표: 선 + 끝 삼각형
    private func drawArrow(_ canvas: Canvas, _ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ p: Paint) {
        canvas.drawLine(x1, y1, x2, y2, p)
        let ang = atan2(Double(y2 - y1), Double(x2 - x1))
        let len = Double(dp(9.0))
        let spread = 0.5
        let path = Path()
        path.moveTo(x2, y2)
        path.lineTo(
            (Double(x2) - len * cos(ang - spread)).toFloat(),
            (Double(y2) - len * sin(ang - spread)).toFloat()
        )
        path.lineTo(
            (Double(x2) - len * cos(ang + spread)).toFloat(),
            (Double(y2) - len * sin(ang + spread)).toFloat()
        )
        path.close()
        let fill = copyPaint(p) { $0.style = .FILL }
        canvas.drawPath(path, fill)
    }

    // ---------- 함수 그래프 (접선·정적분 넓이 — 시험지 그림처럼) ----------

    /// values = 다항식 계수(내림차순). op=tangent 는 x=p 에서 점+접선, op=area 는 [p,q] 구간 음영
    private func drawCurveOp(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let coeffs = v.values.map { $0.toFloat() }
        if coeffs.isEmpty { return }
        func f(_ x: Float) -> Float { var r: Float = 0; for c in coeffs { r = r * x + c }; return r }
        func fp(_ x: Float) -> Float {
            var r: Float = 0
            let n = coeffs.count - 1
            for i in 0..<n { r = r * x + coeffs[i] * Float(n - i) }
            return r
        }
        let isArea = v.op == "area"
        let isPoly = v.op == "poly"
        let x0 = v.p.toFloat()
        let x1 = v.q.toFloat()
        let xLo: Float; let xHi: Float
        if isArea { xLo = min(x0, x1) - 1; xHi = max(x0, x1) + 1 }
        else if isPoly && coeffs.count == 3 && coeffs[0] != 0 {
            // 이차식: 꼭짓점과 근이 다 보이게
            let vx = -coeffs[1] / (2 * coeffs[0])
            let r = max(abs(vx) + 3, 4)
            xLo = -r; xHi = r
        } else { let r = max(abs(x0) + 2, 3); xLo = -r; xHi = r }
        var yMax: Float = 1
        do {
            var x = xLo
            while x <= xHi { yMax = max(yMax, abs(f(x))); x += (xHi - xLo) / 40 }
        }
        let padL = dp(10.0)
        let ux = (w - padL * 2) / (xHi - xLo)
        let cy = h * 0.52
        let uy = h * 0.42 / yMax
        func X(_ x: Float) -> Float { padL + (x - xLo) * ux }
        func Y(_ y: Float) -> Float { cy - y * uy }
        let axis = newPaint {
            $0.color = Color.parseColor("#8D6E63"); $0.strokeWidth = dp(2.0)
        }
        let lbl = newPaint {
            $0.color = Color.parseColor("#4E342E"); $0.textSize = dp(14.0); $0.textAlign = .CENTER
            $0.isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            $0.setShadowLayer(dp(3.0), 0, 0, Color.WHITE)
        }
        canvas.drawLine(X(xLo), Y(0), X(xHi), Y(0), axis)
        if 0 >= xLo && 0 <= xHi { canvas.drawLine(X(0), dp(4.0), X(0), h - dp(4.0), axis) }
        canvas.drawText("O", X(0) - dp(8.0), Y(0) + dp(13.0), lbl)
        // 정적분 영역 음영
        if isArea {
            let fillPath = Path()
            fillPath.moveTo(X(x0), Y(0))
            var x = x0
            while x <= x1 { fillPath.lineTo(X(x), Y(f(x))); x += (x1 - x0) / 60 }
            fillPath.lineTo(X(x1), Y(0)); fillPath.close()
            canvas.drawPath(fillPath, newPaint {
                $0.color = Color.parseColor("#66FFB74D"); $0.style = .FILL
            })
            canvas.drawText(fmtC(x1), X(x1), Y(0) + dp(15.0), lbl)
        }
        // 곡선
        let curvePath = Path()
        var first = true
        var x = xLo
        while x <= xHi {
            let yy = f(x)
            if abs(yy) <= yMax * 1.05 {
                if first { curvePath.moveTo(X(x), Y(yy)); first = false }
                else { curvePath.lineTo(X(x), Y(yy)) }
            } else { first = true }
            x += (xHi - xLo) / 120
        }
        canvas.drawPath(curvePath, newPaint {
            $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
        })
        canvas.drawText("y = f(x)", X(xHi) - dp(26.0), Y(f(xHi)) - dp(8.0), lbl)
        // 이차식의 실근 표시 (판별식·이차방정식)
        if isPoly {
            if coeffs.count == 3 && coeffs[0] != 0 {
                let disc = coeffs[1] * coeffs[1] - 4 * coeffs[0] * coeffs[2]
                if disc >= 0 {
                    let sq = sqrt(disc)
                    let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
                    for root in [(-coeffs[1] - sq) / (2 * coeffs[0]), (-coeffs[1] + sq) / (2 * coeffs[0])] {
                        if root >= xLo && root <= xHi {
                            canvas.drawCircle(X(root), Y(0), dp(5.0), dot)
                            canvas.drawText(fmtC(root), X(root), Y(0) + dp(16.0), lbl)
                        }
                    }
                }
            }
            return
        }
        // 접선 + 접점
        if !isArea {
            let k = fp(x0); let y0 = f(x0)
            let d = (xHi - xLo) * 0.22
            canvas.drawLine(X(x0 - d), Y(y0 - k * d), X(x0 + d), Y(y0 + k * d),
                newPaint {
                    $0.color = Color.parseColor("#42A5F5"); $0.strokeWidth = dp(2.5)
                })
            canvas.drawCircle(X(x0), Y(y0), dp(5.0), newPaint {
                $0.color = Color.parseColor("#5C6BC0")
            })
            canvas.drawText("x = \(fmtC(x0))", X(x0), Y(0) + dp(15.0), lbl)
        }
    }

    // ---------- 치수 표기 도형 (넓이·둘레·피타고라스 — 교과서 그림처럼) ----------

    private func drawGeom(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let fill = newPaint { $0.color = Color.parseColor("#FFF3D6") }
        let edge = newPaint {
            $0.color = Color.parseColor("#6D4C41"); $0.strokeWidth = dp(2.5); $0.style = .STROKE
        }
        let dash = copyPaint(edge) {
            $0.strokeWidth = dp(1.5); $0.color = Color.parseColor("#B39F8C")
            $0.pathEffect = DashPathEffect([dp(5.0), dp(4.0)], 0)
        }
        let lbl = newPaint {
            $0.color = Color.parseColor("#4E342E"); $0.textSize = dp(14.0); $0.textAlign = .CENTER
            $0.isFakeBoldText = true
            $0.setShadowLayer(dp(3.0), 0, 0, Color.WHITE)
        }
        let cx = w / 2; let cy = h / 2
        func num(_ d: Double) -> String { d == floor(d) ? d.toInt().toString() : "\(d)" }
        switch v.op {
        case "rect":
            // 비율은 값 비슷하게, 화면에 맞게 (완전 비례일 필요는 없다)
            let ratio = (v.p / v.q).toFloat().coerceIn(0.5, 2.2)
            var rw = w * 0.52; var rh = rw / ratio
            if rh > h * 0.6 { rh = h * 0.6; rw = rh * ratio }
            let r = RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
            canvas.drawRect(r, fill); canvas.drawRect(r, edge)
            canvas.drawText("\(num(v.p))cm", cx, r.bottom + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("\(num(v.q))cm", r.right + dp(8.0), cy + dp(5.0), lbl)
            lbl.textAlign = .CENTER
        case "tri":
            let bw = w * 0.5; let th = h * 0.56
            let apexX = cx - bw * 0.1
            let path = Path()
            path.moveTo(cx - bw / 2, cy + th / 2); path.lineTo(cx + bw / 2, cy + th / 2)
            path.lineTo(apexX, cy - th / 2); path.close()
            canvas.drawPath(path, fill); canvas.drawPath(path, edge)
            canvas.drawLine(apexX, cy - th / 2, apexX, cy + th / 2, dash)
            canvas.drawText("\(num(v.p))cm", cx, cy + th / 2 + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("\(num(v.q))cm", apexX + dp(6.0), cy, lbl)
            lbl.textAlign = .CENTER
        case "trap":
            let a = (v.values.getOrNull(0) ?? 1.0); let b = (v.values.getOrNull(1) ?? 2.0)
            let top = w * 0.30 * (a / max(a, b)).toFloat().coerceAtLeast(0.35)
            let bot = w * 0.30
            let th = h * 0.52
            let path = Path()
            path.moveTo(cx - top, cy - th / 2); path.lineTo(cx + top, cy - th / 2)
            path.lineTo(cx + bot, cy + th / 2); path.lineTo(cx - bot, cy + th / 2); path.close()
            canvas.drawPath(path, fill); canvas.drawPath(path, edge)
            canvas.drawLine(cx, cy - th / 2, cx, cy + th / 2, dash)
            canvas.drawText("\(num(a))cm", cx, cy - th / 2 - dp(8.0), lbl)
            canvas.drawText("\(num(b))cm", cx, cy + th / 2 + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("\(num(v.values.getOrNull(2) ?? 0.0))cm", cx + dp(6.0), cy + dp(4.0), lbl)
            lbl.textAlign = .CENTER
        case "circle":
            let r = min(w, h) * 0.34
            canvas.drawCircle(cx, cy, r, fill); canvas.drawCircle(cx, cy, r, edge)
            canvas.drawLine(cx, cy, cx + r, cy, edge)
            canvas.drawCircle(cx, cy, dp(3.5), newPaint {
                $0.color = Color.parseColor("#6D4C41")
            })
            canvas.drawText("\(num(v.p))cm", cx + r / 2, cy - dp(8.0), lbl)
        case "rtri":
            // labels = [밑변, 세로, 빗변] — 구하는 변은 "?"
            let bw = w * 0.46; let th = h * 0.56
            let lx = cx - bw / 2; let rx = cx + bw / 2
            let ty = cy - th / 2; let by = cy + th / 2
            let path = Path()
            path.moveTo(lx, by); path.lineTo(rx, by); path.lineTo(rx, ty); path.close()
            canvas.drawPath(path, fill); canvas.drawPath(path, edge)
            // 직각 표시
            canvas.drawRect(rx - dp(12.0), by - dp(12.0), rx, by, dash)
            canvas.drawText(v.labels.getOrNull(0) ?? "", cx, by + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText(v.labels.getOrNull(1) ?? "", rx + dp(8.0), cy + dp(5.0), lbl)
            lbl.textAlign = .RIGHT
            canvas.drawText(v.labels.getOrNull(2) ?? "", cx - dp(10.0), cy - dp(12.0), lbl)
            lbl.textAlign = .CENTER
        case "cyl":
            let rw = w * 0.42; let eh = rw * 0.28; let bh = h * 0.5
            let top = cy - bh / 2; let bot = cy + bh / 2
            let body = RectF(cx - rw / 2, top, cx + rw / 2, bot)
            canvas.drawRect(body, fill)
            canvas.drawLine(body.left, top, body.left, bot, edge)
            canvas.drawLine(body.right, top, body.right, bot, edge)
            let topOval = RectF(body.left, top - eh / 2, body.right, top + eh / 2)
            let botOval = RectF(body.left, bot - eh / 2, body.right, bot + eh / 2)
            canvas.drawOval(botOval, fill)
            canvas.drawArc(botOval, 0, 180, false, edge)
            canvas.drawOval(topOval, fill); canvas.drawOval(topOval, edge)
            canvas.drawLine(cx, top, cx + rw / 2, top, dash)
            canvas.drawText("\(num(v.p))", cx + rw / 4, top - dp(7.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("\(num(v.q))", body.right + dp(8.0), cy + dp(5.0), lbl)
            lbl.textAlign = .CENTER
        case "box":
            // 직육면체 — 앞면 + 위·옆으로 비스듬한 깊이 (교과서 그림)
            let fw = w * 0.40; let fh = h * 0.42
            let dx = fw * 0.38; let dy = fh * 0.34
            let lx = cx - (fw + dx) / 2
            let by = cy + (fh + dy) / 2
            let f = RectF(lx, by - fh, lx + fw, by)
            // 뒷면 모서리 점들
            let tlb = PointF(f.left + dx, f.top - dy)
            let trb = PointF(f.right + dx, f.top - dy)
            let brb = PointF(f.right + dx, f.bottom - dy)
            // 윗면·옆면 (채움 살짝 다르게)
            let topPath = Path()
            topPath.moveTo(f.left, f.top); topPath.lineTo(tlb.x, tlb.y); topPath.lineTo(trb.x, trb.y)
            topPath.lineTo(f.right, f.top); topPath.close()
            let sidePath = Path()
            sidePath.moveTo(f.right, f.top); sidePath.lineTo(trb.x, trb.y); sidePath.lineTo(brb.x, brb.y)
            sidePath.lineTo(f.right, f.bottom); sidePath.close()
            let fill2 = copyPaint(fill) { $0.color = Color.parseColor("#FFE9C4") }
            canvas.drawRect(f, fill)
            canvas.drawPath(topPath, fill2)
            canvas.drawPath(sidePath, fill2)
            canvas.drawRect(f, edge)
            canvas.drawPath(topPath, edge)
            canvas.drawPath(sidePath, edge)
            canvas.drawText("\(num(v.p))cm", cx - dx / 2, f.bottom + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("\(num(v.values.getOrNull(0) ?? 0.0))cm",
                f.right + dx / 2 + dp(4.0), f.bottom - dy / 2 + dp(4.0), lbl)
            canvas.drawText("\(num(v.q))cm", f.right + dx + dp(6.0), (f.top + f.bottom - dy) / 2, lbl)
            lbl.textAlign = .CENTER
        case "ngon":
            // 정n각형 (+ 한 꼭짓점에서 뻗는 대각선)
            let n = v.p.toInt().coerceIn(3, 12)
            let r = min(w, h) * 0.36
            let pts = (0..<n).map { i -> PointF in
                let ang = Double.pi * 2 * Double(i) / Double(n) - Double.pi / 2
                return PointF(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
            }
            let path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for i in 1..<n { path.lineTo(pts[i].x, pts[i].y) }
            path.close()
            canvas.drawPath(path, fill); canvas.drawPath(path, edge)
            if v.q >= 1.0 {
                // 대각선: 0번 꼭짓점에서 이웃 아닌 모든 꼭짓점으로 (n=3 이면 없음)
                for i in stride(from: 2, through: n - 2, by: 1) {
                    canvas.drawLine(pts[0].x, pts[0].y, pts[i].x, pts[i].y, dash)
                }
            } else {
                // 내각 하나에 호 표시
                canvas.drawArc(
                    pts[0].x - dp(16.0), pts[0].y - dp(16.0), pts[0].x + dp(16.0), pts[0].y + dp(16.0),
                    40, 100, false, dash
                )
            }
            canvas.drawText(context.getString(R.string.vis_ngon, n), cx, cy + r + dp(20.0), lbl)
        case "ineq":
            // 수직선 위의 부등식: x > k (열린 원 + 오른쪽 화살표)
            let k = v.p
            let yLine = cy
            let axis = newPaint {
                $0.color = Color.parseColor("#8D6E63"); $0.strokeWidth = dp(2.0)
            }
            canvas.drawLine(w * 0.08, yLine, w * 0.92, yLine, axis)
            let kx = w * 0.42
            // 눈금 (k-1, k, k+1)
            for (i, t) in [-1, 0, 1].enumerated() {
                let tx = kx + Float(i - 1) * w * 0.18
                canvas.drawLine(tx, yLine - dp(5.0), tx, yLine + dp(5.0), axis)
                canvas.drawText(num(k + Double(t)), tx, yLine + dp(20.0), lbl)
            }
            // 오른쪽으로 뻗는 굵은 반직선 + 열린 원
            let ray = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(4.0)
            }
            canvas.drawLine(kx, yLine, w * 0.88, yLine, ray)
            canvas.drawCircle(kx, yLine, dp(6.0), newPaint {
                $0.color = Color.WHITE
            })
            canvas.drawCircle(kx, yLine, dp(6.0), copyPaint(ray) {
                $0.style = .STROKE; $0.strokeWidth = dp(2.5)
            })
        case "ucircle":
            // 단위원과 각 — 삼각비·삼각함수
            let deg = v.p.toFloat()
            let r = min(w, h) * 0.33
            let axis = newPaint {
                $0.color = Color.parseColor("#B39F8C"); $0.strokeWidth = dp(1.5)
            }
            canvas.drawLine(cx - r - dp(14.0), cy, cx + r + dp(14.0), cy, axis)
            canvas.drawLine(cx, cy - r - dp(14.0), cx, cy + r + dp(14.0), axis)
            edge.style = .STROKE
            canvas.drawCircle(cx, cy, r, edge)
            let rad = toRadians(Double(deg))
            let ex = cx + r * cos(rad).toFloat()
            let ey = cy - r * sin(rad).toFloat()
            canvas.drawLine(cx, cy, ex, ey, newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0)
            })
            canvas.drawCircle(ex, ey, dp(5.0), newPaint {
                $0.color = Color.parseColor("#5C6BC0")
            })
            // 각 호
            canvas.drawArc(cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3,
                0, -deg, false, dash)
            canvas.drawText("\(num(v.p))°", cx + r * 0.48, cy - dp(10.0), lbl)
        case "inscribed":
            // 중심각과 원주각 (원주각 = 중심각의 절반)
            let r = min(w, h) * 0.36
            canvas.drawCircle(cx, cy, r, fill)
            edge.style = .STROKE
            canvas.drawCircle(cx, cy, r, edge)
            func onCircle(_ deg: Double) -> PointF {
                PointF(
                    cx + r * cos(toRadians(deg)).toFloat(),
                    cy - r * sin(toRadians(deg)).toFloat()
                )
            }
            let a = onCircle(-35.0); let b = onCircle(215.0); let pTop = onCircle(90.0)
            let lineP = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(2.5)
            }
            // 중심각 (빨강)
            canvas.drawLine(cx, cy, a.x, a.y, lineP)
            canvas.drawLine(cx, cy, b.x, b.y, lineP)
            // 원주각 (파랑)
            let lineB = copyPaint(lineP) { $0.color = Color.parseColor("#42A5F5") }
            canvas.drawLine(pTop.x, pTop.y, a.x, a.y, lineB)
            canvas.drawLine(pTop.x, pTop.y, b.x, b.y, lineB)
            canvas.drawCircle(cx, cy, dp(3.5), newPaint {
                $0.color = Color.parseColor("#6D4C41")
            })
            canvas.drawText("\(num(v.p))°", cx, cy + dp(22.0), lbl)
            canvas.drawText("?", pTop.x, pTop.y + dp(26.0), lbl)
            canvas.drawText("O", cx - dp(11.0), cy + dp(3.0), lbl)
        case "sqarea":
            // 넓이가 주어진 정사각형 — 한 변은 ? (제곱근 도입 그림)
            let side = min(w, h) * 0.5
            let r = RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
            canvas.drawRect(r, fill); canvas.drawRect(r, edge)
            canvas.drawText(context.getString(R.string.vis_area, num(v.p)), cx, cy + dp(5.0), lbl)
            canvas.drawText("?", cx, r.bottom + dp(18.0), lbl)
            lbl.textAlign = .LEFT
            canvas.drawText("?", r.right + dp(8.0), cy + dp(5.0), lbl)
            lbl.textAlign = .CENTER
        case "ratio":
            // 비 a : b — 길이가 비례하는 막대 두 개
            let a = (v.values.getOrNull(0) ?? 1.0).toFloat()
            let b = (v.values.getOrNull(1) ?? 1.0).toFloat()
            let maxLen = w * 0.62
            let unitLen = maxLen / max(a, b)
            let bh = h * 0.16
            let left = w * 0.16
            let barA = newPaint { $0.color = Color.parseColor("#FF8A80") }
            let barB = newPaint { $0.color = Color.parseColor("#81D4FA") }
            let y1 = cy - bh * 1.1; let y2 = cy + bh * 0.5
            canvas.drawRoundRect(left, y1, left + a * unitLen, y1 + bh, dp(6.0), dp(6.0), barA)
            canvas.drawRoundRect(left, y2, left + b * unitLen, y2 + bh, dp(6.0), dp(6.0), barB)
            lbl.textAlign = .LEFT
            canvas.drawText(num(v.values.getOrNull(0) ?? 0.0), left + a * unitLen + dp(8.0), y1 + bh * 0.72, lbl)
            canvas.drawText(num(v.values.getOrNull(1) ?? 0.0), left + b * unitLen + dp(8.0), y2 + bh * 0.72, lbl)
            lbl.textAlign = .CENTER
        case "percent":
            // 전체 중 부분 — 가로 막대 (몇 % 인지는 아이가 계산)
            let part = v.p.toFloat(); let total = v.q.toFloat().coerceAtLeast(1)
            let left = w * 0.1; let right = w * 0.9
            let bh = h * 0.2
            let barBg = newPaint { $0.color = Color.parseColor("#EADFCE") }
            let barFg = newPaint { $0.color = Color.parseColor("#FFB74D") }
            canvas.drawRoundRect(left, cy - bh / 2, right, cy + bh / 2, dp(8.0), dp(8.0), barBg)
            canvas.drawRoundRect(left, cy - bh / 2,
                left + (right - left) * (part / total), cy + bh / 2, dp(8.0), dp(8.0), barFg)
            canvas.drawText("\(num(v.p))", left + (right - left) * (part / total) / 2, cy + dp(5.0), lbl)
            canvas.drawText(context.getString(R.string.vis_total, num(v.q)), (left + right) / 2, cy + bh / 2 + dp(18.0), lbl)
        case "bag":
            // 주머니 속 빨강·파랑 구슬 (values = [빨강, 파랑])
            let rCnt = (v.values.getOrNull(0) ?? 1.0).toInt().coerceIn(0, 12)
            let bCnt = (v.values.getOrNull(1) ?? 1.0).toInt().coerceIn(0, 12)
            let bw = w * 0.52; let bhh = h * 0.66
            let top = cy - bhh / 2
            // 주머니: 위가 오므라진 자루 모양
            let bag = Path()
            bag.moveTo(cx - bw * 0.16, top)
            bag.quadTo(cx - bw * 0.55, top + bhh * 0.25, cx - bw * 0.5, top + bhh * 0.62)
            bag.quadTo(cx - bw * 0.45, top + bhh, cx, top + bhh)
            bag.quadTo(cx + bw * 0.45, top + bhh, cx + bw * 0.5, top + bhh * 0.62)
            bag.quadTo(cx + bw * 0.55, top + bhh * 0.25, cx + bw * 0.16, top)
            bag.close()
            canvas.drawPath(bag, newPaint {
                $0.color = Color.parseColor("#FFE9C4")
            })
            canvas.drawPath(bag, edge)
            // 주머니 입구 묶음
            canvas.drawLine(cx - bw * 0.16, top, cx + bw * 0.16, top, edge)
            // 구슬 배치 — 주머니 안쪽 상자에 꼭 맞게
            let total = rCnt + bCnt
            let innerW = bw * 0.72
            let innerH = bhh * 0.46
            var mr: Float = 0; var perRow = 1
            for rows in 1...max(total, 1) {
                let pr = ceil(Float(total) / Float(rows)).toInt()
                let cell = min(innerW / Float(pr), innerH / Float(rows))
                if cell > mr { mr = cell; perRow = pr }
            }
            mr = (mr / 2) * 0.86
            let rows = ceil(Float(total) / Float(perRow)).toInt()
            let startY = top + bhh * 0.58 - Float(rows - 1) * mr * 1.15
            let red = newPaint { $0.color = Color.parseColor("#EF5350") }
            let blue = newPaint { $0.color = Color.parseColor("#42A5F5") }
            let ring = newPaint {
                $0.color = Color.parseColor("#6D4C41"); $0.style = .STROKE; $0.strokeWidth = dp(1.5)
            }
            for i in 0..<total {
                let row = i / perRow
                let col = i % perRow
                let inRow = min(perRow, total - row * perRow)
                let gx = cx + (Float(col) - Float(inRow - 1) / 2) * mr * 2.3
                let gy = startY + Float(row) * mr * 2.3
                canvas.drawCircle(gx, gy, mr, i < rCnt ? red : blue)
                canvas.drawCircle(gx, gy, mr, ring)
            }
        case "dice":
            // 주사위 눈 1~6 을 늘어놓고, p 이하를 강조
            let upto = v.p.toInt().coerceIn(1, 6)
            let size = min(w / 7.2, h * 0.4)
            let gap = size * 0.18
            let totalW = size * 6 + gap * 5
            let startX = cx - totalW / 2
            let dy = cy - size / 2
            let pip = newPaint { $0.color = Color.parseColor("#4E342E") }
            let okFill = newPaint { $0.color = Color.parseColor("#FFE9C4") }
            let noFill = newPaint { $0.color = Color.WHITE }
            let okEdge = newPaint {
                $0.color = Color.parseColor("#FF7043"); $0.style = .STROKE; $0.strokeWidth = dp(2.5)
            }
            let noEdge = newPaint {
                $0.color = Color.parseColor("#BCAAA4"); $0.style = .STROKE; $0.strokeWidth = dp(1.5)
            }
            for face in 1...6 {
                let x0 = startX + Float(face - 1) * (size + gap)
                let r = RectF(x0, dy, x0 + size, dy + size)
                let ok = face <= upto
                canvas.drawRoundRect(r, size * 0.18, size * 0.18, ok ? okFill : noFill)
                canvas.drawRoundRect(r, size * 0.18, size * 0.18, ok ? okEdge : noEdge)
                // 눈 배치
                let c = size * 0.5; let o = size * 0.26; let pr = size * 0.08
                func dot(_ dxr: Float, _ dyr: Float) {
                    canvas.drawCircle(x0 + c + dxr, dy + c + dyr, pr, pip)
                }
                switch face {
                case 1: dot(0, 0)
                case 2: dot(-o, -o); dot(o, o)
                case 3: dot(-o, -o); dot(0, 0); dot(o, o)
                case 4: dot(-o, -o); dot(o, -o); dot(-o, o); dot(o, o)
                case 5: dot(-o, -o); dot(o, -o); dot(0, 0); dot(-o, o); dot(o, o)
                default: dot(-o, -o); dot(o, -o); dot(-o, 0); dot(o, 0); dot(-o, o); dot(o, o)
                }
            }
        case "setdots":
            // 원소 n개인 집합 — 큰 원 안의 점들
            let n = v.p.toInt().coerceIn(1, 12)
            let r = min(w, h) * 0.36
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
            let dotP = newPaint { $0.color = Color.parseColor("#5C6BC0") }
            for i in 0..<n {
                let ang = Double.pi * 2 * Double(i) / Double(n)
                let rr: Float = n == 1 ? 0 : r * 0.55
                canvas.drawCircle(
                    cx + rr * cos(ang).toFloat(),
                    cy + rr * sin(ang).toFloat(), dp(5.0), dotP
                )
            }
            canvas.drawText(context.getString(R.string.vis_elements, n), cx, cy + r + dp(20.0), lbl)
        default: break
        }
    }

    // ---------- 지수·로그 곡선과 수열의 극한 ----------

    private func drawFuncMisc(_ canvas: Canvas, _ v: MathVisual, _ w: Float, _ h: Float) {
        let axis = newPaint {
            $0.color = Color.parseColor("#8D6E63"); $0.strokeWidth = dp(2.0)
        }
        let curveP = newPaint {
            $0.color = Color.parseColor("#FF7043"); $0.strokeWidth = dp(3.0); $0.style = .STROKE
        }
        let dot = newPaint { $0.color = Color.parseColor("#5C6BC0") }
        let lbl = newPaint {
            $0.color = Color.parseColor("#4E342E"); $0.textSize = dp(14.0); $0.textAlign = .CENTER
            $0.isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            $0.setShadowLayer(dp(3.0), 0, 0, Color.WHITE)
        }
        let dash = newPaint {
            $0.color = Color.parseColor("#B39F8C"); $0.strokeWidth = dp(1.5); $0.style = .STROKE
            $0.pathEffect = DashPathEffect([dp(5.0), dp(4.0)], 0)
        }
        switch v.op {
        case "exp":
            // y = a^x : (0,1) 과 (1,a) 를 지나며 치솟는 곡선
            let base = v.p.toFloat().coerceAtLeast(1.2)
            let xLo: Float = -2.5; let xHi: Float = 2.6
            let yMax = pow(Double(base), 2.6).toFloat()
            let ux = w * 0.86 / (xHi - xLo); let padL = w * 0.07
            let uy = h * 0.82 / yMax
            func X(_ x: Float) -> Float { padL + (x - xLo) * ux }
            func Y(_ y: Float) -> Float { h * 0.9 - y * uy }
            canvas.drawLine(X(xLo), Y(0), X(xHi), Y(0), axis)
            canvas.drawLine(X(0), dp(4.0), X(0), h - dp(4.0), axis)
            let path = Path(); var first = true
            var x = xLo
            while x <= xHi {
                let yy = pow(Double(base), Double(x)).toFloat()
                if first { path.moveTo(X(x), Y(yy)); first = false } else { path.lineTo(X(x), Y(yy)) }
                x += 0.06
            }
            canvas.drawPath(path, curveP)
            canvas.drawCircle(X(1), Y(base), dp(5.0), dot)
            canvas.drawText("(1, \(fmtC(base)))", X(1) - dp(26.0), Y(base) - dp(6.0), lbl)
            canvas.drawText("y = \(fmtC(base))ˣ", X(xHi) - dp(30.0), dp(16.0), lbl)
        case "log":
            // y = log_a x : (1,0) 과 (a,1) 을 지나 천천히 자라는 곡선
            let base = v.p.toFloat().coerceAtLeast(1.2)
            let xHi = max(base * 2.2, 7)
            let lnB = Foundation.log(Double(base))
            let yMax = (Foundation.log(Double(xHi)) / lnB).toFloat()
            let yMin: Float = -1.6
            let ux = w * 0.86 / xHi; let padL = w * 0.09
            let uy = h * 0.8 / (yMax - yMin)
            func X(_ x: Float) -> Float { padL + x * ux }
            func Y(_ y: Float) -> Float { h * 0.86 - (y - yMin) * uy }
            canvas.drawLine(X(0), Y(0), X(xHi), Y(0), axis)
            canvas.drawLine(X(0), dp(4.0), X(0), h - dp(4.0), axis)
            let path = Path(); var first = true
            var x: Float = 0.15
            while x <= xHi {
                let yy = (Foundation.log(Double(x)) / lnB).toFloat()
                if yy >= yMin {
                    if first { path.moveTo(X(x), Y(yy)); first = false } else { path.lineTo(X(x), Y(yy)) }
                }
                x += xHi / 130
            }
            canvas.drawPath(path, curveP)
            canvas.drawCircle(X(base), Y(1), dp(5.0), dot)
            canvas.drawText("(\(fmtC(base)), 1)", X(base), Y(1) - dp(9.0), lbl)
            canvas.drawText("y = log_\(fmtC(base)) x", X(xHi) - dp(36.0), Y(yMax) + dp(2.0), lbl)
        case "seqlim":
            // 수열 (an+b)/(cn+d) 의 점들이 극한값에 다가가는 모습
            let a = vAt(v, 0); let b0 = vAt(v, 1); let c = vAt(v, 2); let d0 = vAt(v, 3)
            func term(_ n: Int) -> Float { (a * Float(n) + b0) / (c * Float(n) + d0) }
            let lim = a / c
            var yMax = lim
            for n in 1...12 { yMax = max(yMax, term(n)) }
            yMax *= 1.15
            let ux = w * 0.86 / 13; let padL = w * 0.08
            let uy = h * 0.78 / yMax
            func X(_ n: Float) -> Float { padL + n * ux }
            func Y(_ y: Float) -> Float { h * 0.88 - y * uy }
            canvas.drawLine(X(0), Y(0), X(12.6), Y(0), axis)
            canvas.drawLine(X(0), dp(4.0), X(0), h - dp(4.0), axis)
            canvas.drawLine(X(0), Y(lim), X(12.6), Y(lim), dash)
            for n in 1...12 { canvas.drawCircle(X(Float(n)), Y(term(n)), dp(4.0), dot) }
            canvas.drawText("n", X(12.4), Y(0) + dp(15.0), lbl)
            canvas.drawText(context.getString(R.string.vis_converge), X(6), Y(lim) - dp(9.0), lbl)
        default: break
        }
    }

    private func dp(_ v: Float) -> Float { v * context.resources.displayMetrics.density }

    // ---------- 코틀린 Math 대신 ----------

    /// 자바 Math.round — .5 는 위로, NaN 은 0
    private func mathRound(_ d: Double) -> Int { d.isNaN ? 0 : d.roundToInt() }
    private func toRadians(_ deg: Double) -> Double { deg / 180.0 * Double.pi }
    private func toDegrees(_ rad: Double) -> Double { rad * 180.0 / Double.pi }

    /// 코틀린 `Paint(Paint.ANTI_ALIAS_FLAG).apply { }`
    private func newPaint(_ block: (Paint) -> Void) -> Paint {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        block(p)
        return p
    }
    /// 코틀린 `Paint(src).apply { }`
    private func copyPaint(_ src: Paint, _ block: (Paint) -> Void) -> Paint {
        let p = Paint(src)
        block(p)
        return p
    }
}
