import UIKit

/// 사물을 손으로 끌어다 바구니에 담는 판.
/// - 나눠 담기(setRound): 같은 사물을 바구니에 똑같은 개수로 나눠 담는다 (나눗셈).
/// - 분류하기(setSort): 사물마다 들어갈 바구니가 정해져 있다 (도형 분류).
/// - 모으기·덜어내기(setGather): 바구니 하나에 정해진 개수만 담는다.
final class GroupDragView: View {

    private final class Item {
        let emoji: String
        /// 들어가야 할 바구니. -1 이면 아무 데나 (나눠 담기 모드)
        let kind: Int
        var x: Float, y: Float
        var homeX: Float, homeY: Float
        /// 지금 담긴 바구니. -1 이면 아직 안 담김
        var group: Int = -1
        /// 바구니 안에서 그릴 크기 (0 이면 기본 크기)
        var size: Float = 0
        init(_ emoji: String, _ kind: Int, _ x: Float, _ y: Float, _ homeX: Float, _ homeY: Float) {
            self.emoji = emoji; self.kind = kind; self.x = x; self.y = y; self.homeX = homeX; self.homeY = homeY
        }
    }

    private var items: [Item] = []
    private var groupRects: [RectF] = []
    private var dragging: Item?
    private var dragDx: Float = 0
    private var dragDy: Float = 0

    private var specs: [(String, Int)] = []   // (이모지, 들어갈 바구니)
    private var groups = 3
    private var labels: [String] = []

    /// 분류 모드인가 (아니면 똑같이 나눠 담기)
    private var sortMode = false

    /// 바구니 하나에 정해진 개수만 담는 모드 (모으기·덜어내기). -1 이면 안 씀
    private var needCount = -1

