import XCTest
@testable import PeepMath

/// 일반 문제의 상호작용(버블 보기 / 선 잇기)이 실제 콘텐츠에 잘 맞는지
final class InteractionTests: XCTestCase {

    func test_effectSoundDefaultsQuietEnoughForSpeech() {
        // 효과음이 크면 듣기·말하기 음성을 덮는다
        XCTAssertTrue(Sfx.DEFAULT_VOLUME_PERCENT <= 40, "기본 효과음이 너무 큼")
        XCTAssertTrue(Sfx.DEFAULT_VOLUME_PERCENT > 0, "기본 효과음이 0이면 피드백이 사라진다")
    }

    func test_bubbleModeOnlyForShortChoices() {
        XCTAssertTrue(BubbleChoiceView.fits(["12", "13", "14", "15"]))
        XCTAssertTrue(BubbleChoiceView.fits(["🍎", "🍌", "🐶", "🐱"]))
        XCTAssertTrue(BubbleChoiceView.fits(["apple", "banana", "dog", "cat"]))
        // 짧은 한글 낱말은 버블에 들어간다
        XCTAssertTrue(BubbleChoiceView.fits(["사과", "고양이", "무지개", "아이스크림"]))
        // 긴 문장은 버블에 안 들어간다 → 읽기 편한 버튼 목록으로
        XCTAssertFalse(
            BubbleChoiceView.fits([
                "나는 사과를 좋아해요.", "이것은 내 개예요.",
                "해는 뜨거워요.", "나는 별을 봐요.",
            ])
        )
        // 보기가 4개가 아니면 버블 배치가 깨진다
        XCTAssertFalse(BubbleChoiceView.fits(["1", "2", "3"]))
    }

    /// 한글은 글자 수가 적어도 폭이 넓다 — 글자 수로 재면 문장이 버블에 들어가 버린다
    func test_widthScoreCountsWideCharactersAsTwo() {
        XCTAssertEqual(BubbleChoiceView.widthScore("사과"), 4)
        XCTAssertEqual(BubbleChoiceView.widthScore("apple"), 5)
        XCTAssertEqual(BubbleChoiceView.widthScore("🍎"), 2)
        XCTAssertEqual(BubbleChoiceView.widthScore("12"), 2)
        // 11자짜리 한글 문장은 폭 점수가 기준을 훌쩍 넘는다
        XCTAssertTrue(BubbleChoiceView.widthScore("나는 사과를 좋아해요.") > BubbleChoiceView.MAX_WIDTH_SCORE)
    }

