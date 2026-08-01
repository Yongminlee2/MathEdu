# Design QA — 삐약수학

## 검증 대상

- 선택 방향: 2번 `스티커북 탐험 지도`
- 공통 기준 시안: `../PiyakEnglish/.codex/design-reference/selected-stickerbook-direction.png`
- 수학 구현 캡처: `../PiyakEnglish/.codex/design-qa/math-home-pass1.png`
- 공통 디자인 비교 입력: `../PiyakEnglish/.codex/design-qa/reference-vs-implementation-pass2.png`
- 검증 환경: Android API 36, 1080 × 2220 px

## 비교 및 보정

1. 영어와 동일한 크림색 종이, 펼친 책 지도, 탐험 병아리, 스티커 보상, 완료·현재·잠금 노드 규칙을 수학 저장소에도 동일하게 반영했다.
2. 수학의 실제 다음 학습 제목과 유치원·초등·중등 코스 구조는 유지하면서 공통 CTA와 카드 위계에 맞췄다.
3. 최종 소스에서 현재 위치 노드를 나침반 아이콘에서 탐험 병아리 에셋으로 교체해 선택 시안과의 시각적 차이를 줄였다.
4. 테스트가 검사하는 수학 커스텀 뷰의 배치 상수는 변경하지 않고 색상·피드백·접근성만 보강했다.

## 화면 품질

- 타이포그래피와 여백은 영어 앱과 같은 토큰을 사용하며 긴 수학 학습 제목도 카드 안에서 안정적으로 표시된다.
- 크림·옐로·민트·스카이·라벤더 팔레트와 둥근 카드·버튼이 전 화면에 일관된다.
- 생성 에셋과 Material Symbols Rounded 아이콘은 원본 비율을 유지하며 이모지 기반 UI 장식은 벡터·이미지 에셋으로 교체했다.
- 선택·채점·피드백 상태와 수학 연습 도구의 조작 영역이 명확하다.

## 핵심 흐름 및 접근성

- 홈의 `다음 모험 시작`에서 실제 수학 레슨 진입을 확인했다.
- 수학 문제 화면의 보기와 연습 도구가 동작하며 최소 48dp 터치 영역을 유지한다.
- ClickableViewAccessibility, TouchTargetSizeCheck, SmallSp, ContentDescription 관련 고위험 Lint 항목은 0건이다.
- 수학 도형·드래그 뷰의 기존 기하 관계 테스트를 유지했다.

## 자동 검증

- `lintDebug`: 0 errors, 452 warnings
- `testDebugUnitTest`: 64 tests, 0 failures, 0 errors
- `assembleDebug`: 성공
- `git diff --check`: 성공

final result: passed
