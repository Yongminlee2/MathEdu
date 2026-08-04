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

    /** "3 / 12" — 문제 뷰가 만들어질 때 종류 라벨 옆에 채운다 */
    private var questionNo = ""

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

    private val okLines = listOf(getString(R.string.praise_correct), getString(R.string.praise_perfect), getString(R.string.praise_genius), getString(R.string.praise_nice), getString(R.string.praise_great))
    private val noLines = listOf(getString(R.string.cheer_close), getString(R.string.cheer_again), getString(R.string.cheer_next), getString(R.string.cheer_more))

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
            lessonTitle = getString(R.string.review_mode)
            if (questions.isEmpty()) {
                Toast.makeText(this, getString(R.string.lesson_no_review), Toast.LENGTH_SHORT).show()
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
                    .setTitle(getString(R.string.heart_empty_title))
                    .setMessage(getString(R.string.heart_empty_msg))
                    .setPositiveButton(getString(R.string.confirm)) { _, _ -> finish() }
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

        registerBackHandler()
        showQuestion()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown(); sfx.release()
        b.root.removeCallbacks(encourageRun)
        b.root.removeCallbacks(sleepRun)
    }

    /**
     * 뒤로가기 — 예전 방식(onBackPressed 재정의)은 안드로이드 13+ 의 예측형 뒤로가기와
     * 어긋나고 린트 오류도 난다. 콜백을 등록하는 방식으로 바꿨다.
     */
    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = confirmQuit()
            })
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setView(cuteDialogView(getString(R.string.lesson_quit_ask)))
            .setPositiveButton(getString(R.string.lesson_quit)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.lesson_continue), null).show()
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
        questionNo = "${(s.solvedCount + 1).coerceAtMost(s.totalCount)} / ${s.totalCount}"
        showHearts(if (reviewMode) null else if (db.heartsEnabled()) s.hearts else -1)
        b.questionBox.removeAllViews()
        b.btnCheck.isEnabled = false
        b.btnCheck.text = getString(R.string.lesson_check)
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

    /**
     * 고른 선택지를 **한눈에 알아보게** 표시한다.
     *
     * 선택지 넷이 원래 알록달록해서, 고른 것만 노랗게 칠하면 그냥 네 색 중 하나로 보인다.
     * 고른 것은 제 색을 유지하고 살짝 키우고, **나머지를 연한 회색으로 죽인다.**
     */
    private fun markChoice(buttons: List<Button>, picked: Int) {
        val faded = Color.parseColor("#E9E4DC")
        for ((i, btn) in buttons.withIndex()) {
            if (!btn.isEnabled) continue          // 힌트로 지운 오답은 그대로 둔다
            val on = i == picked
            btn.backgroundTintList = ColorStateList.valueOf(
                if (on) (btn.tag as? Int ?: Color.WHITE) else faded
            )
            btn.setTextColor(Color.parseColor(if (on) "#4E342E" else "#B0A89E"))
            btn.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            btn.animate().scaleX(if (on) 1.04f else 1f).scaleY(if (on) 1.04f else 1f)
                .setDuration(90).start()
        }
    }

    // ---------------- 힌트권 ----------------

    /**
     * 힌트 버튼은 **항상 누를 수 있게** 둔다.
     * 예전엔 힌트권이 0이면 비활성화라, 눌러도 아무 반응이 없고 왜 안 되는지도 몰랐다.
     * 이제는 눌렀을 때 이유를 말해 준다 (없으면 상점 안내, 4지선다가 아니면 그렇다고).
     */
    private fun refreshHintButton() {
        val n = db.itemCount("hint")
        b.btnHint.text = "$n"
        b.btnHint.isEnabled = true
        b.btnHint.alpha = if (n > 0 && choiceButtons.size >= 4 && !hintUsedHere) 1f else 0.5f
    }

    /** 오답 2개를 지워 준다 (4지선다에서만) */
    private fun useHint() {
        if (hintUsedHere) {
            Toast.makeText(this, getString(R.string.hint_already), Toast.LENGTH_SHORT).show()
            return
        }
        if (choiceButtons.size < 4 || choiceAnswer < 0) {
            Toast.makeText(this, getString(R.string.hint_choice_only), Toast.LENGTH_SHORT).show()
            return
        }
        if (db.itemCount("hint") <= 0) {
            Toast.makeText(this, getString(R.string.hint_none), Toast.LENGTH_SHORT).show()
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
        v.findViewById<TextView>(R.id.txtCountInline)?.text = questionNo
        // 입력칸(받아쓰기·영작)이 포커스를 채가면 스크롤이 아래로 끌려가 문제 위쪽이 잘린다
        v.isFocusableInTouchMode = true
        b.questionBox.addView(v)
        v.requestFocus()
        b.questionScroll.post { b.questionScroll.scrollTo(0, 0) }
        v.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                view: View, l: Int, t: Int, r: Int, bo: Int,
                ol: Int, ot: Int, or_: Int, ob: Int,
            ) {
                val free = b.questionBox.height - view.height
                view.translationY = (free / 2f).coerceIn(0f, dp(10).toFloat())
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
            com.piyak.english.model.MathVisual.CLOCK -> getString(R.string.mkind_clock)
            com.piyak.english.model.MathVisual.CLOCK_SET -> getString(R.string.mkind_clock_set)
            com.piyak.english.model.MathVisual.GROUP -> getString(R.string.mkind_group)
            com.piyak.english.model.MathVisual.FRACTION_PAINT -> getString(R.string.mkind_frac_paint)
            com.piyak.english.model.MathVisual.SHAPE_SORT -> getString(R.string.mkind_shape_sort)
            com.piyak.english.model.MathVisual.NUMBER_LINE_DRAG -> getString(R.string.mkind_numline_drag)
            com.piyak.english.model.MathVisual.ANGLE_SET -> getString(R.string.mkind_angle_set)
            com.piyak.english.model.MathVisual.BALANCE -> getString(R.string.mkind_balance)
            com.piyak.english.model.MathVisual.BAR_BUILD -> getString(R.string.mkind_bar_build)
            com.piyak.english.model.MathVisual.GATHER ->
                if (q.promptKo.contains("-")) getString(R.string.mkind_take_out) else getString(R.string.mkind_gather)
            com.piyak.english.model.MathVisual.SHAPES -> getString(R.string.mkind_shapes)
            com.piyak.english.model.MathVisual.FRACTION -> getString(R.string.mkind_fraction)
            com.piyak.english.model.MathVisual.BAR_GRAPH -> getString(R.string.mkind_bar_graph)
            com.piyak.english.model.MathVisual.NUMBER_LINE -> getString(R.string.mkind_numline)
            com.piyak.english.model.MathVisual.GEOM -> getString(R.string.mkind_geom)
            com.piyak.english.model.MathVisual.COORD3D -> getString(R.string.mkind_coord3d)
            com.piyak.english.model.MathVisual.COORD2D -> getString(R.string.mkind_coord2d)
            com.piyak.english.model.MathVisual.ANGLE -> getString(R.string.mkind_angle)
            // 배열 그림은 곱셈·나눗셈에 모두 쓰인다 — 문제 기호로 구분한다
            com.piyak.english.model.MathVisual.ARRAY ->
                if (q.promptKo.contains("÷")) getString(R.string.mkind_div) else getString(R.string.mkind_mul)
            com.piyak.english.model.MathVisual.EMOJI_OP ->
                if (q.promptKo.contains("-")) getString(R.string.mkind_sub) else getString(R.string.mkind_add)
            null -> getString(R.string.mkind_math)
            else -> getString(R.string.mkind_picture)
        }

        val visualView = v.findViewById<MathVisualView>(R.id.visual)
        if (q.visual != null) visualView.visual = q.visual else {
            visualView.visibility = View.GONE
            // 그림 없는 문제도 글만 덜렁 있지 않게.
            // 이야기 속 사물·동물(쿠키·토끼…)이 그림 사전에 있으면 진짜 일러스트를,
            // 없으면 영역 테마 이모지를 쓴다.
            val storyImg = storyArt(q.promptKo)
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
                getString(R.string.play_count_move)
            else getString(R.string.play_count_tap)
            txtCount.text = hint
            visualView.onCountChanged = { n ->
                txtCount.text = if (n == 0) hint else getString(R.string.play_count_now, n)
                if (n > 0) sfx.piyak()
            }
            v.findViewById<Button>(R.id.btnCountReset).apply {
                text = getString(if (visualView.movable) R.string.play_reset_move else R.string.play_reset_count)
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
                    submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, q.choices.getOrNull(q.answerIndex) ?: ""), q.explain)
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
                            markChoice(buttons, i)
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
                    submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, q.choices.getOrNull(q.answerIndex) ?: ""), q.explain)
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
                    submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, q.answer), q.explain)
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
                    submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, shown), q.explain)
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
                    submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, shown), q.explain)
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
        txtCount.text = getString(R.string.play_clock)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_clock)
            setOnClickListener { visualView.resetClock() }
        }

        b.btnCheck.isEnabled = false
        visualView.onClockChanged = { h, m ->
            txtCount.text = getString(R.string.play_clock_now, h, m)
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val ok = visualView.setHour == wantH && visualView.setMinute == wantM
            submitAnswer(ok, if (ok) null else getString(R.string.fb_answer_time, wantH, wantM), q.explain)
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
        txtCount.text = getString(R.string.play_group, vis.emoji, vis.bb)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_put)
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { counts ->
            val left = vis.a - counts.sum()
            val filled = "🧺 " + counts.joinToString(" · ") { it.toString() }
            txtCount.text = filled + if (left > 0) getString(R.string.play_group_left, left)
                else getString(R.string.play_group_done)
            b.btnCheck.isEnabled = counts.sum() > 0
        }
        checkAction = {
            val ok = gv.isCorrect() && gv.perGroup() == perGroupAnswer
            val why = when {
                ok -> null
                gv.counts().sum() < vis.a -> getString(R.string.fb_group_left, perGroupAnswer)
                else -> getString(R.string.fb_group_uneven, perGroupAnswer)
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
        txtCount.text = getString(R.string.play_paint)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.clear)
            setOnClickListener { visualView.clearPaint(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onPaintChanged = { n ->
            txtCount.text = getString(R.string.play_paint_now, n, denom)
            sfx.piyak()
            b.btnCheck.isEnabled = n > 0
        }
        checkAction = {
            val n = visualView.paintedCount
            val ok = n == want
            val why = when {
                ok -> null
                n < want -> getString(R.string.fb_paint_under, want - n, want, denom)
                else -> getString(R.string.fb_paint_over, n - want, want, denom)
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
        txtCount.text = getString(R.string.play_sort)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_put)
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { _ ->
            val left = gv.leftOver()
            txtCount.text = if (left > 0) getString(R.string.play_sort_left, left) else getString(R.string.play_sort_done)
            b.btnCheck.isEnabled = left == 0
        }
        checkAction = {
            val ok = gv.isCorrect()
            val bad = gv.misplaced()
            submitAnswer(ok, if (ok) null else getString(R.string.fb_sort_wrong, bad), q.explain)
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
        txtCount.text = getString(R.string.play_numline)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_move)
            setOnClickListener { visualView.resetMark(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onMarkChanged = { value ->
            txtCount.text = getString(R.string.play_numline_now, trimNum(value))
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val got = visualView.markedValue
            val ok = kotlin.math.abs(got - want) < 1e-6
            submitAnswer(ok, if (ok) null else getString(R.string.fb_answer, q.answer), q.explain)
        }
    }

    /** 각도 만들기 — 각도를 재는 대신 직접 만들어 본다 */
    private fun showAngleSet(v: View, q: Question.Math, visualView: MathVisualView) {
        val want = q.answer.trim().toIntOrNull() ?: return

        val countBox = v.findViewById<LinearLayout>(R.id.countBox)
        val txtCount = v.findViewById<TextView>(R.id.txtCount)
        countBox.visibility = View.VISIBLE
        txtCount.text = getString(R.string.play_angle)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_angle)
            setOnClickListener { visualView.resetAngle(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        visualView.onAngleChanged = { deg ->
            txtCount.text = getString(R.string.play_angle_now, deg)
            sfx.piyak()
            b.btnCheck.isEnabled = deg > 0
        }
        checkAction = {
            val got = visualView.setAngle
            val ok = got == want
            val why = when {
                ok -> null
                got < want -> getString(R.string.fb_angle_wider, want)
                else -> getString(R.string.fb_angle_narrower, want)
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
        txtCount.text = getString(R.string.play_balance)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_move)
            setOnClickListener { sv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        sv.onChanged = { x ->
            txtCount.text = if (sv.isBalanced())
                getString(R.string.play_balance_ok, x) else getString(R.string.play_balance_now, x)
            sfx.piyak()
            b.btnCheck.isEnabled = true
        }
        checkAction = {
            val ok = sv.isBalanced() && sv.guess == want
            submitAnswer(ok, if (ok) null else getString(R.string.fb_balance, want), q.explain)
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
        txtCount.text = getString(R.string.play_bar)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_bar)
            setOnClickListener { bv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        bv.onBarMoved = { sfx.piyak() }
        bv.onChanged = { vals ->
            val left = bv.wrongBars()
            txtCount.text = if (left == 0) getString(R.string.play_bar_done) else getString(R.string.play_bar_left, left)
            b.btnCheck.isEnabled = vals.any { it > 0 }
        }
        checkAction = {
            val ok = bv.isCorrect()
            submitAnswer(ok, if (ok) null else getString(R.string.fb_bar_wrong, bv.wrongBars()), q.explain)
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
        val label = vis.labels.getOrElse(0) { getString(R.string.box) }
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
        txtCount.text = getString(R.string.play_gather, vis.emoji, label)
        v.findViewById<Button>(R.id.btnCountReset).apply {
            text = getString(R.string.play_reset_gather)
            setOnClickListener { gv.reset(); b.btnCheck.isEnabled = false }
        }

        b.btnCheck.isEnabled = false
        gv.onPlace = { sfx.piyak() }
        gv.onChanged = { _ ->
            val inBox = gv.inBoxCount()
            val outside = gv.outsideCount()
            txtCount.text = if (takeAway)
                getString(R.string.play_gather_sent, inBox, outside)
            else
                getString(R.string.play_gather_now, inBox, outside)
            b.btnCheck.isEnabled = inBox > 0
        }
        checkAction = {
            val ok = gv.isCorrect()
            val why = if (ok) null else if (takeAway)
                getString(R.string.fb_gather_send, label, need, total - need)
            else
                getString(R.string.fb_gather_all, total)
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
        val name = names[Math.abs(q.promptKo.hashCode()) % names.size]
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

    /**
     * 문제 읽어주기 — **화면에 뜬 언어 그대로** 읽는다.
     * 빈칸 기호 "___" 는 그냥 읽으면 "밑줄 밑줄"이 되므로 낱말로 바꿔 준다.
     */
    private fun speakKorean(s: String) {
        tts.speakQuestion(s.replace("___", getString(R.string.tts_blank)))
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
                correct && cleared -> getString(R.string.review_cleared)
                correct -> getString(R.string.review_one_more)
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
            n == null -> getString(R.string.lesson_review_mode)
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
            b.txtResultTitle.text = getString(R.string.result_no_heart_title)
            b.txtResultStats.text = getString(R.string.result_no_heart_msg)
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
                coins = db.earnCoins(Wallet.REVIEW_BONUS, "REVIEW", getString(R.string.review_done))
                db.addBonusCountToday("review")
            }
            b.txtResultTitle.text = getString(R.string.result_review_title)
            b.txtResultStats.text = getString(R.string.result_review_stats, (s.accuracy * 100).toInt(), xp, h)
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
                    getString(R.string.result_lesson_sub, lessonTitle, s.firstTryCorrect)
                )
            }
            b.txtResultTitle.text = if (s.isPerfect) getString(R.string.result_perfect_title) else getString(R.string.result_lesson_title)
            b.txtResultStats.text =
                "$lessonTitle\n${"⭐".repeat(s.stars())}\n" +
                    getString(R.string.result_stats, (s.accuracy * 100).toInt(), xp) +
                    if (s.isPerfect) getString(R.string.result_perfect_bonus) else ""
        }
        if (coins > 0) {
            b.txtResultStats.append("\n\n" + getString(R.string.result_coins, Wallet.format(this, coins), Wallet.format(this, db.coins())))
        } else if (!reviewMode) {
            b.txtResultStats.append("\n\n" + getString(R.string.result_no_coins))
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
                sb.append("\n\n" + getString(R.string.result_skill_up, st.def.emoji, getString(st.def.titleRes), st.level))
            }
        }
        val overall = com.piyak.english.engine.Skills.overallLevel(states)
        val rank = com.piyak.english.engine.Ranks.of(overall)
        if (startRank != null && rank.titleRes != startRank!!.titleRes) {
            sb.append("\n\n" + getString(R.string.result_rank_up, rank.emoji, getString(rank.titleRes)))
        }
        val goal = db.dailyGoal()
        val todayXp = db.xpToday()
        sb.append("\n\n" + getString(R.string.result_goal, todayXp, goal))
        if (com.piyak.english.engine.DailyGoal.isDone(todayXp, goal)) {
            sb.append(getString(R.string.result_goal_done))
            // 목표 달성은 하루 한 번만 집계 + 용돈 보너스
            if (db.metaLong("goal_met_day", -1) != Db.today()) {
                db.setMeta("goal_met_day", Db.today().toString())
                db.setMeta("goals_met", (db.metaInt("goals_met") + 1).toString())
                val bonus = db.earnCoins(Wallet.DAILY_GOAL_BONUS, "GOAL", getString(R.string.goal_reached))
                if (bonus > 0) sb.append("\n" + getString(R.string.result_goal_bonus, Wallet.format(this, bonus)))
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
            Toast.makeText(this, getString(R.string.badge_earned, bd.emoji, getString(bd.titleRes)), Toast.LENGTH_LONG).show()
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
