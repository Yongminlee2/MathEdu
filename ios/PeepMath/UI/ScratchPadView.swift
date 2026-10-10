import UIKit

/// 문제 위에 겹쳐 쓰는 연습장.
/// 배경이 투명해서 문제·그림이 그대로 비치고, 그 위에 식을 쓰거나 표시할 수 있다.
/// 획 목록을 들고 있다가 비트맵에 다시 그린다(되돌리기가 간단). 지우개는 CLEAR 로 진짜 지운다.
final class ScratchPadView: View {

    private final class Stroke {
        let path: Path
        let color: Int
        let width: Float
        let eraser: Bool
        init(_ path: Path, _ color: Int, _ width: Float, _ eraser: Bool) {
            self.path = path; self.color = color; self.width = width; self.eraser = eraser
        }
    }

    private var strokes: [Stroke] = []
    private var current: Stroke?

    private var bitmap: Bitmap?
    private var bitmapCanvas: Canvas?

    private let paint: Paint = {
        let p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = .STROKE
        p.strokeCap = .ROUND
        p.strokeJoin = .ROUND
        return p
    }()
    private let clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /// 지금 쓰고 있는 펜
    var penColor: Int = Color.parseColor("#3E2723")
    var penWidth: Float = 6
    var eraserMode: Bool = false

    /// 획 수가 바뀔 때 (버튼 활성화용)
    var onChanged: (() -> Void)?

    var isEmpty: Bool { strokes.isEmpty }

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
        setLayerType(View.LAYER_TYPE_SOFTWARE, nil)
        isClickable = true
    }
    required init?(coder: NSCoder) { fatalError() }

    override func onSizeChanged(_ w: Int, _ h: Int, _ oldw: Int, _ oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if w <= 0 || h <= 0 { return }
        let bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap = bm
        bitmapCanvas = Canvas(bm)
        redraw()
    }

    private func applyPaint(_ s: Stroke) {
        paint.strokeWidth = s.width * context.resources.displayMetrics.density
        if s.eraser {
            paint.xfermode = clearMode
            paint.color = Color.TRANSPARENT
        } else {
            paint.xfermode = nil
            paint.color = s.color
        }
    }

    /// 획 목록을 비트맵에 처음부터 다시 그린다 (되돌리기·지우기 후)
    private func redraw() {
        guard let c = bitmapCanvas else { return }
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for s in strokes {
            applyPaint(s)
            c.drawPath(s.path, paint)
        }
        invalidate()
    }

    func undo() {
        if strokes.isEmpty { return }
        strokes.removeLast()
        redraw()
        onChanged?()
    }

    func clearAll() {
        if strokes.isEmpty { return }
        strokes.removeAll()
        redraw()
        onChanged?()
    }

    override func onTouchEvent(_ event: MotionEvent) -> Bool {
        switch event.actionMasked {
        case MotionEvent.ACTION_DOWN:
            let path = Path()
            path.moveTo(event.x, event.y)
            // 점 하나만 찍어도 보이도록 아주 짧은 선을 그어 둔다
            path.lineTo(event.x + 0.1, event.y + 0.1)
            let s = Stroke(path, penColor, eraserMode ? penWidth * 4 : penWidth, eraserMode)
            current = s
            strokes.append(s)
            drawCurrent()
            parent?.requestDisallowInterceptTouchEvent(true)
            onChanged?()
            return true
        case MotionEvent.ACTION_MOVE:
            guard let s = current else { return false }
            for h in 0..<event.historySize {
                s.path.lineTo(event.getHistoricalX(h), event.getHistoricalY(h))
            }
            s.path.lineTo(event.x, event.y)
            drawCurrent()
            return true
        case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL:
            current = nil
            return true
        default:
            return false
        }
    }

    /// 지금 획만 비트맵에 덧그린다 (매번 전체를 다시 그리면 느리다)
    private func drawCurrent() {
        guard let s = current else { return }
        guard let c = bitmapCanvas else { return }
        applyPaint(s)
        c.drawPath(s.path, paint)
        invalidate()
    }

    override func onDraw(_ canvas: Canvas) {
        super.onDraw(canvas)
        if let bm = bitmap { canvas.drawBitmap(bm, 0, 0, nil) }
    }
}
