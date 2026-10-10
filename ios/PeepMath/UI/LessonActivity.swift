import UIKit

// 문제 풀이 화면 (LessonActivity.kt 를 옮김).
// Question 은 클래스 상속(Question.Math …)이라 `is X` 는 `as? X` 로 옮겼다.
// 화면에 걸어 두는 클로저는 [weak self] — 아이폰은 화면·클로저가 서로 붙잡으면 풀리지 않는다.

final class LessonActivity: AppCompatActivity {

    private var b: ActivityLessonBinding!
    private var db: Db!
    private var tts: Tts!
    private var sfx: Sfx!

    private var session: LessonSession? = nil
    private var reviewMode = false
    private var trackId = ""

    // ---- 연출 상태 ----
    /** 보기마다 다른 파스텔 — "글 목록"이 아니라 "알록달록 카드"로 보이게 */
    private let choiceTints = ["#FFF3D6", "#E3F4FD", "#E8F6EA", "#F3EDFB"]

    /** 누르는 맛 — 눌리면 살짝 줄었다가 튕겨 돌아온다 */
    private func bouncy(_ v: View) {
        v.setOnTouchListener { view, e in
            switch e.actionMasked {
            case MotionEvent.ACTION_DOWN:
                view.animate().scaleX(0.95).scaleY(0.95).setDuration(60).start()
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL:
                view.animate().scaleX(1).scaleY(1).setDuration(140)
                    .setInterpolator(OvershootInterpolator(2)).start()
            default: break
            }
            return false
        }
    }

    /** 연속 정답 수 (오답이면 조용히 리셋) */
    private var combo = 0

    /** "3 / 12" — 문제 뷰가 만들어질 때 종류 라벨 옆에 채운다 */
    private var questionNo = ""

    /** 첫 문제는 전환 애니메이션 없이 바로 */
    private var firstQuestion = true

    /** 15초 동안 손을 안 대면 병아리가 응원한다 */
    private lazy var encourageRun = Runnable { [weak self] in
        guard let self else { return }
        if self.b.chickView.visibility == View.VISIBLE { self.b.chickView.encourage() }
    }

    /** 45초까지 조용하면 병아리가 잠든다 — 톡 치면 삐약! 하고 일어난다 */
    private lazy var sleepRun = Runnable { [weak self] in
        guard let self else { return }
        if self.b.chickView.visibility == View.VISIBLE { self.b.chickView.sleep() }
    }
    private var lessonId = ""
    private var lessonTitle = ""

    // 현재 문제의 답 상태
    private var checkAction: (() -> Void)? = nil
    private var speakFails = 0

    /** 실력 집계는 문제당 첫 시도만 반영 */
    private var skillRecorded = Set<String>()

    /** 힌트권(오답 2개 지우기)을 쓸 수 있는 현재 문제의 선택지 */
    private var choiceButtons: [Button] = []
    private var choiceAnswer = -1
    private var hintUsedHere = false

    /** 수학 문제를 열자마자 읽어 주지 않는다 — 🔊 버튼을 누를 때만 */
    private let autoReadMath = false

    /** 레슨 시작 시점의 영역별 레벨·칭호 (결과 화면에서 상승분을 보여주려고 기억) */
    private var startSkillLevels: [String: Int] = [:]
    private var startRank: Rank? = nil

    private func recordSkill(_ q: Question, _ correct: Bool) {
        if skillRecorded.insert(q.id).inserted { db.recordSkill(q.skill, correct) }
    }

    // 처음 쓸 때 리소스에서 읽는다
    private lazy var okLines: [String] = [
        getString(R.string.praise_correct), getString(R.string.praise_perfect),
        getString(R.string.praise_genius), getString(R.string.praise_nice), getString(R.string.praise_great),
    ]
    private lazy var noLines: [String] = [
        getString(R.string.cheer_close), getString(R.string.cheer_again),
        getString(R.string.cheer_next), getString(R.string.cheer_more),
    ]

    /** 정답 공개 후 피드백 패널에 보여줄 낱말 그림 — 문제에서 미리 보여주면 답이 새는 유형용 */
    private var feedbackArtRes = 0

    override func onCreate(_ savedInstanceState: SavedState?) {
        super.onCreate(savedInstanceState)
        b = ActivityLessonBinding.inflate(layoutInflater)
        setContentView(b.root)
        db = Db.get(self)
        sfx = Sfx(self)
        // 수학은 화면 위 상주 병아리를 뺀다 — 리액션은 정답 화면 병아리·색종이로 충분하다
        b.chickView.visibility = View.GONE
        tts = Tts(self)
        tts.rate = db.meta("tts_rate", "1.0").toFloatOrNull() ?? 1.0

        reviewMode = intent.getStringExtra("mode") == "review"
        let questions: [Question]
        if reviewMode {
            let wrongs = db.wrongList(12)
            questions = wrongs.compactMap { w in
                let (qid, lid, tid) = lessonTriple(w)
                return ContentRepo.findQuestion(self, tid, lid, qid)
            }
            lessonTitle = getString(R.string.review_mode)
            if questions.isEmpty {
                Toast.makeText(self, getString(R.string.lesson_no_review), Toast.LENGTH_SHORT).show()
                finish(); return
            }
        } else {
            trackId = intent.getStringExtra("track") ?? ""
            lessonId = intent.getStringExtra("lesson") ?? ""
            let t = ContentRepo.track(self, trackId)
            guard let pair = t?.findLesson(lessonId) else { finish(); return }
            let lesson = lessonSecond(pair)
            lessonTitle = lesson.title
            questions = lesson.questions
            if db.heartsEnabled() && db.hearts() <= 0 {
                AlertDialog.Builder(self)
                    .setTitle(getString(R.string.heart_empty_title))
                    .setMessage(getString(R.string.heart_empty_msg))
                    .setPositiveButton(getString(R.string.confirm)) { [weak self] _, _ in self?.finish() }
                    .setCancelable(false).show()
                return
            }
        }

        session = LessonSession(
            questions,
            hearts: (reviewMode || !db.heartsEnabled()) ? 99 : db.hearts(),
            useHearts: !reviewMode && db.heartsEnabled()
        )

        do {
            let states = db.skillStates()
            var levels: [String: Int] = [:]
            for st in states { levels[st.def.id] = st.level }
            startSkillLevels = levels
            startRank = Ranks.of(Skills.overallLevel(states))
        }

        b.btnClose.setOnClickListener { [weak self] _ in self?.confirmQuit() }
        b.btnHint.setOnClickListener { [weak self] _ in self?.useHint() }
        setUpScratchPad()
        b.btnContinue.setOnClickListener { [weak self] _ in self?.hideFeedback(); self?.showQuestion() }
        b.btnCheck.setOnClickListener { [weak self] _ in self?.checkAction?() }
        bouncy(b.btnCheck)
        bouncy(b.btnContinue)
        b.btnDone.setOnClickListener { [weak self] _ in self?.finish() }

        registerBackHandler()
        showQuestion()
    }

    override func onDestroy() {
        super.onDestroy()
        tts.shutdown(); sfx.release()
        b.root.removeCallbacks(encourageRun)
        b.root.removeCallbacks(sleepRun)
    }

    /** 뒤로가기 — 콜백을 등록하는 방식 */
    private func registerBackHandler() {
        onBackPressedDispatcher.addCallback(self, OnBackPressedCallback(true) { [weak self] _ in
            self?.confirmQuit()
        })
    }

