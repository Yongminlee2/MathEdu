import UIKit

final class MainActivity: AppCompatActivity {

    private var b: ActivityMainBinding!
    private var sfx: Sfx? = nil
    private var subject: Subject = Subject.ENGLISH
    // 처음 쓸 때 리소스에서 읽는다
    private lazy var greetings: [String] = [getString(R.string.home_greeting)]

    override func onCreate(_ savedInstanceState: SavedState?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // 삐약수학은 수학 전용 앱 — 과목 대문 없이 항상 수학
        subject = Subject.MATH

        b.txtGreeting.text = greetings.random()
        b.btnSwitchSubject.visibility = View.GONE
        b.bannerPlacement.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.startActivity(
                Intent(self, PlacementActivity.self).putExtra("subject", self.subject.id)
            )
        }
        b.btnReview.setOnClickListener { [weak self] _ in
            guard let self else { return }
            let db = Db.get(self)
            if db.wrongCount() == 0 {
                Toast.makeText(self, self.getString(R.string.home_no_review), Toast.LENGTH_SHORT).show()
            } else {
                self.startActivity(Intent(self, LessonActivity.self).putExtra("mode", "review"))
            }
        }
        b.btnStats.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.startActivity(Intent(self, StatsActivity.self))
        }
        b.btnSettings.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.startActivity(Intent(self, SettingsActivity.self))
        }
        b.txtGoalEdit.setOnClickListener { [weak self] _ in self?.pickDailyGoal() }

        // 살아있는 마스코트 — 천천히 숨쉬고, 톡 치면 삐약! 하고 점프
        sfx = Sfx(self)
        PoseAnim.applyTo(b.imgMascot, R.drawable.ck_idle)   // 가끔 눈을 깜빡인다
        let breath = ObjectAnimator.ofPropertyValuesHolder(
            b.imgMascot,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1, 1.045),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1, 1.045)
        )
        breath.duration = 1300
        breath.repeatCount = ValueAnimator.INFINITE
        breath.repeatMode = ValueAnimator.REVERSE
        breath.interpolator = AccelerateDecelerateInterpolator()
        breath.start()
        b.imgMascot.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.sfx?.piyak()
            let img = self.b.imgMascot
            img.animate().translationY(-self.dp(10)).setDuration(140)
                .withEndAction {
                    img.animate().translationY(0).setDuration(260)
                        .setInterpolator(BounceInterpolator()).start()
                }.start()
        }
        b.cardWallet.setOnClickListener { [weak self] _ in
            guard let self else { return }
            self.startActivity(Intent(self, WalletActivity.self))
        }
    }

    private func pickDailyGoal() {
        let db = Db.get(self)
        let labels = DailyGoal.OPTIONS.map { xp -> String in
            let note: String
            switch xp {
            case 20: note = getString(R.string.goal_light)
            case 50: note = getString(R.string.goal_normal)
            case 100: note = getString(R.string.goal_hard)
            default: note = getString(R.string.goal_beast)
            }
            return "\(xp) XP — \(note)"
        }
        AlertDialog.Builder(self)
            .setTitle(getString(R.string.goal_set_title))
            .setItems(labels) { _, i in
                db.setDailyGoal(DailyGoal.OPTIONS[i])
                self.refresh()
            }
            .setNegativeButton(getString(R.string.cancel), nil)
            .show()
    }

    override func onResume() {
        super.onResume()
        // 시간대에 따라 풍경이 바뀐다 — 아침 하늘, 낮 풀밭, 저녁 교실, 밤엔 별
        let hour = JCalendar.getInstance().get(JCalendar.HOUR_OF_DAY)
        let banner: Int
        switch hour {
        case 6...10: banner = R.drawable.banner_sky
        case 11...16: banner = R.drawable.banner_grass
        case 17...20: banner = R.drawable.banner_class
        default: banner = R.drawable.banner_night
        }
        b.imgBanner.setImageResource(banner)
        b.imgBanner.clipToOutline = true
        let radius = dp(18)
        b.imgBanner.outlineProvider = ViewOutlineProvider { v, o in
            o.setRoundRect(0, 0, v.width, v.height, radius)
        }
        refresh()
    }

    private func refresh() {
        let db = Db.get(self)
        let xp = db.xp()
        let lv = Economy.levelFor(xp)
        b.txtHearts.text = db.heartsEnabled() ? "\(db.hearts())" : ""
        b.txtHearts.setCompoundDrawablesRelativeWithIntrinsicBounds(
            db.heartsEnabled() ? R.drawable.ic_heart : 0, 0, 0, 0
        )
        let (streak, _) = unpair(Economy.streak(db.studyDays(), Db.today()))
        b.txtStreak.text = "\(streak)"
        b.txtLevel.text = "Lv.\(lv)"
        b.xpBar.progress = (Economy.levelProgress(xp) * 100).toInt()
        b.txtReview.text = getString(R.string.home_wrong_count, db.wrongCount())
        // 배치고사 배너: 과목별로 아직 안 본 경우에만
        let placedKey = subject == Subject.MATH ? "math_placement_done" : "placement_done"
        b.bannerPlacement.visibility = db.meta(placedKey) == "1" ? View.GONE : View.VISIBLE
        b.txtPlacement.text = subject == Subject.MATH
            ? getString(R.string.home_placement_math)
            : getString(R.string.home_placement_math)

        // 상점에서 산 테마 배경 적용
        let theme = Color.parseColor(db.themeColor())
        b.root.setBackgroundColor(theme)
        window.statusBarColor = theme

        b.txtCoins.text = Wallet.format(self, db.coins())
        buildGrowth(db)
        buildTrackCards(db)
    }

    /// 칭호 · 오늘의 목표 · 영역별 실력 바
    private func buildGrowth(_ db: Db) {
        let states = db.skillStates(Skills.forSubject(subject))
        let overall = Skills.overallLevel(states)
        let rank = Ranks.of(overall)
        let sticker = db.equippedSticker()
        b.txtRank.text = "\(rank.emoji) " + getString(rank.titleRes) + (!sticker.isEmpty ? " \(sticker)" : "")
        b.rankBar.progress = (Ranks.progress(overall) * 100).toInt()
        let next = Ranks.next(overall)
        b.txtOverall.text = getString(R.string.home_overall_lv, "%.1f".format(overall)) +
            (next != nil ? getString(R.string.rank_next, next!.emoji, getString(next!.titleRes)) : getString(R.string.rank_top))

        let goal = db.dailyGoal()
        let todayXp = db.xpToday()
        let done = DailyGoal.isDone(todayXp, goal)
        b.txtGoal.text = getString(R.string.home_daily_goal, todayXp, goal) +
            (done ? getString(R.string.home_goal_done) : "")
        b.goalBar.progress = (DailyGoal.progress(todayXp, goal) * 100).toInt()
        b.goalBar.progressTintList = ColorStateList.valueOf(
            Color.parseColor(done ? "#66BB6A" : "#FF8A80")
        )

        let weak = Skills.weakest(states)
        if let weak, weak.attempts >= 0 {
            b.txtWeakest.text = getString(R.string.weak_area, weak.def.emoji, getString(weak.def.titleRes))
        } else {
            b.txtWeakest.text = ""
        }

        b.skillsBox.removeAllViews()
        for st in states { b.skillsBox.addView(skillRow(st)) }
    }

    /// 실력 한 줄: 영역  Lv.3  [====----]  정답률 82%
    private func skillRow(_ st: SkillState) -> LinearLayout {
        let row = LinearLayout(self)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(5).toInt(), 0, dp(5).toInt())

        // 영역 이름은 언어마다 길이가 크게 달라 가변 폭 + 한 줄 + 자동 축소로 둔다
        let name = TextView(self)
        name.text = "\(st.def.emoji) " + getString(st.def.titleRes)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        name.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0)
        row.addView(name)

        let lv = TextView(self)
        lv.text = "Lv.\(st.level)"
        lv.textSize = 14
        lv.setTypeface(lv.typeface, Typeface.BOLD)
        lv.minWidth = dp(42).toInt()
        row.addView(lv)

        let bar = ProgressBar(self)
        bar.max = 100
        bar.progress = (st.progress * 100).toInt()
        bar.progressTintList = ColorStateList.valueOf(Color.parseColor(st.def.color))
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF0CC"))
        bar.layoutParams = LinearLayout.LayoutParams(0, dp(10).toInt(), 1)
        row.addView(bar)

        // "시작 전" 문구가 접히지 않게 최소 폭만 주고 늘어나게
        let acc = TextView(self)
        acc.text = st.attempts == 0 ? getString(R.string.not_started) : "  \(st.accuracy)%"
        acc.textSize = 12
        acc.setTextColor(Color.parseColor("#8D6E63"))
        acc.minWidth = dp(52).toInt()
        acc.maxLines = 1
        acc.setPadding(dp(6).toInt(), 0, 0, 0)
        acc.gravity = Gravity.END
        row.addView(acc)
        return row
    }

    private func buildTrackCards(_ db: Db) {
        b.tracksBox.removeAllViews()
        let done = db.completedLessonIds()
        var lastStage: Int? = nil   // 묶음 제목은 리소스 id 로 비교한다
        for tid in subject.tracks {
            guard let t = ContentRepo.track(self, tid) else { continue }
            let doneCount = t.units.sumOf { u in u.lessons.filter { done.contains($0.id) }.count }

            // 수학은 학년이 많아 유치원·초등 / 중학교 / 고등학교로 묶어 보여준다
            let stage = MathGrades.of(tid)?.stageRes
            if let stage, stage != lastStage {
                lastStage = stage
                let head = TextView(self)
                head.text = getString(stage)
                head.textSize = 14
                head.setTypeface(head.typeface, Typeface.BOLD)
                head.setTextColor(Color.parseColor("#8D6E63"))
                head.setPadding(dp(6).toInt(), dp(14).toInt(), 0, dp(2).toInt())
                b.tracksBox.addView(head)
            }

            let card = LinearLayout(self)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setPadding(dp(18).toInt(), dp(16).toInt(), dp(18).toInt(), dp(16).toInt())
            let bg = GradientDrawable()
            bg.cornerRadius = dp(20)
            bg.setColor(Color.parseColor(t.color))
            card.background = bg
            let cardLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardLp.topMargin = dp(10).toInt()
            card.layoutParams = cardLp
            card.setOnClickListener { [weak self] _ in
                guard let self else { return }
                self.startActivity(
                    Intent(self, TrackActivity.self).putExtra("track", tid)
                )
            }
            let row = card
            // 학년마다 어울리는 일러스트 (유치원~초등=사물, 중등=생각·연필, 고등=책·시험)
            let tArt = trackArt(tid)
            if tArt != 0 {
                let iv = ImageView(self)
                iv.setImageResource(tArt)
                iv.layoutParams = LinearLayout.LayoutParams(dp(52).toInt(), dp(52).toInt())
                row.addView(iv)
            } else {
                let em = TextView(self)
                em.text = t.emoji
                em.textSize = 34
                row.addView(em)
            }
            let col = LinearLayout(self)
            col.orientation = LinearLayout.VERTICAL
            col.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
            col.setPadding(dp(12).toInt(), 0, 0, 0)
            // 카드 이름은 팩 JSON(한국어)이 아니라 리소스에서 — 폰 언어를 따라야 하므로
            let grade = MathGrades.of(tid)
            let title = TextView(self)
            title.text = grade != nil ? getString(grade!.titleRes) : t.title
            title.textSize = 18
            title.setTypeface(title.typeface, Typeface.BOLD)
            title.setTextColor(Color.parseColor("#4E342E"))
            col.addView(title)
            let sub = TextView(self)
            sub.text = grade != nil ? getString(grade!.subtitleRes) : t.subtitle
            sub.textSize = 13
            sub.setTextColor(Color.parseColor("#6D4C41"))
            col.addView(sub)
            row.addView(col)
            let cnt = TextView(self)
            cnt.text = "\(doneCount)/\(t.lessonCount)"
            cnt.textSize = 15
            cnt.setTypeface(cnt.typeface, Typeface.BOLD)
            cnt.setTextColor(Color.parseColor("#4E342E"))
            // 부제가 긴 언어에서 글자와 숫자가 맞붙지 않게
            cnt.setPadding(dp(10).toInt(), 0, 0, 0)
            row.addView(cnt)
            b.tracksBox.addView(card)
        }
    }

    /// 학년 카드 일러스트 — 저학년은 세는 사물, 위로 갈수록 공부하는 병아리
    private func trackArt(_ tid: String) -> Int {
        let name: String
        switch tid {
        case "math_k": name = "word_blocks"
        case "math_g1": name = "word_cookie"
        case "math_g2": name = "word_candy"
        case "math_g3": name = "word_marble"
        case "math_g4": name = "word_strawberry"
        case "math_g5": name = "word_balloon"
        case "math_g6": name = "word_acorn"
        case "math_m1": name = "ck_think"
        case "math_m2": name = "ck_write"
        case "math_m3": name = "ck_book"
        case "math_h1": name = "ck_write"
        case "math_h2": name = "ck_think"
        case "math_h3": name = "ck_book"
        default: return 0
        }
        return resources.getIdentifier(name, "drawable", packageName)
    }

    override func onDestroy() { super.onDestroy(); sfx?.release() }

    private func dp(_ v: Float) -> Float { v * resources.displayMetrics.density }
}
