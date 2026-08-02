package com.piyak.english.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.piyak.english.model.MathVisual
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 수학 문제의 그림을 그린다. 이미지 파일 없이 이모지와 Canvas 도형만 쓴다.
 * 새 그림 종류는 [MathVisual] 에 kind 를 추가하고 여기 draw 분기만 늘리면 된다.
 */
class MathVisualView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    companion object {
        /**
         * 각 그림의 꼭짓점 가로 위치와 변 길이 (뷰 폭 대비).
         * 둔각이면 변이 왼쪽으로 뻗으므로 `CX - LEN >= 0` 이고 `CX + LEN <= 1` 이어야
         * 변이 화면 밖으로 나가지 않는다.
         */
        const val ANGLE_CX_RATIO = 0.5f
        const val ANGLE_LEN_RATIO = 0.44f

        /**
         * 원(시계·분수) 밑에 붙는 두 줄을 위해 비워 두는 세로 공간(dp).
         * 아래 두 상수보다 커야 글자가 뷰 밖으로 잘리지 않는다.
         */
        const val LABEL_BLOCK_DP = 54f

        /** 첫째 줄(값) 과 둘째 줄(안내) 의 원 아래 위치(dp) */
        const val LABEL_LINE1_DP = 22f
        const val LABEL_LINE2_DP = 42f
    }

    private val palette = listOf(
        "#FF8A80", "#FFD54F", "#80CBC4", "#81D4FA", "#B39DDB", "#A5D6A7",
    ).map { Color.parseColor(it) }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#5D4037")
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#4E342E")
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var visual: MathVisual? = null
        set(v) {
            field = v
            counted.clear()
            painted.clear()
            itemOffsets.clear()
            dragIndex = -1
            setHour = 12; setMinute = 0
            setAngle = 0
            markValue = v?.p ?: 0.0
            draggingClock = false
            requestLayout()
            invalidate()
        }

    // ---------- 손가락으로 짚어 세기 ----------
    /**
     * 그림의 사물을 하나씩 터치하면 번호가 붙는다.
     * 아이가 실제로 손가락으로 짚으며 세는 행동을 그대로 옮긴 것 —
     * 정지된 그림을 보기만 하는 것과 만지며 세는 것은 완전히 다르다.
     */
    private val itemCenters = ArrayList<PointF>()
    private val itemEmojis = ArrayList<String>()
    private val counted = LinkedHashSet<Int>()

    /**
     * 아이가 끌어 옮긴 거리. 그림을 다시 그릴 때마다 이만큼 더해 준다.
     *
     * 곱셈·나눗셈 배열도 옮길 수 있다. `24 ÷ 8` 에서 강아지를 실제로 끌어다
     * 여덟 묶음으로 갈라 보는 게 이 그림의 본래 뜻이기 때문이다.
     * 대신 하나라도 옮기면 행·열 안내선을 지운다 (더 이상 맞지 않는 선이라).
     */
    private val itemOffsets = HashMap<Int, PointF>()
    private var dragIndex = -1
    private var dragMoved = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    /** 셀 수 있는 그림인지 (이모지·배열 계열) */
    val countable: Boolean
        get() = visual?.kind in setOf(MathVisual.EMOJI, MathVisual.EMOJI_OP, MathVisual.ARRAY)

    /** 손으로 옮길 수 있는 그림인지 — 셀 수 있는 그림은 모두 옮길 수도 있다 */
    val movable: Boolean get() = countable

    /** 지금까지 짚은 개수가 바뀔 때 */
    var onCountChanged: ((Int) -> Unit)? = null

    val countedSoFar: Int get() = counted.size

    fun clearCount() {
        counted.clear()
        itemOffsets.clear()      // 옮겨 놓은 것도 처음 자리로 되돌린다
        onCountChanged?.invoke(0)
        invalidate()
    }

    // ---------- 시계 바늘 돌리기 ----------
    /**
     * 시각을 "읽는" 것에서 한 걸음 더 나아가 직접 **바늘을 돌려 만들어 보는** 모드.
     * 짧은 바늘 근처를 잡으면 시침이, 그 밖을 잡으면 분침이 손가락을 따라온다.
     */
    private var clockCx = 0f
    private var clockCy = 0f
    private var clockR = 0f
    private var draggingHour = false
    private var draggingClock = false

    var setHour = 12
        private set
    var setMinute = 0
        private set

    /** 맞춰 놓은 시각이 바뀔 때 (시, 분) */
    var onClockChanged: ((Int, Int) -> Unit)? = null

    fun resetClock() {
        setHour = 12; setMinute = 0
        onClockChanged?.invoke(setHour, setMinute)
        invalidate()
    }

    private fun handleClockTouch(event: android.view.MotionEvent): Boolean {
        val dx = event.x - clockCx
        val dy = event.y - clockCy
        val dist = kotlin.math.hypot(dx, dy)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (dist > clockR * 1.05f) return false
                // 시침은 짧다 — 안쪽을 잡으면 시침, 바깥을 잡으면 분침
                draggingHour = dist < clockR * 0.55f
                draggingClock = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_MOVE -> if (!draggingClock) return false
            else -> { draggingClock = false; return true }
        }
        // 12시 방향이 0도가 되도록 회전
        var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0
        if (deg < 0) deg += 360.0
        if (draggingHour) {
            val h = Math.round(deg / 30.0).toInt() % 12
            setHour = if (h == 0) 12 else h
        } else {
            // 5분 단위로 딱 붙게 — 아이 손가락으로 1분 단위를 맞추긴 어렵다
            setMinute = (Math.round(deg / 6.0).toInt() / 5 * 5) % 60
        }
        onClockChanged?.invoke(setHour, setMinute)
        invalidate()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (visual?.kind) {
            MathVisual.CLOCK_SET -> return handleClockTouch(event)
            MathVisual.FRACTION_PAINT -> return handlePaintTouch(event)
            MathVisual.NUMBER_LINE_DRAG -> return handleLineTouch(event)
            MathVisual.ANGLE_SET -> return handleAngleTouch(event)
        }
        if (!countable) return false

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val hit = nearestItem(event.x, event.y)
                if (hit < 0) return false
                dragIndex = hit
                dragMoved = false
                dragStartX = event.x
                dragStartY = event.y
                if (movable) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (dragIndex < 0 || !movable) return false
                val dx = event.x - dragStartX
                val dy = event.y - dragStartY
                // 손가락이 조금 흔들린 것까지 끌기로 보면 세기가 안 된다
                if (!dragMoved && kotlin.math.hypot(dx, dy) < dp(10)) return true
                dragMoved = true
                val base = itemOffsets.getOrPut(dragIndex) { PointF(0f, 0f) }
                base.x += dx
                base.y += dy
                dragStartX = event.x
                dragStartY = event.y
                invalidate()
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                val hit = dragIndex
                val moved = dragMoved
                dragIndex = -1
                dragMoved = false
                if (hit < 0) return false
                // 끌지 않고 톡 눌렀으면 "세기" — 다시 누르면 취소
                if (!moved) {
                    if (!counted.add(hit)) counted.remove(hit)
                    onCountChanged?.invoke(counted.size)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    /** 손가락에 가장 가까운 이모지 (너무 멀면 -1) */
    private fun nearestItem(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in itemCenters.indices) {
            val c = itemCenters[i]
            val d = kotlin.math.hypot(x - c.x, y - c.y)
            if (d < bestD) { bestD = d; best = i }
        }
        return if (best >= 0 && bestD <= itemSpacing * 0.6f) best else -1
    }

    /** 항목 사이 간격 (터치 판정 범위 계산용) */
    private var itemSpacing = 0f

    /** 이모지 기준점과 글자 기준선 사이 거리 (그림 종류마다 다르다) */
    private var itemCenterDy = 0f

    /** 짚은 순서대로 번호를 그린다 */
    private fun drawCountMarks(canvas: Canvas) {
        if (counted.isEmpty()) return
        val r = itemSpacing * 0.20f
        text.textSize = r * 1.5f
        text.isFakeBoldText = true
        counted.forEachIndexed { order, idx ->
            val c = itemCenters.getOrNull(idx) ?: return@forEachIndexed
            fill.color = Color.parseColor("#66BB6A")
            canvas.drawCircle(c.x + itemSpacing * 0.30f, c.y - itemSpacing * 0.30f, r, fill)
            text.color = Color.WHITE
            canvas.drawText(
                "${order + 1}",
                c.x + itemSpacing * 0.30f,
                c.y - itemSpacing * 0.30f + text.textSize * 0.35f, text
            )
        }
        text.color = Color.parseColor("#4E342E")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val v = visual
        val h = when (v?.kind) {
            null -> 0
            MathVisual.EMOJI -> {
                val perRow = if (v.a > 10) 5 else 5
                val rows = (v.a + perRow - 1) / perRow
                (w * 0.18f * rows).toInt().coerceAtLeast(dp(90))
            }
            MathVisual.EMOJI_OP -> (w * 0.42f).toInt()
            MathVisual.ARRAY -> (w * 0.13f * v.a + dp(30)).toInt().coerceAtLeast(dp(110))
            MathVisual.SHAPES, MathVisual.COMPARE -> (w * 0.38f).toInt()
            MathVisual.CLOCK -> (w * 0.62f).toInt()
            // 밑에 붙는 두 줄까지 들어갈 높이 (그림 그리는 쪽에서 그만큼 빼고 원을 그린다)
            MathVisual.CLOCK_SET -> (w * 0.85f).toInt()
            MathVisual.GROUP -> 0          // GroupDragView 가 따로 그린다
            MathVisual.FRACTION -> (w * 0.40f).toInt()
            MathVisual.FRACTION_PAINT -> (w * 0.70f).toInt()
            MathVisual.SHAPE_SORT -> 0     // GroupDragView 가 따로 그린다
            MathVisual.NUMBER_LINE -> dp(96)
            MathVisual.NUMBER_LINE_DRAG -> dp(150)      // 손잡이·값 표시 자리
            MathVisual.ANGLE_SET -> (w * 0.68f).toInt()
            // 전용 뷰가 따로 그린다
            MathVisual.BALANCE, MathVisual.BAR_BUILD, MathVisual.GATHER -> 0
            MathVisual.BAR_GRAPH -> (w * 0.50f).toInt()
            MathVisual.COORD3D, MathVisual.COORD2D -> (w * 0.72f).toInt()
            MathVisual.GEOM -> when (v.op) {
                "ineq" -> (w * 0.30f).toInt()
                "dice" -> (w * 0.34f).toInt()
                else -> (w * 0.56f).toInt()
            }
            MathVisual.ANGLE -> (w * 0.50f).toInt()
            else -> dp(100)
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val v = visual ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        itemCenters.clear()
        itemEmojis.clear()
        when (v.kind) {
            MathVisual.EMOJI -> drawEmojiGrid(canvas, v.emoji, v.a, w, h)
            MathVisual.COORD3D -> drawCoord3d(canvas, v, w, h)
            MathVisual.COORD2D -> when (v.op) {
                "tangent", "area", "poly" -> drawCurveOp(canvas, v, w, h)
                "exp", "log", "seqlim" -> drawFuncMisc(canvas, v, w, h)
                else -> drawCoord2d(canvas, v, w, h)
            }
            MathVisual.GEOM -> drawGeom(canvas, v, w, h)
            MathVisual.EMOJI_OP -> drawEmojiOp(canvas, v, w, h)
            MathVisual.ARRAY -> drawArray(canvas, v, w, h)
            MathVisual.SHAPES -> drawShapes(canvas, v, w, h)
            MathVisual.CLOCK, MathVisual.CLOCK_SET -> drawClock(canvas, v, w, h)
            MathVisual.FRACTION, MathVisual.FRACTION_PAINT -> drawFraction(canvas, v, w, h)
            MathVisual.NUMBER_LINE, MathVisual.NUMBER_LINE_DRAG -> drawNumberLine(canvas, v, w, h)
            MathVisual.BAR_GRAPH -> drawBarGraph(canvas, v, w, h)
            MathVisual.ANGLE, MathVisual.ANGLE_SET -> drawAngle(canvas, v, w, h)
            MathVisual.COMPARE -> drawCompare(canvas, v, w, h)
        }
        drawDraggedOnTop(canvas)
        drawCountMarks(canvas)
    }

    /**
     * 이모지 하나를 그리고 터치 판정 자리를 등록한다.
     * 아이가 끌어 옮긴 만큼(`itemOffsets`) 자리를 옮겨서 그리므로,
     * 그림 속 토끼·펭귄을 실제로 이리저리 움직일 수 있다.
     */
    private fun placeEmoji(canvas: Canvas, emoji: String, x: Float, y: Float, centerDy: Float) {
        val i = itemCenters.size
        val off = itemOffsets[i]
        val ox = x + (off?.x ?: 0f)
        val oy = y + (off?.y ?: 0f)
        itemCenters.add(PointF(ox, oy - centerDy))
        itemEmojis.add(emoji)
        itemCenterDy = centerDy
        // 지금 끌고 있는 것은 맨 위에 크게 다시 그린다
        if (i != dragIndex) drawItem(canvas, emoji, ox, oy - centerDy, emojiPaint.textSize)
    }

    /** 끌고 있는 이모지를 가장 위에 (다른 것에 가리지 않게) */
    private fun drawDraggedOnTop(canvas: Canvas) {
        val i = dragIndex
        if (i < 0 || i >= itemCenters.size) return
        val c = itemCenters[i]
        drawItem(canvas, itemEmojis[i], c.x, c.y, emojiPaint.textSize * 1.2f)
    }

    // ---------- 이모지 → codex 일러스트 ----------

    /** 세고 끄는 사물: 그림 사전 일러스트가 있으면 이모지 대신 그린다 (목록은 EmojiArt) */
    private fun artFor(emoji: String): android.graphics.drawable.Drawable? =
        EmojiArt.of(context, emoji)

    /** (cx, cy) 를 중심으로 일러스트 또는 이모지 하나를 그린다. ts = 이모지 글자 크기 기준 */
    private fun drawItem(canvas: Canvas, emoji: String, cx: Float, cy: Float, ts: Float) {
        val d = artFor(emoji)
        if (d != null) {
            val half = (ts * 0.62f).toInt()
            d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            d.draw(canvas)
        } else {
            val base = emojiPaint.textSize
            emojiPaint.textSize = ts
            canvas.drawText(emoji, cx, cy + ts * 0.35f, emojiPaint)
            emojiPaint.textSize = base
        }
    }

    // ---------- 이모지 세기 ----------
    private fun drawEmojiGrid(canvas: Canvas, emoji: String, n: Int, w: Float, h: Float) {
        if (n <= 0) return
        val perRow = min(5, n)
        val rows = (n + perRow - 1) / perRow
        val cell = min(w / (perRow + 0.6f), h / (rows + 0.3f))
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.78f
        val startX = (w - cell * perRow) / 2f + cell / 2f
        val startY = (h - cell * rows) / 2f + cell * 0.78f
        for (r in 0 until rows) {
            val cols = min(perRow, n - r * perRow)
            val rowX = startX + (perRow - cols) * cell / 2f
            for (c in 0 until cols) {
                placeEmoji(canvas, emoji, rowX + c * cell, startY + r * cell, cell * 0.28f)
            }
        }
    }

    // ---------- 🍎🍎 + 🍎 ----------
    private fun drawEmojiOp(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val total = v.a + v.bb
        val cell = min(w / (total + 3.2f), h * 0.42f)
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.85f
        text.textSize = cell * 0.9f
        text.isFakeBoldText = true
        val gap = cell * 0.9f
        val totalW = v.a * cell + gap + v.bb * cell
        var x = (w - totalW) / 2f + cell / 2f
        val y = h / 2f + cell * 0.3f
        repeat(v.a) {
            placeEmoji(canvas, v.emoji, x, y, cell * 0.3f)
            x += cell
        }
        canvas.drawText(if (v.op == "-") "－" else "＋", x + gap / 2f - cell / 2f, y, text)
        x += gap
        repeat(v.bb) {
            placeEmoji(canvas, v.emoji, x, y, cell * 0.3f)
            x += cell
        }
    }

    // ---------- 곱셈 배열 ----------
    private fun drawArray(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val rows = v.a.coerceAtLeast(1)
        val cols = v.bb.coerceAtLeast(1)
        val cell = min((w - dp(40)) / cols, (h - dp(30)) / rows)
        itemSpacing = cell
        emojiPaint.textSize = cell * 0.72f
        val startX = (w - cell * cols) / 2f + cell / 2f
        val startY = (h - cell * rows) / 2f + cell * 0.75f
        for (r in 0 until rows) for (c in 0 until cols) {
            placeEmoji(canvas, v.emoji, startX + c * cell, startY + r * cell, cell * 0.26f)
        }

        // 행·열 안내선.
        // 하나라도 옮기고 나면 선은 더 이상 맞지 않으므로 지운다 —
        // 아이가 직접 나눠 놓은 자리를 선이 방해하면 안 된다.
        if (itemOffsets.isNotEmpty()) return
        stroke.strokeWidth = dp(1.5f).toFloat()
        stroke.color = Color.parseColor("#33795548")
        val left = (w - cell * cols) / 2f
        val top = (h - cell * rows) / 2f
        for (r in 1 until rows) {
            canvas.drawLine(left, top + r * cell, left + cell * cols, top + r * cell, stroke)
        }
        for (c in 1 until cols) {
            canvas.drawLine(left + c * cell, top, left + c * cell, top + cell * rows, stroke)
        }
        stroke.color = Color.parseColor("#5D4037")
    }

    // ---------- 도형 ----------
    private fun drawShapes(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val n = v.labels.size.coerceAtLeast(1)
        val cellW = w / n
        val r = min(cellW * 0.32f, h * 0.34f)
        text.textSize = r * 0.42f
        text.isFakeBoldText = false
        for (i in v.labels.indices) {
            val cx = cellW * (i + 0.5f)
            val cy = h * 0.45f
            fill.color = palette[i % palette.size]
            drawShape(canvas, v.labels[i], cx, cy, r)
            canvas.drawText("${i + 1}", cx, h * 0.93f, text)
        }
    }

    private fun drawShape(canvas: Canvas, name: String, cx: Float, cy: Float, r: Float) {
        stroke.strokeWidth = dp(3f).toFloat()
        when (name) {
            "원" -> {
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, stroke)
            }
            "타원" -> {
                val rect = RectF(cx - r * 1.25f, cy - r * 0.75f, cx + r * 1.25f, cy + r * 0.75f)
                canvas.drawOval(rect, fill); canvas.drawOval(rect, stroke)
            }
            "사각형", "정사각형" -> {
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRect(rect, fill); canvas.drawRect(rect, stroke)
            }
            "직사각형" -> {
                val rect = RectF(cx - r * 1.3f, cy - r * 0.75f, cx + r * 1.3f, cy + r * 0.75f)
                canvas.drawRect(rect, fill); canvas.drawRect(rect, stroke)
            }
            else -> {
                val sides = when (name) {
                    "삼각형" -> 3; "오각형" -> 5; "육각형" -> 6; "팔각형" -> 8
                    else -> 3
                }
                val path = polygonPath(cx, cy, r, sides)
                canvas.drawPath(path, fill); canvas.drawPath(path, stroke)
            }
        }
    }

    private fun polygonPath(cx: Float, cy: Float, r: Float, sides: Int): Path {
        val path = Path()
        for (i in 0 until sides) {
            val a = Math.toRadians(-90.0 + 360.0 * i / sides)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ---------- 시계 ----------
    private fun drawClock(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val setMode = v.kind == MathVisual.CLOCK_SET
        val cx = w / 2f
        // 바늘 돌리기는 시계 밑에 두 줄이 붙는다 — 그만큼 자리를 비워 두지 않으면 글자가 잘린다
        val r = if (setMode) min(w * 0.42f, (h - dp(LABEL_BLOCK_DP)) * 0.5f)
        else min(w, h) * 0.42f
        val cy = if (setMode) r + dp(6) else h / 2f
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, r, fill)
        stroke.strokeWidth = dp(4f).toFloat()
        stroke.color = Color.parseColor("#FFB300")
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.parseColor("#5D4037")

        text.textSize = r * 0.20f
        text.isFakeBoldText = true
        for (i in 1..12) {
            val a = Math.toRadians(-90.0 + 30.0 * i)
            val tx = cx + r * 0.80f * cos(a).toFloat()
            val ty = cy + r * 0.80f * sin(a).toFloat() + text.textSize * 0.35f
            canvas.drawText("$i", tx, ty, text)
        }
        // 분 눈금
        stroke.strokeWidth = dp(1.5f).toFloat()
        for (i in 0 until 60) {
            if (i % 5 == 0) continue
            val a = Math.toRadians(-90.0 + 6.0 * i)
            canvas.drawLine(
                cx + r * 0.93f * cos(a).toFloat(), cy + r * 0.93f * sin(a).toFloat(),
                cx + r * 0.99f * cos(a).toFloat(), cy + r * 0.99f * sin(a).toFloat(), stroke
            )
        }

        // 마지막으로 그린 판 위치 — 바늘을 끌 때 각도 계산에 쓴다
        clockCx = cx; clockCy = cy; clockR = r

        val hour = if (v.kind == MathVisual.CLOCK_SET) setHour.toDouble() else v.p
        val minute = if (v.kind == MathVisual.CLOCK_SET) setMinute.toDouble() else v.q
        // 시침 (분에 따라 조금씩 이동)
        val ha = Math.toRadians(-90.0 + 30.0 * (hour % 12) + 0.5 * minute)
        stroke.strokeWidth = dp(6f).toFloat()
        stroke.color = Color.parseColor("#FF7043")
        canvas.drawLine(
            cx, cy,
            cx + r * 0.48f * cos(ha).toFloat(), cy + r * 0.48f * sin(ha).toFloat(), stroke
        )
        // 분침
        val ma = Math.toRadians(-90.0 + 6.0 * minute)
        stroke.strokeWidth = dp(4f).toFloat()
        stroke.color = Color.parseColor("#42A5F5")
        canvas.drawLine(
            cx, cy,
            cx + r * 0.72f * cos(ma).toFloat(), cy + r * 0.72f * sin(ma).toFloat(), stroke
        )
        fill.color = Color.parseColor("#5D4037")
        canvas.drawCircle(cx, cy, dp(5f).toFloat(), fill)
        stroke.color = Color.parseColor("#5D4037")

        if (setMode) {
            // 지금 맞춰 놓은 시각을 숫자로도 보여 준다
            text.textSize = dp(19f).toFloat()
            text.color = Color.parseColor("#4E342E")
            canvas.drawText("${setHour}시 ${setMinute}분", cx, cy + r + dp(LABEL_LINE1_DP), text)
            text.textSize = dp(13f).toFloat()
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText("바늘을 끌어서 맞춰 보세요", cx, cy + r + dp(LABEL_LINE2_DP), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 분수 (원을 나눈 그림) ----------
    private fun drawFraction(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val paintMode = v.kind == MathVisual.FRACTION_PAINT
        val denom = v.q.toInt().coerceAtLeast(1)
        val numer = v.p.toInt().coerceIn(0, denom)
        val cx = w / 2f
        // 색칠 모드는 원 밑에 두 줄이 붙는다 — 자리를 먼저 빼 놓고 원 크기를 정한다
        val r = if (paintMode) min(w * 0.40f, (h - dp(LABEL_BLOCK_DP)) * 0.5f)
        else min(w, h) * 0.40f
        val cy = if (paintMode) r + dp(6) else h / 2f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f / denom

        // 색칠 모드에서는 "아이가 칠한 칸"이, 아니면 "문제가 정해 준 칸"이 색칠된다
        fun filled(i: Int) = if (paintMode) i in painted else i < numer

        for (i in 0 until denom) {
            fill.color = if (filled(i)) Color.parseColor("#FF8A65") else Color.parseColor("#FFF3E0")
            canvas.drawArc(rect, -90f + sweep * i, sweep, true, fill)
        }
        stroke.strokeWidth = dp(3f).toFloat()
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.strokeWidth = dp(2f).toFloat()
        for (i in 0 until denom) {
            val a = Math.toRadians(-90.0 + sweep * i)
            canvas.drawLine(cx, cy, cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), stroke)
        }

        if (paintMode) {
            pieCx = cx; pieCy = cy; pieR = r; pieSlices = denom
            text.textSize = dp(18f).toFloat()
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
            canvas.drawText(
                "칠한 칸 ${painted.size} / $denom", cx, cy + r + dp(LABEL_LINE1_DP), text
            )
            text.textSize = dp(13f).toFloat()
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText("조각을 눌러서 색칠해요", cx, cy + r + dp(LABEL_LINE2_DP), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 분수 색칠하기 ----------
    /**
     * "3/8 만큼 색칠해 보세요" — 조각을 눌러 칠한다.
     * 색칠된 그림을 읽고 분수를 답하는 것과 반대 방향이라, 분수를 *만들어* 보게 된다.
     */
    private var pieCx = 0f
    private var pieCy = 0f
    private var pieR = 0f
    private var pieSlices = 1
    private val painted = LinkedHashSet<Int>()

    val paintedCount: Int get() = painted.size

    /** 칠한 칸 수가 바뀔 때 */
    var onPaintChanged: ((Int) -> Unit)? = null

    fun clearPaint() {
        painted.clear()
        onPaintChanged?.invoke(0)
        invalidate()
    }

    private fun handlePaintTouch(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked != android.view.MotionEvent.ACTION_DOWN) return false
        val dx = event.x - pieCx
        val dy = event.y - pieCy
        if (kotlin.math.hypot(dx, dy) > pieR) return false
        var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0
        if (deg < 0) deg += 360.0
        val idx = ((deg / (360.0 / pieSlices)).toInt()).coerceIn(0, pieSlices - 1)
        if (!painted.add(idx)) painted.remove(idx)   // 다시 누르면 지운다
        onPaintChanged?.invoke(painted.size)
        invalidate()
        return true
    }

    // ---------- 수직선 ----------
    private fun drawNumberLine(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val lo = v.p
        val hi = v.q
        if (hi <= lo) return
        val left = dp(28).toFloat()
        val right = w - dp(28)
        val y = h * 0.55f
        stroke.strokeWidth = dp(3f).toFloat()
        canvas.drawLine(left, y, right, y, stroke)
        // 화살촉
        canvas.drawLine(right, y, right - dp(10), y - dp(6), stroke)
        canvas.drawLine(right, y, right - dp(10), y + dp(6), stroke)

        // 구간 수가 지정돼 있으면 그대로 (소수·음수 수직선은 눈금 간격이 1이 아니다)
        val steps = if (v.a > 0) v.a.coerceAtMost(20) else (hi - lo).toInt().coerceIn(1, 20)
        val dragMode = v.kind == MathVisual.NUMBER_LINE_DRAG
        text.textSize = if (dragMode) dp(13f).toFloat() else h * 0.20f
        text.isFakeBoldText = false
        // 눈금이 촘촘하면 숫자는 띄엄띄엄 적는다.
        // 20칸이면 두 칸 걸러도 "100" 같은 세 자리가 서로 닿는다 — 네 칸 걸러야 읽힌다.
        val labelEvery = when {
            steps > 12 -> 4
            steps > 6 -> 2
            else -> 1
        }
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            canvas.drawLine(x, y - dp(8), x, y + dp(8), stroke)
            if (i % labelEvery == 0) {
                val labelY = y + h * (if (v.kind == MathVisual.NUMBER_LINE_DRAG) 0.26f else 0.36f)
                canvas.drawText(fmt(lo + (hi - lo) * i / steps), x, labelY, text)
            }
        }
        // 표시할 값
        fill.color = Color.parseColor("#FF7043")
        for (value in v.values) {
            val x = left + (right - left) * ((value - lo) / (hi - lo)).toFloat()
            canvas.drawCircle(x, y, dp(9f).toFloat(), fill)
        }

        if (v.kind == MathVisual.NUMBER_LINE_DRAG) {
            lineLeft = left; lineRight = right; lineY = y
            val x = left + (right - left) * ((markValue - lo) / (hi - lo)).toFloat()
            // 끌 수 있다는 걸 알 수 있게 큼직한 손잡이로
            fill.color = Color.parseColor("#42A5F5")
            canvas.drawCircle(x, y, dp(16f).toFloat(), fill)
            fill.color = Color.WHITE
            canvas.drawCircle(x, y, dp(7f).toFloat(), fill)

            text.textSize = h * 0.20f
            text.isFakeBoldText = true
            text.color = Color.parseColor("#1565C0")
            canvas.drawText(fmt(markValue), x, y - dp(24), text)
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 수직선 위의 점 끌기 ----------
    /**
     * 수를 보고 고르는 대신 **수직선 위 어디쯤인지 직접 짚어 본다.**
     * 눈금에 딱 붙게 해서 손가락으로도 정확히 맞출 수 있다.
     */
    private var lineLeft = 0f
    private var lineRight = 0f
    private var lineY = 0f
    private var markValue = 0.0

    /** 지금 짚은 값 */
    val markedValue: Double get() = markValue

    var onMarkChanged: ((Double) -> Unit)? = null

    fun resetMark() {
        markValue = visual?.p ?: 0.0
        onMarkChanged?.invoke(markValue)
        invalidate()
    }

    private fun handleLineTouch(event: android.view.MotionEvent): Boolean {
        val v = visual ?: return false
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) return true
        if (lineRight <= lineLeft) return false
        // 선에서 너무 멀면 무시 (연습장 필기와 헷갈리지 않게)
        if (kotlin.math.abs(event.y - lineY) > dp(56)) return false
        parent?.requestDisallowInterceptTouchEvent(true)

        val t = ((event.x - lineLeft) / (lineRight - lineLeft)).coerceIn(0f, 1f)
        val steps = v.a.coerceAtLeast(1)
        val raw = v.p + (v.q - v.p) * t
        // 눈금에 딱 붙인다
        val stepSize = (v.q - v.p) / steps
        val snapped = v.p + Math.round((raw - v.p) / stepSize) * stepSize
        val rounded = Math.round(snapped * 1000.0) / 1000.0
        if (rounded != markValue) {
            markValue = rounded
            onMarkChanged?.invoke(markValue)
            invalidate()
        }
        return true
    }

    // ---------- 막대그래프 ----------
    private fun drawBarGraph(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        if (v.values.isEmpty()) return
        val maxV = (v.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val gridSteps = 4

        // 눈금 글자 크기를 먼저 정하고, 가장 긴 눈금값을 재서 왼쪽 여백을 잡는다.
        // (고정 여백을 쓰면 "12.8" 같은 값이 화면 밖으로 잘려 나간다)
        text.textSize = (h * 0.085f).coerceIn(dp(11f).toFloat(), dp(15f).toFloat())
        text.isFakeBoldText = false
        val gridLabels = (1..gridSteps).map { fmt(maxV * it / gridSteps) }
        val labelW = gridLabels.maxOf { text.measureText(it) }
        val left = labelW + dp(14)
        val bottom = h - dp(30)
        val top = dp(14).toFloat()
        val right = w - dp(14)
        stroke.strokeWidth = dp(2.5f).toFloat()
        canvas.drawLine(left, top, left, bottom, stroke)
        canvas.drawLine(left, bottom, right, bottom, stroke)

        // 눈금 — 축 왼쪽에 오른쪽 정렬
        text.textAlign = Paint.Align.RIGHT
        for ((i, label) in gridLabels.withIndex()) {
            val yy = bottom - (bottom - top) * (i + 1) / gridSteps
            canvas.drawText(label, left - dp(6), yy + text.textSize * 0.35f, text)
        }
        text.textAlign = Paint.Align.CENTER

        val n = v.values.size
        val slot = (right - left) / n
        val barW = slot * 0.55f
        for (i in v.values.indices) {
            val bh = ((bottom - top) * (v.values[i] / maxV)).toFloat()
            val cx = left + slot * (i + 0.5f)
            fill.color = palette[i % palette.size]
            val rect = RectF(cx - barW / 2f, bottom - bh, cx + barW / 2f, bottom)
            canvas.drawRoundRect(rect, dp(6f).toFloat(), dp(6f).toFloat(), fill)
            v.labels.getOrNull(i)?.let {
                val base = text.textSize
                if (text.measureText(it) > slot * 0.92f) {
                    text.textSize = base * (slot * 0.92f / text.measureText(it))
                }
                canvas.drawText(it, cx, bottom + base * 1.4f, text)
                text.textSize = base
            }
        }
    }

    // ---------- 각도 ----------
    private fun drawAngle(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val setMode = v.kind == MathVisual.ANGLE_SET
        val deg = if (setMode) setAngle.toDouble() else v.p
        // 꼭짓점을 가운데에 둔다. 왼쪽에 두면 둔각(90°↑)일 때 변이 화면 밖으로 나간다.
        val cx = w * ANGLE_CX_RATIO
        val cy = h * 0.78f
        val len = min(w * ANGLE_LEN_RATIO, cy - dp(14))
        angleCx = cx; angleCy = cy; angleLen = len

        stroke.strokeWidth = dp(4f).toFloat()
        canvas.drawLine(cx, cy, cx + len, cy, stroke)
        val a = Math.toRadians(-deg)
        if (setMode) stroke.color = Color.parseColor("#42A5F5")
        canvas.drawLine(cx, cy, cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(), stroke)
        stroke.color = Color.parseColor("#5D4037")
        // 각 표시 호
        stroke.strokeWidth = dp(2.5f).toFloat()
        stroke.color = Color.parseColor("#FF7043")
        val r = len * 0.28f
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 0f, -deg.toFloat(), false, stroke)
        stroke.color = Color.parseColor("#5D4037")
        fill.color = Color.parseColor("#5D4037")
        canvas.drawCircle(cx, cy, dp(4f).toFloat(), fill)

        if (setMode) {
            // 끝점에 손잡이를 그려서 "여기를 잡아 돌린다"를 알려 준다
            fill.color = Color.parseColor("#42A5F5")
            canvas.drawCircle(
                cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(),
                dp(14f).toFloat(), fill
            )
            fill.color = Color.WHITE
            canvas.drawCircle(
                cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(),
                dp(6f).toFloat(), fill
            )
            text.textSize = len * 0.20f
            text.isFakeBoldText = true
            text.color = Color.parseColor("#1565C0")
            canvas.drawText("${setAngle}°", cx + len * 0.42f, cy - len * 0.16f, text)
            text.textSize = len * 0.13f
            text.isFakeBoldText = false
            text.color = Color.parseColor("#8D6E63")
            canvas.drawText("파란 손잡이를 돌려요", w * 0.5f, h - dp(8), text)
            text.isFakeBoldText = true
            text.color = Color.parseColor("#4E342E")
        }
    }

    // ---------- 각도 만들기 ----------
    /**
     * 각도를 **재는** 대신 직접 **만들어 본다.**
     * 5° 단위로 붙게 해서 60°·90° 같은 목표를 손가락으로 맞출 수 있다.
     */
    private var angleCx = 0f
    private var angleCy = 0f
    private var angleLen = 0f

    var setAngle = 0
        private set

    var onAngleChanged: ((Int) -> Unit)? = null

    fun resetAngle() {
        setAngle = 0
        onAngleChanged?.invoke(0)
        invalidate()
    }

    private fun handleAngleTouch(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) return true
        val dx = event.x - angleCx
        val dy = event.y - angleCy
        if (kotlin.math.hypot(dx, dy) > angleLen * 1.35f) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        // 위로 열리는 각만 다룬다 (0°~180°)
        var deg = Math.toDegrees(kotlin.math.atan2(-dy.toDouble(), dx.toDouble()))
        if (deg < 0) deg = 0.0
        val snapped = (Math.round(deg / 5.0) * 5).toInt().coerceIn(0, 180)
        if (snapped != setAngle) {
            setAngle = snapped
            onAngleChanged?.invoke(snapped)
            invalidate()
        }
        return true
    }

    // ---------- 두 그룹 비교 ----------
    private fun drawCompare(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val halfW = w / 2f
        drawEmojiGridIn(canvas, v.emoji, v.a, 0f, 0f, halfW - dp(8), h)
        drawEmojiGridIn(canvas, v.labels.getOrNull(0) ?: v.emoji, v.bb, halfW + dp(8), 0f, halfW - dp(8), h)
        stroke.strokeWidth = dp(2f).toFloat()
        stroke.color = Color.parseColor("#33795548")
        canvas.drawLine(halfW, dp(8).toFloat(), halfW, h - dp(8), stroke)
        stroke.color = Color.parseColor("#5D4037")
    }

    private fun drawEmojiGridIn(
        canvas: Canvas, emoji: String, n: Int, x0: Float, y0: Float, w: Float, h: Float,
    ) {
        if (n <= 0) return
        val perRow = min(3, n)
        val rows = (n + perRow - 1) / perRow
        val cell = min(w / (perRow + 0.4f), h / (rows + 0.4f))
        emojiPaint.textSize = cell * 0.76f
        val startX = x0 + (w - cell * perRow) / 2f + cell / 2f
        val startY = y0 + (h - cell * rows) / 2f + cell * 0.78f
        var left = n
        for (r in 0 until rows) {
            val cols = min(perRow, left)
            for (c in 0 until cols) {
                canvas.drawText(emoji, startX + c * cell, startY + r * cell, emojiPaint)
            }
            left -= cols
        }
    }

    private fun fmt(d: Double): String =
        if (d == d.toInt().toDouble()) d.toInt().toString() else "%.1f".format(d)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    // ---------- 좌표 도해 (교과서 그림처럼, 문제 속 숫자와 정확히 일치) ----------

    /** 공간좌표: 오른손 좌표계 축 3개 + 점 P(x,y,z) 까지 안내 직육면체 */
    private fun drawCoord3d(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val x = (v.values.getOrNull(0) ?: 0.0).toFloat()
        val y = (v.values.getOrNull(1) ?: 0.0).toFloat()
        val z = (v.values.getOrNull(2) ?: 0.0).toFloat()
        val m = maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z), 1f)
        val cx = w * 0.46f
        val cy = h * 0.55f
        val u = minOf(w, h) * 0.30f / m
        // 투영: y = 오른쪽, z = 위, x = 왼쪽 아래로 비스듬히 (오른손 좌표계)
        fun pt(px: Float, py: Float, pz: Float) = PointF(
            cx + py * u - px * u * 0.55f,
            cy - pz * u + px * u * 0.38f
        )
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D4C41"); strokeWidth = dp(2f); style = Paint.Style.STROKE
        }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B39F8C"); strokeWidth = dp(1.5f); style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
        }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E342E"); textSize = dp(14f); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(dp(3f), 0f, 0f, Color.WHITE)
        }
        fun line(a: PointF, b: PointF, p: Paint) = canvas.drawLine(a.x, a.y, b.x, b.y, p)
        val o = pt(0f, 0f, 0f)
        val ax = m + 1.3f
        val xe = pt(ax, 0f, 0f); val ye = pt(0f, ax, 0f); val ze = pt(0f, 0f, ax)
        line(o, xe, axis); line(o, ye, axis); line(o, ze, axis)
        canvas.drawText("x", xe.x - dp(9f), xe.y + dp(11f), lbl)
        canvas.drawText("y", ye.x + dp(10f), ye.y + dp(4f), lbl)
        canvas.drawText("z", ze.x, ze.y - dp(7f), lbl)
        canvas.drawText("O", o.x - dp(10f), o.y + dp(13f), lbl)
        // 점까지 가는 직육면체 모서리 (점선)
        val pX = pt(x, 0f, 0f); val pY = pt(0f, y, 0f); val pZ = pt(0f, 0f, z)
        val pXY = pt(x, y, 0f); val pXZ = pt(x, 0f, z); val pYZ = pt(0f, y, z)
        val pp = pt(x, y, z)
        line(pX, pXY, dash); line(pY, pXY, dash); line(pXY, pp, dash)
        line(pZ, pXZ, dash); line(pZ, pYZ, dash); line(pXZ, pp, dash); line(pYZ, pp, dash)
        line(pX, pXZ, dash); line(pY, pYZ, dash)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7043") }
        canvas.drawCircle(pp.x, pp.y, dp(5f), dot)
        lbl.textAlign = Paint.Align.LEFT
        canvas.drawText("P(${fmtC(x)}, ${fmtC(y)}, ${fmtC(z)})", pp.x + dp(8f), pp.y - dp(7f), lbl)
    }

    /** 좌표평면: 벡터 화살표 2개 / 포물선과 꼭짓점 / 타원 */
    private fun drawCoord2d(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f
        var m = 1f
        when (v.op) {
            "vec" -> for (i in 0 until 4) m = maxOf(m, kotlin.math.abs(vAt(v, i)))
            "parab" -> m = maxOf(kotlin.math.abs(v.p.toFloat()), kotlin.math.abs(v.q.toFloat()) * 0.6f, 3f)
            "ellipse" -> m = maxOf(v.p.toFloat(), v.q.toFloat())
            "line" -> {
                m = maxOf(kotlin.math.abs(vAt(v, 1)) + 1.5f, 4f)
                if (v.values.size >= 3) m = maxOf(
                    m, kotlin.math.abs(vAt(v, 2)) + 1.5f,
                    kotlin.math.abs(vAt(v, 0) * vAt(v, 2) + vAt(v, 1)) + 1.5f
                )
            }
            "line2" -> m = maxOf(
                kotlin.math.abs(vAt(v, 4)), kotlin.math.abs(vAt(v, 5)),
                kotlin.math.abs(vAt(v, 1)), kotlin.math.abs(vAt(v, 3)), 4f
            )
            "seg" -> for (i in 0 until 4) m = maxOf(m, kotlin.math.abs(vAt(v, i)))
            "circ" -> m = maxOf(
                kotlin.math.abs(vAt(v, 0)) + vAt(v, 2),
                kotlin.math.abs(vAt(v, 1)) + vAt(v, 2), 3f
            )
            "hyper" -> m = maxOf(kotlin.math.sqrt(kotlin.math.abs(vAt(v, 0))) + 2f, 5f)
        }
        m += 1.5f
        val u = minOf(w, h) * 0.44f / m
        fun X(x: Float) = cx + x * u
        fun Y(y: Float) = cy - y * u
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EADFCE"); strokeWidth = dp(1f)
        }
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8D6E63"); strokeWidth = dp(2f)
        }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E342E"); textSize = dp(14f); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            setShadowLayer(dp(3f), 0f, 0f, Color.WHITE)
        }
        // 눈금 (너무 촘촘하지 않게 정수 간격을 고른다)
        val step = kotlin.math.ceil(m / 6f).toInt().coerceAtLeast(1)
        var g = step
        while (g < m) {
            canvas.drawLine(X(g.toFloat()), Y(-m), X(g.toFloat()), Y(m), grid)
            canvas.drawLine(X(-g.toFloat()), Y(-m), X(-g.toFloat()), Y(m), grid)
            canvas.drawLine(X(-m), Y(g.toFloat()), X(m), Y(g.toFloat()), grid)
            canvas.drawLine(X(-m), Y(-g.toFloat()), X(m), Y(-g.toFloat()), grid)
            g += step
        }
        canvas.drawLine(X(-m), cy, X(m), cy, axis)
        canvas.drawLine(cx, Y(-m), cx, Y(m), axis)
        canvas.drawText("x", X(m) - dp(9f), cy - dp(8f), lbl)
        canvas.drawText("y", cx + dp(11f), Y(m) + dp(13f), lbl)
        canvas.drawText("O", cx - dp(8f), cy + dp(13f), lbl)

        when (v.op) {
            "vec" -> {
                val colors = listOf("#FF7043", "#42A5F5")
                for (k in 0..1) {
                    val vx = vAt(v, k * 2); val vy = vAt(v, k * 2 + 1)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor(colors[k]); strokeWidth = dp(3f)
                    }
                    drawArrow(canvas, cx, cy, X(vx), Y(vy), p)
                    lbl.color = p.color
                    canvas.drawText(
                        "(${fmtC(vx)}, ${fmtC(vy)})",
                        X(vx), Y(vy) + (if (vy >= 0) -dp(9f) else dp(16f)), lbl
                    )
                }
                lbl.color = Color.parseColor("#4E342E")
            }
            "parab" -> {
                val a = v.a.toFloat(); val p0 = v.p.toFloat(); val q0 = v.q.toFloat()
                val path = android.graphics.Path()
                var first = true
                var xx = -m
                while (xx <= m) {
                    val yy = a * (xx - p0) * (xx - p0) + q0
                    if (yy in -m..m) {
                        if (first) { path.moveTo(X(xx), Y(yy)); first = false }
                        else path.lineTo(X(xx), Y(yy))
                    } else first = true
                    xx += 0.05f
                }
                val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
                }
                canvas.drawPath(path, curve)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                canvas.drawCircle(X(p0), Y(q0), dp(5f), dot)
                canvas.drawText("(${fmtC(p0)}, ${fmtC(q0)})", X(p0), Y(q0) + dp(18f), lbl)
            }
            "line2" -> {
                // 연립방정식: 두 직선과 교점 — 시험지 그림처럼
                val colors = listOf("#FF7043", "#42A5F5")
                for (k in 0..1) {
                    val sl = vAt(v, k * 2); val ic = vAt(v, k * 2 + 1)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor(colors[k]); strokeWidth = dp(3f)
                    }
                    var xs = -m; var xe = m
                    if (sl != 0f) {
                        val cands = listOf(-m, m, (m - ic) / sl, (-m - ic) / sl).sorted()
                        xs = cands[1]; xe = cands[2]
                    }
                    canvas.drawLine(X(xs), Y(sl * xs + ic), X(xe), Y(sl * xe + ic), p)
                }
                val px = vAt(v, 4); val py = vAt(v, 5)
                canvas.drawCircle(X(px), Y(py), dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#5C6BC0")
                })
                canvas.drawText("(${fmtC(px)}, ${fmtC(py)})", X(px), Y(py) - dp(9f), lbl)
            }
            "seg" -> {
                // 두 점 사이 거리 — 선분과 점선 직각변
                val x1 = vAt(v, 0); val y1 = vAt(v, 1); val x2 = vAt(v, 2); val y2 = vAt(v, 3)
                val dashP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B39F8C"); strokeWidth = dp(1.5f); style = Paint.Style.STROKE
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
                }
                canvas.drawLine(X(x1), Y(y1), X(x2), Y(y1), dashP)
                canvas.drawLine(X(x2), Y(y1), X(x2), Y(y2), dashP)
                canvas.drawLine(X(x1), Y(y1), X(x2), Y(y2), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f)
                })
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                canvas.drawCircle(X(x1), Y(y1), dp(5f), dot)
                canvas.drawCircle(X(x2), Y(y2), dp(5f), dot)
                canvas.drawText("(${fmtC(x1)}, ${fmtC(y1)})", X(x1), Y(y1) + dp(17f), lbl)
                canvas.drawText("(${fmtC(x2)}, ${fmtC(y2)})", X(x2), Y(y2) - dp(9f), lbl)
            }
            "circ" -> {
                // 좌표평면 위의 원 (중심 (a,b), 반지름 r)
                val a = vAt(v, 0); val b0 = vAt(v, 1); val r = vAt(v, 2)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
                }
                canvas.drawCircle(X(a), Y(b0), r * u, ring)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                canvas.drawCircle(X(a), Y(b0), dp(4f), dot)
                canvas.drawLine(X(a), Y(b0), X(a + r), Y(b0), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#8D6E63"); strokeWidth = dp(2f)
                })
                canvas.drawText("(${fmtC(a)}, ${fmtC(b0)})", X(a), Y(b0) + dp(17f), lbl)
                canvas.drawText("r", X(a + r / 2f), Y(b0) - dp(7f), lbl)
            }
            "hyper" -> {
                // 반비례 y = k/x — 쌍곡선 두 가지
                val k = vAt(v, 0)
                val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
                }
                for (sign in listOf(1f, -1f)) {
                    val path = android.graphics.Path()
                    var first = true
                    var x = 0.25f
                    while (x <= m) {
                        val yy = k / (x * sign)
                        if (kotlin.math.abs(yy) <= m) {
                            if (first) { path.moveTo(X(x * sign), Y(yy)); first = false }
                            else path.lineTo(X(x * sign), Y(yy))
                        } else first = true
                        x += m / 90f
                    }
                    canvas.drawPath(path, curve)
                }
            }
            "line" -> {
                // y = ax + b 직선과 y절편 — 교과서 일차함수 그림
                val a = vAt(v, 0); val b0 = vAt(v, 1)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f)
                }
                // 화면(±m) 안에 들어오는 구간만 긋는다
                fun yOf(x: Float) = a * x + b0
                var xs = -m; var xe = m
                if (a != 0f) {
                    val cands = listOf(-m, m, (m - b0) / a, (-m - b0) / a).sorted()
                    xs = cands[1]; xe = cands[2]
                }
                canvas.drawLine(X(xs), Y(yOf(xs)), X(xe), Y(yOf(xe)), p)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                canvas.drawCircle(X(0f), Y(b0), dp(5f), dot)
                canvas.drawText("(0, ${fmtC(b0)})", X(0f) + dp(34f), Y(b0) - dp(6f), lbl)
                if (v.values.size >= 3) {
                    // x → k 로 다가가는 점 (함수의 극한)
                    val k = vAt(v, 2); val yk = a * k + b0
                    val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#B39F8C"); strokeWidth = dp(1.5f); style = Paint.Style.STROKE
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
                    }
                    canvas.drawLine(X(k), Y(0f), X(k), Y(yk), guide)
                    canvas.drawLine(X(0f), Y(yk), X(k), Y(yk), guide)
                    canvas.drawCircle(X(k), Y(yk), dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FF7043")
                    })
                    canvas.drawText("x=${fmtC(k)}", X(k), Y(0f) + dp(15f), lbl)
                }
            }
            "ellipse" -> {
                val a = v.p.toFloat(); val b = v.q.toFloat()
                val oval = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
                }
                canvas.drawOval(X(-a), Y(b), X(a), Y(-b), oval)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                canvas.drawCircle(X(a), cy, dp(4f), dot)
                canvas.drawCircle(X(-a), cy, dp(4f), dot)
                canvas.drawText(fmtC(a), X(a), cy + dp(15f), lbl)
                canvas.drawText(fmtC(-a), X(-a), cy + dp(15f), lbl)
                canvas.drawText(fmtC(b), cx + dp(13f), Y(b) + dp(4f), lbl)
            }
        }
    }

    private fun vAt(v: MathVisual, i: Int): Float = (v.values.getOrNull(i) ?: 0.0).toFloat()

    private fun fmtC(f: Float): String =
        if (f == kotlin.math.floor(f)) f.toInt().toString() else f.toString()

    /** 화살표: 선 + 끝 삼각형 */
    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        canvas.drawLine(x1, y1, x2, y2, p)
        val ang = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = dp(9f)
        val spread = 0.5
        val path = android.graphics.Path()
        path.moveTo(x2, y2)
        path.lineTo(
            (x2 - len * kotlin.math.cos(ang - spread)).toFloat(),
            (y2 - len * kotlin.math.sin(ang - spread)).toFloat()
        )
        path.lineTo(
            (x2 - len * kotlin.math.cos(ang + spread)).toFloat(),
            (y2 - len * kotlin.math.sin(ang + spread)).toFloat()
        )
        path.close()
        val fill = Paint(p).apply { style = Paint.Style.FILL }
        canvas.drawPath(path, fill)
    }

    // ---------- 함수 그래프 (접선·정적분 넓이 — 시험지 그림처럼) ----------

    /** values = 다항식 계수(내림차순). op=tangent 는 x=p 에서 점+접선, op=area 는 [p,q] 구간 음영 */
    private fun drawCurveOp(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val coeffs = v.values.map { it.toFloat() }
        if (coeffs.isEmpty()) return
        fun f(x: Float): Float { var r = 0f; for (c in coeffs) r = r * x + c; return r }
        fun fp(x: Float): Float {
            var r = 0f
            val n = coeffs.size - 1
            for (i in 0 until n) r = r * x + coeffs[i] * (n - i)
            return r
        }
        val isArea = v.op == "area"
        val isPoly = v.op == "poly"
        val x0 = v.p.toFloat()
        val x1 = v.q.toFloat()
        val xLo: Float; val xHi: Float
        if (isArea) { xLo = minOf(x0, x1) - 1f; xHi = maxOf(x0, x1) + 1f }
        else if (isPoly && coeffs.size == 3 && coeffs[0] != 0f) {
            // 이차식: 꼭짓점과 근이 다 보이게
            val vx = -coeffs[1] / (2 * coeffs[0])
            val r = maxOf(kotlin.math.abs(vx) + 3f, 4f)
            xLo = -r; xHi = r
        } else { val r = maxOf(kotlin.math.abs(x0) + 2f, 3f); xLo = -r; xHi = r }
        var yMax = 1f
        run {
            var x = xLo
            while (x <= xHi) { yMax = maxOf(yMax, kotlin.math.abs(f(x))); x += (xHi - xLo) / 40f }
        }
        val padL = dp(10f)
        val ux = (w - padL * 2) / (xHi - xLo)
        val cy = h * 0.52f
        val uy = h * 0.42f / yMax
        fun X(x: Float) = padL + (x - xLo) * ux
        fun Y(y: Float) = cy - y * uy
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8D6E63"); strokeWidth = dp(2f)
        }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E342E"); textSize = dp(14f); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            setShadowLayer(dp(3f), 0f, 0f, Color.WHITE)
        }
        canvas.drawLine(X(xLo), Y(0f), X(xHi), Y(0f), axis)
        if (0f in xLo..xHi) canvas.drawLine(X(0f), dp(4f), X(0f), h - dp(4f), axis)
        canvas.drawText("O", X(0f) - dp(8f), Y(0f) + dp(13f), lbl)
        // 정적분 영역 음영
        if (isArea) {
            val fillPath = android.graphics.Path()
            fillPath.moveTo(X(x0), Y(0f))
            var x = x0
            while (x <= x1) { fillPath.lineTo(X(x), Y(f(x))); x += (x1 - x0) / 60f }
            fillPath.lineTo(X(x1), Y(0f)); fillPath.close()
            canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#66FFB74D"); style = Paint.Style.FILL
            })
            canvas.drawText(fmtC(x1), X(x1), Y(0f) + dp(15f), lbl)
        }
        // 곡선
        val curvePath = android.graphics.Path()
        var first = true
        var x = xLo
        while (x <= xHi) {
            val yy = f(x)
            if (kotlin.math.abs(yy) <= yMax * 1.05f) {
                if (first) { curvePath.moveTo(X(x), Y(yy)); first = false }
                else curvePath.lineTo(X(x), Y(yy))
            } else first = true
            x += (xHi - xLo) / 120f
        }
        canvas.drawPath(curvePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
        })
        canvas.drawText("y = f(x)", X(xHi) - dp(26f), Y(f(xHi)) - dp(8f), lbl)
        // 이차식의 실근 표시 (판별식·이차방정식)
        if (isPoly) {
            if (coeffs.size == 3 && coeffs[0] != 0f) {
                val disc = coeffs[1] * coeffs[1] - 4 * coeffs[0] * coeffs[2]
                if (disc >= 0f) {
                    val sq = kotlin.math.sqrt(disc)
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                    for (root in listOf((-coeffs[1] - sq) / (2 * coeffs[0]), (-coeffs[1] + sq) / (2 * coeffs[0]))) {
                        if (root in xLo..xHi) {
                            canvas.drawCircle(X(root), Y(0f), dp(5f), dot)
                            canvas.drawText(fmtC(root), X(root), Y(0f) + dp(16f), lbl)
                        }
                    }
                }
            }
            return
        }
        // 접선 + 접점
        if (!isArea) {
            val k = fp(x0); val y0 = f(x0)
            val d = (xHi - xLo) * 0.22f
            canvas.drawLine(X(x0 - d), Y(y0 - k * d), X(x0 + d), Y(y0 + k * d),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#42A5F5"); strokeWidth = dp(2.5f)
                })
            canvas.drawCircle(X(x0), Y(y0), dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#5C6BC0")
            })
            canvas.drawText("x = ${fmtC(x0)}", X(x0), Y(0f) + dp(15f), lbl)
        }
    }

    // ---------- 치수 표기 도형 (넓이·둘레·피타고라스 — 교과서 그림처럼) ----------

    private fun drawGeom(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF3D6") }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D4C41"); strokeWidth = dp(2.5f); style = Paint.Style.STROKE
        }
        val dash = Paint(edge).apply {
            strokeWidth = dp(1.5f); color = Color.parseColor("#B39F8C")
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
        }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E342E"); textSize = dp(14f); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(dp(3f), 0f, 0f, Color.WHITE)
        }
        val cx = w / 2f; val cy = h / 2f
        fun num(d: Double): String = if (d == Math.floor(d)) d.toInt().toString() else d.toString()
        when (v.op) {
            "rect" -> {
                // 비율은 값 비슷하게, 화면에 맞게 (완전 비례일 필요는 없다 — 교과서 그림도 그렇다)
                val ratio = (v.p / v.q).toFloat().coerceIn(0.5f, 2.2f)
                var rw = w * 0.52f; var rh = rw / ratio
                if (rh > h * 0.6f) { rh = h * 0.6f; rw = rh * ratio }
                val r = android.graphics.RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
                canvas.drawRect(r, fill); canvas.drawRect(r, edge)
                canvas.drawText("${num(v.p)}cm", cx, r.bottom + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("${num(v.q)}cm", r.right + dp(8f), cy + dp(5f), lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "tri" -> {
                val bw = w * 0.5f; val th = h * 0.56f
                val apexX = cx - bw * 0.1f
                val path = android.graphics.Path().apply {
                    moveTo(cx - bw / 2, cy + th / 2); lineTo(cx + bw / 2, cy + th / 2)
                    lineTo(apexX, cy - th / 2); close()
                }
                canvas.drawPath(path, fill); canvas.drawPath(path, edge)
                canvas.drawLine(apexX, cy - th / 2, apexX, cy + th / 2, dash)
                canvas.drawText("${num(v.p)}cm", cx, cy + th / 2 + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("${num(v.q)}cm", apexX + dp(6f), cy, lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "trap" -> {
                val a = (v.values.getOrNull(0) ?: 1.0); val b = (v.values.getOrNull(1) ?: 2.0)
                val top = w * 0.30f * (a / maxOf(a, b)).toFloat().coerceAtLeast(0.35f)
                val bot = w * 0.30f
                val th = h * 0.52f
                val path = android.graphics.Path().apply {
                    moveTo(cx - top, cy - th / 2); lineTo(cx + top, cy - th / 2)
                    lineTo(cx + bot, cy + th / 2); lineTo(cx - bot, cy + th / 2); close()
                }
                canvas.drawPath(path, fill); canvas.drawPath(path, edge)
                canvas.drawLine(cx, cy - th / 2, cx, cy + th / 2, dash)
                canvas.drawText("${num(a)}cm", cx, cy - th / 2 - dp(8f), lbl)
                canvas.drawText("${num(b)}cm", cx, cy + th / 2 + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("${num(v.values.getOrNull(2) ?: 0.0)}cm", cx + dp(6f), cy + dp(4f), lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "circle" -> {
                val r = minOf(w, h) * 0.34f
                canvas.drawCircle(cx, cy, r, fill); canvas.drawCircle(cx, cy, r, edge)
                canvas.drawLine(cx, cy, cx + r, cy, edge)
                canvas.drawCircle(cx, cy, dp(3.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6D4C41")
                })
                canvas.drawText("${num(v.p)}cm", cx + r / 2f, cy - dp(8f), lbl)
            }
            "rtri" -> {
                // labels = [밑변, 세로, 빗변] — 구하는 변은 "?"
                val bw = w * 0.46f; val th = h * 0.56f
                val lx = cx - bw / 2; val rx = cx + bw / 2
                val ty = cy - th / 2; val by = cy + th / 2
                val path = android.graphics.Path().apply {
                    moveTo(lx, by); lineTo(rx, by); lineTo(rx, ty); close()
                }
                canvas.drawPath(path, fill); canvas.drawPath(path, edge)
                // 직각 표시
                canvas.drawRect(rx - dp(12f), by - dp(12f), rx, by, dash)
                canvas.drawText(v.labels.getOrNull(0) ?: "", cx, by + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText(v.labels.getOrNull(1) ?: "", rx + dp(8f), cy + dp(5f), lbl)
                lbl.textAlign = Paint.Align.RIGHT
                canvas.drawText(v.labels.getOrNull(2) ?: "", cx - dp(10f), cy - dp(12f), lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "cyl" -> {
                val rw = w * 0.42f; val eh = rw * 0.28f; val bh = h * 0.5f
                val top = cy - bh / 2; val bot = cy + bh / 2
                val body = android.graphics.RectF(cx - rw / 2, top, cx + rw / 2, bot)
                canvas.drawRect(body, fill)
                canvas.drawLine(body.left, top, body.left, bot, edge)
                canvas.drawLine(body.right, top, body.right, bot, edge)
                val topOval = android.graphics.RectF(body.left, top - eh / 2, body.right, top + eh / 2)
                val botOval = android.graphics.RectF(body.left, bot - eh / 2, body.right, bot + eh / 2)
                canvas.drawOval(botOval, fill)
                canvas.drawArc(botOval, 0f, 180f, false, edge)
                canvas.drawOval(topOval, fill); canvas.drawOval(topOval, edge)
                canvas.drawLine(cx, top, cx + rw / 2, top, dash)
                canvas.drawText("${num(v.p)}", cx + rw / 4, top - dp(7f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("${num(v.q)}", body.right + dp(8f), cy + dp(5f), lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "box" -> {
                // 직육면체 — 앞면 + 위·옆으로 비스듬한 깊이 (교과서 그림)
                val fw = w * 0.40f; val fh = h * 0.42f
                val dx = fw * 0.38f; val dy = fh * 0.34f
                val lx = cx - (fw + dx) / 2f
                val by = cy + (fh + dy) / 2f
                val f = android.graphics.RectF(lx, by - fh, lx + fw, by)
                // 뒷면 모서리 점들
                val tlb = PointF(f.left + dx, f.top - dy)
                val trb = PointF(f.right + dx, f.top - dy)
                val brb = PointF(f.right + dx, f.bottom - dy)
                // 윗면·옆면 (채움 살짝 다르게)
                val topPath = android.graphics.Path().apply {
                    moveTo(f.left, f.top); lineTo(tlb.x, tlb.y); lineTo(trb.x, trb.y)
                    lineTo(f.right, f.top); close()
                }
                val sidePath = android.graphics.Path().apply {
                    moveTo(f.right, f.top); lineTo(trb.x, trb.y); lineTo(brb.x, brb.y)
                    lineTo(f.right, f.bottom); close()
                }
                val fill2 = Paint(fill).apply { color = Color.parseColor("#FFE9C4") }
                canvas.drawRect(f, fill)
                canvas.drawPath(topPath, fill2)
                canvas.drawPath(sidePath, fill2)
                canvas.drawRect(f, edge)
                canvas.drawPath(topPath, edge)
                canvas.drawPath(sidePath, edge)
                canvas.drawText("${num(v.p)}cm", cx - dx / 2f, f.bottom + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("${num(v.values.getOrNull(0) ?: 0.0)}cm",
                    f.right + dx / 2f + dp(4f), f.bottom - dy / 2f + dp(4f), lbl)
                canvas.drawText("${num(v.q)}cm", f.right + dx + dp(6f), (f.top + f.bottom - dy) / 2f, lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "ngon" -> {
                // 정n각형 (+ 한 꼭짓점에서 뻗는 대각선)
                val n = v.p.toInt().coerceIn(3, 12)
                val r = minOf(w, h) * 0.36f
                val pts = (0 until n).map { i ->
                    val ang = Math.PI * 2 * i / n - Math.PI / 2
                    PointF(cx + r * Math.cos(ang).toFloat(), cy + r * Math.sin(ang).toFloat())
                }
                val path = android.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until n) lineTo(pts[i].x, pts[i].y)
                    close()
                }
                canvas.drawPath(path, fill); canvas.drawPath(path, edge)
                if (v.q >= 1.0) {
                    // 대각선: 0번 꼭짓점에서 이웃 아닌 모든 꼭짓점으로
                    for (i in 2..n - 2) canvas.drawLine(pts[0].x, pts[0].y, pts[i].x, pts[i].y, dash)
                } else {
                    // 내각 하나에 호 표시
                    canvas.drawArc(
                        pts[0].x - dp(16f), pts[0].y - dp(16f), pts[0].x + dp(16f), pts[0].y + dp(16f),
                        40f, 100f, false, dash
                    )
                }
                canvas.drawText("정${n}각형", cx, cy + r + dp(20f), lbl)
            }
            "ineq" -> {
                // 수직선 위의 부등식: x > k (열린 원 + 오른쪽 화살표)
                val k = v.p
                val yLine = cy
                val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#8D6E63"); strokeWidth = dp(2f)
                }
                canvas.drawLine(w * 0.08f, yLine, w * 0.92f, yLine, axis)
                val kx = w * 0.42f
                // 눈금 (k-1, k, k+1)
                for ((i, t) in listOf(-1, 0, 1).withIndex()) {
                    val tx = kx + (i - 1) * w * 0.18f
                    canvas.drawLine(tx, yLine - dp(5f), tx, yLine + dp(5f), axis)
                    canvas.drawText(num(k + t), tx, yLine + dp(20f), lbl)
                }
                // 오른쪽으로 뻗는 굵은 반직선 + 열린 원
                val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(4f)
                }
                canvas.drawLine(kx, yLine, w * 0.88f, yLine, ray)
                canvas.drawCircle(kx, yLine, dp(6f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                })
                canvas.drawCircle(kx, yLine, dp(6f), Paint(ray).apply {
                    style = Paint.Style.STROKE; strokeWidth = dp(2.5f)
                })
            }
            "ucircle" -> {
                // 단위원과 각 — 삼각비·삼각함수
                val deg = v.p.toFloat()
                val r = minOf(w, h) * 0.33f
                val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B39F8C"); strokeWidth = dp(1.5f)
                }
                canvas.drawLine(cx - r - dp(14f), cy, cx + r + dp(14f), cy, axis)
                canvas.drawLine(cx, cy - r - dp(14f), cx, cy + r + dp(14f), axis)
                canvas.drawCircle(cx, cy, r, edge.apply { style = Paint.Style.STROKE })
                val rad = Math.toRadians(deg.toDouble())
                val ex = cx + r * Math.cos(rad).toFloat()
                val ey = cy - r * Math.sin(rad).toFloat()
                canvas.drawLine(cx, cy, ex, ey, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(3f)
                })
                canvas.drawCircle(ex, ey, dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#5C6BC0")
                })
                // 각 호
                canvas.drawArc(cx - r * 0.3f, cy - r * 0.3f, cx + r * 0.3f, cy + r * 0.3f,
                    0f, -deg, false, dash)
                canvas.drawText("${num(v.p)}°", cx + r * 0.48f, cy - dp(10f), lbl)
            }
            "inscribed" -> {
                // 중심각과 원주각 (원주각 = 중심각의 절반)
                val r = minOf(w, h) * 0.36f
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, edge.apply { style = Paint.Style.STROKE })
                fun onCircle(deg: Double) = PointF(
                    cx + r * Math.cos(Math.toRadians(deg)).toFloat(),
                    cy - r * Math.sin(Math.toRadians(deg)).toFloat()
                )
                val a = onCircle(-35.0); val b = onCircle(215.0); val pTop = onCircle(90.0)
                val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); strokeWidth = dp(2.5f)
                }
                // 중심각 (빨강)
                canvas.drawLine(cx, cy, a.x, a.y, lineP)
                canvas.drawLine(cx, cy, b.x, b.y, lineP)
                // 원주각 (파랑)
                val lineB = Paint(lineP).apply { color = Color.parseColor("#42A5F5") }
                canvas.drawLine(pTop.x, pTop.y, a.x, a.y, lineB)
                canvas.drawLine(pTop.x, pTop.y, b.x, b.y, lineB)
                canvas.drawCircle(cx, cy, dp(3.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6D4C41")
                })
                canvas.drawText("${num(v.p)}°", cx, cy + dp(22f), lbl)
                canvas.drawText("?", pTop.x, pTop.y + dp(26f), lbl)
                canvas.drawText("O", cx - dp(11f), cy + dp(3f), lbl)
            }
            "sqarea" -> {
                // 넓이가 주어진 정사각형 — 한 변은 ? (제곱근 도입 그림)
                val side = minOf(w, h) * 0.5f
                val r = android.graphics.RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
                canvas.drawRect(r, fill); canvas.drawRect(r, edge)
                canvas.drawText("넓이 ${num(v.p)}", cx, cy + dp(5f), lbl)
                canvas.drawText("?", cx, r.bottom + dp(18f), lbl)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText("?", r.right + dp(8f), cy + dp(5f), lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "ratio" -> {
                // 비 a : b — 길이가 비례하는 막대 두 개
                val a = (v.values.getOrNull(0) ?: 1.0).toFloat()
                val b = (v.values.getOrNull(1) ?: 1.0).toFloat()
                val maxLen = w * 0.62f
                val unitLen = maxLen / maxOf(a, b)
                val bh = h * 0.16f
                val left = w * 0.16f
                val barA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A80") }
                val barB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#81D4FA") }
                val y1 = cy - bh * 1.1f; val y2 = cy + bh * 0.5f
                canvas.drawRoundRect(left, y1, left + a * unitLen, y1 + bh, dp(6f), dp(6f), barA)
                canvas.drawRoundRect(left, y2, left + b * unitLen, y2 + bh, dp(6f), dp(6f), barB)
                lbl.textAlign = Paint.Align.LEFT
                canvas.drawText(num(v.values.getOrNull(0) ?: 0.0), left + a * unitLen + dp(8f), y1 + bh * 0.72f, lbl)
                canvas.drawText(num(v.values.getOrNull(1) ?: 0.0), left + b * unitLen + dp(8f), y2 + bh * 0.72f, lbl)
                lbl.textAlign = Paint.Align.CENTER
            }
            "percent" -> {
                // 전체 중 부분 — 가로 막대 (몇 % 인지는 아이가 계산)
                val part = v.p.toFloat(); val total = v.q.toFloat().coerceAtLeast(1f)
                val left = w * 0.1f; val right = w * 0.9f
                val bh = h * 0.2f
                val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EADFCE") }
                val barFg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB74D") }
                canvas.drawRoundRect(left, cy - bh / 2, right, cy + bh / 2, dp(8f), dp(8f), barBg)
                canvas.drawRoundRect(left, cy - bh / 2,
                    left + (right - left) * (part / total), cy + bh / 2, dp(8f), dp(8f), barFg)
                canvas.drawText("${num(v.p)}", left + (right - left) * (part / total) / 2f, cy + dp(5f), lbl)
                canvas.drawText("전체 ${num(v.q)}", (left + right) / 2f, cy + bh / 2 + dp(18f), lbl)
            }
            "bag" -> {
                // 주머니 속 빨강·파랑 구슬 (values = [빨강, 파랑])
                val rCnt = (v.values.getOrNull(0) ?: 1.0).toInt().coerceIn(0, 12)
                val bCnt = (v.values.getOrNull(1) ?: 1.0).toInt().coerceIn(0, 12)
                val bw = w * 0.52f; val bhh = h * 0.66f
                val top = cy - bhh / 2
                // 주머니: 위가 오므라진 자루 모양
                val bag = android.graphics.Path().apply {
                    moveTo(cx - bw * 0.16f, top)
                    quadTo(cx - bw * 0.55f, top + bhh * 0.25f, cx - bw * 0.5f, top + bhh * 0.62f)
                    quadTo(cx - bw * 0.45f, top + bhh, cx, top + bhh)
                    quadTo(cx + bw * 0.45f, top + bhh, cx + bw * 0.5f, top + bhh * 0.62f)
                    quadTo(cx + bw * 0.55f, top + bhh * 0.25f, cx + bw * 0.16f, top)
                    close()
                }
                canvas.drawPath(bag, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFE9C4")
                })
                canvas.drawPath(bag, edge)
                // 주머니 입구 묶음
                canvas.drawLine(cx - bw * 0.16f, top, cx + bw * 0.16f, top, edge)
                // 구슬 배치 (4개씩 줄지어)
                val total = rCnt + bCnt
                val perRow = 4
                val rows = (total + perRow - 1) / perRow
                val mr = minOf(bw * 0.09f, bhh * 0.4f / maxOf(rows, 1))
                val startY = top + bhh * 0.42f
                val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
                val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#42A5F5") }
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6D4C41"); style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
                }
                for (i in 0 until total) {
                    val row = i / perRow
                    val col = i % perRow
                    val inRow = minOf(perRow, total - row * perRow)
                    val gx = cx + (col - (inRow - 1) / 2f) * mr * 2.3f
                    val gy = startY + row * mr * 2.3f
                    canvas.drawCircle(gx, gy, mr, if (i < rCnt) red else blue)
                    canvas.drawCircle(gx, gy, mr, ring)
                }
            }
            "dice" -> {
                // 주사위 눈 1~6 을 늘어놓고, p 이하를 강조
                val upto = v.p.toInt().coerceIn(1, 6)
                val size = minOf(w / 7.2f, h * 0.4f)
                val gap = size * 0.18f
                val totalW = size * 6 + gap * 5
                val startX = cx - totalW / 2
                val dy = cy - size / 2
                val pip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4E342E") }
                val okFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE9C4") }
                val noFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                val okEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF7043"); style = Paint.Style.STROKE; strokeWidth = dp(2.5f)
                }
                val noEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#BCAAA4"); style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
                }
                for (face in 1..6) {
                    val x0 = startX + (face - 1) * (size + gap)
                    val r = android.graphics.RectF(x0, dy, x0 + size, dy + size)
                    val ok = face <= upto
                    canvas.drawRoundRect(r, size * 0.18f, size * 0.18f, if (ok) okFill else noFill)
                    canvas.drawRoundRect(r, size * 0.18f, size * 0.18f, if (ok) okEdge else noEdge)
                    // 눈 배치
                    val c = size * 0.5f; val o = size * 0.26f; val pr = size * 0.08f
                    fun dot(dxr: Float, dyr: Float) =
                        canvas.drawCircle(x0 + c + dxr, dy + c + dyr, pr, pip)
                    when (face) {
                        1 -> dot(0f, 0f)
                        2 -> { dot(-o, -o); dot(o, o) }
                        3 -> { dot(-o, -o); dot(0f, 0f); dot(o, o) }
                        4 -> { dot(-o, -o); dot(o, -o); dot(-o, o); dot(o, o) }
                        5 -> { dot(-o, -o); dot(o, -o); dot(0f, 0f); dot(-o, o); dot(o, o) }
                        else -> { dot(-o, -o); dot(o, -o); dot(-o, 0f); dot(o, 0f); dot(-o, o); dot(o, o) }
                    }
                }
            }
            "setdots" -> {
                // 원소 n개인 집합 — 큰 원 안의 점들
                val n = v.p.toInt().coerceIn(1, 12)
                val r = minOf(w, h) * 0.36f
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, edge)
                val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
                for (i in 0 until n) {
                    val ang = Math.PI * 2 * i / n
                    val rr = if (n == 1) 0f else r * 0.55f
                    canvas.drawCircle(
                        cx + rr * Math.cos(ang).toFloat(),
                        cy + rr * Math.sin(ang).toFloat(), dp(5f), dotP
                    )
                }
                canvas.drawText("원소 ${n}개", cx, cy + r + dp(20f), lbl)
            }
        }
    }

    // ---------- 지수·로그 곡선과 수열의 극한 ----------

    private fun drawFuncMisc(canvas: Canvas, v: MathVisual, w: Float, h: Float) {
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8D6E63"); strokeWidth = dp(2f)
        }
        val curveP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7043"); strokeWidth = dp(3f); style = Paint.Style.STROKE
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C6BC0") }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E342E"); textSize = dp(14f); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            // 격자·곡선 위에서도 읽히도록 흰 테두리를 두른다
            setShadowLayer(dp(3f), 0f, 0f, Color.WHITE)
        }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B39F8C"); strokeWidth = dp(1.5f); style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
        }
        when (v.op) {
            "exp" -> {
                // y = a^x : (0,1) 과 (1,a) 를 지나며 치솟는 곡선
                val base = v.p.toFloat().coerceAtLeast(1.2f)
                val xLo = -2.5f; val xHi = 2.6f
                val yMax = Math.pow(base.toDouble(), 2.6).toFloat()
                val ux = w * 0.86f / (xHi - xLo); val padL = w * 0.07f
                val uy = h * 0.82f / yMax
                fun X(x: Float) = padL + (x - xLo) * ux
                fun Y(y: Float) = h * 0.9f - y * uy
                canvas.drawLine(X(xLo), Y(0f), X(xHi), Y(0f), axis)
                canvas.drawLine(X(0f), dp(4f), X(0f), h - dp(4f), axis)
                val path = android.graphics.Path(); var first = true
                var x = xLo
                while (x <= xHi) {
                    val yy = Math.pow(base.toDouble(), x.toDouble()).toFloat()
                    if (first) { path.moveTo(X(x), Y(yy)); first = false } else path.lineTo(X(x), Y(yy))
                    x += 0.06f
                }
                canvas.drawPath(path, curveP)
                canvas.drawCircle(X(1f), Y(base), dp(5f), dot)
                canvas.drawText("(1, ${fmtC(base)})", X(1f) - dp(26f), Y(base) - dp(6f), lbl)
                canvas.drawText("y = ${fmtC(base)}ˣ", X(xHi) - dp(30f), dp(16f), lbl)
            }
            "log" -> {
                // y = log_a x : (1,0) 과 (a,1) 을 지나 천천히 자라는 곡선
                val base = v.p.toFloat().coerceAtLeast(1.2f)
                val xHi = maxOf(base * 2.2f, 7f)
                val lnB = Math.log(base.toDouble())
                val yMax = (Math.log(xHi.toDouble()) / lnB).toFloat()
                val yMin = -1.6f
                val ux = w * 0.86f / xHi; val padL = w * 0.09f
                val uy = h * 0.8f / (yMax - yMin)
                fun X(x: Float) = padL + x * ux
                fun Y(y: Float) = h * 0.86f - (y - yMin) * uy
                canvas.drawLine(X(0f), Y(0f), X(xHi), Y(0f), axis)
                canvas.drawLine(X(0f), dp(4f), X(0f), h - dp(4f), axis)
                val path = android.graphics.Path(); var first = true
                var x = 0.15f
                while (x <= xHi) {
                    val yy = (Math.log(x.toDouble()) / lnB).toFloat()
                    if (yy >= yMin) {
                        if (first) { path.moveTo(X(x), Y(yy)); first = false } else path.lineTo(X(x), Y(yy))
                    }
                    x += xHi / 130f
                }
                canvas.drawPath(path, curveP)
                canvas.drawCircle(X(base), Y(1f), dp(5f), dot)
                canvas.drawText("(${fmtC(base)}, 1)", X(base), Y(1f) - dp(9f), lbl)
                canvas.drawText("y = log_${fmtC(base)} x", X(xHi) - dp(36f), Y(yMax) + dp(2f), lbl)
            }
            "seqlim" -> {
                // 수열 (an+b)/(cn+d) 의 점들이 극한값에 다가가는 모습
                val a = vAt(v, 0); val b0 = vAt(v, 1); val c = vAt(v, 2); val d0 = vAt(v, 3)
                fun term(n: Int) = (a * n + b0) / (c * n + d0)
                val lim = a / c
                var yMax = lim
                for (n in 1..12) yMax = maxOf(yMax, term(n))
                yMax *= 1.15f
                val ux = w * 0.86f / 13f; val padL = w * 0.08f
                val uy = h * 0.78f / yMax
                fun X(n: Float) = padL + n * ux
                fun Y(y: Float) = h * 0.88f - y * uy
                canvas.drawLine(X(0f), Y(0f), X(12.6f), Y(0f), axis)
                canvas.drawLine(X(0f), dp(4f), X(0f), h - dp(4f), axis)
                canvas.drawLine(X(0f), Y(lim), X(12.6f), Y(lim), dash)
                for (n in 1..12) canvas.drawCircle(X(n.toFloat()), Y(term(n)), dp(4f), dot)
                canvas.drawText("n", X(12.4f), Y(0f) + dp(15f), lbl)
                canvas.drawText("aₙ이 점점 다가가요", X(6f), Y(lim) - dp(9f), lbl)
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