    private func confirmQuit() {
        AlertDialog.Builder(self)
            .setView(cuteDialogView(getString(R.string.lesson_quit_ask)))
            .setPositiveButton(getString(R.string.lesson_quit)) { [weak self] _, _ in self?.finish() }
            .setNegativeButton(getString(R.string.lesson_continue), nil).show()
    }

    /** 응원 병아리가 있는 확인 대화상자 내용 (이모지 대신 진짜 일러스트) */
    private func cuteDialogView(_ msg: String) -> View {
        let box = LinearLayout(self)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(20), dp(20), dp(4))

        let img = ImageView(self)
        img.setImageResource(R.drawable.ck_cheerup)
        img.layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        box.addView(img)

        let tv = TextView(self)
        tv.text = msg
        tv.textSize = 15
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.parseColor("#4E342E"))
        tv.setPadding(0, dp(10), 0, 0)
        box.addView(tv)
        return box
    }


    // ---------------- 문제 표시 ----------------

    private func showQuestion() {
        guard let s = session else { return }
        if s.isFinished { showResult(); return }
        guard let q = s.current() else { showResult(); return }

        if firstQuestion {
            firstQuestion = false
            renderQuestion(q)
        } else {
            // 이전 문제가 왼쪽으로 미끄러져 나가고 새 문제가 오른쪽에서 들어온다
            let slide = Float(b.questionBox.width) * 0.22
            b.questionBox.animate().translationX(-slide).alpha(0).setDuration(90)
                .withEndAction { [weak self] in
                    guard let self else { return }
                    self.renderQuestion(q)
                    self.b.questionBox.translationX = slide
                    self.b.questionBox.animate().translationX(0).alpha(1).setDuration(110).start()
                }.start()
        }
    }

    private func renderQuestion(_ q: Question) {
        guard let s = session else { return }
        #if DEBUG
        defer { debugShotHook(q) }
        #endif
        // 응원 타이머 재시작
        b.root.removeCallbacks(encourageRun)
        b.root.removeCallbacks(sleepRun)
        b.root.postDelayed(encourageRun, 15_000)
        b.root.postDelayed(sleepRun, 45_000)

        b.progressBar.progress = (s.progress * 100).toInt()
        questionNo = "\((s.solvedCount + 1).coerceAtMost(s.totalCount)) / \(s.totalCount)"
        showHearts(reviewMode ? nil : (db.heartsEnabled() ? s.hearts : -1))
        b.questionBox.removeAllViews()
        b.btnCheck.isEnabled = false
        b.btnCheck.text = getString(R.string.lesson_check)
        b.btnCheck.visibility = View.VISIBLE
        checkAction = nil
        speakFails = 0
        choiceButtons = []
        choiceAnswer = -1
        hintUsedHere = false
        feedbackArtRes = 0
        tts.stop()

        if let m = q as? Question.Math {
            showMath(m)
        }
        // 수학 전용 앱 — 영어 문제 타입은 팩에 없어 도달하지 않는다

        // 선택지가 만들어진 뒤에 힌트 버튼 상태를 갱신한다
        refreshHintButton()
        // 연습장은 수학 문제에서만 쓴다 (문제가 바뀌면 비운다)
        resetScratchPad(q is Question.Math)
    }

    // ---------------- 연습장 ----------------

    /** 문제 위에 겹쳐 식을 쓰거나 그림에 표시할 수 있는 판 */
    private func setUpScratchPad() {
        b.btnScratch.setOnClickListener { [weak self] _ in self?.showScratch(true) }
        b.btnClosePad.setOnClickListener { [weak self] _ in self?.showScratch(false) }
        b.btnUndo.setOnClickListener { [weak self] _ in self?.b.scratchPad.undo() }
        b.btnClearPad.setOnClickListener { [weak self] _ in self?.b.scratchPad.clearAll() }

        let pen: (String) -> Void = { [weak self] color in
            guard let self else { return }
            self.b.scratchPad.eraserMode = false
            self.b.scratchPad.penColor = Color.parseColor(color)
            self.b.btnEraser.alpha = 0.5
        }
        b.btnPenDark.setOnClickListener { _ in pen("#3E2723") }
        b.btnPenRed.setOnClickListener { _ in pen("#E53935") }
        b.btnPenBlue.setOnClickListener { _ in pen("#1E88E5") }
        b.btnEraser.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.b.scratchPad.eraserMode = true
            self.b.btnEraser.alpha = 1
        }
    }

    private func showScratch(_ on: Bool) {
        b.scratchPad.visibility = on ? View.VISIBLE : View.GONE
        b.scratchBar.visibility = on ? View.VISIBLE : View.GONE
        // 연습장을 켜면 문제 화면의 버튼을 잘못 누르지 않도록 확인 버튼을 감춘다
        b.btnCheck.visibility = on ? View.GONE : View.VISIBLE
        if on {
            b.btnEraser.alpha = 0.5
            b.scratchPad.eraserMode = false
        }
    }

    /** 다음 문제로 넘어가면 연습장을 비운다 */
    private func resetScratchPad(_ show: Bool) {
        b.scratchPad.clearAll()
        showScratch(false)
        b.btnScratch.visibility = show ? View.VISIBLE : View.GONE
    }

    /**
     * 고른 선택지를 한눈에 알아보게 표시한다.
     * 고른 것은 제 색을 유지하고 살짝 키우고, 나머지를 연한 회색으로 죽인다.
     */
    private func markChoice(_ buttons: [Button], _ picked: Int) {
        let faded = Color.parseColor("#E9E4DC")
        for (i, btn) in buttons.enumerated() {
            if !btn.isEnabled { continue }          // 힌트로 지운 오답은 그대로 둔다
            let on = i == picked
            btn.backgroundTintList = ColorStateList.valueOf(
                on ? ((btn.tagAny as? Int) ?? Color.WHITE) : faded
            )
            btn.setTextColor(Color.parseColor(on ? "#4E342E" : "#B0A89E"))
            btn.setTypeface(nil, on ? Typeface.BOLD : Typeface.NORMAL)
            btn.animate().scaleX(on ? 1.04 : 1).scaleY(on ? 1.04 : 1)
                .setDuration(90).start()
        }
    }

    // ---------------- 힌트권 ----------------

    /** 힌트 버튼은 항상 누를 수 있게 둔다 — 못 쓰면 눌렀을 때 이유를 말해 준다 */
    private func refreshHintButton() {
        let n = db.itemCount("hint")
        b.btnHint.text = "\(n)"
        b.btnHint.isEnabled = true
        b.btnHint.alpha = (n > 0 && choiceButtons.count >= 4 && !hintUsedHere) ? 1 : 0.5
    }

    /** 오답 2개를 지워 준다 (4지선다에서만) */
    private func useHint() {
        if hintUsedHere {
            Toast.makeText(self, getString(R.string.hint_already), Toast.LENGTH_SHORT).show()
            return
        }
        if choiceButtons.count < 4 || choiceAnswer < 0 {
            Toast.makeText(self, getString(R.string.hint_choice_only), Toast.LENGTH_SHORT).show()
            return
        }
        if db.itemCount("hint") <= 0 {
            Toast.makeText(self, getString(R.string.hint_none), Toast.LENGTH_SHORT).show()
            return
        }
        if !db.useItem("hint") { return }
        hintUsedHere = true
        let wrongIdx = Array(choiceButtons.indices).filter { $0 != choiceAnswer }
            .shuffled(random: KRandom.default).take(2)
        for i in wrongIdx {
            let btn = choiceButtons[i]
            btn.isEnabled = false
            btn.alpha = 0.3
            btn.paintFlags = btn.paintFlags | Paint.STRIKE_THRU_TEXT_FLAG
        }
        sfx.piyak()
        refreshHintButton()
    }

    private func inflate(_ layout: Int) -> View {
        let v = LayoutInflater.from(self).inflate(layout, b.questionBox, false)
        // 위쪽에 붙이되, 내용이 짧으면 조금만 내려 준다
        (v.layoutParams as? FrameLayout.LayoutParams)?.gravity = Gravity.TOP
        (v.findViewById(R.id.txtCountInline) as TextView?)?.text = questionNo
        // 안드로이드는 입력칸이 포커스를 채가지 않게 문제 뷰가 포커스를 가져갔다 —
        // 아이폰 입력칸은 스스로 포커스를 잡지 않아 할 일이 없다
        v.isFocusableInTouchMode = true
        b.questionBox.addView(v)
        b.questionScroll.post { [weak self] in self?.b.questionScroll.scrollTo(0, 0) }
        v.addOnLayoutChangeListener { [weak self] view, _, _, _, _, _, _, _, _ in
            guard let self else { return }
            let free = self.b.questionBox.height - view.height
            view.translationY = (Float(free) / 2).coerceIn(0, Float(self.dp(10)))
        }
        return v
    }

    // ---------------- 수학 ----------------

    private func showMath(_ q: Question.Math) {
        let v = inflate(R.layout.view_q_math)
        // 수식이면 진짜 수학 기호로 그린다 (세로 분수·아래첨자·근호·적분 상하한)
        let txtPrompt: TextView = v.findViewById(R.id.txtPrompt)!
        let formulaView: FormulaView = v.findViewById(R.id.formulaView)!
        if Formula.looksLikeMath(q.prompt) {
            txtPrompt.visibility = View.GONE
            formulaView.visibility = View.VISIBLE
            formulaView.setFormula(q.prompt)
        } else {
            txtPrompt.text = q.prompt
        }
        let txtKind: TextView = v.findViewById(R.id.txtKind)!
        if let kind = q.visual?.kind {
            switch kind {
            case MathVisual.CLOCK: txtKind.text = getString(R.string.mkind_clock)
            case MathVisual.CLOCK_SET: txtKind.text = getString(R.string.mkind_clock_set)
            case MathVisual.GROUP: txtKind.text = getString(R.string.mkind_group)
            case MathVisual.FRACTION_PAINT: txtKind.text = getString(R.string.mkind_frac_paint)
            case MathVisual.SHAPE_SORT: txtKind.text = getString(R.string.mkind_shape_sort)
            case MathVisual.NUMBER_LINE_DRAG: txtKind.text = getString(R.string.mkind_numline_drag)
            case MathVisual.ANGLE_SET: txtKind.text = getString(R.string.mkind_angle_set)
            case MathVisual.BALANCE: txtKind.text = getString(R.string.mkind_balance)
            case MathVisual.BAR_BUILD: txtKind.text = getString(R.string.mkind_bar_build)
            case MathVisual.GATHER:
                txtKind.text = q.promptKo.contains("-") ? getString(R.string.mkind_take_out) : getString(R.string.mkind_gather)
            case MathVisual.SHAPES: txtKind.text = getString(R.string.mkind_shapes)
            case MathVisual.FRACTION: txtKind.text = getString(R.string.mkind_fraction)
            case MathVisual.BAR_GRAPH: txtKind.text = getString(R.string.mkind_bar_graph)
            case MathVisual.NUMBER_LINE: txtKind.text = getString(R.string.mkind_numline)
            case MathVisual.GEOM: txtKind.text = getString(R.string.mkind_geom)
            case MathVisual.COORD3D: txtKind.text = getString(R.string.mkind_coord3d)
            case MathVisual.COORD2D: txtKind.text = getString(R.string.mkind_coord2d)
            case MathVisual.ANGLE: txtKind.text = getString(R.string.mkind_angle)
            // 배열 그림은 곱셈·나눗셈에 모두 쓰인다 — 문제 기호로 구분한다
            case MathVisual.ARRAY:
                txtKind.text = q.promptKo.contains("÷") ? getString(R.string.mkind_div) : getString(R.string.mkind_mul)
            case MathVisual.EMOJI_OP:
                txtKind.text = q.promptKo.contains("-") ? getString(R.string.mkind_sub) : getString(R.string.mkind_add)
            default: txtKind.text = getString(R.string.mkind_picture)
            }
        } else {
            txtKind.text = getString(R.string.mkind_math)
        }

        let visualView: MathVisualView = v.findViewById(R.id.visual)!
        if let vis = q.visual { visualView.visual = vis } else {
            visualView.visibility = View.GONE
            // 그림 없는 문제도 글만 덜렁 있지 않게 — 이야기 속 사물 그림, 없으면 병아리 포즈, 최후엔 이모지
            let storyImg = storyArt(q.promptKo)
            if storyImg != 0 {
                let img = ImageView(self)
                let lp = LinearLayout.LayoutParams(dp(155), dp(155))
                lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(6)
                img.layoutParams = lp
                PoseAnim.applyTo(img, storyImg)
                (v as? LinearLayout)?.addView(img, 2)
                // 뿅 하고 등장
                img.scaleX = 0.7; img.scaleY = 0.7; img.alpha = 0
                img.animate().scaleX(1).scaleY(1).alpha(1)
                    .setDuration(260).setInterpolator(OvershootInterpolator(1.6)).start()
            } else {
                // 이야기 그림이 없으면 영역별 병아리 포즈 일러스트 — 이모지는 최후 폴백
                let pose = decoPose(q)
                if pose != 0 {
                    let img = ImageView(self)
                    let lp = LinearLayout.LayoutParams(dp(130), dp(130))
                    lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(6)
                    img.layoutParams = lp
                    PoseAnim.applyTo(img, pose)
                    (v as? LinearLayout)?.addView(img, 2)
                    // 뿅 하고 등장
                    img.scaleX = 0.7; img.scaleY = 0.7; img.alpha = 0
                    img.animate().scaleX(1).scaleY(1).alpha(1)
                        .setDuration(260).setInterpolator(OvershootInterpolator(1.6)).start()
                } else {
                    let deco = TextView(self)
                    deco.text = decoArt(q)
                    deco.textSize = 58
                    deco.gravity = Gravity.CENTER
                    deco.setPadding(0, dp(6), 0, dp(2))
                    (v as? LinearLayout)?.addView(deco, 2)
                }
            }
        }

        // 그림을 손가락으로 하나씩 짚어 셀 수 있게
        if q.visual != nil && visualView.countable {
            let countBox: LinearLayout = v.findViewById(R.id.countBox)!
            let txtCount: TextView = v.findViewById(R.id.txtCount)!
            countBox.visibility = View.VISIBLE
            // 옮길 수 있는 그림이면 그렇다고 알려 준다
            let hint = visualView.movable
                ? getString(R.string.play_count_move)
                : getString(R.string.play_count_tap)
            txtCount.text = hint
            visualView.onCountChanged = { [weak self] n in
                guard let self else { return }
                txtCount.text = n == 0 ? hint : self.getString(R.string.play_count_now, n)
                if n > 0 { self.sfx.piyak() }
            }
            let reset: Button = v.findViewById(R.id.btnCountReset)!
            reset.text = getString(visualView.movable ? R.string.play_reset_move : R.string.play_reset_count)
            reset.setOnClickListener { [weak visualView] _ in visualView?.clearCount() }
        }

        // 한국어로 문제를 읽어 준다 (아이용)
        let sayBtn: Button = v.findViewById(R.id.btnSay)!
        sayBtn.setOnClickListener { [weak self] _ in self?.speakKorean(q.prompt) }
        if autoReadMath { b.root.postDelayed({ [weak self] in self?.speakKorean(q.prompt) }, 350) }

        // 그림 자체가 답이 되는 문제 — 키패드도 보기도 없이 손으로 만들어 낸다
        let vk = q.visual?.kind
        if let vk, MathVisual.INPUT_KINDS.contains(vk) {
            // 연습장이 그림을 덮으면 끌거나 누를 수가 없다
            b.btnScratch.visibility = View.GONE
        }
        if vk == MathVisual.CLOCK_SET {
            showClockSet(v, q, visualView); return
        }
        if vk == MathVisual.GROUP {
            showGroupDrag(v, q, visualView); return
        }
        if vk == MathVisual.FRACTION_PAINT {
            showFractionPaint(v, q, visualView); return
        }
        if vk == MathVisual.SHAPE_SORT {
            showShapeSort(v, q, visualView); return
        }
        if vk == MathVisual.NUMBER_LINE_DRAG {
            showNumberLineDrag(v, q, visualView); return
        }
        if vk == MathVisual.ANGLE_SET {
            showAngleSet(v, q, visualView); return
        }
        if vk == MathVisual.BALANCE {
            showBalance(v, q, visualView); return
        }
        if vk == MathVisual.BAR_BUILD {
            showBarBuild(v, q, visualView); return
        }
        if vk == MathVisual.GATHER {
            showGather(v, q, visualView); return
        }

        switch q.input {
        // 숫자 보기는 대부분 짧으니 버블로 (만지는 재미)
        case "choice":
            if BubbleChoiceView.fits(q.choices) {
                let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
                grid.visibility = View.VISIBLE
                let bubbles = BubbleChoiceView(self)
                let lp = GridLayout.LayoutParams()
                lp.width = GridLayout.LayoutParams.MATCH_PARENT
                lp.height = dp(230)
                lp.columnSpec = GridLayout.spec(0, 2)
                bubbles.layoutParams = lp
                var selected = -1
                bubbles.onPick = { [weak self] it in selected = it; self?.sfx.piyak(); self?.b.btnCheck.isEnabled = true }
                bubbles.setChoices(q.choices)
                grid.addView(bubbles)
                checkAction = { [weak self] in
                    guard let self else { return }
                    let ok = selected == q.answerIndex
                    bubbles.reveal(q.answerIndex)
                    bubbles.lock()
                    self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, q.choices.getOrNull(q.answerIndex) ?? ""), q.explain)
                }
            } else {
                let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
                grid.visibility = View.VISIBLE
                var selected = -1
                var buttons: [Button] = []
                for (i, c) in q.choices.enumerated() {
                    let btn = Button(self)
                    btn.text = c
                    btn.textSize = 19
                    btn.isAllCaps = false
                    btn.setTextColor(Color.parseColor("#4E342E"))
                    let base = Color.parseColor(choiceTints[i % choiceTints.count])
                    btn.tagAny = base
                    btn.backgroundTintList = ColorStateList.valueOf(base)
                    bouncy(btn)
                    let lp = GridLayout.LayoutParams()
                    lp.width = 0
                    lp.height = dp(60)
                    lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, Float(1))
                    lp.setMargins(dp(5), dp(5), dp(5), dp(5))
                    btn.layoutParams = lp
                    btn.setOnClickListener { [weak self] _ in
                        guard let self else { return }
                        selected = i
                        // 다 만든 뒤의 목록을 본다 (코틀린 ArrayList 와 같게)
                        self.markChoice(buttons, i)
                        self.b.btnCheck.isEnabled = true
                    }
                    buttons.append(btn)
                    grid.addView(btn)
                }
                choiceButtons = buttons
                choiceAnswer = q.answerIndex
                checkAction = { [weak self] in
                    guard let self else { return }
                    let ok = selected == q.answerIndex
                    self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, q.choices.getOrNull(q.answerIndex) ?? ""), q.explain)
                }
            }
        case "text":
            let edit: EditText = v.findViewById(R.id.editAnswer)!
            edit.visibility = View.VISIBLE
            edit.addTextChangedListener(SimpleWatcher { [weak self] s in self?.b.btnCheck.isEnabled = s.isNotBlank() })
            checkAction = { [weak self, weak edit] in
                guard let self, let edit else { return }
                let ok = MathGrader.grade(edit.text, q.answer, q.alts)
                self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, q.answer), q.explain)
            }
        // 저학년은 키패드 대신 버블을 탭한다 (자판을 치는 것보다 만지는 재미가 크다)
        default:
            if useBubbleForNumber(q) {
                let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
                grid.visibility = View.VISIBLE
                let opts = MiniGames
                    .wrongNumbers(q.answer.toInt(), 4)
                    .map { String($0) }
                let answerIdx = opts.indexOf(String(q.answer.toInt()))
                let bubbles = BubbleChoiceView(self)
                let lp = GridLayout.LayoutParams()
                lp.width = GridLayout.LayoutParams.MATCH_PARENT
                lp.height = dp(230)
                lp.columnSpec = GridLayout.spec(0, 2)
                bubbles.layoutParams = lp
                var selected = -1
                bubbles.onPick = { [weak self] it in selected = it; self?.sfx.piyak(); self?.b.btnCheck.isEnabled = true }
                bubbles.setChoices(opts)
                grid.addView(bubbles)
                checkAction = { [weak self] in
                    guard let self else { return }
                    let ok = selected == answerIdx
                    bubbles.reveal(answerIdx)
                    bubbles.lock()
                    let shown = q.answer + (q.unit.isNotEmpty() ? " \(q.unit)" : "")
                    self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, shown), q.explain)
                }
            } else {
                let box: LinearLayout = v.findViewById(R.id.numberBox)!
                box.visibility = View.VISIBLE
                let show: TextView = v.findViewById(R.id.txtAnswer)!
                let pad: NumberPadView = v.findViewById(R.id.numberPad)!
                pad.allowFraction = q.answer.contains("/")
                pad.allowMinus = q.answer.startsWith("-") || q.alts.any { $0.startsWith("-") }
                pad.allowDecimal = q.answer.contains(".") || q.alts.any { $0.contains(".") }
                pad.onChange = { [weak self] s in
                    show.text = s.isEmpty ? "" : s + (q.unit.isNotEmpty() ? " \(q.unit)" : "")
                    self?.b.btnCheck.isEnabled = s.isNotEmpty()
                }
                checkAction = { [weak self, weak pad] in
                    guard let self, let pad else { return }
                    let ok = MathGrader.grade(pad.value, q.answer, q.alts)
                    let shown = q.answer + (q.unit.isNotEmpty() ? " \(q.unit)" : "")
                    self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, shown), q.explain)
                }
            }
        }
    }

    /**
     * 시계 바늘 돌리기.
     * 바늘을 직접 끌어 "3시 30분"을 만들어 본다. 답은 "H:MM" 꼴로 저장돼 있다.
     */
    private func showClockSet(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        let parts = q.answer.split(":")
        let wantH: Int = {
            let it = parts.getOrNull(0)?.trim().toIntOrNull() ?? 12
            let m = it % 12; return m == 0 ? 12 : m
        }()
        let wantM = parts.getOrNull(1)?.trim().toIntOrNull() ?? 0

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_clock)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_clock)
        reset.setOnClickListener { [weak visualView] _ in visualView?.resetClock() }

        b.btnCheck.isEnabled = false
        visualView.onClockChanged = { [weak self] h, m in
            guard let self else { return }
            txtCount.text = self.getString(R.string.play_clock_now, h, m)
            self.sfx.piyak()
            self.b.btnCheck.isEnabled = true
        }
        checkAction = { [weak self, weak visualView] in
            guard let self, let visualView else { return }
            let ok = visualView.setHour == wantH && visualView.setMinute == wantM
            self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer_time, wantH, wantM), q.explain)
        }
    }

    /**
     * 끌어다 똑같이 나누기.
     * 사물을 실제로 묶음에 나눠 담아 본다. 답은 한 묶음에 들어가는 개수.
     */
    private func showGroupDrag(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        visualView.visibility = View.GONE
        guard let vis = q.visual else { return }
        guard let perGroupAnswer = q.answer.trim().toIntOrNull() else { return }

        let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
        grid.visibility = View.VISIBLE
        let gv = GroupDragView(self)
        let lp = GridLayout.LayoutParams()
        lp.width = GridLayout.LayoutParams.MATCH_PARENT
        lp.height = dp(380)
        lp.columnSpec = GridLayout.spec(0, 2)
        gv.layoutParams = lp
        gv.setRound(vis.emoji, vis.a, vis.bb)
        grid.addView(gv)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_group, vis.emoji, vis.bb)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_put)
        reset.setOnClickListener { [weak self, weak gv] _ in gv?.reset(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        gv.onPlace = { [weak self] in self?.sfx.piyak() }
        gv.onChanged = { [weak self] counts in
            guard let self else { return }
            let left = vis.a - counts.sum()
            let filled = "🧺 " + counts.map { String($0) }.joined(separator: " · ")
            txtCount.text = filled + (left > 0 ? self.getString(R.string.play_group_left, left)
                : self.getString(R.string.play_group_done))
            self.b.btnCheck.isEnabled = counts.sum() > 0
        }
        checkAction = { [weak self, weak gv] in
            guard let self, let gv else { return }
            let ok = gv.isCorrect() && gv.perGroup() == perGroupAnswer
            let why: String?
            if ok { why = nil }
            else if gv.counts().sum() < vis.a { why = self.getString(R.string.fb_group_left, perGroupAnswer) }
            else { why = self.getString(R.string.fb_group_uneven, perGroupAnswer) }
            self.submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 분수만큼 색칠하기.
     * 분수를 듣고 직접 만들어 본다. 답은 칠해야 하는 칸 수(분자).
     */
    private func showFractionPaint(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        guard let want = q.answer.trim().toIntOrNull() else { return }
        let denom = (q.visual?.q ?? 1.0).toInt().coerceAtLeast(1)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_paint)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.clear)
        reset.setOnClickListener { [weak self, weak visualView] _ in visualView?.clearPaint(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        visualView.onPaintChanged = { [weak self] n in
            guard let self else { return }
            txtCount.text = self.getString(R.string.play_paint_now, n, denom)
            self.sfx.piyak()
            self.b.btnCheck.isEnabled = n > 0
        }
        checkAction = { [weak self, weak visualView] in
            guard let self, let visualView else { return }
            let n = visualView.paintedCount
            let ok = n == want
            let why: String?
            if ok { why = nil }
            else if n < want { why = self.getString(R.string.fb_paint_under, want - n, want, denom) }
            else { why = self.getString(R.string.fb_paint_over, n - want, want, denom) }
            self.submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 도형 분류하기.
     * 이름이 붙은 바구니에 도형을 끌어 담는다 — 이름과 모양을 짝지어 익힌다.
     */
    private func showShapeSort(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        visualView.visibility = View.GONE
        guard let vis = q.visual else { return }

        let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
        grid.visibility = View.VISIBLE
        let gv = GroupDragView(self)
        let lp = GridLayout.LayoutParams()
        lp.width = GridLayout.LayoutParams.MATCH_PARENT
        lp.height = dp(380)
        lp.columnSpec = GridLayout.spec(0, 2)
        gv.layoutParams = lp
        gv.setSort(vis.items, vis.kinds, vis.labels)
        grid.addView(gv)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_sort)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_put)
        reset.setOnClickListener { [weak self, weak gv] _ in gv?.reset(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        gv.onPlace = { [weak self] in self?.sfx.piyak() }
        gv.onChanged = { [weak self, weak gv] _ in
            guard let self, let gv else { return }
            let left = gv.leftOver()
            txtCount.text = left > 0 ? self.getString(R.string.play_sort_left, left) : self.getString(R.string.play_sort_done)
            self.b.btnCheck.isEnabled = left == 0
        }
        checkAction = { [weak self, weak gv] in
            guard let self, let gv else { return }
            let ok = gv.isCorrect()
            let bad = gv.misplaced()
            self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_sort_wrong, bad), q.explain)
        }
    }

    /**
     * 수직선 위의 점 끌기.
     * 수를 고르는 대신 "수직선 어디쯤인지"를 직접 짚는다.
     */
    private func showNumberLineDrag(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        guard let want = q.answer.trim().toDoubleOrNull() else { return }

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_numline)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_move)
        reset.setOnClickListener { [weak self, weak visualView] _ in visualView?.resetMark(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        visualView.onMarkChanged = { [weak self] value in
            guard let self else { return }
            txtCount.text = self.getString(R.string.play_numline_now, self.trimNum(value))
            self.sfx.piyak()
            self.b.btnCheck.isEnabled = true
        }
        checkAction = { [weak self, weak visualView] in
            guard let self, let visualView else { return }
            let got = visualView.markedValue
            let ok = Swift.abs(got - want) < 1e-6
            self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_answer, q.answer), q.explain)
        }
    }

    /** 각도 만들기 — 각도를 재는 대신 직접 만들어 본다 */
    private func showAngleSet(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        guard let want = q.answer.trim().toIntOrNull() else { return }

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_angle)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_angle)
        reset.setOnClickListener { [weak self, weak visualView] _ in visualView?.resetAngle(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        visualView.onAngleChanged = { [weak self] deg in
            guard let self else { return }
            txtCount.text = self.getString(R.string.play_angle_now, deg)
            self.sfx.piyak()
            self.b.btnCheck.isEnabled = deg > 0
        }
        checkAction = { [weak self, weak visualView] in
            guard let self, let visualView else { return }
            let got = visualView.setAngle
            let ok = got == want
            let why: String?
            if ok { why = nil }
            else if got < want { why = self.getString(R.string.fb_angle_wider, want) }
            else { why = self.getString(R.string.fb_angle_narrower, want) }
            self.submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 저울 균형 맞추기.
     * `a·x + b = c` 에서 x 를 바꾸면 저울이 실시간으로 기운다 — 등식의 뜻을 눈으로 본다.
     */
    private func showBalance(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        visualView.visibility = View.GONE
        guard let vis = q.visual else { return }
        guard let want = q.answer.trim().toIntOrNull() else { return }

        let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
        grid.visibility = View.VISIBLE
        let sv = BalanceScaleView(self)
        let lp = GridLayout.LayoutParams()
        lp.width = GridLayout.LayoutParams.MATCH_PARENT
        lp.height = dp(360)
        lp.columnSpec = GridLayout.spec(0, 2)
        sv.layoutParams = lp
        sv.setEquation(vis.a, vis.bb, vis.p.toInt())
        grid.addView(sv)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_balance)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_move)
        reset.setOnClickListener { [weak self, weak sv] _ in sv?.reset(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        sv.onChanged = { [weak self, weak sv] x in
            guard let self, let sv else { return }
            txtCount.text = sv.isBalanced()
                ? self.getString(R.string.play_balance_ok, x) : self.getString(R.string.play_balance_now, x)
            self.sfx.piyak()
            self.b.btnCheck.isEnabled = true
        }
        checkAction = { [weak self, weak sv] in
            guard let self, let sv else { return }
            let ok = sv.isBalanced() && sv.guess == want
            self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_balance, want), q.explain)
        }
    }

    /** 막대그래프 세우기 — 표를 보고 막대를 끌어 올려 그래프를 완성한다 */
    private func showBarBuild(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        visualView.visibility = View.GONE
        guard let vis = q.visual else { return }

        let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
        grid.visibility = View.VISIBLE
        let bv = BarBuildView(self)
        let lp = GridLayout.LayoutParams()
        lp.width = GridLayout.LayoutParams.MATCH_PARENT
        lp.height = dp(340)
        lp.columnSpec = GridLayout.spec(0, 2)
        bv.layoutParams = lp
        bv.setTarget(vis.labels, vis.values.map { $0.toInt() })
        grid.addView(bv)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_bar)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_bar)
        reset.setOnClickListener { [weak self, weak bv] _ in bv?.reset(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        bv.onBarMoved = { [weak self] in self?.sfx.piyak() }
        bv.onChanged = { [weak self, weak bv] vals in
            guard let self, let bv else { return }
            let left = bv.wrongBars()
            txtCount.text = left == 0 ? self.getString(R.string.play_bar_done) : self.getString(R.string.play_bar_left, left)
            self.b.btnCheck.isEnabled = vals.any { $0 > 0 }
        }
        checkAction = { [weak self, weak bv] in
            guard let self, let bv else { return }
            let ok = bv.isCorrect()
            self.submitAnswer(ok, ok ? nil : self.getString(R.string.fb_bar_wrong, bv.wrongBars()), q.explain)
        }
    }

    /**
     * 상자로 옮겨 담기 (모으기·덜어내기).
     * 덧셈은 실제로 한 상자에 모아 보고, 뺄셈은 실제로 내보내 보고 남은 것을 센다.
     */
    private func showGather(_ v: View, _ q: Question.Math, _ visualView: MathVisualView) {
        visualView.visibility = View.GONE
        guard let vis = q.visual else { return }
        let total = vis.a
        let need = vis.bb
        let label = vis.labels.getOrElse(0) { _ in getString(R.string.box) }
        let takeAway = need < total   // 덜어내기면 상자 밖에 남는 게 답

        let grid: GridLayout = v.findViewById(R.id.choicesGrid)!
        grid.visibility = View.VISIBLE
        let gv = GroupDragView(self)
        let lp = GridLayout.LayoutParams()
        lp.width = GridLayout.LayoutParams.MATCH_PARENT
        lp.height = dp(360)
        lp.columnSpec = GridLayout.spec(0, 2)
        gv.layoutParams = lp
        gv.setGather(vis.emoji, total, need, label)
        grid.addView(gv)

        let countBox: LinearLayout = v.findViewById(R.id.countBox)!
        let txtCount: TextView = v.findViewById(R.id.txtCount)!
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_gather, vis.emoji, label)
        let reset: Button = v.findViewById(R.id.btnCountReset)!
        reset.text = getString(R.string.play_reset_gather)
        reset.setOnClickListener { [weak self, weak gv] _ in gv?.reset(); self?.b.btnCheck.isEnabled = false }

        b.btnCheck.isEnabled = false
        gv.onPlace = { [weak self] in self?.sfx.piyak() }
        gv.onChanged = { [weak self, weak gv] _ in
            guard let self, let gv else { return }
            let inBox = gv.inBoxCount()
            let outside = gv.outsideCount()
            txtCount.text = takeAway
                ? self.getString(R.string.play_gather_sent, inBox, outside)
                : self.getString(R.string.play_gather_now, inBox, outside)
            self.b.btnCheck.isEnabled = inBox > 0
        }
        checkAction = { [weak self, weak gv] in
            guard let self, let gv else { return }
            let ok = gv.isCorrect()
            let why: String? = ok ? nil : (takeAway
                ? self.getString(R.string.fb_gather_send, label, need, total - need)
                : self.getString(R.string.fb_gather_all, total))
            self.submitAnswer(ok, why, q.explain)
        }
    }

    /** 이야기 문제 속 사물·동물 → 그림 사전 일러스트 (없으면 0) */
    private func storyArt(_ prompt: String) -> Int {
        // 수학은 "무엇을 세는지"가 핵심이라 사물을 동물 주인공보다 먼저 찾는다
        let map: [(String, String)] = [
            ("쿠키", "word_cookie"), ("사탕", "word_candy"), ("구슬", "word_marble"),
            ("딸기", "word_strawberry"), ("풍선", "word_balloon"), ("도토리", "word_acorn"),
            ("블록", "word_blocks"), ("스티커", "word_sticker"),
            ("달걀", "word_egg"), ("계란", "word_egg"), ("우유", "word_milk"), ("물고기", "word_fish"),
            ("토끼", "word_rabbit"), ("고양이", "word_cat"), ("곰돌이", "word_bear"),
            ("강아지", "word_dog"), ("펭귄", "word_penguin"), ("다람쥐", "word_squirrel"),
            ("삐약이", "ck_idle"),
        ]
        for (ko, res) in map {
            if prompt.contains(ko) {
                let id = resources.getIdentifier(res, "drawable", packageName)
                if id != 0 { return id }
            }
        }
        return 0
    }

    /** 그림 없는 문제의 테마 삽화 — 개수를 오해할 사물 나열은 피하고 장면 하나짜리 이모지 */
    private func decoArt(_ q: Question.Math) -> String {
        switch q.skill {
        case "m_calc": return ["🐥✏️", "🧮🐥", "✏️📄"].random()
        case "m_number": return ["🔢🐥", "🐥💭", "⭐🔢"].random()
        case "m_shape": return ["📐🐥", "🐥🔺", "✂️📐"].random()
        case "m_measure": return ["📏🐥", "⏰🐥", "🐥⚖️"].random()
        case "m_data": return ["📊🐥", "🐥📋", "🔍📊"].random()
        case "m_word": return ["📖🐥", "🐥💬", "🧩📖"].random()
        default: return "🐥"
        }
    }

    /** 그림 없는 문제의 병아리 포즈 — 영역별로 어울리는 포즈를 문제마다 번갈아 쓴다 */
    private func decoPose(_ q: Question.Math) -> Int {
        let names: [String]
        switch q.skill {
        case "m_calc": names = ["ck_write", "ck_think"]
        case "m_number": names = ["ck_think", "ck_idle"]
        case "m_shape": names = ["ck_book", "ck_think"]
        case "m_measure": names = ["ck_think", "ck_write"]
        case "m_data": names = ["ck_book", "ck_think"]
        case "m_word": names = ["ck_book", "ck_idle"]
        default: names = ["ck_idle"]
        }
        // 자바 String.hashCode 와 같은 값으로 골라 안드로이드와 같은 포즈가 나온다
        let name = names[Swift.abs(Int(javaStringHash(q.promptKo))) % names.count]
        return resources.getIdentifier(name, "drawable", packageName)
    }

    /** 소수점 뒤 0 을 떼서 보기 좋게 (3.0 → 3) */
    private func trimNum(_ d: Double) -> String {
        if d == d.rounded(.down) && !d.isInfinite { return String(d.toInt()) }
        // Math.round(x) = floor(x + 0.5)
        return JavaFormat.kotlinFloatString((d * 1000.0 + 0.5).rounded(.down) / 1000.0)
    }

    /**
     * 숫자 답 문제를 버블로 낼지 결정한다.
     * 유치원~초3 은 만지는 편이 낫고, 초4 이상은 직접 계산해 써 보도록 키패드를 유지한다.
     */
    private func useBubbleForNumber(_ q: Question.Math) -> Bool {
        let lowGrade: Set<String> = ["math_k", "math_g1", "math_g2", "math_g3"]
        if !lowGrade.contains(trackId) { return false }
        guard let n = q.answer.toIntOrNull() else { return false }   // 분수·소수는 키패드로
        return (0...200).contains(n) && q.unit.isEmpty
    }

    /**
     * 문제 읽어주기 — 화면에 뜬 언어 그대로 읽는다.
     * 빈칸 기호 "___" 는 낱말로 바꿔 준다.
     */
    private func speakKorean(_ s: String) {
        tts.speakQuestion(s.replace("___", getString(R.string.tts_blank)))
    }

    // ---------------- 채점·피드백 ----------------

    /** 일반 제출 (하트·재출제 규칙 적용) */
    private func submitAnswer(_ correct: Bool, _ note: String?, _ explain: String?) {
        guard let s = session else { return }
        guard let q = s.current() else { return }
        recordSkill(q, correct)
        if reviewMode {
            let cleared = db.reviewOutcome(q.id, correct)
            _ = s.submit(correct)
            let msg: String?
            if correct && cleared { msg = getString(R.string.review_cleared) }
            else if correct { msg = getString(R.string.review_one_more) }
            else { msg = note }
            showFeedback(correct, msg, explain, penalty: false)
        } else {
            if !correct && !(q is Question.Match) { db.recordWrong(q, lessonId, trackId) }
            _ = s.submit(correct)
            showFeedback(correct, note, explain, penalty: true)
        }
    }

    private func showFeedback(_ correct: Bool, _ note: String?, _ explain: String?, penalty: Bool) {
        if correct { sfx.correct() } else if penalty { sfx.wrong() }
        // 병아리 리액션 + 콤보 (답을 냈으니 응원 타이머는 멈춘다)
        b.root.removeCallbacks(encourageRun)
        if correct {
            combo += 1
            b.celebrate.correct(combo)
            if b.chickView.visibility == View.VISIBLE { b.chickView.cheer() }
        } else {
            combo = 0
            if penalty && b.chickView.visibility == View.VISIBLE { b.chickView.oops() }
        }
        showScratch(false)   // 채점 결과를 가리지 않도록 연습장을 접는다
        // GONE 으로 없애면 그만큼 자리가 남아 문제가 아래로 밀린다 — 자리는 남겨 둔다
        b.btnCheck.visibility = View.INVISIBLE
        b.feedbackPanel.visibility = View.VISIBLE
        b.feedbackPanel.background = getDrawable(
            correct ? R.drawable.bg_feedback_ok : R.drawable.bg_feedback_no
        )
        // 정답 공개 후에는 낱말 그림으로 한 번 더 각인 (없으면 병아리)
        let fbArt = feedbackArtRes
        PoseAnim.applyTo(
            b.imgFeedback,
            fbArt != 0 ? fbArt : (correct ? R.drawable.ck_cheer : R.drawable.ck_sad)   // 날갯짓하며 축하
        )
        let fbSize = dp(fbArt != 0 ? 84 : 56)
        if let lp = b.imgFeedback.layoutParams {
            lp.width = fbSize; lp.height = fbSize
            b.imgFeedback.layoutParams = lp
        }
        b.txtFeedback.text = correct ? okLines.random() : noLines.random()
        // 패널이 아래에서 통통 올라오고, 그림이 뿅 하고 커진다
        b.feedbackPanel.translationY = Float(dp(48))
        b.feedbackPanel.alpha = 0
        b.feedbackPanel.animate().translationY(0).alpha(1)
            .setDuration(240).setInterpolator(OvershootInterpolator(1.1)).start()
        b.imgFeedback.scaleX = 0.4; b.imgFeedback.scaleY = 0.4
        b.imgFeedback.animate().scaleX(1).scaleY(1)
            .setDuration(340).setInterpolator(OvershootInterpolator(2.2)).start()
        let detail = [note, explain].compactMap { $0 }.joined(separator: "\n\n")
        if !detail.isEmpty {
            b.txtExplain.visibility = View.VISIBLE
            b.txtExplain.text = detail
            // 긴 해설은 패널 안에서 스크롤
            b.txtExplain.movementMethod = ScrollingMovementMethod()
            b.txtExplain.scrollTo(0, 0)
        } else { b.txtExplain.visibility = View.GONE }
        b.btnContinue.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(correct ? "#66BB6A" : "#FF5252")
        )
        showHearts(reviewMode ? nil : (db.heartsEnabled() ? (session?.hearts ?? 0) : -1))
    }


    /**
     * 상단 하트 표시. n 이 nil 이면 복습 모드, -1 이면 하트 기능 끔.
     * 아이콘도 코드에서 같이 끈다.
     */
    private func showHearts(_ n: Int?) {
        let icon = (n != nil && n! >= 0) ? R.drawable.ic_heart : 0
        b.txtHearts.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        if let n {
            b.txtHearts.text = n < 0 ? "" : "\(n)"
        } else {
            b.txtHearts.text = getString(R.string.lesson_review_mode)
        }
    }

    private func hideFeedback() {
        b.feedbackPanel.visibility = View.GONE
        b.btnCheck.visibility = View.VISIBLE
    }

    // ---------------- 결과 ----------------

    private func showResult() {
        guard let s = session else { return }
        tts.stop()
        b.resultPanel.visibility = View.VISIBLE

        if s.failed {
            b.imgResult.setImageResource(R.drawable.ck_cheerup)
            b.txtResultTitle.text = getString(R.string.result_no_heart_title)
            b.txtResultStats.text = getString(R.string.result_no_heart_msg)
            db.setHearts(0)
            return
        }

        sfx.done()
        PoseAnim.applyTo(b.imgResult, s.isPerfect ? R.drawable.ck_cheer : R.drawable.ck_clap)
        b.celebrate.finale()
        let xp = s.xpEarned()
        var coins = 0
        if reviewMode {
            let h = (db.hearts() + 1).coerceAtMost(db.maxHearts())
            db.setHearts(h)
            db.addXp(xp)
            db.markToday()
            // 복습 보너스는 하루 한도까지만 (파밍 방지)
            if db.bonusCountToday("review") < Wallet.REVIEW_DAILY_LIMIT {
                coins = db.earnCoins(Wallet.REVIEW_BONUS, "REVIEW", getString(R.string.review_done))
                db.addBonusCountToday("review")
            }
            b.txtResultTitle.text = getString(R.string.result_review_title)
            b.txtResultStats.text = getString(R.string.result_review_stats, (s.accuracy * 100).toInt(), xp, h)
        } else {
            if db.heartsEnabled() { db.setHearts(s.hearts) }
            db.addXp(xp)
            // 코인은 이 레슨을 "처음" 깰 때만 — 다시 풀어도 0원이라 반복 파밍이 안 된다
            let firstClear = db.lessonStars(lessonId) == 0
            db.completeLesson(lessonId, trackId, s.stars(), s.accuracy)
            if s.isPerfect { db.setMeta("perfect_count", String(db.metaInt("perfect_count") + 1)) }
            if firstClear {
                coins = db.earnCoins(
                    Wallet.lessonReward(s.firstTryCorrect, s.isPerfect), "LESSON",
                    getString(R.string.result_lesson_sub, lessonTitle, s.firstTryCorrect)
                )
            }
            b.txtResultTitle.text = s.isPerfect ? getString(R.string.result_perfect_title) : getString(R.string.result_lesson_title)
            b.txtResultStats.text =
                "\(lessonTitle)\n\("⭐".repeat_(s.stars()))\n" +
                getString(R.string.result_stats, (s.accuracy * 100).toInt(), xp) +
                (s.isPerfect ? getString(R.string.result_perfect_bonus) : "")
        }
        // TextView.append → text 에 이어 붙이기
        if coins > 0 {
            b.txtResultStats.text += "\n\n" + getString(R.string.result_coins, Wallet.format(self, coins), Wallet.format(self, db.coins()))
        } else if !reviewMode {
            b.txtResultStats.text += "\n\n" + getString(R.string.result_no_coins)
        }
        b.txtResultStats.text += growthReport()
        checkBadges()
    }

    /** 결과 화면 하단: 영역 레벨업·칭호 승급·오늘의 목표 달성 알림 */
    private func growthReport() -> String {
        let states = db.skillStates()
        var sb = ""

        for st in states {
            let before = startSkillLevels[st.def.id] ?? 0
            if st.level > before {
                sb += "\n\n" + getString(R.string.result_skill_up, st.def.emoji, getString(st.def.titleRes), st.level)
            }
        }
        let overall = Skills.overallLevel(states)
        let rank = Ranks.of(overall)
        if let start = startRank, rank.titleRes != start.titleRes {
            sb += "\n\n" + getString(R.string.result_rank_up, rank.emoji, getString(rank.titleRes))
        }
        let goal = db.dailyGoal()
        let todayXp = db.xpToday()
        sb += "\n\n" + getString(R.string.result_goal, todayXp, goal)
        if DailyGoal.isDone(todayXp, goal) {
            sb += getString(R.string.result_goal_done)
            // 목표 달성은 하루 한 번만 집계 + 용돈 보너스
            if db.metaLong("goal_met_day", -1) != Db.today() {
                db.setMeta("goal_met_day", String(Db.today()))
                db.setMeta("goals_met", String(db.metaInt("goals_met") + 1))
                let bonus = db.earnCoins(Wallet.DAILY_GOAL_BONUS, "GOAL", getString(R.string.goal_reached))
                if bonus > 0 { sb += "\n" + getString(R.string.result_goal_bonus, Wallet.format(self, bonus)) }
            }
        }
        return sb
    }

    private func checkBadges() {
        let days = db.studyDays()
        let best = lessonSecond(Economy.streak(days, Db.today()))
        var unitMap: [String: Int] = [:]
        if !trackId.isEmpty {
            if let t = ContentRepo.track(self, trackId) {
                let done = db.completedLessonIds()
                unitMap[trackId] = t.units.filter { u in
                    !u.lessons.isEmpty && u.lessons.allSatisfy { done.contains($0.id) }
                }.count
            }
        }
        // 배지 판정에는 두 과목의 영역을 모두 넘긴다
        var skillLevels: [String: Int] = [:]
        for st in db.skillStates(Skills.ALL + Skills.MATH) { skillLevels[st.def.id] = st.level }
        let snap = StatsSnapshot(
            lessonsDone: db.lessonsDoneCount(),
            perfectCount: db.metaInt("perfect_count"),
            xp: db.xp(),
            streakBest: best,
            placementDone: db.meta("placement_done") == "1",
            reviewCleared: db.metaInt("review_cleared"),
            unitsCompleted: unitMap,
            skillLevels: skillLevels,
            goalsMet: db.metaInt("goals_met")
        )
        let newly = Badges.check(snap, db.earnedBadges())
        for bd in newly {
            db.earnBadge(bd.id)
            Toast.makeText(self, getString(R.string.badge_earned, bd.emoji, getString(bd.titleRes)), Toast.LENGTH_LONG).show()
        }
    }

    private func dp(_ v: Int) -> Int { Int(Float(v) * resources.displayMetrics.density) }
}

