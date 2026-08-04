package com.piyak.english.ui

import com.piyak.english.R

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * 사물을 손으로 끌어다 바구니에 담는 판. 두 가지로 쓴다.
 *
 * - **나눠 담기**(`setRound`) : 같은 사물을 바구니에 **똑같은 개수**로 나눠 담는다 (나눗셈).
 * - **분류하기**(`setSort`) : 사물마다 들어갈 바구니가 정해져 있다 (도형 분류).
 *
 * 어느 쪽이든 머리로 계산하거나 고르기 전에 **직접 옮겨 보는** 것이 핵심이다.
 */
class GroupDragView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private class Item(
        val emoji: String,
        /** 들어가야 할 바구니. -1 이면 아무 데나 (나눠 담기 모드) */
        val kind: Int,
        var x: Float, var y: Float,
        var homeX: Float, var homeY: Float,
        /** 지금 담긴 바구니. -1 이면 아직 안 담김 */
        var group: Int = -1,
        /** 바구니 안에서 그릴 크기 (0 이면 기본 크기) */
        var size: Float = 0f,
    )

    private val items = ArrayList<Item>()
    private var groupRects = ArrayList<RectF>()
    private var dragging: Item? = null
    private var dragDx = 0f
    private var dragDy = 0f

    private var specs = ArrayList<Pair<String, Int>>()   // (이모지, 들어갈 바구니)
    private var groups = 3
    private var labels = emptyList<String>()

    /** 분류 모드인가 (아니면 똑같이 나눠 담기) */
    private var sortMode = false

    /** 바구니 하나에 정해진 개수만 담는 모드 (모으기·덜어내기). -1 이면 안 씀 */
    private var needCount = -1

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var itemSize = 0f

    private companion object {
        /** 줄 간격 배수 — 이모지가 지정 크기보다 크게 그려지는 걸 감안한 값 */
        const val ROW_STEP = 1.32f
    }

    /** 담긴 상태가 바뀔 때 (바구니별 개수) */
    var onChanged: ((List<Int>) -> Unit)? = null

    /** 바구니에 하나 담을 때마다 */
    var onPlace: (() -> Unit)? = null

    /** 똑같이 나눠 담기 — 같은 사물 total 개를 바구니 groups 개에 나눈다 */
    fun setRound(emoji: String, total: Int, groups: Int) {
        sortMode = false
        needCount = -1
        this.groups = groups.coerceAtLeast(1)
        labels = (1..this.groups).map { context.getString(R.string.basket_no, it) }
        specs = ArrayList((0 until total).map { emoji to -1 })
        rebuild()
    }

    /**
     * 바구니 하나에 손으로 옮겨 담기.
     * 덧셈은 전부 모으고(`need == total`), 뺄셈은 덜어낼 만큼만 담는다.
     * 수를 계산해서 쓰는 대신 **실제로 옮겨 보고 세는** 방식이다.
     */
    fun setGather(emoji: String, total: Int, need: Int, label: String) {
        sortMode = false
        needCount = need
        groups = 1
        labels = listOf(label)
        specs = ArrayList((0 until total).map { emoji to -1 })
        rebuild()
    }

    /** 아직 바구니 밖에 남아 있는 개수 */
    fun outsideCount(): Int = items.count { it.group < 0 }

    /** 바구니에 담긴 개수 */
    fun inBoxCount(): Int = items.count { it.group == 0 }

    /** 분류하기 — items[i] 는 labels[kinds[i]] 바구니에 들어가야 한다 */
    fun setSort(items: List<String>, kinds: List<Int>, labels: List<String>) {
        sortMode = true
        this.groups = labels.size.coerceAtLeast(1)
        this.labels = labels
        specs = ArrayList(items.mapIndexed { i, e -> e to (kinds.getOrNull(i) ?: 0) })
        rebuild()
    }

    private fun rebuild() {
        items.clear()
        layoutAll()
        onChanged?.invoke(counts())
        invalidate()
    }

    /** 바구니별 담긴 개수 */
    fun counts(): List<Int> = (0 until groups).map { g -> items.count { it.group == g } }

    /** 아직 안 담은 개수 */
    fun leftOver(): Int = items.count { it.group < 0 }

    /** 엉뚱한 바구니에 담긴 개수 (분류 모드에서만 의미 있다) */
    fun misplaced(): Int = items.count { it.group >= 0 && it.kind >= 0 && it.group != it.kind }

    fun isCorrect(): Boolean {
        if (items.isEmpty()) return false
        // 모으기·덜어내기는 바구니 밖에 남는 게 정상이다
        if (needCount >= 0) return inBoxCount() == needCount
        if (leftOver() > 0) return false
        return if (sortMode) {
            misplaced() == 0
        } else {
            val c = counts()
            c.isNotEmpty() && c.all { it == c[0] } && c[0] > 0
        }
    }

    /** 한 바구니에 담긴 개수 (나눠 담기 정답일 때의 몫) */
    fun perGroup(): Int = counts().firstOrNull() ?: 0

    fun reset() {
        for (it in items) {
            it.group = -1
            it.x = it.homeX
            it.y = it.homeY
        }
        onChanged?.invoke(counts())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutAll()
    }

    private fun layoutAll() {
        if (width == 0 || height == 0 || specs.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val total = specs.size

        // 아래쪽을 바구니 자리로
        val zoneTop = h * 0.50f
        groupRects = ArrayList()
        val gw = (w - dp(10f) * (groups + 1)) / groups
        for (g in 0 until groups) {
            val left = dp(10f) + (gw + dp(10f)) * g
            groupRects.add(RectF(left, zoneTop, left + gw, h - dp(8f)))
        }

        val perRow = min(6, total)
        val rows = (total + perRow - 1) / perRow
        // 이모지 글리프는 지정 크기보다 위아래로 더 튀어나온다 (토끼 귀!).
        // 줄 간격을 1.32배로 벌려야 윗줄과 겹치지 않는다.
        itemSize = min(w / (perRow + 1.4f), (zoneTop - dp(16f)) / (rows * ROW_STEP + 0.4f))
        // 바구니 이름은 바구니 폭에 맞춰야 한다 — 이모지 크기에 맞추면 칸 밖으로 넘친다
        textPaint.textSize = min(itemSize * 0.45f, gw * 0.20f)

        // 이미 담아 놓은 걸 잃지 않도록, 크기만 바뀌면 자리만 다시 잡는다
        val keep = items.size == total
        if (!keep) items.clear()
        for (i in 0 until total) {
            val r = i / perRow
            val c = i % perRow
            val cols = min(perRow, total - r * perRow)
            val rowW = cols * itemSize * 1.15f
            val x = (w - rowW) / 2f + itemSize * 1.15f * (c + 0.5f)
            val y = dp(14f) + itemSize * ROW_STEP * (r + 0.5f)
            if (keep) {
                val it = items[i]
                it.homeX = x; it.homeY = y
                if (it.group < 0) { it.x = x; it.y = y }
            } else {
                val (e, kind) = specs[i]
                items.add(Item(e, kind, x, y, x, y))
            }
        }
        if (keep) for (g in 0 until groups) {
            items.filter { it.group == g }.forEach { placeInGroup(it, g) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        for ((g, rect) in groupRects.withIndex()) {
            val n = items.count { it.group == g }
            boxPaint.color = Color.parseColor(if (n > 0) "#FFF3E0" else "#FAFAFA")
            canvas.drawRoundRect(rect, dp(16f), dp(16f), boxPaint)
            strokePaint.color = Color.parseColor("#FFB300")
            strokePaint.strokeWidth = dp(3f)
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(10f), dp(8f)), 0f)
            canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)
            strokePaint.pathEffect = null

            // 이름은 위, 개수는 아래 — 한 줄로 붙이면 좁은 바구니에서 칸 밖으로 넘친다
            textPaint.color = Color.parseColor("#8D6E63")
            canvas.drawText(
                labels.getOrElse(g) { context.getString(R.string.basket_no, g + 1) },
                rect.centerX(), rect.top + textPaint.textSize * 1.1f, textPaint
            )
            if (!sortMode) {
                textPaint.color =
                    if (n > 0) Color.parseColor("#EF6C00") else Color.parseColor("#BCAAA4")
                canvas.drawText(
                    "$n", rect.centerX(), rect.bottom - dp(6f), textPaint
                )
            }
        }

        for (it in items) {
            if (it === dragging) continue
            val sz = if (it.group >= 0 && it.size > 0f) it.size else itemSize
            drawEmojiItem(canvas, it.emoji, it.x, it.y, sz)
        }
        dragging?.let {
            drawEmojiItem(canvas, it.emoji, it.x, it.y, itemSize * 1.18f)
        }
    }

    /** 일러스트가 있으면 그림을, 없으면 이모지를 (ts = 이모지 글자 크기 기준) */
    private fun drawEmojiItem(canvas: Canvas, emoji: String, x: Float, y: Float, ts: Float) {
        val d = EmojiArt.of(context, emoji)
        if (d != null) {
            // 이모지 글자의 눈에 보이는 중심이 (x, y) — 그림도 같은 자리에 놓는다
            val half = (ts * 0.52f).toInt()
            d.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
            d.draw(canvas)
        } else {
            emojiPaint.textSize = ts
            canvas.drawText(emoji, x, y + ts * 0.35f, emojiPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = items.lastOrNull {
                    hypot(event.x - it.x, event.y - it.y) < itemSize * 0.7f
                }
                dragging?.let { dragDx = it.x - event.x; dragDy = it.y - event.y }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return dragging != null
            }
            MotionEvent.ACTION_MOVE -> {
                dragging?.let {
                    it.x = event.x + dragDx
                    it.y = event.y + dragDy
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val d = dragging ?: return true
                dragging = null
                val gi = groupRects.indexOfFirst { it.contains(d.x, d.y) }
                if (gi >= 0) {
                    d.group = gi
                    placeInGroup(d, gi)
                    onPlace?.invoke()
                } else {
                    d.group = -1
                    d.x = d.homeX
                    d.y = d.homeY
                }
                onChanged?.invoke(counts())
                invalidate()
                return true
            }
        }
        return false
    }

    /** 바구니 안에서 겹치지 않게 자리 잡기 */
    /**
     * 바구니에 담긴 것들을 다시 줄 세운다.
     *
     * ⚠️ 예전에는 칸 크기를 고정해 두고 순서대로 놓기만 해서, 많이 담으면
     *    아래·옆으로 밀려나 **바구니 밖에 그려져 사라진 것처럼** 보였다.
     *    이제는 담긴 개수에 맞춰 칸(과 그림)을 줄여 항상 바구니 안에 들어온다.
     */
    private fun placeInGroup(item: Item, g: Int) = relayoutGroup(g)

    private fun relayoutGroup(g: Int) {
        val rect = groupRects.getOrNull(g) ?: return
        val mine = items.filter { it.group == g }
        if (mine.isEmpty()) return
        val top = rect.top + textPaint.textSize * 1.5f
        val bottom = rect.bottom - if (sortMode) dp(6f) else textPaint.textSize * 1.3f
        val availW = rect.width() - dp(8f)
        val availH = (bottom - top).coerceAtLeast(dp(24f))
        val n = mine.size

        // 줄 수를 늘려 가며 바구니에 들어가는 가장 큰 칸을 고른다
        var best = 0f; var bestPerRow = 1
        for (rows in 1..n) {
            val perRow = kotlin.math.ceil(n / rows.toFloat()).toInt()
            val cell = minOf(availW / perRow, availH / rows)
            if (cell > best) { best = cell; bestPerRow = perRow }
        }
        val cell = best.coerceAtMost(itemSize * 1.05f)
        val perRow = bestPerRow
        val rows = kotlin.math.ceil(n / perRow.toFloat()).toInt()
        val startY = top + (availH - cell * rows) / 2f + cell / 2f
        mine.forEachIndexed { idx, it ->
            val r = idx / perRow
            val c = idx % perRow
            val inRow = minOf(perRow, n - r * perRow)
            it.x = rect.centerX() + (c - (inRow - 1) / 2f) * cell
            it.y = startY + r * cell
            it.size = cell * 0.82f
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
