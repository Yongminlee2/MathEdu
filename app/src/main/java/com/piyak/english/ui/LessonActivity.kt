package com.piyak.english.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.piyak.english.R
import com.piyak.english.audio.Sfx
import com.piyak.english.audio.Tts
import com.piyak.english.databinding.ActivityLessonBinding
import com.piyak.english.db.Db
import com.piyak.english.engine.Badges
import com.piyak.english.engine.Economy
import com.piyak.english.engine.LessonSession
import com.piyak.english.engine.StatsSnapshot
import com.piyak.english.engine.Wallet
import com.piyak.english.model.ContentRepo
import com.piyak.english.model.Question

class LessonActivity : AppCompatActivity() {

    private lateinit var b: ActivityLessonBinding
    private lateinit var db: Db
    private lateinit var tts: Tts
    private lateinit var sfx: Sfx

    private var session: LessonSession? = null
    private var reviewMode = false
    private var trackId = ""

    // ---- 연출 상태 ----
    /** 보기마다 다른 파스텔 — "글 목록"이 아니라 "알록달록 카드"로 보이게 */
    private val choiceTints = listOf("#FFF3D6", "#E3F4FD", "#E8F6EA", "#F3EDFB")

    /** 누르는 맛 — 눌리면 살짝 줄었다가 튕겨 돌아온다 */
    private fun bouncy(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(60).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    /** 연속 정답 수 (오답이면 조용히 리셋) */
    private var combo = 0

    /** 첫 문제는 전환 애니메이션 없이 바로 */
    private var firstQuestion = true

    /** 15초 동안 손을 안 대면 병아리가 응원한다 */
    private val encourageRun = Runnable {
        if (b.chickView.visibility == View.VISIBLE) b.chickView.encourage()
    }

    /** 45초까지 조용하면 병아리가 잠든다 — 톡 치면 삐약! 하고 일어난다 */
    private val sleepRun = Runnable {
        if (b.chickView.visibility == View.VISIBLE) b.chickView.sleep()
    }
    private var lessonId = ""
    private var lessonTitle = ""

    // 현재 문제의 답 상태
    private var checkAction: (() -> Unit)? = null
    private var speakFails = 0

    /** 실력 집계는 문제당 첫 시도만 반영 */
    private val skillRecorded = HashSet<String>()

    /** 힌트권(오답 2개 지우기)을 쓸 수 있는 현재 문제의 선택지 */
    private var choiceButtons: List<Button> = emptyList()
    private var choiceAnswer = -1
    private var hintUsedHere = false

    /** 저학년 수학은 문제를 자동으로 읽어 준다 */
    /**
     * 수학 문제를 열자마자 읽어 주지 않는다.
     * 읽어 주는 소리가 갑자기 나오면 방해가 되므로 🔊 버튼을 누를 때만 재생한다.
     */
    private val autoReadMath = false

    /** 레슨 시작 시점의 영역별 레벨·칭호 (결과 화면에서 상승분을 보여주려고 기억) */
    private var startSkillLevels: Map<String, Int> = emptyMap()
    private var startRank: com.piyak.english.engine.Rank? = null

    private fun recordSkill(q: Question, correct: Boolean) {
        if (skillRecorded.add(q.id)) db.recordSkill(q.skill, correct)
    }

    private val okLines = listOf("삐약! 정답이에요!", "완벽해요! 🐥", "역시 천재!", "삐약삐약~ 좋아요!", "굿굿! 최고예요!")
    private val noLines = listOf("아쉬워요 😢", "괜찮아요, 다시 나와요!", "삐약… 다음엔 맞혀요!", "조금만 더 힘내요!")

    /** 정답 공개 후 피드백 패널에 보여줄 낱말 그림 — 문제에서 미리 보여주면 답이 새는 유형용 */
    private var feedbackArtRes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLessonBinding.inflate(layoutInflater)
        setContentView(b.root)
        db = Db.get(this)
        sfx = Sfx(this)
        // 수학은 화면 위 상주 병아리를 뺀다 — 그림·수식과 겹치고,
        // 리액션은 정답 화면 병아리·색종이로 충분하다 (사용자 결정 2026-08-03)
        b.chickView.visibility = View.GONE
        tts = Tts(this)
        tts.rate = db.meta("tts_rate", "1.0").toFloatOrNull() ?: 1.0f

        reviewMode = intent.getStringExtra("mode") == "review"
        val questions: List<Question>
        if (reviewMode) {
            val wrongs = db.wrongList(12)
            questions = wrongs.mapNotNull { (qid, lid, tid) -> ContentRepo.findQuestion(this, tid, lid, qid) }
            lessonTitle = "오답 복습"
            if (questions.isEmpty()) {
                Toast.makeText(this, "복습할 오답이 없어요!", Toast.LENGTH_SHORT).show()
                finish(); return
            }
        } else {
            trackId = intent.getStringExtra("track") ?: ""
            lessonId = intent.getStringExtra("lesson") ?: ""
            val t = ContentRepo.track(this, trackId)
            val pair = t?.findLesson(lessonId)
            if (pair == null) { finish(); return }
            lessonTitle = pair.second.title
            questions = pair.second.questions
            if (db.heartsEnabled() && db.hearts() <= 0) {
                AlertDialog.Builder(this)
                    .setTitle("하트가 없어요 💔")
                    .setMessage("30분마다 하트가 1개씩 차요.\n오답 복습을 완료하면 하트 1개를 받을 수 있어요!")
                    .setPositiveButton("확인") { _, _ -> finish() }
                    .setCancelable(false).show()
                return
            }
        }

        session = LessonSession(
            questions,
            hearts = if (reviewMode || !db.heartsEnabled()) 99 else db.hearts(),
            useHearts = !reviewMode && db.heartsEnabled(),
        )

        db.skillStates().let { states ->
            startSkillLevels = states.associate { it.def.id to it.level }
            startRank = com.piyak.english.engine.Ranks.of(com.piyak.english.engine.Skills.overallLevel(states))
        }

        b.btnClose.setOnClickListener { confirmQuit() }
        b.btnHint.setOnClickListener { useHint() }
        setUpScratchPad()
        b.btnContinue.setOnClickListener { hideFeedback(); showQuestion() }
        b.btnCheck.setOnClickListener { checkAction?.invoke() }
        bouncy(b.btnCheck)
        bouncy(b.btnContinue)
        b.btnDone.setOnClickListener { finish() }

        // 병아리는 전 학년 상주 — "고등은 깔끔하게"를 시도했다가
        // 사용자가 "전부 귀염뽀짝"으로 결정해서 게이트를 걷어냈다

        showQuestion()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown(); sfx.release()
        b.root.removeCallbacks(encourageRun)
        b.root.removeCallbacks(sleepRun)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { confirmQuit() }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setView(cuteDialogView("레슨을 그만둘까요?\n진행 상황은 저장되지 않아요"))
            .setPositiveButton("그만두기") { _, _ -> finish() }
            .setNegativeButton("계속하기", null).show()
    }