/** EditText 간단 워처 */
private struct SimpleWatcher: TextWatcher {
    let onChange: (String) -> Void
    init(_ onChange: @escaping (String) -> Void) { self.onChange = onChange }
    func afterTextChanged(_ s: String) { onChange(s) }
}

// MARK: - 이 파일 도우미

// 코틀린 Pair/Triple 을 다른 파일이 Pair 구조체로 옮겼든 튜플로 옮겼든 받는다
private func lessonSecond<A, B>(_ p: Pair<A, B>) -> B { p.second }
private func lessonSecond<A, B>(_ p: (A, B)) -> B { p.1 }
private func lessonTriple<A, B, C>(_ t: Triple<A, B, C>) -> (A, B, C) { (t.first, t.second, t.third) }
private func lessonTriple<A, B, C>(_ t: (A, B, C)) -> (A, B, C) { t }

/// 자바 String.hashCode (UTF-16 단위, 32비트 넘침)
private func javaStringHash(_ s: String) -> Int32 {
    var h: Int32 = 0
    for c in s.utf16 { h = h &* 31 &+ Int32(c) }
    return h
}

extension LessonActivity {
    #if DEBUG
    /// 스토어 스크린샷용 (DEBUG 빌드만): -answer 1 정답 화면, -scratch 1 연습장에 써 본 모습
    static var debugShotDone = false
    func debugShotHook(_ q: Question) {
        guard !LessonActivity.debugShotDone else { return }
        LessonActivity.debugShotDone = true
        if DebugLaunch.args["answer"] == "1" {
            b.root.postDelayed({ [weak self] in self?.submitAnswer(true, nil, q.explain) }, 700)
        }
        if DebugLaunch.args["scratch"] == "1" {
            b.root.postDelayed({ [weak self] in
                guard let self else { return }
                self.showScratch(true)
                self.b.root.postDelayed({ [weak self] in self?.debugScratchStrokes() }, 600)
            }, 700)
        }
    }

