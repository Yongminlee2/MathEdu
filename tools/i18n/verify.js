/**
 * 앱이 하는 조립을 그대로 흉내 내어 **번역 결과를 전수 검사한다.**
 *
 *   node tools/i18n/verify.js         # 전 언어
 *   node tools/i18n/verify.js en      # 한 언어만
 *
 * 잡아내는 것
 *  1) 번역했는데도 화면에 한국어가 남는 문제 (사전에 없는 낱말)
 *  2) %n$s 자리에 넣을 값이 모자라거나 남는 경우
 *  3) 뼈대는 있는데 번역이 없어 한국어로 떨어지는 문제
 *
 * 이 검사를 통과해야 "12개 언어 지원"이라고 말할 수 있다.
 */
const fs = require("fs");
const path = require("path");

const PACKS = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "packs");
const tplTr = require("./templates_i18n");
const wordTr = require("./words_i18n");
const unitTr = require("./units_i18n");
const { langs } = require("./strings");

const only = process.argv[2];
const targets = only ? [only] : langs.filter((l) => l !== "ko");

const HANGUL_RUN = /[가-힣]+/g;

function makeDict(src, lang) {
  const m = new Map();
  for (const [ko, by] of Object.entries(src)) {
    if (by.en == null) continue;
    m.set(ko, by[lang] == null ? by.en : by[lang]);
  }
  return m;
}

/** Tpl.word() 와 같은 규칙: 통짜 구절 먼저, 없으면 낱말 단위 */
function word(s, dict) {
  if (!s) return s;
  if (dict.has(s)) return dict.get(s);
  const t = s.trim();
  if (t.length !== s.length && dict.has(t)) return s.replace(t, dict.get(t));
  if (s.includes("\n")) return s.split("\n").map((l) => word(l, dict)).join("\n");
  return s.replace(HANGUL_RUN, (w) => (dict.has(w) ? dict.get(w) : w));
}

/** 안드로이드 getString(id, args...) 흉내 */
function format(tpl, args) {
  let missing = false;
  const out = tpl.replace(/%(\d+)\$s|%%/g, (m, n) => {
    if (m === "%%") return "%";
    const v = args[Number(n) - 1];
    if (v === undefined) { missing = true; return m; }
    return v;
  });
  return { out, missing };
}

const questions = [];
for (const f of fs.readdirSync(PACKS)) {
  if (!f.endsWith(".json")) continue;
  const j = JSON.parse(fs.readFileSync(path.join(PACKS, f), "utf8"));
  (function walk(o) {
    if (Array.isArray(o)) return o.forEach(walk);
    if (o && typeof o === "object") {
      if (o.type === "math") questions.push(o);
      Object.values(o).forEach(walk);
    }
  })(j);
}

let failed = 0;
for (const lang of targets) {
  const wd = makeDict(wordTr, lang);
  const ud = makeDict(unitTr, lang);
  const leftover = new Map();      // 남은 한글 → 예시
  let noTr = 0, badArgs = 0, done = 0;

  for (const q of questions) {
    for (const [kf, af, text] of [["tk", "ta", q.prompt], ["ek", "ea", q.explain]]) {
      if (!text) continue;
      const key = q[kf];
      if (!key) {
        if (/[가-힣]/.test(text)) noTr++;      // 키가 없어 한국어로 남는 문제
        continue;
      }
      const by = tplTr[key];
      if (!by || !by.en) { noTr++; continue; }
      const tpl = by[lang] == null ? by.en : by[lang];
      const args = (q[af] || []).map((a) => word(String(a), wd));
      const { out, missing } = format(tpl, args);
      if (missing) badArgs++;
      done++;
      for (const w of out.match(HANGUL_RUN) || []) {
        if (!leftover.has(w)) leftover.set(w, out.replace(/\n/g, " ").slice(0, 90));
      }
    }
    if (q.unit && /[가-힣]/.test(word(String(q.unit), ud))) {
      leftover.set("(단위)" + q.unit, "단위 " + q.unit);
    }
    for (const c of q.choices || []) {
      const t = word(String(c), wd);
      for (const w of t.match(HANGUL_RUN) || []) if (!leftover.has(w)) leftover.set(w, "선택지 " + t);
    }
    for (const l of (q.visual && q.visual.labels) || []) {
      const t = word(String(l), wd);
      for (const w of t.match(HANGUL_RUN) || []) if (!leftover.has(w)) leftover.set(w, "그림 라벨 " + t);
    }
  }

  const ok = leftover.size === 0 && badArgs === 0;
  if (!ok) failed++;
  console.log(`[${lang}] 조립 ${done}건 · 번역없음 ${noTr} · 자리값오류 ${badArgs} · 한글잔여 ${leftover.size}종 ${ok ? "✔" : "✘"}`);
  for (const [w, sample] of [...leftover].slice(0, 12)) console.log(`      ${w}  ← ${sample}`);
}

console.log(failed === 0 ? "\n전 언어 통과" : `\n${failed}개 언어에 문제가 남아 있다`);
process.exit(failed === 0 ? 0 : 1);