    /** 응원 병아리가 있는 확인 대화상자 내용 (이모지 대신 진짜 일러스트) */
    private fun cuteDialogView(msg: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(4))
        }
        box.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ck_cheerup)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        })
        box.addView(TextView(this).apply {
            text = msg
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#4E342E"))
            setPadding(0, dp(10), 0, 0)
        })
        return box
    }


    // ---------------- 문제 표시 ----------------

    private fun showQuestion() {
        val s = session ?: return
        if (s.isFinished) { showResult(); return }
        val q = s.current() ?: run { showResult(); return }

        if (firstQuestion) {
            firstQuestion = false
            renderQuestion(q)
        } else {
            // 이전 문제가 왼쪽으로 미끄러져 나가고 새 문제가 오른쪽에서 들어온다
            val slide = b.questionBox.width * 0.22f
            b.questionBox.animate().translationX(-slide).alpha(0f).setDuration(90L)
                .withEndAction {
                    renderQuestion(q)
                    b.questionBox.translationX = slide
                    b.questionBox.animate().translationX(0f).alpha(1f).setDuration(110L).start()
                }.start()
        }
    }

    private fun renderQuestion(q: Question) {
        val s = session ?: return
        // 응원 타이머 재시작
        b.root.removeCallbacks(encourageRun)
        b.root.removeCallbacks(sleepRun)
        b.root.postDelayed(encourageRun, 15_000L)
        b.root.postDelayed(sleepRun, 45_000L)

        b.progressBar.progress = (s.progress * 100).toInt()
        showHearts(if (reviewMode) null else if (db.heartsEnabled()) s.hearts else -1)
        b.questionBox.removeAllViews()
        b.btnCheck.isEnabled = false
        b.btnCheck.text = "확인"
        b.btnCheck.visibility = View.VISIBLE
        checkAction = null
        speakFails = 0
        choiceButtons = emptyList()
        choiceAnswer = -1
        hintUsedHere = false
        feedbackArtRes = 0
        tts.stop()

        when (q) {
            is Question.Math -> showMath(q)
            // 수학 전용 앱 — 영어 문제 타입은 팩에 없어 도달하지 않는다
            else -> Unit
        }
        // 선택지가 만들어진 뒤에 힌트 버튼 상태를 갱신한다
        refreshHintButton()
        // 연습장은 수학 문제에서만 쓴다 (문제가 바뀌면 비운다)
        resetScratchPad(q is Question.Math)
    }

    // ---------------- 연습장 ----------------

    /** 문제 위에 겹쳐 식을 쓰거나 그림에 표시할 수 있는 판 */
    private fun setUpScratchPad() {
        b.btnScratch.setOnClickListener { showScratch(true) }
        b.btnClosePad.setOnClickListener { showScratch(false) }
        b.btnUndo.setOnClickListener { b.scratchPad.undo() }
        b.btnClearPad.setOnClickListener { b.scratchPad.clearAll() }

        fun pen(color: String) {
            b.scratchPad.eraserMode = false
            b.scratchPad.penColor = Color.parseColor(color)
            b.btnEraser.alpha = 0.5f
        }
        b.btnPenDark.setOnClickListener { pen("#3E2723") }
        b.btnPenRed.setOnClickListener { pen("#E53935") }
        b.btnPenBlue.setOnClickListener { pen("#1E88E5") }
        b.btnEraser.setOnClickListener {
            b.scratchPad.eraserMode = true
            b.btnEraser.alpha = 1f
        }
    }

    private fun showScratch(on: Boolean) {
        b.scratchPad.visibility = if (on) View.VISIBLE else View.GONE
        b.scratchBar.visibility = if (on) View.VISIBLE else View.GONE
        // 연습장을 켜면 문제 화면의 버튼을 잘못 누르지 않도록 확인 버튼을 감춘다
        b.btnCheck.visibility = if (on) View.GONE else View.VISIBLE
        if (on) {
            b.btnEraser.alpha = 0.5f
            b.scratchPad.eraserMode = false
        }
    }

    /** 다음 문제로 넘어가면 연습장을 비운다 */
    private fun resetScratchPad(show: Boolean) {
        b.scratchPad.clearAll()
        showScratch(false)
        b.btnScratch.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---------------- 힌트권 ----------------

    private fun refreshHintButton() {
        val n = db.itemCount("hint")
        b.btnHint.text = "$n"
        b.btnHint.isEnabled = n > 0 && choiceButtons.size >= 4 && !hintUsedHere
        b.btnHint.alpha = if (b.btnHint.isEnabled) 1f else 0.45f
    }

    /** 오답 2개를 지워 준다 (4지선다에서만) */
    private fun useHint() {
        if (hintUsedHere || choiceButtons.size < 4 || choiceAnswer < 0) return
        if (db.itemCount("hint") <= 0) {
            Toast.makeText(this, "힌트권이 없어요. 상점에서 살 수 있어요! 💡", Toast.LENGTH_SHORT).show()
            return
        }
        if (!db.useItem("hint")) return
        hintUsedHere = true
        val wrongIdx = choiceButtons.indices.filter { it != choiceAnswer }.shuffled().take(2)
        for (i in wrongIdx) {
            choiceButtons[i].apply {
                isEnabled = false
                alpha = 0.3f
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
        }
        sfx.piyak()
        refreshHintButton()
    }

    private fun inflate(layout: Int): View {
        val v = LayoutInflater.from(this).inflate(layout, b.questionBox, false)
        // 내용이 화면보다 짧을 때 위로 쏠리지 않도록 세로 중앙에 놓는다
        // 위쪽에 붙이되, 내용이 짧으면 조금만 내려 준다 —
        // 그냥 가운데 정렬하면 진행바와 문제 사이가 손가락 두 마디만큼 벌어진다
        (v.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity =
            android.view.Gravity.TOP
        b.questionBox.addView(v)
        v.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                view: View, l: Int, t: Int, r: Int, bo: Int,
                ol: Int, ot: Int, or_: Int, ob: Int,
            ) {
                val free = b.questionBox.height - view.height
                view.translationY = (free / 2f).coerceIn(0f, dp(28).toFloat())
            }
        })
        return v
    }

    // ---------------- 수학 ----------------

    private fun showMath(q: Question.Math) {
        val v = inflate(R.layout.view_q_math)
        // 수식이면 진짜 수학 기호로 그린다 (세로 분수·아래첨자·근호·적분 상하한)
        val txtPrompt = v.findViewById<TextView>(R.id.txtPrompt)
        val formulaView = v.findViewById<FormulaView>(R.id.formulaView)
        if (com.piyak.english.engine.Formula.looksLikeMath(q.prompt)) {
            txtPrompt.visibility = View.GONE
            formulaView.visibility = View.VISIBLE
            formulaView.setFormula(q.prompt)
        } else {
            txtPrompt.text = q.prompt
        }
        v.findViewById<TextView>(R.id.txtKind).text = when (q.visual?.kind) {
            com.piyak.english.model.MathVisual.CLOCK -> "🕐 시계 보기"
            com.piyak.english.model.MathVisual.CLOCK_SET -> "🕐 시계 바늘 돌리기"
            com.piyak.english.model.MathVisual.GROUP -> "🧺 끌어서 똑같이 나누기"
            com.piyak.english.model.MathVisual.FRACTION_PAINT -> "🍰 분수만큼 색칠하기"
            com.piyak.english.model.MathVisual.SHAPE_SORT -> "🔺 도형 분류하기"
            com.piyak.english.model.MathVisual.NUMBER_LINE_DRAG -> "📏 수직선에서 찾기"
            com.piyak.english.model.MathVisual.ANGLE_SET -> "📐 각도 만들기"
            com.piyak.english.model.MathVisual.BALANCE -> "⚖️ 저울 맞추기"
            com.piyak.english.model.MathVisual.BAR_BUILD -> "📊 그래프 세우기"
            com.piyak.english.model.MathVisual.GATHER ->
                if (q.prompt.contains("-")) "➖ 덜어내고 세기" else "➕ 모아서 세기"
            com.piyak.english.model.MathVisual.SHAPES -> "🔺 도형"
            com.piyak.english.model.MathVisual.FRACTION -> "🍰 분수"
            com.piyak.english.model.MathVisual.BAR_GRAPH -> "📊 그래프"
            com.piyak.english.model.MathVisual.NUMBER_LINE -> "📏 수직선"
            com.piyak.english.model.MathVisual.GEOM -> "📐 도형"
            com.piyak.english.model.MathVisual.COORD3D -> "🧊 공간좌표"
            com.piyak.english.model.MathVisual.COORD2D -> "📈 좌표평면"
            com.piyak.english.model.MathVisual.ANGLE -> "📐 각도"
            // 배열 그림은 곱셈·나눗셈에 모두 쓰인다 — 문제 기호로 구분한다
            com.piyak.english.model.MathVisual.ARRAY ->
                if (q.prompt.contains("÷")) "➗ 나눗셈" else "✖️ 곱셈"
            com.piyak.english.model.MathVisual.EMOJI_OP ->
                if (q.prompt.contains("-")) "➖ 빼기" else "➕ 더하기"
            null -> "🔢 수학"
            else -> "🐥 그림 문제"
        }

        val visualView = v.findViewById<MathVisualView>(R.id.visual)
        if (q.visual != null) visualView.visual = q.visual else {
            visualView.visibility = View.GONE
            // 그림 없는 문제도 글만 덜렁 있지 않게.
            // 이야기 속 사물·동물(쿠키·토끼…)이 그림 사전에 있으면 진짜 일러스트를,
            // 없으면 영역 테마 이모지를 쓴다.
            val storyImg = storyArt(q.prompt)
            if (storyImg != 0) {
                val img = android.widget.ImageView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(155), dp(155))
                        .apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = dp(6) }
                }
                PoseAnim.applyTo(img, storyImg)
                (v as? android.widget.LinearLayout)?.addView(img, 2)
                // 뿅 하고 등장
                img.scaleX = 0.7f; img.scaleY = 0.7f; img.alpha = 0f
                img.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(260L).setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
            } else {
                // 이야기 그림이 없으면 영역별 병아리 포즈 일러스트 — 이모지는 최후 폴백
                val pose = decoPose(q)
                if (pose != 0) {
                    val img = android.widget.ImageView(this).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(dp(130), dp(130))
                            .apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = dp(6) }
                    }
                    PoseAnim.applyTo(img, pose)
                    (v as? android.widget.LinearLayout)?.addView(img, 2)
                    // 뿅 하고 등장
                    img.scaleX = 0.7f; img.scaleY = 0.7f; img.alpha = 0f
                    img.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(260L).setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
                } else {
                    val deco = android.widget.TextView(this).apply {
                        text = decoArt(q)
                        textSize = 58f
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, dp(6), 0, dp(2))
                    }
                    (v as? android.widget.LinearLayout)?.addView(deco, 2)
                }
            }
        }

        // 그림을 손가락으로 하나씩 짚어 셀 수 있게 (정지 그림을 보기만 하는 것과 다르다)
        if (q.visual != null && visualView.countable) {
            val countBox = v.findViewById<LinearLayout>(R.id.countBox)
            val txtCount = v.findViewById<TextView>(R.id.txtCount)
            countBox.visibility = View.VISIBLE
            // 옮길 수 있는 그림이면 그렇다고 알려 준다 — 모르면 아무도 안 끌어 본다
            val hint = if (visualView.movable)
                "👆 톡 누르면 세어지고, 끌면 옮겨져요"
            else "👆 그림을 하나씩 짚어 세어 보세요"
            txtCount.text = hint
            visualView.onCountChanged = { n ->
                txtCount.text = if (n == 0) hint else "👆 지금까지 $n 개 세었어요"
                if (n > 0) sfx.piyak()
            }
            v.findViewById<Button>(R.id.btnCountReset).apply {
                text = if (visualView.movable) "처음으로" else "다시 세기"
                setOnClickListener { visualView.clearCount() }
            }
        }

        // 한국어로 문제를 읽어 준다 (아이용)
        val sayBtn = v.findViewById<Button>(R.id.btnSay)
        sayBtn.setOnClickListener { speakKorean(q.prompt) }
        @Suppress("KotlinConstantConditions")
        if (autoReadMath) b.root.postDelayed({ speakKorean(q.prompt) }, 350)

        // 그림 자체가 답이 되는 문제 — 키패드도 보기도 없이 손으로 만들어 낸다
        val vk = q.visual?.kind
        if (vk != null && vk in com.piyak.english.model.MathVisual.INPUT_KINDS) {
            // 연습장이 그림을 덮으면 끌거나 누를 수가 없다
            b.btnScratch.visibility = View.GONE
        }
        if (vk == com.piyak.english.model.MathVisual.CLOCK_SET) {
            showClockSet(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.GROUP) {
            showGroupDrag(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.FRACTION_PAINT) {
            showFractionPaint(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.SHAPE_SORT) {
            showShapeSort(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.NUMBER_LINE_DRAG) {
            showNumberLineDrag(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.ANGLE_SET) {
            showAngleSet(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.BALANCE) {
            showBalance(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.BAR_BUILD) {
            showBarBuild(v, q, visualView); return
        }
        if (vk == com.piyak.english.model.MathVisual.GATHER) {
            showGather(v, q, visualView); return
        }

        when (q.input) {
            // 숫자 보기는 대부분 짧으니 버블로 (만지는 재미)
            "choice" -> if (com.piyak.english.ui.game.BubbleChoiceView.fits(q.choices)) {
                val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
                grid.visibility = View.VISIBLE
                val bubbles = com.piyak.english.ui.game.BubbleChoiceView(this).apply {
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                        height = dp(230)
                        columnSpec = android.widget.GridLayout.spec(0, 2)
                    }
                }
                var selected = -1
                bubbles.onPick = { selected = it; sfx.piyak(); b.btnCheck.isEnabled = true }
                bubbles.setChoices(q.choices)
                grid.addView(bubbles)
                checkAction = {
                    val ok = selected == q.answerIndex
                    bubbles.reveal(q.answerIndex)
                    bubbles.lock()
                    submitAnswer(ok, if (ok) null else "정답: ${q.choices.getOrNull(q.answerIndex)}", q.explain)
                }
            } else {
                val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
                grid.visibility = View.VISIBLE
                var selected = -1
                val buttons = ArrayList<Button>()
                q.choices.forEachIndexed { i, c ->
                    val btn = Button(this).apply {
                        text = c
                        textSize = 19f
                        isAllCaps = false
                        setTextColor(Color.parseColor("#4E342E"))
                        val base = Color.parseColor(choiceTints[i % choiceTints.size])
                        tag = base
                        backgroundTintList = ColorStateList.valueOf(base)
                        bouncy(this)
                        layoutParams = android.widget.GridLayout.LayoutParams().apply {
                            width = 0
                            height = dp(60)
                            columnSpec = android.widget.GridLayout.spec(
                                android.widget.GridLayout.UNDEFINED, 1f
                            )
                            setMargins(dp(5), dp(5), dp(5), dp(5))
                        }
                        setOnClickListener {
                            selected = i
                            buttons.forEach {
                                it.backgroundTintList =
                                    ColorStateList.valueOf(it.tag as? Int ?: Color.WHITE)
                            }
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD54F"))
                            b.btnCheck.isEnabled = true
                        }
                    }
                    buttons.add(btn)
                    grid.addView(btn)
                }
                choiceButtons = buttons
                choiceAnswer = q.answerIndex
                checkAction = {
                    val ok = selected == q.answerIndex
                    submitAnswer(ok, if (ok) null else "정답: ${q.choices.getOrNull(q.answerIndex)}", q.explain)
                }
            }
            "text" -> {
                val edit = v.findViewById<EditText>(R.id.editAnswer)
                edit.visibility = View.VISIBLE
                edit.addTextChangedListener(SimpleWatcher { b.btnCheck.isEnabled = it.isNotBlank() })
                checkAction = {
                    val ok = com.piyak.english.engine.MathGrader.grade(
                        edit.text.toString(), q.answer, q.alts
                    )
                    submitAnswer(ok, if (ok) null else "정답: ${q.answer}", q.explain)
                }
            }
            // 저학년은 키패드 대신 버블을 탭한다 (자판을 치는 것보다 만지는 재미가 크다)
            else -> if (useBubbleForNumber(q)) {
                val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
                grid.visibility = View.VISIBLE
                val opts = com.piyak.english.engine.MiniGames
                    .wrongNumbers(q.answer.toInt(), 4)
                    .map { it.toString() }
                val answerIdx = opts.indexOf(q.answer.toInt().toString())
                val bubbles = com.piyak.english.ui.game.BubbleChoiceView(this).apply {
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                        height = dp(230)
                        columnSpec = android.widget.GridLayout.spec(0, 2)
                    }
                }
                var selected = -1
                bubbles.onPick = { selected = it; sfx.piyak(); b.btnCheck.isEnabled = true }
                bubbles.setChoices(opts)
                grid.addView(bubbles)
                checkAction = {
                    val ok = selected == answerIdx
                    bubbles.reveal(answerIdx)
                    bubbles.lock()
                    val shown = q.answer + if (q.unit.isNotEmpty()) " ${q.unit}" else ""
                    submitAnswer(ok, if (ok) null else "정답: $shown", q.explain)
                }
            } else {
                val box = v.findViewById<LinearLayout>(R.id.numberBox)
                box.visibility = View.VISIBLE
                val show = v.findViewById<TextView>(R.id.txtAnswer)
                val pad = v.findViewById<NumberPadView>(R.id.numberPad)
                pad.allowFraction = q.answer.contains("/")
                pad.allowMinus = q.answer.startsWith("-") || q.alts.any { it.startsWith("-") }
                pad.allowDecimal = q.answer.contains(".") || q.alts.any { it.contains(".") }
                pad.onChange = { s ->
                    show.text = if (s.isEmpty()) "" else s + (if (q.unit.isNotEmpty()) " ${q.unit}" else "")
                    b.btnCheck.isEnabled = s.isNotEmpty()
                }
                checkAction = {
                    val ok = com.piyak.english.engine.MathGrader.grade(pad.value, q.answer, q.alts)
                    val shown = q.answer + if (q.unit.isNotEmpty()) " ${q.unit}" else ""
                    submitAnswer(ok, if (ok) null else "정답: $shown", q.explain)
                }
            }
        }
    }

    /**
     * 시계 바늘 돌리기.
     * 시각을 읽어 숫자로 쓰는 대신 바늘을 직접 끌어 "3시 30분"을 만들어 본다.
     * 답은 "H:MM" 꼴로 저장돼 있다.
     */
    private fun showClockSet(v: View, q: Question.Math, visualView: MathVisualView) {
        val parts = q.answer.split(":")
        val wantH = (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 12).let {
            val m = it % 12; if (m == 0) 12 else m
        }
        val wantM = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "🕐 바늘을 끌어서 시각을 맞춰 보세요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "12시로"
            setOnClickListener { visualView.resetClock() }
        }

        b.btnCheck.isEnabled = false
        visualView.onClockChanged = { h, m ->
            txtCount.text = "🕐 지금 맞춘 시각: ${h}시 ${m}분"
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val ok = visualView.setHour == wantH && visualView.setMinute == wantM
            submitAnswer(ok, if (ok) null else "정답: ${wantH}시 ${wantM}분", q.explain)
        }
    }

    /**
     * 끌어다 똑같이 나누기.
     * 12 ÷ 3 을 머리로 계산하기 전에 실제로 사물을 세 묶음에 나눠 담아 본다.
     * 답은 한 묶음에 들어가는 개수.
     */
    private fun showGroupDrag(v: View, q: Question.Math, visualView: MathVisualView) {
        visualView.visibility = View.GONE
        val vis = q.visual ?: return
        val perGroupAnswer = q.answer.trim().toIntOrNull() ?: return

        val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
        grid.visibility = View.VISIBLE
        val gv = GroupDragView(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                height = dp(380)
                columnSpec = android.widget.GridLayout.spec(0, 2)
            }
        }
        gv.setRound(vis.emoji, vis.a, vis.bb)
        grid.addView(gv)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "🧺 ${vis.emoji} 를 끌어서 ${vis.bb}개의 바구니에 똑같이 나눠 담아요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "다시 담기"
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { counts ->
            val left = vis.a - counts.sum()
            txtCount.text = if (left > 0)
                "🧺 " + counts.joinToString(" · ") { "${it}개" } + "   (남은 것 ${left}개)"
            else
                "🧺 " + counts.joinToString(" · ") { "${it}개" } + "   다 담았어요!"
            b.btnCheck.isEnabled = counts.sum() > 0
        }
        checkAction = {
            val ok = gv.isCorrect() && gv.perGroup() == perGroupAnswer
            val why = when {
                ok -> null
                gv.counts().sum() < vis.a -> "아직 다 담지 않았어요. 정답: 한 바구니에 ${perGroupAnswer}개"
                else -> "바구니마다 개수가 같아야 해요. 정답: 한 바구니에 ${perGroupAnswer}개"
            }
            submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 분수만큼 색칠하기.
     * 색칠된 그림을 보고 분수를 답하는 것의 **반대 방향** — 분수를 듣고 직접 만들어 본다.
     * 답은 칠해야 하는 칸 수(분자).
     */
    private fun showFractionPaint(v: View, q: Question.Math, visualView: MathVisualView) {
        val want = q.answer.trim().toIntOrNull() ?: return
        val denom = (q.visual?.q ?: 1.0).toInt().coerceAtLeast(1)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "🍰 조각을 눌러서 색칠해 보세요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "지우기"
            setOnClickListener { visualView.clearPaint(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onPaintChanged = { n ->
            txtCount.text = "🍰 지금 $n / $denom 칸을 칠했어요"
            sfx.piyak()
            b.btnCheck.isEnabled = n > 0
        }
        checkAction = {
            val n = visualView.paintedCount
            val ok = n == want
            val why = when {
                ok -> null
                n < want -> "${want - n}칸 더 칠해야 해요. 정답: $want / $denom"
                else -> "${n - want}칸을 더 칠했어요. 정답: $want / $denom"
            }
            submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 도형 분류하기.
     * 이름이 붙은 바구니에 도형을 끌어 담는다 — 이름과 모양을 짝지어 익힌다.
     */
    private fun showShapeSort(v: View, q: Question.Math, visualView: MathVisualView) {
        visualView.visibility = View.GONE
        val vis = q.visual ?: return

        val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
        grid.visibility = View.VISIBLE
        val gv = GroupDragView(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                height = dp(380)
                columnSpec = android.widget.GridLayout.spec(0, 2)
            }
        }
        gv.setSort(vis.items, vis.kinds, vis.labels)
        grid.addView(gv)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "🔺 도형을 알맞은 바구니로 끌어 담아요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "다시 담기"
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { _ ->
            val left = gv.leftOver()
            txtCount.text = if (left > 0) "🔺 아직 ${left}개 남았어요" else "🔺 다 담았어요!"
            b.btnCheck.isEnabled = left == 0
        }
        checkAction = {
            val ok = gv.isCorrect()
            val bad = gv.misplaced()
            submitAnswer(ok, if (ok) null else "${bad}개가 다른 바구니에 들어갔어요.", q.explain)
        }
    }

    /**
     * 수직선 위의 점 끌기.
     * 수를 고르는 대신 "수직선 어디쯤인지"를 직접 짚는다 — 수의 크기 감각이 손에 남는다.
     */
    private fun showNumberLineDrag(v: View, q: Question.Math, visualView: MathVisualView) {
        val want = q.answer.trim().toDoubleOrNull() ?: return

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "📏 파란 점을 끌어서 자리를 찾아요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "처음으로"
            setOnClickListener { visualView.resetMark(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onMarkChanged = { value ->
            txtCount.text = "📏 지금 짚은 곳: ${trimNum(value)}"
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val got = visualView.markedValue
            val ok = kotlin.math.abs(got - want) < 1e-6
            submitAnswer(ok, if (ok) null else "정답: ${q.answer}", q.explain)
        }
    }

    /** 각도 만들기 — 각도를 재는 대신 직접 만들어 본다 */
    private fun showAngleSet(v: View, q: Question.Math, visualView: MathVisualView) {
        val want = q.answer.trim().toIntOrNull() ?: return

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "📐 파란 손잡이를 돌려서 각을 만들어요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "0°로"
            setOnClickListener { visualView.resetAngle(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onAngleChanged = { deg ->
            txtCount.text = "📐 지금 만든 각: ${deg}°"
            sfx.piyak()
            b.btnCheck.isEnabled = deg > 0
        }
        checkAction = {
            val got = visualView.setAngle
            val ok = got == want
            val why = when {
                ok -> null
                got < want -> "조금 더 벌려야 해요. 정답: ${want}°"
                else -> "조금 더 좁혀야 해요. 정답: ${want}°"
            }
            submitAnswer(ok, why, q.explain)
        }
    }

    /**
     * 저울 균형 맞추기.
     * `a·x + b = c` 에서 x 를 바꾸면 저울이 실시간으로 기운다 —
     * 이항을 외우기 전에 "양쪽이 같다"는 등식의 뜻을 눈으로 본다.
     */
    private fun showBalance(v: View, q: Question.Math, visualView: MathVisualView) {
        visualView.visibility = View.GONE
        val vis = q.visual ?: return
        val want = q.answer.trim().toIntOrNull() ?: return

        val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
        grid.visibility = View.VISIBLE
        val sv = BalanceScaleView(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                height = dp(360)
                columnSpec = android.widget.GridLayout.spec(0, 2)
            }
        }
        sv.setEquation(vis.a, vis.bb, vis.p.toInt())
        grid.addView(sv)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "⚖️ 손잡이를 끌어 x 를 바꿔 보세요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "처음으로"
            setOnClickListener { sv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        sv.onChanged = { x ->
            txtCount.text = if (sv.isBalanced())
                "⚖️ x = $x — 평형이에요!" else "⚖️ 지금 x = $x"
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val ok = sv.isBalanced() && sv.guess == want
            submitAnswer(ok, if (ok) null else "저울이 평형이 되는 값은 x = $want 이에요.", q.explain)
        }
    }

    /** 막대그래프 세우기 — 표를 보고 막대를 끌어 올려 그래프를 완성한다 */
    private fun showBarBuild(v: View, q: Question.Math, visualView: MathVisualView) {
        visualView.visibility = View.GONE
        val vis = q.visual ?: return

        val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
        grid.visibility = View.VISIBLE
        val bv = BarBuildView(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                height = dp(340)
                columnSpec = android.widget.GridLayout.spec(0, 2)
            }
        }
        bv.setTarget(vis.labels, vis.values.map { it.toInt() })
        grid.addView(bv)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "📊 막대를 위로 끌어 올려요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "다시 세우기"
            setOnClickListener { bv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        bv.onBarMoved = { sfx.piyak() }
        bv.onChanged = { vals ->
            val left = bv.wrongBars()
            txtCount.text = if (left == 0) "📊 다 맞췄어요!" else "📊 아직 ${left}개가 안 맞아요"
            b.btnCheck.isEnabled = vals.any { it > 0 }
        }
        checkAction = {
            val ok = bv.isCorrect()
            submitAnswer(ok, if (ok) null else "${bv.wrongBars()}개의 막대 높이가 달라요.", q.explain)
        }
    }

    /**
     * 상자로 옮겨 담기 (모으기·덜어내기).
     * `5 + 3` 을 머리로 더하기 전에 **여덟 마리를 실제로 한 상자에 모아 보고**,
     * `8 - 3` 은 **세 마리를 실제로 내보내 보고** 남은 것을 센다.
     * 답을 계산해서 쓰는 게 아니라, 옮기다 보면 답이 나온다.
     */
    private fun showGather(v: View, q: Question.Math, visualView: MathVisualView) {
        visualView.visibility = View.GONE
        val vis = q.visual ?: return
        val total = vis.a
        val need = vis.bb
        val label = vis.labels.getOrElse(0) { "상자" }
        val takeAway = need < total   // 덜어내기면 상자 밖에 남는 게 답

        val grid = v.findViewById<android.widget.GridLayout>(R.id.choicesGrid)
        grid.visibility = View.VISIBLE
        val gv = GroupDragView(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = android.widget.GridLayout.LayoutParams.MATCH_PARENT
                height = dp(360)
                columnSpec = android.widget.GridLayout.spec(0, 2)
            }
        }
        gv.setGather(vis.emoji, total, need, label)
        grid.addView(gv)

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = "👆 ${vis.emoji} 를 끌어서 $label 으로 옮겨요"
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = "다시 옮기기"
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { _ ->
            val inBox = gv.inBoxCount()
            val outside = gv.outsideCount()
            txtCount.text = if (takeAway)
                "📦 보낸 것 ${inBox}마리 · 남은 것 ${outside}마리"
            else
                "📦 모은 것 ${inBox}마리 · 아직 ${outside}마리"
            b.btnCheck.isEnabled = inBox > 0
        }
        checkAction = {
            val ok = gv.isCorrect()
            val why = if (ok) null else if (takeAway)
                "${label}으로 ${need}마리를 보내야 해요. 그러면 ${total - need}마리가 남아요."
            else
                "${total}마리를 모두 모아야 해요."
            submitAnswer(ok, why, q.explain)
        }
    }

    /** 이야기 문제 속 사물·동물 → 그림 사전 일러스트 (없으면 0) */
    private fun storyArt(prompt: String): Int {
        // 수학은 "무엇을 세는지"가 핵심이라 사물을 동물 주인공보다 먼저 찾는다
        val map = listOf(
            "쿠키" to "word_cookie", "사탕" to "word_candy", "구슬" to "word_marble",
            "딸기" to "word_strawberry", "풍선" to "word_balloon", "도토리" to "word_acorn",
            "블록" to "word_blocks", "스티커" to "word_sticker",
            "달걀" to "word_egg", "계란" to "word_egg", "우유" to "word_milk", "물고기" to "word_fish",
            "토끼" to "word_rabbit", "고양이" to "word_cat", "곰돌이" to "word_bear",
            "강아지" to "word_dog", "펭귄" to "word_penguin", "다람쥐" to "word_squirrel",
            "삐약이" to "ck_idle",
        )
        for ((ko, res) in map) {
            if (prompt.contains(ko)) {
                val id = resources.getIdentifier(res, "drawable", packageName)
                if (id != 0) return id
            }
        }
        return 0
    }

    /**
     * 그림 없는 문제의 테마 삽화. 문제 속 숫자·사물과 무관한 **분위기용**이라
     * 개수를 오해할 사물 나열은 피하고 장면 하나짜리 이모지를 쓴다.
     */
    private fun decoArt(q: Question.Math): String = when (q.skill) {
        "m_calc" -> listOf("🐥✏️", "🧮🐥", "✏️📄").random()
        "m_number" -> listOf("🔢🐥", "🐥💭", "⭐🔢").random()
        "m_shape" -> listOf("📐🐥", "🐥🔺", "✂️📐").random()
        "m_measure" -> listOf("📏🐥", "⏰🐥", "🐥⚖️").random()
        "m_data" -> listOf("📊🐥", "🐥📋", "🔍📊").random()
        "m_word" -> listOf("📖🐥", "🐥💬", "🧩📖").random()
        else -> "🐥"
    }

    /** 그림 없는 문제의 병아리 포즈 — 영역별로 어울리는 포즈를 문제마다 번갈아 쓴다 */
    private fun decoPose(q: Question.Math): Int {
        val names = when (q.skill) {
            "m_calc" -> listOf("ck_write", "ck_think")
            "m_number" -> listOf("ck_think", "ck_idle")
            "m_shape" -> listOf("ck_book", "ck_think")
            "m_measure" -> listOf("ck_think", "ck_write")
            "m_data" -> listOf("ck_book", "ck_think")
            "m_word" -> listOf("ck_book", "ck_idle")
            else -> listOf("ck_idle")
        }
        val name = names[Math.abs(q.prompt.hashCode()) % names.size]
        return resources.getIdentifier(name, "drawable", packageName)
    }

    /** 소수점 뒤 0 을 떼서 보기 좋게 (3.0 → 3) */
    private fun trimNum(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toInt().toString()
        else (Math.round(d * 1000.0) / 1000.0).toString()

    /**
     * 숫자 답 문제를 버블로 낼지 결정한다.
     * 유치원~초3 은 자판을 치는 것보다 만지는 편이 재미도 학습도 낫고,
     * 초4 이상은 직접 계산해 써 보는 게 중요하므로 키패드를 유지한다.
     */
    private fun useBubbleForNumber(q: Question.Math): Boolean {
        val lowGrade = trackId in setOf("math_k", "math_g1", "math_g2", "math_g3")
        if (!lowGrade) return false
        val n = q.answer.toIntOrNull() ?: return false   // 분수·소수는 키패드로
        return n in 0..200 && q.unit.isEmpty()
    }

    /** 한국어 TTS (수학 문제 읽어주기) */
    private fun speakKorean(s: String) {
        tts.speakKo(s.replace("___", "몇"))
    }

    // ---------------- 채점·피드백 ----------------

    /** 일반 제출 (하트·재출제 규칙 적용) */
    private fun submitAnswer(correct: Boolean, note: String?, explain: String?) {
        val s = session ?: return
        val q = s.current() ?: return
        recordSkill(q, correct)
        if (reviewMode) {
            val cleared = db.reviewOutcome(q.id, correct)
            s.submit(correct)
            val msg = when {
                correct && cleared -> "이 오답은 완전히 클리어! 💊✨"
                correct -> "좋아요! 한 번 더 맞히면 클리어!"
                else -> note
            }
            showFeedback(correct, msg, explain, penalty = false)
        } else {
            if (!correct && q !is Question.Match) db.recordWrong(q, lessonId, trackId)
            s.submit(correct)
            showFeedback(correct, note, explain, penalty = true)
        }
    }

    private fun showFeedback(correct: Boolean, note: String?, explain: String?, penalty: Boolean) {
        if (correct) sfx.correct() else if (penalty) sfx.wrong()
        // 병아리 리액션 + 콤보 (답을 냈으니 응원 타이머는 멈춘다)
        b.root.removeCallbacks(encourageRun)
        if (correct) {
            combo++
            b.celebrate.correct(combo)
            if (b.chickView.visibility == View.VISIBLE) b.chickView.cheer()
        } else {
            combo = 0
            if (penalty && b.chickView.visibility == View.VISIBLE) b.chickView.oops()
        }
        showScratch(false)   // 채점 결과를 가리지 않도록 연습장을 접는다
        // GONE 으로 없애면 그만큼 자리가 남아 문제가 아래로 밀린다 — 자리는 남겨 둔다
        b.btnCheck.visibility = View.INVISIBLE
        b.feedbackPanel.visibility = View.VISIBLE
        b.feedbackPanel.background = getDrawable(
            if (correct) R.drawable.bg_feedback_ok else R.drawable.bg_feedback_no
        )
        // 정답 공개 후에는 낱말 그림으로 한 번 더 각인 (없으면 병아리)
        val fbArt = feedbackArtRes
        PoseAnim.applyTo(
            b.imgFeedback,
            when {
                fbArt != 0 -> fbArt
                correct -> R.drawable.ck_cheer   // 날갯짓하며 축하
                else -> R.drawable.ck_sad
            }
        )
        val fbSize = dp(if (fbArt != 0) 84 else 56)
        b.imgFeedback.layoutParams = b.imgFeedback.layoutParams.apply { width = fbSize; height = fbSize }
        b.txtFeedback.text = if (correct) okLines.random() else noLines.random()
        // 패널이 아래에서 통통 올라오고, 그림이 뿅 하고 커진다
        b.feedbackPanel.translationY = dp(48).toFloat()
        b.feedbackPanel.alpha = 0f
        b.feedbackPanel.animate().translationY(0f).alpha(1f)
            .setDuration(240L).setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
        b.imgFeedback.scaleX = 0.4f; b.imgFeedback.scaleY = 0.4f
        b.imgFeedback.animate().scaleX(1f).scaleY(1f)
            .setDuration(340L).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        val detail = listOfNotNull(note, explain).joinToString("\n\n")
        if (detail.isNotEmpty()) {
            b.txtExplain.visibility = View.VISIBLE
            b.txtExplain.text = detail
            // 긴 해설은 패널 안에서 스크롤
            b.txtExplain.movementMethod = android.text.method.ScrollingMovementMethod()
            b.txtExplain.scrollTo(0, 0)
        } else b.txtExplain.visibility = View.GONE
        b.btnContinue.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (correct) "#66BB6A" else "#FF5252")
        )
        showHearts(if (reviewMode) null else if (db.heartsEnabled()) (session?.hearts ?: 0) else -1)
    }


    /**
     * 상단 하트 표시. [n] 이 null 이면 복습 모드, -1 이면 하트 기능 끔.
     * 아이콘을 레이아웃에 박아 두면 글자만 지워도 하트가 남으므로 코드에서 같이 끈다.
     */
    private fun showHearts(n: Int?) {
        val icon = if (n != null && n >= 0) R.drawable.ic_heart else 0
        b.txtHearts.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        b.txtHearts.text = when {
            n == null -> "복습"
            n < 0 -> ""
            else -> "$n"
        }
    }

    private fun hideFeedback() {
        b.feedbackPanel.visibility = View.GONE
        b.btnCheck.visibility = View.VISIBLE
    }

    // ---------------- 결과 ----------------

    private fun showResult() {
        val s = session ?: return
        tts.stop()
        b.resultPanel.visibility = View.VISIBLE

        if (s.failed) {
            b.imgResult.setImageResource(R.drawable.ck_cheerup)
            b.txtResultTitle.text = "하트가 다 떨어졌어요 💔"
            b.txtResultStats.text = "오답 복습으로 하트를 채우고\n다시 도전해 봐요!"
            db.setHearts(0)
            return
        }

        sfx.done()
        PoseAnim.applyTo(b.imgResult, if (s.isPerfect) R.drawable.ck_cheer else R.drawable.ck_clap)
        b.celebrate.finale()
        val xp = s.xpEarned()
        var coins = 0
        if (reviewMode) {
            val h = (db.hearts() + 1).coerceAtMost(db.maxHearts())
            db.setHearts(h)
            db.addXp(xp)
            db.markToday()
            // 복습 보너스는 하루 한도까지만 (파밍 방지)
            if (db.bonusCountToday("review") < Wallet.REVIEW_DAILY_LIMIT) {
                coins = db.earnCoins(Wallet.REVIEW_BONUS, "REVIEW", "오답 복습 완료")
                db.addBonusCountToday("review")
            }
            b.txtResultTitle.text = "복습 완료! 💊"
            b.txtResultStats.text = "정답률 ${(s.accuracy * 100).toInt()}% · +${xp} XP\n하트 1개 회복! ❤️ $h"
        } else {
            if (db.heartsEnabled()) db.setHearts(s.hearts)
            db.addXp(xp)
            // 코인은 이 레슨을 "처음" 깰 때만 — 다시 풀어도 0원이라 반복 파밍이 안 된다
            val firstClear = db.lessonStars(lessonId) == 0
            db.completeLesson(lessonId, trackId, s.stars(), s.accuracy)
            if (s.isPerfect) db.setMeta("perfect_count", (db.metaInt("perfect_count") + 1).toString())
            if (firstClear) {
                coins = db.earnCoins(
                    Wallet.lessonReward(s.firstTryCorrect, s.isPerfect), "LESSON",
                    "$lessonTitle (첫 시도 정답 ${s.firstTryCorrect}문제)"
                )
            }
            b.txtResultTitle.text = if (s.isPerfect) "퍼펙트! 💯" else "레슨 완료! 🎉"
            b.txtResultStats.text =
                "$lessonTitle\n${"⭐".repeat(s.stars())}\n정답률 ${(s.accuracy * 100).toInt()}% · +${xp} XP" +
                    if (s.isPerfect) " (퍼펙트 +5 포함)" else ""
        }
        if (coins > 0) {
            b.txtResultStats.append("\n\n💰 용돈 +${Wallet.format(coins)}  (지갑 ${Wallet.format(db.coins())})")
        } else if (!reviewMode) {
            b.txtResultStats.append("\n\n💰 이미 깬 레슨이라 용돈은 없어요")
        }
        b.txtResultStats.append(growthReport())
        checkBadges()
    }

    /** 결과 화면 하단: 영역 레벨업·칭호 승급·오늘의 목표 달성 알림 */
    private fun growthReport(): String {
        val states = db.skillStates()
        val sb = StringBuilder()

        for (st in states) {
            val before = startSkillLevels[st.def.id] ?: 0
            if (st.level > before) {
                sb.append("\n\n🎉 ${st.def.emoji} ${st.def.title} 실력이 Lv.${st.level} 로 올랐어요!")
            }
        }
        val overall = com.piyak.english.engine.Skills.overallLevel(states)
        val rank = com.piyak.english.engine.Ranks.of(overall)
        if (startRank != null && rank.title != startRank!!.title) {
            sb.append("\n\n👑 칭호 승급! ${rank.emoji} ${rank.title}")
        }
        val goal = db.dailyGoal()
        val todayXp = db.xpToday()
        sb.append("\n\n🎯 오늘의 목표 $todayXp / $goal XP")
        if (com.piyak.english.engine.DailyGoal.isDone(todayXp, goal)) {
            sb.append("  ✅ 달성!")
            // 목표 달성은 하루 한 번만 집계 + 용돈 보너스
            if (db.metaLong("goal_met_day", -1) != Db.today()) {
                db.setMeta("goal_met_day", Db.today().toString())
                db.setMeta("goals_met", (db.metaInt("goals_met") + 1).toString())
                val bonus = db.earnCoins(Wallet.DAILY_GOAL_BONUS, "GOAL", "오늘의 목표 달성")
                if (bonus > 0) sb.append("\n💰 목표 달성 보너스 +${Wallet.format(bonus)}")
            }
        }
        return sb.toString()
    }

    private fun checkBadges() {
        val days = db.studyDays()
        val (_, best) = Economy.streak(days, Db.today())
        val unitMap = HashMap<String, Int>()
        if (trackId.isNotEmpty()) {
            ContentRepo.track(this, trackId)?.let { t ->
                val done = db.completedLessonIds()
                unitMap[trackId] = t.units.count { u -> u.lessons.isNotEmpty() && u.lessons.all { it.id in done } }
            }
        }
        val snap = StatsSnapshot(
            lessonsDone = db.lessonsDoneCount(),
            perfectCount = db.metaInt("perfect_count"),
            xp = db.xp(),
            streakBest = best,
            placementDone = db.meta("placement_done") == "1",
            reviewCleared = db.metaInt("review_cleared"),
            unitsCompleted = unitMap,
            // 배지 판정에는 두 과목의 영역을 모두 넘긴다
            skillLevels = db.skillStates(
                com.piyak.english.engine.Skills.ALL + com.piyak.english.engine.Skills.MATH
            ).associate { it.def.id to it.level },
            goalsMet = db.metaInt("goals_met"),
        )
        val newly = Badges.check(snap, db.earnedBadges())
        for (bd in newly) {
            db.earnBadge(bd.id)
            Toast.makeText(this, "🏆 배지 획득: ${bd.emoji} ${bd.title}!", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

/** EditText 간단 워처 */
private class SimpleWatcher(val onChange: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
}
