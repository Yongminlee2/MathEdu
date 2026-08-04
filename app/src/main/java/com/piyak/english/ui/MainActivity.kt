package com.piyak.english.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.piyak.english.R
import com.piyak.english.databinding.ActivityMainBinding
import com.piyak.english.db.Db
import com.piyak.english.engine.DailyGoal
import com.piyak.english.engine.Economy
import com.piyak.english.engine.Ranks
import com.piyak.english.engine.SkillState
import com.piyak.english.engine.Skills
import com.piyak.english.model.ContentRepo

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var sfx: com.piyak.english.audio.Sfx? = null
    private var subject: com.piyak.english.model.Subject = com.piyak.english.model.Subject.ENGLISH
    // 필드 초기화 시점에는 Context 가 아직 없다 — 처음 쓸 때 리소스에서 읽는다
    private val greetings by lazy { listOf(getString(R.string.home_greeting)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // 삐약수학은 수학 전용 앱 — 과목 대문 없이 항상 수학
        subject = com.piyak.english.model.Subject.MATH

        b.txtGreeting.text = greetings.random()
        b.btnSwitchSubject.visibility = View.GONE
        b.bannerPlacement.setOnClickListener {
            startActivity(
                Intent(this, PlacementActivity::class.java).putExtra("subject", subject.id)
            )
        }
        b.btnReview.setOnClickListener {
            val db = Db.get(this)
            if (db.wrongCount() == 0) {
                android.widget.Toast.makeText(this, getString(R.string.home_no_review), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, LessonActivity::class.java).putExtra("mode", "review"))
            }
        }
        b.btnStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.txtGoalEdit.setOnClickListener { pickDailyGoal() }

        // 살아있는 마스코트 — 천천히 숨쉬고, 톡 치면 삐약! 하고 점프
        sfx = com.piyak.english.audio.Sfx(this)
        PoseAnim.applyTo(b.imgMascot, R.drawable.ck_idle)   // 가끔 눈을 깜빡인다
        android.animation.ObjectAnimator.ofPropertyValuesHolder(
            b.imgMascot,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.045f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.045f),
        ).apply {
            duration = 1300L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
        b.imgMascot.setOnClickListener {
            sfx?.piyak()
            b.imgMascot.animate().translationY(-dp(10f)).setDuration(140L)
                .withEndAction {
                    b.imgMascot.animate().translationY(0f).setDuration(260L)
                        .setInterpolator(android.view.animation.BounceInterpolator()).start()
                }.start()
        }
        b.cardWallet.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }
    }

    private fun pickDailyGoal() {
        val db = Db.get(this)
        val labels = DailyGoal.OPTIONS.map { xp ->
            val note = when (xp) {
                20 -> getString(R.string.goal_light)
                50 -> getString(R.string.goal_normal)
                100 -> getString(R.string.goal_hard)
                else -> getString(R.string.goal_beast)
            }
            "$xp XP — $note"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.goal_set_title))
            .setItems(labels) { _, i ->
                db.setDailyGoal(DailyGoal.OPTIONS[i])
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 시간대에 따라 풍경이 바뀐다 — 아침 하늘, 낮 풀밭, 저녁 교실, 밤엔 별
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val banner = when (hour) {
            in 6..10 -> R.drawable.banner_sky
            in 11..16 -> R.drawable.banner_grass
            in 17..20 -> R.drawable.banner_class
            else -> R.drawable.banner_night
        }
        b.imgBanner.setImageResource(banner)
        b.imgBanner.clipToOutline = true
        b.imgBanner.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) =
                o.setRoundRect(0, 0, v.width, v.height, dp(18f))
        }
        refresh()
    }

    private fun refresh() {
        val db = Db.get(this)
        val xp = db.xp()
        val lv = Economy.levelFor(xp)
        b.txtHearts.text = if (db.heartsEnabled()) "${db.hearts()}" else ""
        b.txtHearts.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (db.heartsEnabled()) R.drawable.ic_heart else 0, 0, 0, 0
        )
        val (streak, _) = Economy.streak(db.studyDays(), Db.today())
        b.txtStreak.text = "$streak"
        b.txtLevel.text = "Lv.$lv"
        b.xpBar.progress = (Economy.levelProgress(xp) * 100).toInt()
        b.txtReview.text = getString(R.string.home_wrong_count, db.wrongCount())
        // 배치고사 배너: 과목별로 아직 안 본 경우에만
        val placedKey = if (subject == com.piyak.english.model.Subject.MATH)
            "math_placement_done" else "placement_done"
        b.bannerPlacement.visibility = if (db.meta(placedKey) == "1") View.GONE else View.VISIBLE
        b.txtPlacement.text = if (subject == com.piyak.english.model.Subject.MATH)
            getString(R.string.home_placement_math)
        else getString(R.string.home_placement_math)

        // 상점에서 산 테마 배경 적용
        val theme = Color.parseColor(db.themeColor())
        b.root.setBackgroundColor(theme)
        window.statusBarColor = theme

        b.txtCoins.text = com.piyak.english.engine.Wallet.format(this, db.coins())
        buildGrowth(db)
        buildTrackCards(db)
    }

    /** 칭호 · 오늘의 목표 · 영역별 실력 바 */
    private fun buildGrowth(db: Db) {
        val states = db.skillStates(Skills.forSubject(subject))
        val overall = Skills.overallLevel(states)
        val rank = Ranks.of(overall)
        val sticker = db.equippedSticker()
        b.txtRank.text = "${rank.emoji} " + getString(rank.titleRes) + if (sticker.isNotEmpty()) " $sticker" else ""
        b.rankBar.progress = (Ranks.progress(overall) * 100).toInt()
        val next = Ranks.next(overall)
        b.txtOverall.text = getString(R.string.home_overall_lv, String.format("%.1f", overall)) +
            if (next != null) getString(R.string.rank_next, next.emoji, getString(next.titleRes)) else getString(R.string.rank_top)

        val goal = db.dailyGoal()
        val todayXp = db.xpToday()
        val done = DailyGoal.isDone(todayXp, goal)
        b.txtGoal.text = getString(R.string.home_daily_goal, todayXp, goal) +
            if (done) getString(R.string.home_goal_done) else ""
        b.goalBar.progress = (DailyGoal.progress(todayXp, goal) * 100).toInt()
        b.goalBar.progressTintList = ColorStateList.valueOf(
            Color.parseColor(if (done) "#66BB6A" else "#FF8A80")
        )

        val weak = Skills.weakest(states)
        b.txtWeakest.text = if (weak != null && weak.attempts >= 0)
            getString(R.string.weak_area, weak.def.emoji, getString(weak.def.titleRes)) else ""

        b.skillsBox.removeAllViews()
        for (st in states) b.skillsBox.addView(skillRow(st))
    }

    /** 실력 한 줄: 🎧 듣기  Lv.3  [====----]  정답률 82% */
    private fun skillRow(st: SkillState): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5f).toInt(), 0, dp(5f).toInt())
        }
        // 영역 이름은 언어마다 길이가 3배까지 차이 난다("도형" vs "Bangun ruang").
        // 고정 폭으로 두면 글자가 접히면서 막대와 어긋나므로 **가변 폭 + 한 줄**로 둔다.
        row.addView(TextView(this).apply {
            text = "${st.def.emoji} " + getString(st.def.titleRes)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        })
        row.addView(TextView(this).apply {
            text = "Lv.${st.level}"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            minWidth = dp(42f).toInt()
        })
        row.addView(android.widget.ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = (st.progress * 100).toInt()
            progressTintList = ColorStateList.valueOf(Color.parseColor(st.def.color))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF0CC"))
            layoutParams = LinearLayout.LayoutParams(0, dp(10f).toInt(), 1f)
        })
        row.addView(TextView(this).apply {
            // "Не начато"(시작 전)는 58dp 고정 폭에서 두 줄로 접힌다 — 최소 폭만 주고 늘어나게
            text = if (st.attempts == 0) getString(R.string.not_started) else "  ${st.accuracy}%"
            textSize = 12f
            setTextColor(Color.parseColor("#8D6E63"))
            minWidth = dp(52f).toInt()
            maxLines = 1
            setPadding(dp(6f).toInt(), 0, 0, 0)
            gravity = Gravity.END
        })
        return row
    }

    private fun buildTrackCards(db: Db) {
        b.tracksBox.removeAllViews()
        val done = db.completedLessonIds()
        var lastStage: Int? = null   // 묶음 제목은 리소스 id 로 비교한다
        for (tid in subject.tracks) {
            val t = ContentRepo.track(this, tid) ?: continue
            val doneCount = t.units.sumOf { u -> u.lessons.count { it.id in done } }

            // 수학은 학년이 많아 유치원·초등 / 중학교 / 고등학교로 묶어 보여준다
            val stage = com.piyak.english.model.MathGrades.of(tid)?.stageRes
            if (stage != null && stage != lastStage) {
                lastStage = stage
                b.tracksBox.addView(TextView(this).apply {
                    text = getString(stage)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#8D6E63"))
                    setPadding(dp(6f).toInt(), dp(14f).toInt(), 0, dp(2f).toInt())
                })
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18f).toInt(), dp(16f).toInt(), dp(18f).toInt(), dp(16f).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(20f)
                    setColor(Color.parseColor(t.color))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10f).toInt() }
                setOnClickListener {
                    startActivity(
                        Intent(this@MainActivity, TrackActivity::class.java).putExtra("track", tid)
                    )
                }
            }
            val row = card
            // 학년마다 어울리는 일러스트 (유치원~초등=사물, 중등=생각·연필, 고등=책·시험)
            val tArt = trackArt(tid)
            if (tArt != 0) row.addView(android.widget.ImageView(this).apply {
                setImageResource(tArt)
                layoutParams = LinearLayout.LayoutParams(dp(52f).toInt(), dp(52f).toInt())
            }) else row.addView(TextView(this).apply { text = t.emoji; textSize = 34f })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12f).toInt(), 0, 0, 0)
            }
            // 카드 이름은 팩 JSON(한국어)이 아니라 리소스에서 가져온다 — 폰 언어를 따라야 하므로
            val grade = com.piyak.english.model.MathGrades.of(tid)
            col.addView(TextView(this).apply {
                text = if (grade != null) getString(grade.titleRes) else t.title
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#4E342E"))
            })
            col.addView(TextView(this).apply {
                text = if (grade != null) getString(grade.subtitleRes) else t.subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#6D4C41"))
            })
            row.addView(col)
            row.addView(TextView(this).apply {
                text = "$doneCount/${t.lessonCount}"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#4E342E"))
                // 부제가 긴 언어(독일어)에서는 글자와 숫자가 맞붙는다
                setPadding(dp(10f).toInt(), 0, 0, 0)
            })
            b.tracksBox.addView(card)
        }
    }

    /** 학년 카드 일러스트 — 저학년은 세는 사물, 위로 갈수록 공부하는 병아리 */
    private fun trackArt(tid: String): Int {
        val name = when (tid) {
            "math_k" -> "word_blocks"
            "math_g1" -> "word_cookie"
            "math_g2" -> "word_candy"
            "math_g3" -> "word_marble"
            "math_g4" -> "word_strawberry"
            "math_g5" -> "word_balloon"
            "math_g6" -> "word_acorn"
            "math_m1" -> "ck_think"
            "math_m2" -> "ck_write"
            "math_m3" -> "ck_book"
            "math_h1" -> "ck_write"
            "math_h2" -> "ck_think"
            "math_h3" -> "ck_book"
            else -> return 0
        }
        return resources.getIdentifier(name, "drawable", packageName)
    }

    override fun onDestroy() { super.onDestroy(); sfx?.release() }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
