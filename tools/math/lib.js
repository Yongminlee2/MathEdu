// 수학 문제 생성 공용 도구
// 문제는 미리 구워서 팩에 고정한다 — ID가 고정돼야 오답노트·진행도·코인이 작동한다.

// ---------- 시드 난수 (재현 가능) ----------
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(20260727);
const ri = (n) => Math.floor(rng() * n);
/** lo 이상 hi 이하 정수 */
const rint = (lo, hi) => lo + ri(hi - lo + 1);
const pick = (arr) => arr[ri(arr.length)];

function shuffled(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = ri(i + 1);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// ---------- ID ----------
let seq = 0;
function qid() {
  seq++;
  return "M" + String(seq).padStart(6, "0");
}

let lessonSeq = 0;
function lessonId() {
  lessonSeq++;
  return "ml" + lessonSeq;
}

// ---------- 검증 ----------
let total = 0;
const VISUAL_KINDS = new Set([
  "emoji", "emoji_op", "array", "shapes", "clock", "fraction",
  "number_line", "bar_graph", "angle", "compare", "coord3d", "coord2d",
  // 그림을 손으로 조작해서 답하는 종류
  "clock_set", "group", "fraction_paint", "shape_sort",
  "number_line_drag", "angle_set", "balance", "bar_build", "gather",
]);

/**
 * 계수 1 표기 정리: "1x² + 1x" → "x² + x", "-1x" → "-x".
 * 앞이 숫자·소수점이면 건드리지 않는다 (21x, 0.1x 는 그대로).
 * prompt 와 explain 양쪽에 적용 — 진짜 수학책 표기를 위해.
 */
function tidyCoef(s) {
  if (!s) return s;
  return s.replace(/(^|[^0-9.])1([xn])(?=[²³⁴⁵^ +\-=)\/,]|[가-힣]|$)/g, "$1$2");
}

function validate(q) {
  q.prompt = tidyCoef(q.prompt);
  if (q.explain) q.explain = tidyCoef(q.explain);
  const fail = (m) => {
    throw new Error(`검증 실패 [${q.id}] ${m}: ${JSON.stringify(q).slice(0, 220)}`);
  };
  if (!q.prompt) fail("prompt 없음");
  if (q.input === "choice") {
    if (!q.choices || q.choices.length !== 4) fail("선택지 4개 아님");
    if (new Set(q.choices).size !== 4) fail("선택지 중복");
    if (q.answerIndex < 0 || q.answerIndex > 3) fail("정답 인덱스");
  } else {
    if (q.answer === undefined || q.answer === "") fail("answer 없음");
  }
  if (q.visual && !VISUAL_KINDS.has(q.visual.kind)) fail("알 수 없는 visual: " + q.visual.kind);
  if (!q.explain) fail("해설 없음");
  total++;
  return q;
}

// ---------- 문제 빌더 ----------
/** 숫자 키패드로 답하는 문제 */
function numQ(prompt, answer, explain, opts = {}) {
  return validate({
    id: qid(), type: "math", prompt,
    input: "number", answer: String(answer),
    ...(opts.alts ? { alts: opts.alts.map(String) } : {}),
    ...(opts.unit ? { unit: opts.unit } : {}),
    ...(opts.visual ? { visual: opts.visual } : {}),
    ...(opts.skill ? { skill: opts.skill } : {}),
    explain,
  });
}

/**
 * 4지선다. 서로 다른 선택지 4개를 못 만들면 null 을 돌려준다
 * (gen 이 다른 난수로 다시 뽑는다) — 중복 선택지가 팩에 들어가는 걸 막는다.
 */
