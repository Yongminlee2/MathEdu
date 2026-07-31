# 문제 화면 비주얼 개선 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 답 영역 축소 + 진짜 수식 렌더 + 병아리 리액션 + 콤보·파티클 + 문제 전환 애니.

**Architecture:** 전부 Canvas·속성 애니메이션 (외부 라이브러리 금지). 수학 앱에서 전부 구현·검증 후 수식 렌더를 제외한 나머지를 영어 앱에 이식. 설계서: `docs/superpowers/specs/2026-07-31-lively-question-ui-design.md`

**Tech Stack:** Kotlin/Views, Canvas, ValueAnimator, JUnit

## Global Constraints

- `GRADLE_USER_HOME=C:/gradle-home` 필수
- 팩 재생성 시 `node tools/gen_index.js` 필수
- FormulaView 파서는 어떤 입력에도 예외를 던지지 않는다 (실패 = 일반 텍스트 폴백)
- 조작판(시계·저울 등) 크기는 건드리지 않는다
- 커밋 끝에 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

---

## Phase 1 — 삐약수학 (`C:\workAndroid\PiyakMath`)

### M1: FormulaView (수식 파서 + Canvas 렌더)

**Files:** Create `ui/FormulaView.kt`, `engine/Formula.kt`(파서, 뷰와 분리해 JVM 테스트 가능), Test `FormulaTest.kt`, Modify `view_q_math.xml`(FormulaView 추가)·`LessonActivity.showMath`(수식 감지 시 교체)

**파서 산출물(토큰 트리)**: Text / Sup(위첨자) / Sub(아래첨자) / Frac(위,아래) / Sqrt(내용) / Limit(조건, 본문) / Integral(하한, 상한, 본문)
인식 패턴: `lim(X→Y) BODY`, `∫₀^N BODY dx`, `log_B X`, `A^B`, 유니코드 ²³, `√N`, `(A)/(B)`·`A/B`
- [ ] 파서 작성 → 테스트: 중·고등 팩 전체 prompt 전수 파싱 예외 0, lim≥100·log≥40·∫≥100·√≥100 검출, `사과 3/4개` 같은 문장은 수식 아님 판정
- [ ] FormulaView: 토큰 트리를 재귀 측정→그리기 (분수 가로줄, 근호 윗줄, lim 아래 조건, ∫ 상하한)
- [ ] showMath 배선: `Formula.looksLikeMath(prompt)` 이면 FormulaView, 아니면 TextView
- [ ] 빌드·테스트 통과 → Commit

### M2: 답 영역 컴팩트 + 문제 확대

- [ ] BubbleChoiceView 사용처 높이 `dp(280)` → `dp(170)` (LessonActivity 2곳), 버블 반지름 축소는 뷰 내부 자동(높이 비례)이면 무수정 — 확인 후 조정
- [ ] 선다 버튼 76→60dp, NumberPadView 높이 축소(내부 버튼 패딩)
- [ ] view_q_math txtPrompt/FormulaView 글자 상향 (문장 20→22sp, 수식은 FormulaView 자체 크기)
- [ ] 빌드 → Commit

### M3: 병아리 상주 리액션 (ChickView)

**Files:** Create `ui/ChickView.kt` (기존 chick_happy/neutral/sad 벡터 + Canvas 하트·말풍선)
- [ ] 상태기계: IDLE(숨쉬기 스케일 반복) / CHEER(점프+하트 2개, 1.2초) / OOPS(처짐, 1초) / ENCOURAGE(말풍선 "힘내! 🐥")
- [ ] activity_lesson.xml 상단(진행바 줄)에 52dp 배치
- [ ] LessonActivity: 정답→cheer(), 오답→oops(), 문제 표시 15초 무입력→encourage() (핸들러, 문제 넘어가면 취소)
- [ ] 게이트: trackId in math_h1·h2·h3 → ChickView GONE
- [ ] 빌드 → Commit

### M4: 콤보 + 색종이 + 문제 전환

**Files:** Create `ui/CelebrateOverlayView.kt` (색종이+콤보 배지, 전체 화면 오버레이, 터치 통과)
- [ ] 색종이: TraceView 의 Confetti 물리 이식, 정답 시 18개(5연속↑ 32개), 1.2초
- [ ] 콤보: LessonActivity 에 연속 정답 카운터, 2연속↑ 정답 시 "🔥 N연속!" 배지 스케일 팝(0.5→1.0 overshoot), 오답 시 조용히 리셋
- [ ] 전환: showQuestion 에서 questionBox 슬라이드아웃(−화면폭×0.25, alpha 0, 90ms) → 내용 교체 → 슬라이드인(+폭×0.25→0, 90ms). 첫 문제는 애니 없음
- [ ] 빌드 → Commit

### M5: 계수 1 정리 + 재생성 + v1.1 출고

- [ ] tools/math 의 `1x²`·`1x` 계수 출력을 `x²`·`x` 로 (`coef()` 헬퍼), `+ 0x` 항 제거
- [ ] `node tools/gen_math.js && node tools/gen_index.js` → FormulaTest·전체 테스트 통과
- [ ] versionCode 2 / versionName 1.1, 빌드 → `삐약수학-v1.1.apk` 설치
- [ ] 실기기 스크린샷: 고2 로그·고3 lim·정적분 화면, 저학년 버블 화면, 병아리·콤보 동작
- [ ] README v1.1 절 + Commit + Push

## Phase 2 — 삐약영어 (`C:\workAndroid\PiyakEnglish`)

### E1: 이식 (수식 제외 전부)

- [ ] ChickView·CelebrateOverlayView 파일 복사, activity_lesson.xml 배치, LessonActivity 배선 (게이트: toeic·toefl GONE)
- [ ] 버블 280→170dp, 선다 버튼 축소, 전환 애니 — 수학 앱과 동일 수치
- [ ] 빌드·기존 테스트 74건 통과 → Commit

### E2: v3.1 출고

- [ ] versionCode 20 / 3.1, 빌드 → `삐약영어-v3.1.apk` 설치, 실기기 확인
- [ ] README v3.1 절 + Commit + Push

## 검증 요약

| 검증 | 어디서 |
|---|---|
| 파서 전수 통과·패턴 검출 하한 | M1 FormulaTest |
| 기존 수학 테스트 61건 + 신규 | M5 |
| 기존 영어 테스트 74건 | E1 |
| 실기기 배치·동작 | M5·E2 스크린샷 |