    /// 실제 팩에서 버블이 얼마나 쓰이는지 — 아예 안 쓰이면 의미가 없다
    func test_realContentActuallyUsesBubbles() throws {
        var bubble = 0
        var list = 0
        for tid in Subject.MATH.tracks {
            guard let t = try TestSupport.track(tid) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                let choices: [String]?
                switch q {
                case let q as Question.Mcq: choices = q.choices
                case let q as Question.ListenMcq: choices = q.choices
                case let q as Question.ListenDialog: choices = q.choices
                case let q as Question.Math: choices = q.input == "choice" ? q.choices : nil
                default: choices = nil
                }
                guard let c = choices else { continue }
                if BubbleChoiceView.fits(c) { bubble += 1 } else { list += 1 }
            } } }
        }
        print("버블로 뜨는 문제 \(bubble) · 목록으로 뜨는 문제 \(list)")
        XCTAssertTrue(bubble > 500, "버블이 전혀 안 쓰인다")
    }

    /// 저학년 수학의 숫자 답이 버블 4개로 낼 수 있는 범위인지.
    /// 정수가 아니거나 너무 크면 버블이 아니라 키패드로 가야 한다.
    func test_lowGradeMathAnswersFitBubbles() throws {
        let lowGrades = ["math_k", "math_g1", "math_g2", "math_g3"]
        var bubbleable = 0
        var keypad = 0
        for tid in lowGrades {
            guard let t = try TestSupport.track(tid) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                guard let m = q as? Question.Math else { continue }
                if m.input != "number" { continue }
                if let n = m.answer.toIntOrNull(), (0...200).contains(n), m.unit.isEmpty {
                    // 버블로 낼 문제 — 오답 3개를 실제로 만들 수 있어야 한다
                    let opts = MiniGames.wrongNumbers(n, 4)
                    XCTAssertEqual(opts.count, 4, "\(m.id): 보기 4개를 못 만듦")
                    XCTAssertEqual(Set(opts).count, 4, "\(m.id): 보기 중복")
                    XCTAssertTrue(opts.contains(n), "\(m.id): 정답이 보기에 없음")
                    bubbleable += 1
                } else {
                    keypad += 1
                }
            } } }
        }
        print("저학년 숫자문제 — 버블 \(bubbleable) · 키패드 \(keypad)")
        XCTAssertTrue(bubbleable > 300, "저학년인데 버블로 낼 문제가 거의 없다")
    }

    /// 그림을 직접 조작해 답하는 문제(input=visual)가 앱이 읽을 수 있는 꼴인지.
    /// 시계는 "H:MM", 끌어서 나누기는 나눠떨어지는 정수여야 채점이 성립한다.
    func test_visualInputQuestionsAreWellFormed() throws {
        var clocks = 0, groups = 0, paints = 0, sorts = 0, lines = 0
        var angles = 0, balances = 0, bars = 0, gathers = 0
        for tid in Subject.MATH.tracks {
            guard let t = try TestSupport.track(tid) else { continue }
            for u in t.units { for l in u.lessons { for q in l.questions {
                guard let m = q as? Question.Math else { continue }
                if m.input != "visual" { continue }
                guard let v = m.visual else { XCTFail("\(m.id): visual 입력인데 그림이 없다"); continue }
                switch v.kind {
                case MathVisual.CLOCK_SET:
                    let parts = m.answer.split(":")
                    XCTAssertEqual(parts.count, 2, "\(m.id): 답이 H:MM 꼴이 아님")
                    guard parts.count == 2 else { continue }
                    guard let h = parts[0].toIntOrNull() else { XCTFail("\(m.id): 시 없음"); continue }
                    guard let mm = parts[1].toIntOrNull() else { XCTFail("\(m.id): 분 없음"); continue }
                    XCTAssertTrue((1...12).contains(h), "\(m.id): 시 범위")
                    XCTAssertTrue((0...59).contains(mm), "\(m.id): 분 범위")
                    // 손가락으로는 5분 단위까지만 맞출 수 있다
                    XCTAssertEqual(mm % 5, 0, "\(m.id): 5분 단위가 아니라 맞출 수 없다")
                    clocks += 1
                case MathVisual.GROUP:
                    guard let per = m.answer.toIntOrNull() else { XCTFail("\(m.id): 답이 정수가 아님"); continue }
                    XCTAssertTrue((2...4).contains(v.bb), "\(m.id): 묶음 수 범위")
                    XCTAssertTrue((4...18).contains(v.a), "\(m.id): 화면에 안 들어갈 만큼 많다")
                    guard v.bb != 0 else { continue }
                    XCTAssertEqual(v.a % v.bb, 0, "\(m.id): 똑같이 나눠지지 않는다")
                    XCTAssertEqual(per, v.a / v.bb, "\(m.id): 답이 몫과 다르다")
                    groups += 1
                case MathVisual.FRACTION_PAINT:
                    guard let n = m.answer.toIntOrNull() else { XCTFail("\(m.id): 답이 정수가 아님"); continue }
                    let d = v.q.toInt()
                    XCTAssertTrue((2...12).contains(d), "\(m.id): 분모 범위(조각이 너무 잘다)")
                    XCTAssertTrue(n >= 1 && n <= d, "\(m.id): 칠할 칸이 분모보다 많다")
                    XCTAssertEqual(n, v.p.toInt(), "\(m.id): 답이 분자와 다르다")
                    paints += 1
                case MathVisual.SHAPE_SORT:
                    XCTAssertEqual(v.kinds.count, v.items.count, "\(m.id): 도형 수와 바구니 지정 수가 다르다")
                    XCTAssertTrue((2...3).contains(v.labels.count), "\(m.id): 바구니가 2~3개여야 화면에 맞는다")
                    XCTAssertTrue((4...12).contains(v.items.count), "\(m.id): 도형이 너무 많다")
                    XCTAssertTrue(v.kinds.allSatisfy { v.labels.indices.contains($0) }, "\(m.id): 없는 바구니를 가리킨다")
                    // 빈 바구니가 있으면 분류가 아니라 몰아 담기가 된다
                    XCTAssertEqual(Set(v.kinds).count, v.labels.count, "\(m.id): 아무 도형도 안 들어가는 바구니가 있다")
                    // 같은 이모지가 두 번 나오면 어느 걸 옮겼는지 헷갈린다
                    XCTAssertEqual(Set(v.items).count, v.items.count, "\(m.id): 같은 도형이 중복")
                    XCTAssertEqual(m.answer.toIntOrNull(), v.items.count, "\(m.id): 답이 도형 수와 다르다")
                    sorts += 1
                case MathVisual.NUMBER_LINE_DRAG:
                    guard let t = m.answer.toDoubleOrNull() else { XCTFail("\(m.id): 답이 수가 아님"); continue }
                    XCTAssertTrue(v.q > v.p, "\(m.id): 수직선 범위가 뒤집혔다")
                    XCTAssertTrue(t >= v.p && t <= v.q, "\(m.id): 답이 수직선 밖에 있다")
                    XCTAssertTrue((2...20).contains(v.a), "\(m.id): 눈금이 없거나 너무 촘촘하다")
                    // 눈금에 딱 붙는 값만 손가락으로 만들 수 있다
                    let step = (v.q - v.p) / Double(v.a)
                    let k = ((t - v.p) / step).roundToLong()   // 자바 Math.round
                    XCTAssertTrue(abs(v.p + Double(k) * step - t) < 1e-6, "\(m.id): 눈금에 없는 값이라 짚을 수 없다")
                    // 점은 왼쪽 끝에서 시작한다 — 답이 거기면 안 움직여도 맞아 버린다
                    XCTAssertTrue(t > v.p, "\(m.id): 답이 점의 시작 위치와 같다")
                    lines += 1
                case MathVisual.ANGLE_SET:
                    guard let deg = m.answer.toIntOrNull() else { XCTFail("\(m.id): 답이 정수가 아님"); continue }
                    XCTAssertEqual(deg, v.p.toInt(), "\(m.id): 답이 그림의 각도와 다르다")
                    XCTAssertTrue((5...180).contains(deg), "\(m.id): 각도 범위")
                    // 손가락 조작은 5° 단위로 붙는다
                    XCTAssertEqual(deg % 5, 0, "\(m.id): 5° 단위가 아니라 맞출 수 없다")
                    angles += 1
                case MathVisual.BALANCE:
                    guard let x = m.answer.toIntOrNull() else { XCTFail("\(m.id): 답이 정수가 아님"); continue }
                    let right = v.p.toInt()
                    XCTAssertTrue(v.a >= 1, "\(m.id): x 상자가 없다")
                    XCTAssertEqual(v.a * x + v.bb, right, "\(m.id): 저울이 그 값에서 평형이 되지 않는다")
                    XCTAssertTrue((1...20).contains(x), "\(m.id): 손잡이 눈금 밖의 답")
                    XCTAssertTrue(right <= 45, "\(m.id): 접시에 추가 너무 많다")
                    balances += 1
                case MathVisual.BAR_BUILD:
                    let want = m.answer.split(",").map { Int($0.trim()) }
                    XCTAssertEqual(v.values.count, v.labels.count, "\(m.id): 막대 수와 이름 수가 다르다")
                    XCTAssertEqual(want, v.values.map { Optional($0.toInt()) }, "\(m.id): 답이 막대 높이와 다르다")
                    XCTAssertTrue((3...5).contains(v.values.count), "\(m.id): 막대가 3~5개여야 화면에 맞는다")
                    // 0 이면 세울 게 없고, 너무 높으면 화면 밖으로 나간다
                    XCTAssertTrue(v.values.allSatisfy { $0 >= 1 && $0 <= 9 }, "\(m.id): 막대 높이 범위")
                    XCTAssertEqual(Set(v.labels).count, v.labels.count, "\(m.id): 항목 이름 중복")
                    bars += 1
                case MathVisual.GATHER:
                    guard let ans = m.answer.toIntOrNull() else { XCTFail("\(m.id): 답이 정수가 아님"); continue }
                    XCTAssertTrue(v.bb <= v.a, "\(m.id): 상자에 담을 개수가 전체보다 많다")
                    XCTAssertTrue(v.bb >= 1, "\(m.id): 옮길 게 없다")
                    XCTAssertTrue((3...10).contains(v.a), "\(m.id): 화면에 안 들어갈 만큼 많다")
                    XCTAssertTrue(v.labels.isNotEmpty(), "\(m.id): 상자 이름이 없다")
                    // 모으기(전부 담기)면 답은 전체, 덜어내기면 답은 남은 것
                    let expect = v.bb == v.a ? v.a : v.a - v.bb
                    XCTAssertEqual(ans, expect, "\(m.id): 답이 옮긴 결과와 다르다")
                    gathers += 1
                default:
                    XCTFail("\(m.id): 조작할 수 없는 그림인데 visual 입력")
                }
            } } }
        }
        print(
            "바늘 \(clocks) · 나누기 \(groups) · 색칠 \(paints) · 분류 \(sorts) · " +
                "수직선 \(lines) · 각도 \(angles) · 저울 \(balances) · 그래프 \(bars) · 옮겨담기 \(gathers)"
        )
        XCTAssertTrue(clocks > 50, "시계 바늘 돌리기 문제가 없다")
        XCTAssertTrue(groups > 50, "끌어서 나누기 문제가 없다")
        XCTAssertTrue(paints > 40, "분수 색칠 문제가 없다")
        XCTAssertTrue(sorts > 40, "도형 분류 문제가 없다")
        XCTAssertTrue(lines > 40, "수직선 점 끌기 문제가 없다")
        XCTAssertTrue(angles > 20, "각도 만들기 문제가 없다")
        XCTAssertTrue(balances > 20, "저울 문제가 없다")
        XCTAssertTrue(bars > 20, "그래프 세우기 문제가 없다")
        XCTAssertTrue(gathers > 40, "옮겨 담기 문제가 없다")
    }

    /// 그림이 뷰 밖으로 삐져나오지 않는지 — 자리 계산에 쓰는 비율 사이의 관계를 못 박아 둔다.
    func test_drawingsStayInsideTheirView() {
        // 각: 둔각이면 변이 왼쪽으로 뻗는다. 꼭짓점에서 변 길이를 뺀 자리가 0 보다 작으면 화면 밖이다
        let cx = MathVisualView.ANGLE_CX_RATIO
        let len = MathVisualView.ANGLE_LEN_RATIO
        XCTAssertTrue(cx - len >= 0, "180°일 때 변이 왼쪽으로 삐져나온다")
        XCTAssertTrue(cx + len <= 1, "0°일 때 변이 오른쪽으로 삐져나온다")

        // 저울: 저울대 끝에 접시 절반이 더 붙는다
        let arm = BalanceScaleView.ARM_RATIO
        let pan = BalanceScaleView.PAN_RATIO
        XCTAssertTrue(arm + pan / 2 <= 0.5, "접시가 화면 밖으로 나간다")

        // 시계·분수: 원 밑에 두 줄이 붙는다. 비워 둔 공간이 둘째 줄보다 좁으면 글자가 잘린다
        let block = MathVisualView.LABEL_BLOCK_DP
        let line2 = MathVisualView.LABEL_LINE2_DP
        XCTAssertTrue(block >= line2 + 8, "둘째 줄이 뷰 밖으로 잘린다")
        XCTAssertTrue(line2 - MathVisualView.LABEL_LINE1_DP >= 14, "두 줄이 서로 겹친다")
    }

    /// 이모지 그림은 **셀 수 있으면 옮길 수도** 있어야 한다 (나눗셈 배열도 끌어다 갈라 볼 수 있게).
    func test_everyCountablePictureIsAlsoMovable() {
        let countable: Set<String> = [MathVisual.EMOJI, MathVisual.EMOJI_OP, MathVisual.ARRAY]
        XCTAssertTrue(countable.contains(MathVisual.ARRAY), "나눗셈 배열을 못 옮기면 그림을 갈라 볼 수가 없다")
        // 조작해서 답하는 그림은 전용 화면이 따로 있으므로 이 목록에 들어오면 안 된다
        XCTAssertTrue(
            countable.allSatisfy { !MathVisual.INPUT_KINDS.contains($0) },
            "조작 전용 그림이 세기·옮기기 대상에 섞였다"
        )
    }

    /// 톡톡 누를 때 나는 소리는 자주 울리므로 정답·오답 소리보다 작아야 한다
    func test_tapSoundIsQuieterThanFeedbackSounds() {
        XCTAssertTrue(Sfx.TAP_VOLUME_SCALE < 0.5, "삐약 소리가 정답 소리만큼 크다")
        XCTAssertTrue(Sfx.TAP_VOLUME_SCALE > 0, "삐약 소리가 아예 안 들린다")
    }
}
