/**
 * 구워진 팩을 훑어 **번역 우선순위**를 매긴다.
 *
 *   node tools/i18n/rank.js
 *
 * - templates.json 의 뼈대마다 "실제로 몇 문제에 쓰였는지"(use)를 채운다
 * - 인자·선택지·그림 라벨에 박혀 오는 한국어 낱말을 words.json 으로 모은다
 *
 * 많이 쓰이는 뼈대부터 번역하면 적은 수고로 많은 문제를 덮을 수 있다.
 */
const fs = require("fs");
const path = require("path");

const PACKS = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "packs");
const tpl = JSON.parse(fs.readFileSync(path.join(__dirname, "templates.json"), "utf8"));

const use = {};
const words = {};
let total = 0, keyed = 0, noKo = 0;

const bumpWords = (s) => {
  const m = String(s).match(/[가-힣]+/g);
  if (m) for (const w of m) words[w] = (words[w] || 0) + 1;
};

for (const f of fs.readdirSync(PACKS)) {
  if (!f.endsWith(".json")) continue;
  const j = JSON.parse(fs.readFileSync(path.join(PACKS, f), "utf8"));
  const qs = [];
  (function walk(o) {
    if (Array.isArray(o)) return o.forEach(walk);
    if (o && typeof o === "object") {
      if (o.type === "math") qs.push(o);
      Object.values(o).forEach(walk);
    }
  })(j);

  for (const q of qs) {
    total++;
    if (q.tk) { keyed++; use[q.tk] = (use[q.tk] || 0) + 1; }
    else if (!/[가-힣]/.test(q.prompt)) noKo++;
    if (q.ek) use[q.ek] = (use[q.ek] || 0) + 1;
    (q.ta || []).forEach(bumpWords);
    (q.ea || []).forEach(bumpWords);
    (q.choices || []).forEach(bumpWords);
    if (q.unit) bumpWords(q.unit);
    if (q.visual && q.visual.labels) q.visual.labels.forEach(bumpWords);
  }
}

for (const [k, v] of Object.entries(tpl)) v.use = use[k] || 0;

// 많이 쓰인 순서로 다시 쓴다 — 위에서부터 번역하면 된다
const sorted = Object.fromEntries(
  Object.entries(tpl).sort((a, b) => b[1].use - a[1].use || a[0].localeCompare(b[0]))
);
fs.writeFileSync(path.join(__dirname, "templates.json"), JSON.stringify(sorted, null, 1), "utf8");

const wsorted = Object.entries(words).sort((a, b) => b[1] - a[1]);
fs.writeFileSync(
  path.join(__dirname, "words.json"),
  JSON.stringify(Object.fromEntries(wsorted), null, 1), "utf8"
);

let acc = 0;
const ranks = Object.values(sorted);
const need = ranks.reduce((s, v) => s + v.use, 0);
let n80 = 0;
for (const v of ranks) { acc += v.use; n80++; if (acc >= need * 0.8) break; }

console.log(`문제 ${total} · 키 붙음 ${keyed} · 한글 없음(번역 불필요) ${noKo}`);
console.log(`뼈대 ${ranks.length}종 — 상위 ${n80}종이 한글 문제의 80%`);
console.log(`인자 속 한국어 낱말 ${wsorted.length}종`);