function choiceQ(prompt, correct, distractors, explain, opts = {}) {
  const seen = new Set([String(correct)]);
  const clean = [];
  for (const d of distractors) {
    const s = String(d);
    if (seen.has(s)) continue;
    seen.add(s);
    clean.push(s);
    if (clean.length === 3) break;
  }
  if (clean.length < 3) return null;
  const choices = shuffled([String(correct), ...clean]);
  return validate({
    id: qid(), type: "math", prompt,
    input: "choice", choices, answerIndex: choices.indexOf(String(correct)),
    ...(opts.visual ? { visual: opts.visual } : {}),
    ...(opts.skill ? { skill: opts.skill } : {}),
    explain,
  });
}

/**
 * 그림을 직접 조작해서 답하는 문제.
 * 시계 바늘을 돌리거나 사물을 끌어다 나눠 담으면 앱이 결과를 읽어 채점한다.
 * answer 는 시계면 "H:MM", 나눠 담기면 한 묶음의 개수.
 */
function visualQ(prompt, answer, explain, opts = {}) {
  return validate({
    id: qid(), type: "math", prompt,
    input: "visual", answer: String(answer),
    ...(opts.visual ? { visual: opts.visual } : {}),
    ...(opts.skill ? { skill: opts.skill } : {}),
    explain,
  });
}

/** 식·문자로 답하는 문제 (중·고등) */
function textQ(prompt, answer, explain, opts = {}) {
  return validate({
    id: qid(), type: "math", prompt,
    input: "text", answer: String(answer),
    ...(opts.alts ? { alts: opts.alts.map(String) } : {}),
    ...(opts.visual ? { visual: opts.visual } : {}),
    ...(opts.skill ? { skill: opts.skill } : {}),
    explain,
  });
}

// ---------- 그림 명세 ----------
const V = {
  emoji: (emoji, n) => ({ kind: "emoji", emoji, a: n }),
  emojiOp: (emoji, a, b, op) => ({ kind: "emoji_op", emoji, a, b, op }),
  array: (emoji, rows, cols) => ({ kind: "array", emoji, a: rows, b: cols }),
  shapes: (names) => ({ kind: "shapes", labels: names }),
  clock: (h, m) => ({ kind: "clock", p: h, q: m }),
  fraction: (n, d) => ({ kind: "fraction", p: n, q: d }),
  numberLine: (lo, hi, marks) => ({ kind: "number_line", p: lo, q: hi, values: marks }),
  barGraph: (labels, values) => ({ kind: "bar_graph", labels, values }),
  angle: (deg) => ({ kind: "angle", p: deg }),
  compare: (emojiA, a, emojiB, b) => ({ kind: "compare", emoji: emojiA, a, b, labels: [emojiB] }),
  /** 바늘을 끌어 맞추는 시계 — p·q 는 정답 시·분 */
  clockSet: (h, m) => ({ kind: "clock_set", p: h, q: m }),
  /** 사물을 끌어다 묶음에 나눠 담기 — a 는 전체 개수, b 는 묶음 수 */
  group: (emoji, total, groups) => ({ kind: "group", emoji, a: total, b: groups }),
  /** 조각을 눌러 색칠하기 — p 는 칠해야 할 칸 수, q 는 전체 칸 수 */
  fractionPaint: (n, d) => ({ kind: "fraction_paint", p: n, q: d }),
  /** 도형을 이름 붙은 바구니로 분류하기 — kinds[i] 는 items[i] 가 들어갈 바구니 */
  shapeSort: (items, kinds, labels) => ({ kind: "shape_sort", items, kinds, labels }),
  /** 수직선 위의 점 끌기 — lo~hi 를 steps 칸으로 나눈다 */
  numberLineDrag: (lo, hi, steps) => ({ kind: "number_line_drag", p: lo, q: hi, a: steps }),
  /** 반직선을 돌려 각도 만들기 */
  angleSet: (deg) => ({ kind: "angle_set", p: deg }),
  /** 양팔 저울 — coef·x + left = right */
  balance: (coef, left, right) => ({ kind: "balance", a: coef, b: left, p: right }),
  /** 막대를 끌어 올려 그래프 완성 */
  barBuild: (labels, values) => ({ kind: "bar_build", labels, values }),
  /** 사물을 상자로 옮겨 담기 — total 개 중 need 개를 담는다 */
  gather: (emoji, total, need, label) =>
    ({ kind: "gather", emoji, a: total, b: need, labels: [label] }),
  /** 공간좌표 점 (교과서식 오른손 좌표계 그림) */
  coord3d: (x, y, z) => ({ kind: "coord3d", values: [x, y, z] }),
  /** 좌표평면 벡터 화살표 2개 */
  vec2: (x1, y1, x2, y2) => ({ kind: "coord2d", op: "vec", values: [x1, y1, x2, y2] }),
  /** 포물선 y = a(x-p)² + q 와 꼭짓점 */
  parabola: (a, p, q) => ({ kind: "coord2d", op: "parab", a, p, q }),
  /** 타원 x²/a² + y²/b² = 1 */
  ellipse: (a, b) => ({ kind: "coord2d", op: "ellipse", p: a, q: b }),
};

