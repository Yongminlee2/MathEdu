import UIKit

/// 아이도 누르기 쉬운 큰 숫자 키패드
final class NumberPadView: LinearLayout {

    var onChange: ((String) -> Void)?

    private var sb = ""

    /// 분수·음수를 쓰는 학년에서만 켠다
    var allowFraction = false { didSet { rebuild() } }
    var allowMinus = false { didSet { rebuild() } }
    var allowDecimal = true { didSet { rebuild() } }

    var value: String { sb }

    override init(_ context: Context, _ attrs: AttributeSet? = nil) {
        super.init(context, attrs)
        orientation = LinearLayout.VERTICAL
        rebuild()
    }
    required init?(coder: NSCoder) { fatalError() }

    func clear() {
        sb = ""
        onChange?("")
    }

    private func rebuild() {
        removeAllViews()
        var last: [String] = []
        last.append(allowMinus ? "-" : (allowDecimal ? "." : " "))
        last.append("0")
        last.append("⌫")
        let rows: [[String]] = [
            ["1", "2", "3"],
            ["4", "5", "6"],
            ["7", "8", "9"],
            last,
        ]
        for r in rows { addView(row(r)) }
        if allowFraction || (allowMinus && allowDecimal) {
            var extra: [String] = []
            if allowDecimal && allowMinus { extra.append(".") }
            if allowFraction { extra.append("/") }
            extra.append(context.getString(R.string.clear))
            addView(row(extra))
        }
    }

    private func row(_ keys: [String]) -> LinearLayout {
        let row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        let rlp = LinearLayout.LayoutParams(BaseLayoutParams.MATCH_PARENT, BaseLayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(3)
        row.layoutParams = rlp
        let clearLabel = context.getString(R.string.clear)
        for k in keys {
            let btn = Button(context)
            btn.text = k
            btn.textSize = k.length > 1 ? 15 : 21
            btn.isAllCaps = false
            btn.setTextColor(Color.parseColor("#4E342E"))
            // 기본 버튼 배경의 위아래 여백 때문에 글자가 잘리지 않게 배경을 직접 지정하고 패딩·최소높이를 0으로
            switch k {
            case "⌫", clearLabel: btn.background = context.getDrawable(R.drawable.bg_key_del)
            case " ": btn.background = nil
            default: btn.background = context.getDrawable(R.drawable.bg_key)
            }
            btn.backgroundTintList = nil
            btn.stateListAnimator = nil
            btn.setPadding(0, 0, 0, 0)
            btn.minHeight = 0
            btn.minimumHeight = 0
            btn.minWidth = 0
            btn.minimumWidth = 0
            btn.includeFontPadding = false
            btn.gravity = Gravity.CENTER
            btn.isEnabled = k != " "
            let lp = LinearLayout.LayoutParams(0, dp(46), 1)
            lp.marginStart = dp(3); lp.marginEnd = dp(3)
            btn.layoutParams = lp
            btn.setOnClickListener { [weak self] _ in self?.press(k) }
            row.addView(btn)
        }
        return row
    }

    private func press(_ k: String) {
        switch k {
        case " ": return
        case context.getString(R.string.clear): sb = ""
        case "⌫": if !sb.isEmpty { sb.removeLast() }
        case "-": if sb.isEmpty { sb.append("-") }
        case ".": if !sb.isEmpty && !currentPartHas(".") { sb.append(".") }
        case "/": if !sb.isEmpty && !sb.contains("/") { sb.append("/") }
        default: if sb.length < 12 { sb.append(k) }
        }
        onChange?(sb)
    }

    /// 분수의 각 항마다 소수점은 하나까지
    private func currentPartHas(_ c: Character) -> Bool {
        let after = sb.components(separatedBy: "/").last ?? sb
        return after.contains(c)
    }

    private func dp(_ v: Int) -> Int { Int(Float(v) * context.resources.displayMetrics.density) }
}