    private let emojiPaint: Paint = { let p = Paint(Paint.ANTI_ALIAS_FLAG); p.textAlign = .CENTER; return p }()
    private let boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private let strokePaint: Paint = { let p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = .STROKE; return p }()
    private let textPaint: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = .CENTER
        p.isFakeBoldText = true
        return p
    }()

    private var itemSize: Float = 0

    /// 줄 간격 배수 — 이모지가 지정 크기보다 크게 그려지는 걸 감안한 값
    private static let ROW_STEP: Float = 1.32

    /// 담긴 상태가 바뀔 때 (바구니별 개수)
    var onChanged: (([Int]) -> Void)?

    /// 바구니에 하나 담을 때마다
    var onPlace: (() -> Void)?

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
    }
    required init?(coder: NSCoder) { fatalError() }

    /// 똑같이 나눠 담기 — 같은 사물 total 개를 바구니 groups 개에 나눈다
    func setRound(_ emoji: String, _ total: Int, _ groups: Int) {
        sortMode = false
        needCount = -1
        self.groups = groups.coerceAtLeast(1)
        labels = (1...self.groups).map { context.getString(R.string.basket_no, $0) }
        specs = (0..<max(0, total)).map { _ in (emoji, -1) }
        rebuild()
    }

    /// 바구니 하나에 손으로 옮겨 담기. 덧셈은 전부 모으고(need == total), 뺄셈은 덜어낼 만큼만 담는다.
    func setGather(_ emoji: String, _ total: Int, _ need: Int, _ label: String) {
        sortMode = false
        needCount = need
        groups = 1
        labels = [label]
        specs = (0..<max(0, total)).map { _ in (emoji, -1) }
        rebuild()
    }

    /// 아직 바구니 밖에 남아 있는 개수
    func outsideCount() -> Int { items.filter { $0.group < 0 }.count }

    /// 바구니에 담긴 개수
    func inBoxCount() -> Int { items.filter { $0.group == 0 }.count }

    /// 분류하기 — items[i] 는 labels[kinds[i]] 바구니에 들어가야 한다
    func setSort(_ items: [String], _ kinds: [Int], _ labels: [String]) {
        sortMode = true
        self.groups = labels.count.coerceAtLeast(1)
        self.labels = labels
        specs = items.enumerated().map { ($0.element, kinds.getOrNull($0.offset) ?? 0) }
        rebuild()
    }

    private func rebuild() {
        items.removeAll()
        layoutAll()
        onChanged?(counts())
        invalidate()
    }

    /// 바구니별 담긴 개수
    func counts() -> [Int] { (0..<groups).map { g in items.filter { $0.group == g }.count } }

    /// 아직 안 담은 개수
    func leftOver() -> Int { items.filter { $0.group < 0 }.count }

    /// 엉뚱한 바구니에 담긴 개수 (분류 모드에서만 의미 있다)
    func misplaced() -> Int { items.filter { $0.group >= 0 && $0.kind >= 0 && $0.group != $0.kind }.count }

    func isCorrect() -> Bool {
        if items.isEmpty { return false }
        // 모으기·덜어내기는 바구니 밖에 남는 게 정상이다
        if needCount >= 0 { return inBoxCount() == needCount }
        if leftOver() > 0 { return false }
        if sortMode {
            return misplaced() == 0
        } else {
            let c = counts()
            return !c.isEmpty && c.allSatisfy { $0 == c[0] } && c[0] > 0
        }
    }

    /// 한 바구니에 담긴 개수 (나눠 담기 정답일 때의 몫)
    func perGroup() -> Int { counts().first ?? 0 }

    func reset() {
        for it in items {
            it.group = -1
            it.x = it.homeX
            it.y = it.homeY
        }
        onChanged?(counts())
        invalidate()
    }

    override func onSizeChanged(_ w: Int, _ h: Int, _ oldw: Int, _ oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutAll()
    }

    private func layoutAll() {
        if width == 0 || height == 0 || specs.isEmpty { return }
        let w = Float(width)
        let h = Float(height)
        let total = specs.count

        // 아래쪽을 바구니 자리로
        let zoneTop = h * 0.50
        groupRects = []
        let gw = (w - dp(10) * Float(groups + 1)) / Float(groups)
        for g in 0..<groups {
            let left = dp(10) + (gw + dp(10)) * Float(g)
            groupRects.append(RectF(left, zoneTop, left + gw, h - dp(8)))
        }

        let perRow = min(6, total)
        let rows = (total + perRow - 1) / perRow
        // 이모지 글리프는 지정 크기보다 위아래로 더 튀어나온다 — 줄 간격을 1.32배로 벌린다
        itemSize = min(w / (Float(perRow) + 1.4), (zoneTop - dp(16)) / (Float(rows) * GroupDragView.ROW_STEP + 0.4))
        // 바구니 이름은 바구니 폭에 맞춘다
        textPaint.textSize = min(itemSize * 0.45, gw * 0.20)

        // 이미 담아 놓은 걸 잃지 않도록, 크기만 바뀌면 자리만 다시 잡는다
        let keep = items.count == total
        if !keep { items.removeAll() }
        for i in 0..<total {
            let r = i / perRow
            let c = i % perRow
            let cols = min(perRow, total - r * perRow)
            let rowW = Float(cols) * itemSize * 1.15
            let x = (w - rowW) / 2 + itemSize * 1.15 * (Float(c) + 0.5)
            let y = dp(14) + itemSize * GroupDragView.ROW_STEP * (Float(r) + 0.5)
            if keep {
                let it = items[i]
                it.homeX = x; it.homeY = y
                if it.group < 0 { it.x = x; it.y = y }
            } else {
                let (e, kind) = specs[i]
                items.append(Item(e, kind, x, y, x, y))
            }
        }
        if keep {
            for g in 0..<groups {
                items.filter { $0.group == g }.forEach { placeInGroup($0, g) }
            }
        }
    }

    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        if items.isEmpty { return }

        for (g, rect) in groupRects.enumerated() {
            let n = items.filter { $0.group == g }.count
            boxPaint.color = Color.parseColor(n > 0 ? "#FFF3E0" : "#FAFAFA")
            canvas.drawRoundRect(rect, dp(16), dp(16), boxPaint)
            strokePaint.color = Color.parseColor("#FFB300")
            strokePaint.strokeWidth = dp(3)
            strokePaint.pathEffect = DashPathEffect([dp(10), dp(8)], 0)
            canvas.drawRoundRect(rect, dp(16), dp(16), strokePaint)
            strokePaint.pathEffect = nil

            // 이름은 위, 개수는 아래 — 한 줄로 붙이면 좁은 바구니에서 칸 밖으로 넘친다
            textPaint.color = Color.parseColor("#8D6E63")
            canvas.drawText(
                labels.getOrNull(g) ?? context.getString(R.string.basket_no, g + 1),
                rect.centerX(), rect.top + textPaint.textSize * 1.1, textPaint
            )
            if !sortMode {
                textPaint.color = n > 0 ? Color.parseColor("#EF6C00") : Color.parseColor("#BCAAA4")
                canvas.drawText("\(n)", rect.centerX(), rect.bottom - dp(6), textPaint)
            }
        }

        for it in items {
            if it === dragging { continue }
            let sz = (it.group >= 0 && it.size > 0) ? it.size : itemSize
            drawEmojiItem(canvas, it.emoji, it.x, it.y, sz)
        }
        if let it = dragging {
            drawEmojiItem(canvas, it.emoji, it.x, it.y, itemSize * 1.18)
        }
    }

    /// 일러스트가 있으면 그림을, 없으면 이모지를 (ts = 이모지 글자 크기 기준)
    private func drawEmojiItem(_ canvas: Canvas, _ emoji: String, _ x: Float, _ y: Float, _ ts: Float) {
        if let d = EmojiArt.of(context, emoji) {
            // 이모지 글자의 눈에 보이는 중심이 (x, y) — 그림도 같은 자리에 놓는다
            let half = (ts * 0.52).toInt()
            d.setBounds((x - Float(half)).toInt(), (y - Float(half)).toInt(), (x + Float(half)).toInt(), (y + Float(half)).toInt())
            d.draw(canvas)
        } else {
            emojiPaint.textSize = ts
            canvas.drawText(emoji, x, y + ts * 0.35, emojiPaint)
        }
    }

    override func onTouchEvent(_ event: MotionEvent) -> Bool {
        switch event.actionMasked {
        case MotionEvent.ACTION_DOWN:
            dragging = items.last { hypotf(event.x - $0.x, event.y - $0.y) < itemSize * 0.7 }
            if let it = dragging { dragDx = it.x - event.x; dragDy = it.y - event.y }
            parent?.requestDisallowInterceptTouchEvent(true)
            invalidate()
            return dragging != nil
        case MotionEvent.ACTION_MOVE:
            if let it = dragging {
                it.x = event.x + dragDx
                it.y = event.y + dragDy
                invalidate()
            }
            return true
        case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL:
            guard let d = dragging else { return true }
            dragging = nil
            let gi = groupRects.indexOfFirst { $0.contains(d.x, d.y) }
            if gi >= 0 {
                d.group = gi
                placeInGroup(d, gi)
                onPlace?()
            } else {
                d.group = -1
                d.x = d.homeX
                d.y = d.homeY
            }
            onChanged?(counts())
            invalidate()
            return true
        default:
            return false
        }
    }

    /// 바구니 안에서 겹치지 않게 자리 잡기
    private func placeInGroup(_ item: Item, _ g: Int) { relayoutGroup(g) }

    /// 바구니에 담긴 것들을 다시 줄 세운다 — 담긴 개수에 맞춰 칸(과 그림)을 줄여 항상 바구니 안에 들어온다.
    private func relayoutGroup(_ g: Int) {
        guard let rect = groupRects.getOrNull(g) else { return }
        let mine = items.filter { $0.group == g }
        if mine.isEmpty { return }
        let top = rect.top + textPaint.textSize * 1.5
        let bottom = rect.bottom - (sortMode ? dp(6) : textPaint.textSize * 1.3)
        let availW = rect.width() - dp(8)
        let availH = (bottom - top).coerceAtLeast(dp(24))
        let n = mine.count

        // 줄 수를 늘려 가며 바구니에 들어가는 가장 큰 칸을 고른다
        var best: Float = 0; var bestPerRow = 1
        for rows in 1...n {
            let perRow = Int(ceil(Float(n) / Float(rows)))
            let cell = min(availW / Float(perRow), availH / Float(rows))
            if cell > best { best = cell; bestPerRow = perRow }
        }
        let cell = best.coerceAtMost(itemSize * 1.05)
        let perRow = bestPerRow
        let rows = Int(ceil(Float(n) / Float(perRow)))
        let startY = top + (availH - cell * Float(rows)) / 2 + cell / 2
        for (idx, it) in mine.enumerated() {
            let r = idx / perRow
            let c = idx % perRow
            let inRow = min(perRow, n - r * perRow)
            it.x = rect.centerX() + (Float(c) - Float(inRow - 1) / 2) * cell
            it.y = startY + Float(r) * cell
            it.size = cell * 0.82
        }
    }

    private func dp(_ v: Float) -> Float { v * context.resources.displayMetrics.density }
}