/** 모양별 이모지 — 같은 종류라도 색·방향이 달라야 "모양"으로 분류하게 된다 */
const SHAPE_POOL = {
  삼각형: ["🔺", "🔻", "🔼", "🔽"],
  사각형: ["🟥", "🟦", "🟩", "🟨", "🟧", "🟪"],
  원: ["🔴", "🔵", "🟢", "🟡", "🟠", "🟣"],
};

const SHAPE_HINT = {
  삼각형: "뾰족한 곳이 3개",
  사각형: "뾰족한 곳이 4개",
  원: "뾰족한 곳이 없고 동그란",
};

// 아이가 좋아하는 사물 이모지
const FRUITS = ["🍎", "🍓", "🍊", "🍇", "🍌", "🍑", "🍉", "🥝"];
const ANIMALS = ["🐥", "🐶", "🐱", "🐰", "🐸", "🐼", "🐧", "🦊"];
const THINGS = ["⭐", "🎈", "🍪", "🚗", "✏️", "🌸", "🧸", "⚽"];
const ALL_EMOJI = [...FRUITS, ...ANIMALS, ...THINGS];


// ---------- 수식 표기 ----------
/** 계수를 수학 표기로: 1x² → x², -1x → -x, 5x² → 5x² */
function coef(n, sym) {
  if (n === 1) return sym;
  if (n === -1) return "-" + sym;
  return n + sym;
}


// ---------- 미니 상황 랩핑 ----------
// "식만 덜렁 있어 계산기 같다"는 피드백. 계산 문제 일부를 작은 이야기로 감싼다.
// 시드 난수 대신 숫자 해시로 골라 문제 ID 순서(진행도 호환)를 흔들지 않는다.
const CAST = ["삐약이", "토끼", "펭귄", "고양이", "곰돌이", "다람쥐"];
const OBJS = ["쿠키", "사탕", "구슬", "딸기", "풍선", "도토리", "블록", "스티커"];
function scenePick(key, arr) {
  let h = 0;
  for (const c of String(key)) h = (h * 31 + c.charCodeAt(0)) | 0;
  return arr[Math.abs(h) % arr.length];
}
/** 덧셈 프롬프트 — 절반쯤은 순수 계산으로 남긴다 (계산 훈련도 중요) */
function sceneAdd(a, b) {
  const w = scenePick(a * 100 + b, CAST), o = scenePick(a * 7 + b * 3, OBJS);
  return scenePick(`${a}+${b}`, [
    () => `${a} + ${b} = ?`,
    () => `${a} + ${b} = ?`,
    () => `${w}가 ${o} ${a}개를 모았는데 친구가 ${b}개를 더 줬어요. 모두 몇 개일까요? (${a} + ${b})`,
    () => `바구니에 ${o} ${a}개, 상자에 ${b}개가 있어요. 모두 몇 개일까요? (${a} + ${b})`,
  ])();
}
function sceneSub(a, b) {
  const w = scenePick(a * 100 + b, CAST), o = scenePick(a * 7 + b * 3, OBJS);
  return scenePick(`${a}-${b}`, [
    () => `${a} - ${b} = ?`,
    () => `${a} - ${b} = ?`,
    () => `${w}가 ${o} ${a}개 중 ${b}개를 먹었어요. 몇 개 남았을까요? (${a} - ${b})`,
    () => `${o} ${a}개에서 ${b}개를 동생에게 줬어요. 몇 개 남았을까요? (${a} - ${b})`,
  ])();
}
function sceneMul(a, b) {
  const w = scenePick(a * 100 + b, CAST), o = scenePick(a * 7 + b * 3, OBJS);
  return scenePick(`${a}x${b}`, [
    () => `${a} × ${b} = ?`,
    () => `${a} × ${b} = ?`,
    () => `${o}를 한 상자에 ${b}개씩 ${a}상자에 담으면 모두 몇 개일까요? (${a} × ${b})`,
    () => `${w}네 반 ${a}명이 ${o}를 ${b}개씩 가졌어요. 모두 몇 개일까요? (${a} × ${b})`,
  ])();
}

