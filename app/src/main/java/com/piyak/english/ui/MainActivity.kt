package com.piyak.english.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.piyak.english.R
import com.piyak.english.databinding.ActivityMainBinding
import com.piyak.english.db.Db
import com.piyak.english.engine.DailyGoal
import com.piyak.english.engine.Economy
import com.piyak.english.engine.Ranks
import com.piyak.english.engine.SkillState
import com.piyak.english.engine.Skills
import com.piyak.english.model.ContentRepo
import com.piyak.english.model.MathGrades
import com.piyak.english.model.Subject
import com.piyak.english.model.TrackData

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val subject = Subject.MATH
    private var mascotAnimator: ObjectAnimator? = null
    private var currentAdventure: Adventure? = null

    private val greetings = listOf(
        "오늘도 신나는 수학 모험을 떠나요!",
        "작은 한 걸음이 멋진 실력을 만들어요.",
        "숫자 속에 숨은 규칙을 찾아 볼까요?",
        "천천히, 씩씩하게 한 문제씩 가요!",
        "삐약이와 오늘의 지도를 펼쳐 봐요.",
    )

    private data class Adventure(
        val trackId: String,
        val track: TrackData,
        val lessonId: String?,
        val lessonTitle: String?,
        val doneCount: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.txtGreeting.text = greetings.random()
        b.btnSwitchSubject.visibility = View.GONE
        b.btnHome.setOnClickListener { b.homeScroll.smoothScrollTo(0, 0) }
        b.bannerPlacement.setOnClickListener {
            startActivity(Intent(this, PlacementActivity::class.java).putExtra("subject", subject.id))
        }
        b.btnReview.setOnClickListener {
            val db = Db.get(this)
            if (db.wrongCount() == 0) {
                android.widget.Toast.makeText(
                    this,
                    "지금은 복습할 오답이 없어요!",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                startActivity(Intent(this, LessonActivity::class.java).putExtra("mode", "review"))
            }
        }
        b.btnStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.txtGoalEdit.setOnClickListener { pickDailyGoal() }
        b.cardWallet.setOnClickListener { startActivity(Intent(this, WalletActivity::class.java)) }
        b.btnStartAdventure.setOnClickListener { openAdventure() }
        b.heroCurrentNode.setOnClickListener { openAdventure() }
    }

    private fun pickDailyGoal() {
        val db = Db.get(this)
        val labels = DailyGoal.OPTIONS.map { xp ->
            val note = when (xp) {
                20 -> "가볍게 (레슨 1개쯤)"
                50 -> "보통 (레슨 2~3개)"
                100 -> "열심히 (레슨 5개쯤)"
                else -> "도전하기 (레슨 10개쯤)"
            }
            "$xp XP — $note"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("오늘의 목표 정하기")
            .setItems(labels) { _, i ->
                db.setDailyGoal(DailyGoal.OPTIONS[i])
                refresh()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        startMascotAnimation()
    }

    override fun onPause() {
        mascotAnimator?.cancel()
        mascotAnimator = null
        b.imgMascot.translationY = 0f
        super.onPause()
    }

    private fun startMascotAnimation() {
        mascotAnimator?.cancel()
        mascotAnimator = ObjectAnimator.ofFloat(b.imgMascot, View.TRANSLATION_Y, 0f, -dp(6f)).apply {
            duration = 1_100L
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun refresh() {
        val db = Db.get(this)
        val xp = db.xp()
        val level = Economy.levelFor(xp)
        val hearts = db.hearts()
        val (streak, _) = Economy.streak(db.studyDays(), Db.today())

        b.txtHearts.text = hearts.toString()
        b.txtHearts.contentDescription = "남은 하트 ${hearts}개"
        b.txtStreak.text = streak.toString()
        b.txtStreak.contentDescription = "연속 학습 ${streak}일"
        b.txtLevel.text = "Lv.$level"
        b.txtLevel.contentDescription = "레벨 $level"
        b.xpBar.progress = (Economy.levelProgress(xp) * 100).toInt()
        b.btnReview.text = "오답 ${db.wrongCount()}"

        b.bannerPlacement.visibility =
            if (db.meta("math_placement_done") == "1") View.GONE else View.VISIBLE
        b.txtPlacement.text = "레벨테스트로 나에게 딱 맞는 수학 학년을 찾아요"

        val theme = Color.parseColor(db.themeColor())
        b.root.setBackgroundColor(theme)
        window.statusBarColor = theme
        window.navigationBarColor = ContextCompat.getColor(this, R.color.paper)

        b.txtCoins.text = com.piyak.english.engine.Wallet.format(db.coins())
        val weekStart = Db.today() - 6
        val studyDaysThisWeek = db.studyDays().count { it >= weekStart }
        b.txtWeeklySticker.text = when (studyDaysThisWeek) {
            0 -> "첫 스티커를 모아 볼까요?"
            1 -> "이번 주 첫 탐험을 시작했어요!"
            else -> "이번 주 ${studyDaysThisWeek}일 탐험했어요"
        }

        currentAdventure = findAdventure(db)
        updateAdventureCard()
        buildGrowth(db)
        buildTrackCards(db)
    }

    private fun findAdventure(db: Db): Adventure? {
        val done = db.completedLessonIds()
        val tracks = subject.tracks
        val placedLevel = db.metaInt("math_placement_level", 0)
        val orderedTracks = if (placedLevel in 1..tracks.size) {
            tracks.drop(placedLevel - 1) + tracks.take(placedLevel - 1)
        } else {
            tracks
        }

        var completedFallback: Adventure? = null
        for (trackId in orderedTracks) {
            val track = ContentRepo.track(this, trackId) ?: continue
            val lessons = track.units.flatMap { it.lessons }
            val doneCount = lessons.count { it.id in done }
            val next = lessons.firstOrNull { it.id !in done }
            val candidate = Adventure(trackId, track, next?.id, next?.title, doneCount)
            if (completedFallback == null) completedFallback = candidate
            if (next != null) return candidate
        }
        return completedFallback
    }

    private fun updateAdventureCard() {
        val adventure = currentAdventure
        if (adventure == null) {
            b.txtNextAdventure.text = "새 모험을 준비하고 있어요"
            b.txtNextSubtitle.text = "학습 코스에서 원하는 지도를 골라 보세요"
            b.btnStartAdventure.text = "학습 코스 보기"
            b.btnStartAdventure.isEnabled = false
            return
        }

        val allDone = adventure.lessonId == null
        b.txtNextAdventure.text = adventure.lessonTitle ?: "모든 모험을 완료했어요!"
        b.txtNextSubtitle.text =
            "${adventure.track.title} · ${adventure.doneCount}/${adventure.track.lessonCount} 완료"
        b.btnStartAdventure.text = if (allDone) "모험 지도 다시 보기" else "다음 모험 시작"
        b.btnStartAdventure.isEnabled = true
    }

    private fun openAdventure() {
        val adventure = currentAdventure ?: return
        val intent = if (adventure.lessonId != null) {
            Intent(this, LessonActivity::class.java)
                .putExtra("track", adventure.trackId)
                .putExtra("lesson", adventure.lessonId)
        } else {
            Intent(this, TrackActivity::class.java).putExtra("track", adventure.trackId)
        }
        startActivity(intent)
    }

    private fun buildGrowth(db: Db) {
        val states = db.skillStates(Skills.forSubject(subject))
        val overall = Skills.overallLevel(states)
        val rank = Ranks.of(overall)
        b.txtRank.text = rank.title
        b.rankBar.progress = (Ranks.progress(overall) * 100).toInt()
        val next = Ranks.next(overall)
        b.txtOverall.text = String.format("종합 실력 Lv.%.1f", overall) +
            if (next != null) " · 다음 칭호 ${next.title}" else " · 최고 칭호 달성"

        val goal = db.dailyGoal()
        val todayXp = db.xpToday()
        val done = DailyGoal.isDone(todayXp, goal)
        b.txtGoal.text = "오늘의 목표  $todayXp / $goal XP" + if (done) " · 달성!" else ""
        b.goalBar.progress = (DailyGoal.progress(todayXp, goal) * 100).toInt()
        b.goalBar.progressTintList = ColorStateList.valueOf(
            color(if (done) R.color.green_ok else R.color.coral_deep),
        )

        val weak = Skills.weakest(states)
        b.txtWeakest.visibility = if (weak != null) View.VISIBLE else View.GONE
        b.txtWeakest.text = if (weak != null) "연습 추천 · ${weak.def.title}" else ""

        b.skillsBox.removeAllViews()
        states.forEach { b.skillsBox.addView(skillRow(it)) }
    }

    private fun skillRow(state: SkillState): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(42f).toInt()

            addView(TextView(this@MainActivity).apply {
                text = state.def.title
                textSize = 14f
                setTextColor(color(R.color.ink))
                layoutParams = LinearLayout.LayoutParams(dp(82f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Lv.${state.level}"
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color(R.color.ink))
                layoutParams = LinearLayout.LayoutParams(dp(46f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(ProgressBar(
                this@MainActivity,
                null,
                android.R.attr.progressBarStyleHorizontal,
            ).apply {
                max = 100
                progress = (state.progress * 100).toInt()
                progressTintList = ColorStateList.valueOf(Color.parseColor(state.def.color))
                progressBackgroundTintList = ColorStateList.valueOf(color(R.color.paper_alt))
                layoutParams = LinearLayout.LayoutParams(0, dp(9f).toInt(), 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = if (state.attempts == 0) "시작 전" else "${state.accuracy}%"
                textSize = 12f
                setTextColor(color(R.color.ink_muted))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(58f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
    }

    private fun buildTrackCards(db: Db) {
        b.tracksBox.removeAllViews()
        val done = db.completedLessonIds()
        val accentBackgrounds = intArrayOf(
            R.drawable.bg_chip_mint,
            R.drawable.bg_chip_sky,
            R.drawable.bg_chip_lavender,
            R.drawable.bg_chip_coral,
        )
        var lastStage: String? = null

        subject.tracks.forEachIndexed { index, trackId ->
            val track = ContentRepo.track(this, trackId) ?: return@forEachIndexed
            val stage = MathGrades.of(trackId)?.stage
            if (stage != null && stage != lastStage) {
                lastStage = stage
                b.tracksBox.addView(TextView(this).apply {
                    text = stage
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(color(R.color.ink_muted))
                    setPadding(dp(4f).toInt(), dp(15f).toInt(), 0, dp(1f).toInt())
                })
            }

            val doneCount = track.units.sumOf { unit -> unit.lessons.count { it.id in done } }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(78f).toInt()
                setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_paper)
                isClickable = true
                isFocusable = true
                contentDescription = "${track.title}, $doneCount/${track.lessonCount} 완료"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(9f).toInt() }
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, TrackActivity::class.java).putExtra("track", trackId))
                }
            }

            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_menu_book_rounded)
                imageTintList = ColorStateList.valueOf(color(R.color.ink))
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    accentBackgrounds[index % accentBackgrounds.size],
                )
                setPadding(dp(11f).toInt(), dp(11f).toInt(), dp(11f).toInt(), dp(11f).toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(46f).toInt(), dp(46f).toInt())
            })

            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f).toInt(), 0, dp(8f).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = track.title
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(color(R.color.ink))
                })
                addView(TextView(this@MainActivity).apply {
                    text = track.subtitle
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(color(R.color.ink_muted))
                })
            })

            card.addView(TextView(this).apply {
                text = "$doneCount/${track.lessonCount}"
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color(R.color.ink_muted))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right_rounded)
                imageTintList = ColorStateList.valueOf(color(R.color.ink_muted))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt())
            })
            b.tracksBox.addView(card)
        }
    }

    private fun color(resource: Int): Int = ContextCompat.getColor(this, resource)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
