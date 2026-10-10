import Foundation

// data class → struct. labelsKo 의 기본값(= labels)은 스위프트 기본 인자로 못 써서 nil 이면 labels 로 채운다.

/// 수학 문제에 붙는 "그림 명세". 이미지 파일 없이 이모지와 Canvas 로 그린다.
/// 새 그림이 필요하면 kind 를 늘리고 MathVisualView 에 그리는 법만 추가하면 된다.
struct MathVisual: Equatable {
    var kind: String
    /// 이모지 하나 (emoji / emoji_op / array 에서 사용)
    var emoji: String
    /// 개수 (emoji), 왼쪽 개수 (emoji_op), 행 (array)
    var a: Int
    /// 오른쪽 개수 (emoji_op), 열 (array)
    var bb: Int
    /// 연산 기호 (emoji_op): + 또는 -
    var op: String
    /// 화면에 뜰 글자 (폰 언어로 번역돼 있다) — 도형 이름들(shapes) / 막대 이름들(bar_graph)
    var labels: [String]
    /// 번역 전 한국어 라벨. 라벨로 무엇을 그릴지 고르는 곳은 번역본이 아니라 이걸 봐야 한다.
    var labelsKo: [String]
    /// 막대 값들 (bar_graph), 수직선 표시 위치 (number_line)
    var values: [Double]
    /// 분자 (fraction), 시(clock), 각도(angle), 수직선 최소(number_line)
    var p: Double
    /// 분모 (fraction), 분(clock), 수직선 최대(number_line)
    var q: Double
    /// 끌어 옮길 사물들 (shape_sort)
    var items: [String]
    /// items 각각이 들어가야 할 바구니 번호 (shape_sort)
    var kinds: [Int]

    init(
        kind: String,
        emoji: String = "🍎",
        a: Int = 0,
        bb: Int = 0,
        op: String = "+",
        labels: [String] = [],
        labelsKo: [String]? = nil,
        values: [Double] = [],
        p: Double = 0.0,
        q: Double = 1.0,
        items: [String] = [],
        kinds: [Int] = []
    ) {
        self.kind = kind
        self.emoji = emoji
        self.a = a
        self.bb = bb
        self.op = op
        self.labels = labels
        self.labelsKo = labelsKo ?? labels
        self.values = values
        self.p = p
        self.q = q
        self.items = items
        self.kinds = kinds
    }

    /// 치수 표기 도형: op = rect|tri|trap|circle|rtri|cyl (교과서 그림처럼)
    static let GEOM = "geom"
    /// 공간좌표: 오른손 좌표계 + 점 (values = [x, y, z])
    static let COORD3D = "coord3d"
    /// 좌표평면: op = "vec"(벡터 2개) | "parab"(포물선 a·p·q) | "ellipse"(타원 p=a, q=b)
    static let COORD2D = "coord2d"
    static let EMOJI = "emoji"
    static let EMOJI_OP = "emoji_op"
    static let ARRAY = "array"
    static let SHAPES = "shapes"
    static let CLOCK = "clock"
    static let FRACTION = "fraction"
    static let NUMBER_LINE = "number_line"
    static let BAR_GRAPH = "bar_graph"
    static let ANGLE = "angle"
    static let COMPARE = "compare"

    /// 바늘을 돌려 시각을 맞추는 시계 (p·q = 정답 시·분)
    static let CLOCK_SET = "clock_set"

    /// 사물을 끌어다 묶음에 나눠 담기 (a = 전체 개수, b = 묶음 수)
    static let GROUP = "group"

    /// 조각을 탭해서 분수만큼 색칠하기 (p = 목표 분자, q = 분모)
    static let FRACTION_PAINT = "fraction_paint"

    /// 도형을 이름 붙은 바구니로 끌어 분류하기 (items·kinds·labels)
    static let SHAPE_SORT = "shape_sort"

    /// 수직선 위의 점을 끌어 수를 찾기 (p·q = 왼쪽·오른쪽 끝, a = 구간 수)
    static let NUMBER_LINE_DRAG = "number_line_drag"

    /// 반직선을 돌려 각도를 만들기 (p = 목표 각도)
    static let ANGLE_SET = "angle_set"

    /// 양팔 저울을 평형으로 만들기 (a·x + b = p 에서 x 를 찾는다)
    static let BALANCE = "balance"

    /// 막대를 끌어 올려 그래프 완성하기 (labels = 항목, values = 목표 높이)
    static let BAR_BUILD = "bar_build"

    /// 사물을 상자로 옮겨 담기 (a = 전체 개수, b = 상자에 담아야 할 개수). labels[0] 은 상자 이름.
    static let GATHER = "gather"

    static let KINDS: Set<String> = [
        EMOJI, EMOJI_OP, ARRAY, SHAPES, CLOCK, FRACTION,
        NUMBER_LINE, BAR_GRAPH, ANGLE, COMPARE,
        CLOCK_SET, GROUP, FRACTION_PAINT, SHAPE_SORT,
        NUMBER_LINE_DRAG, ANGLE_SET, BALANCE, BAR_BUILD, GATHER,
        // 좌표·함수·도형 도해 — 여기 빠뜨리면 fromJson 이 조용히 버린다!
        COORD3D, COORD2D, GEOM,
    ]

    /// 그림 자체가 답을 입력받는 종류 (키패드·보기가 필요 없다)
    static let INPUT_KINDS: Set<String> = [
        CLOCK_SET, GROUP, FRACTION_PAINT, SHAPE_SORT,
        NUMBER_LINE_DRAG, ANGLE_SET, BALANCE, BAR_BUILD, GATHER,
    ]

    static func fromJson(_ o: JSONObject?) -> MathVisual? {
        guard let o = o else { return nil }
        let kind = o.optString("kind")
        if kind.isEmpty || !KINDS.contains(kind) { return nil }
        return MathVisual(
            kind: kind,
            emoji: o.optString("emoji", "🍎"),
            a: o.optInt("a", 0),
            bb: o.optInt("b", 0),
            op: o.optString("op", "+"),
            // 그림 속 글자도 폰 언어로. 그리기 로직이 보는 원문은 labelsKo 에 남긴다.
            labels: Tpl.words(strList(o.optJSONArray("labels"))),
            labelsKo: strList(o.optJSONArray("labels")),
            values: numList(o.optJSONArray("values")),
            p: o.optDouble("p", 0.0),
            q: o.optDouble("q", 1.0),
            items: strList(o.optJSONArray("items")),
            kinds: intList(o.optJSONArray("kinds"))
        )
    }

    private static func strList(_ a: JSONArray?) -> [String] {
        guard let a = a else { return [] }
        return (0..<a.length()).map { a.getString($0) }
    }

    private static func numList(_ a: JSONArray?) -> [Double] {
        guard let a = a else { return [] }
        return (0..<a.length()).map { a.getDouble($0) }
    }

    private static func intList(_ a: JSONArray?) -> [Int] {
        guard let a = a else { return [] }
        return (0..<a.length()).map { a.getInt($0) }
    }
}