    private func debugScratchStrokes() {
        do {
            do {
                let pad = self.b.scratchPad
                let w = Float(pad.width), h = Float(pad.height)
                // 손글씨 흉내: 물음표 옆에 "16" 을 쓰고, 보기 16 에 빨간 동그라미
                var strokes: [[(Float, Float)]] = []
                strokes.append([(0.34, 0.175), (0.36, 0.16), (0.36, 0.225)])                  // 1
                var six: [(Float, Float)] = [(0.44, 0.16), (0.41, 0.19)]
                for k in 0...16 {
                    let a = Float(k) / 16 * 2 * Float.pi + Float.pi
                    six.append((0.425 + 0.028 * cos(a), 0.205 + 0.018 * sin(a)))
                }
                strokes.append(six)                                                           // 6
                var ring: [(Float, Float)] = []
                for k in 0...26 {
                    let a = Float(k) / 24 * 2 * Float.pi - 0.6
                    ring.append((0.265 + 0.15 * cos(a), 0.495 + 0.065 * sin(a)))
                }
                strokes.append(ring)
                for (i, st) in strokes.enumerated() {
                    if i == 2 { pad.penColor = Color.parseColor("#E53935") }
                    for (j, pt) in st.enumerated() {
                        let a = j == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE
                        _ = pad.onTouchEvent(MotionEvent(action: a, x: pt.0 * w, y: pt.1 * h, rawX: 0, rawY: 0, downTime: 0))
                    }
                    _ = pad.onTouchEvent(MotionEvent(action: MotionEvent.ACTION_UP, x: 0, y: 0, rawX: 0, rawY: 0, downTime: 0))
                }
                pad.invalidate()
            }
        }
    }
    #endif
}
