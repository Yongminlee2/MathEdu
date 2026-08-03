package com.piyak.english.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.Button
import android.widget.LinearLayout
import com.piyak.english.R

/** 아이도 누르기 쉬운 큰 숫자 키패드 */
class NumberPadView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null,
) : LinearLayout(ctx, attrs) {

    var onChange: ((String) -> Unit)? = null

    private val sb = StringBuilder()

    /** 분수·음수를 쓰는 학년에서만 켠다 */
    var allowFraction = false
        set(v) { field = v; rebuild() }
    var allowMinus = false
        set(v) { field = v; rebuild() }
    var allowDecimal = true
        set(v) { field = v; rebuild() }

    val value: String get() = sb.toString()

    init {
        orientation = VERTICAL
        rebuild()
    }

    fun clear() {
        sb.clear()
        onChange?.invoke("")
    }

    private fun rebuild() {
        removeAllViews()
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            buildList {
                add(if (allowMinus) "-" else if (allowDecimal) "." else " ")
                add("0")
                add("⌫")
            },
        )
        for (r in rows) addView(row(r))
        if (allowFraction || (allowMinus && allowDecimal)) {
            val extra = buildList {
                if (allowDecimal && allowMinus) add(".")
                if (allowFraction) add("/")
                add("지우기")
            }
            addView(row(extra))
        }
    }

    private fun row(keys: List<String>): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(3) }
        }
        for (k in keys) {
            val btn = Button(context).apply {
                text = k
                textSize = if (k.length > 1) 15f else 21f
                isAllCaps = false
                setTextColor(Color.parseColor("#4E342E"))
                // 기본 버튼 배경에는 눈에 안 보이는 위아래 여백이 있어 글자가 잘린다 —
                // 배경을 직접 지정하고 패딩·최소높이를 0으로 둔다
                background = when (k) {
                    "⌫", "지우기" -> context.getDrawable(R.drawable.bg_key_del)
                    " " -> null
                    else -> context.getDrawable(R.drawable.bg_key)
                }
                backgroundTintList = null
                stateListAnimator = null
                setPadding(0, 0, 0, 0)
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                includeFontPadding = false
                gravity = android.view.Gravity.CENTER
                isEnabled = k != " "
                layoutParams = LayoutParams(0, dp(46), 1f).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                }
                setOnClickListener { press(k) }
            }
            row.addView(btn)
        }
        return row
    }

    private fun press(k: String) {
        when (k) {
            " " -> return
            "지우기" -> sb.clear()
            "⌫" -> if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            "-" -> if (sb.isEmpty()) sb.append("-")
            "." -> if (sb.isNotEmpty() && !currentPartHas('.')) sb.append(".")
            "/" -> if (sb.isNotEmpty() && !sb.contains("/")) sb.append("/")
            else -> if (sb.length < 12) sb.append(k)
        }
        onChange?.invoke(sb.toString())
    }

    /** 분수의 각 항마다 소수점은 하나까지 */
    private fun currentPartHas(c: Char): Boolean =
        sb.toString().substringAfterLast('/').contains(c)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
