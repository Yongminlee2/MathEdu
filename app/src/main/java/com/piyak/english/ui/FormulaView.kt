package com.piyak.english.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.piyak.english.engine.Formula
import com.piyak.english.engine.Formula.Tok
import kotlin.math.max

/**
 * [Formula] 토큰을 진짜 수학 기호처럼 그린다 — 세로 분수, log 아래첨자,
 * lim 아래 조건, 근호 윗줄, ∫ 상·하한. 한글 문장 토큰은 그대로 글자로 흘린다.
 *
 * 긴 문장은 토큰(원자 상자) 단위로 줄바꿈한다. 분수·근호·lim·∫ 는 쪼개지 않는다.
 */
class FormulaView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private var toks: List<Tok> = emptyList()

    private val main = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E2723")
        textSize = sp(24f)
        isFakeBoldText = true
    }
    private val script = Paint(main).apply { textSize = main.textSize * 0.58f }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E2723")
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }

    fun setFormula(prompt: String) {
        toks = Formula.parse(prompt)
        requestLayout()
        invalidate()
    }

    // ---------- 상자 측정 ----------
    /** 토큰 하나의 렌더 상자: 폭, 기준선 위 높이(ascent), 기준선 아래 깊이(descent) */
    private data class Box(val w: Float, val asc: Float, val desc: Float)

    private fun textBox(p: Paint, s: String): Box {
        val fm = p.fontMetrics
        return Box(p.measureText(s), -fm.ascent, fm.descent)
    }

    private fun measure(t: Tok): Box = when (t) {
        is Tok.Txt -> textBox(main, t.s)
        is Tok.Sup -> textBox(script, t.s).let { Box(it.w + dp(1f), it.asc + main.textSize * 0.42f, 0f) }
        is Tok.Sub -> textBox(script, t.s).let { Box(it.w + dp(1f), 0f, it.desc + main.textSize * 0.28f) }
        is Tok.Frac -> {
            val n = measureRow(t.num); val d = measureRow(t.den)
            val w = max(n.w, d.w) + dp(10f)
            // 가로줄이 기준선 살짝 위 — 위로 분자 전체, 아래로 분모 전체
            Box(w, n.asc + n.desc + dp(7f), d.asc + d.desc + dp(5f))
        }
        is Tok.Sqrt -> {
            val b = measureRow(t.body)
            Box(b.w + main.textSize * 0.62f + dp(4f), b.asc + dp(6f), b.desc)
        }
        is Tok.Lim -> {
            val limW = main.measureText("lim")
            val condW = script.measureText(t.cond)
            val head = max(limW, condW)
            val b = measureRow(t.body)
            Box(head + dp(6f) + b.w, max(textBox(main, "lim").asc, b.asc),
                max(script.textSize + dp(3f), b.desc))
        }
        is Tok.Integral -> {
            val glyph = main.textSize * 1.45f
            val boundW = max(script.measureText(t.hi), script.measureText(t.lo))
            val b = measureRow(t.body)
            Box(main.measureText("∫") * (glyph / main.textSize) + boundW + dp(6f) + b.w,
                max(glyph * 0.72f, b.asc), max(glyph * 0.38f, b.desc))
        }
    }

    private fun measureRow(row: List<Tok>): Box {
        var w = 0f; var asc = 0f; var desc = 0f
        for (t in row) {
            val b = measure(t)
            w += b.w
            asc = max(asc, b.asc)
            desc = max(desc, b.desc)
        }
        return Box(w, asc, desc)
    }

    // ---------- 줄바꿈 ----------
    /** Txt 는 단어 단위로 쪼개고 나머지는 원자 상자로 취급해 greedy 줄바꿈 */
    private fun flatten(): List<Tok> {
        val out = ArrayList<Tok>()
        for (t in toks) {
            if (t is Tok.Txt) {
                // 공백을 유지한 채 단어 단위로 (수식과 자연스럽게 섞이도록)
                var word = StringBuilder()
                for (c in t.s) {
                    word.append(c)
                    if (c == ' ') { out.add(Tok.Txt(word.toString())); word = StringBuilder() }
                }
                if (word.isNotEmpty()) out.add(Tok.Txt(word.toString()))
            } else out.add(t)
        }
        return out
    }

    private fun buildLines(maxW: Float): List<List<Tok>> {
        val lines = ArrayList<List<Tok>>()
        var line = ArrayList<Tok>()
        var w = 0f
        for (t in flatten()) {
            val bw = measure(t).w
            if (w + bw > maxW && line.isNotEmpty()) {
                lines.add(line); line = ArrayList(); w = 0f
            }
            line.add(t); w += bw
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val lines = buildLines(w.toFloat() - dp(8f))
        var h = dp(8f)
        for (line in lines) {
            val b = measureRow(line)
            h += b.asc + b.desc + dp(6f)
        }
        setMeasuredDimension(w, h.toInt().coerceAtLeast(dp(40f).toInt()))
    }

    // ---------- 그리기 ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var y = dp(4f)
        for (line in buildLines(width.toFloat() - dp(8f))) {
            val b = measureRow(line)
            val base = y + b.asc
            var x = dp(4f)
            for (t in line) x = drawTok(canvas, t, x, base)
            y += b.asc + b.desc + dp(6f)
        }
    }

    /** 토큰 하나를 (x, 기준선 base) 에 그리고 다음 x 를 돌려준다 */
    private fun drawTok(canvas: Canvas, t: Tok, x: Float, base: Float): Float {
        when (t) {
            is Tok.Txt -> {
                canvas.drawText(t.s, x, base, main)
                return x + main.measureText(t.s)
            }
            is Tok.Sup -> {
                canvas.drawText(t.s, x + dp(1f), base - main.textSize * 0.42f, script)
                return x + script.measureText(t.s) + dp(1f)
            }
            is Tok.Sub -> {
                canvas.drawText(t.s, x + dp(1f), base + main.textSize * 0.28f, script)
                return x + script.measureText(t.s) + dp(1f)
            }
            is Tok.Frac -> {
                val n = measureRow(t.num); val d = measureRow(t.den)
                val w = max(n.w, d.w) + dp(10f)
                val mid = base - main.textSize * 0.30f   // 가로줄 위치
                // 분자 (가로줄 위에)
                var nx = x + (w - n.w) / 2f
                for (tt in t.num) nx = drawTok(canvas, tt, nx, mid - dp(5f) - n.desc)
                // 가로줄
                canvas.drawLine(x + dp(2f), mid, x + w - dp(2f), mid, rule)
                // 분모
                var dx = x + (w - d.w) / 2f
                for (tt in t.den) dx = drawTok(canvas, tt, dx, mid + dp(5f) + d.asc)
                return x + w
            }
            is Tok.Sqrt -> {
                val b = measureRow(t.body)
                val hook = main.textSize * 0.62f
                val top = base - b.asc - dp(4f)
                // 근호: 체크 모양 + 윗줄
                canvas.drawLine(x, base - b.asc * 0.35f, x + hook * 0.35f, base + b.desc, rule)
                canvas.drawLine(x + hook * 0.35f, base + b.desc, x + hook, top, rule)
                canvas.drawLine(x + hook, top, x + hook + b.w + dp(4f), top, rule)
                var bx = x + hook + dp(2f)
                for (tt in t.body) bx = drawTok(canvas, tt, bx, base)
                return x + hook + b.w + dp(6f)
            }
            is Tok.Lim -> {
                val limW = main.measureText("lim")
                val condW = script.measureText(t.cond)
                val head = max(limW, condW)
                canvas.drawText("lim", x + (head - limW) / 2f, base, main)
                canvas.drawText(t.cond, x + (head - condW) / 2f, base + script.textSize + dp(2f), script)
                var bx = x + head + dp(6f)
                for (tt in t.body) bx = drawTok(canvas, tt, bx, base)
                return bx
            }
            is Tok.Integral -> {
                val big = Paint(main).apply { textSize = main.textSize * 1.45f; isFakeBoldText = false }
                val gw = big.measureText("∫")
                canvas.drawText("∫", x, base + big.textSize * 0.18f, big)
                val boundX = x + gw + dp(1f)
                canvas.drawText(t.hi, boundX, base - main.textSize * 0.62f, script)
                canvas.drawText(t.lo, boundX, base + main.textSize * 0.34f, script)
                val boundW = max(script.measureText(t.hi), script.measureText(t.lo))
                var bx = boundX + boundW + dp(5f)
                for (tt in t.body) bx = drawTok(canvas, tt, bx, base)
                return bx
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