// ---------- 오답 만들기 ----------
/** 정답 근처의 그럴듯한 오답 3개 (중복·음수 방지) */
function nearWrong(answer, spread = 3, allowNegative = false) {
  const out = new Set();
  let guard = 0;
  while (out.size < 3 && guard++ < 100) {
    const d = rint(1, spread) * (rng() < 0.5 ? -1 : 1);
    const w = answer + d;
    if (w === answer) continue;
    if (!allowNegative && w < 0) continue;
    out.add(w);
  }
  let n = answer + 10;
  while (out.size < 3) out.add(n++);
  return [...out];
}

// ---------- 레슨/유닛 패킹 ----------
function chunk(arr, n) {
  const out = [];
  for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n));
  return out;
}

/** 문제 배열 → 레슨 배열 (기본 10문제) */
function packLessons(questions, per, titleFn) {
  return chunk(questions, per || 10)
    .filter((qs) => qs.length >= 4)
    .map((qs, i) => ({
      id: lessonId(),
      title: titleFn ? titleFn(i) : `연습 ${i + 1}`,
      questions: qs,
    }));
}

/** 단원 하나 = 유닛 하나 */
function unit(title, emoji, level, lessons) {
  return { id: "mu" + (++lessonSeq), title, emoji, level, lessons };
}

/** 문제들을 받아 단원(유닛) 하나로 만든다 */
function makeUnit(title, emoji, level, questions, per) {
  const lessons = packLessons(questions, per, (i) => `${title} ${i + 1}`);
  if (!lessons.length) return null;
  return unit(title, emoji, level, lessons);
}

/**
 * 문제를 넉넉히 뽑는 배수. 생성기마다 만들 수 있는 서로 다른 문제의 수가 다른데,
 * gen 이 중복을 버리므로 실제로는 각 생성기의 한계까지만 나온다 — 크게 잡아도 안전하다.
 */
const SCALE = 4;

/** n개를 만들되 중복 문제(같은 prompt+그림)는 버린다 */
function gen(want, fn) {
  const n = Math.round(want * SCALE);
  const out = [];
  const seen = new Set();
  let guard = 0;
  while (out.length < n && guard++ < n * 25) {
    const q = fn();
    if (!q) continue;
    const key = q.prompt + "|" + JSON.stringify(q.visual || "");
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(q);
  }
  return out;
}

module.exports = {
  rng, ri, rint, pick, shuffled,
  numQ, choiceQ, textQ, visualQ, V,
  FRUITS, ANIMALS, THINGS, ALL_EMOJI, SHAPE_POOL, SHAPE_HINT,
  coef, nearWrong, packLessons, makeUnit, gen, chunk, SCALE,
  scenePick, sceneAdd, sceneSub, sceneMul, CAST, OBJS,
  stats: () => ({ total }),
};
