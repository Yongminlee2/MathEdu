/**
 * 팩의 유닛·레슨 **제목**에 번역 키(tk)와 인자(ta)를 붙인다.
 *
 *   node tools/i18n/tag_titles.js
 *
 * - 한국어 제목 문자열은 **한 글자도 안 바꾼다** — tk/ta 필드만 얹는다.
 * - 유닛 이름은 words 사전(units_title*.js)이 갈아 끼우고, 레슨은 "이름 N" 꼴이라
 *   t_num 뼈대에 (이름, 번호)를 넣는다.
 * - 자기 검증: 뼈대에 인자를 도로 끼우면 원래 한국어 제목과 정확히 같아야 한다.
 * - **콘텐츠를 재생성(gen_math.js)하면 tk 가 사라지므로 이 스크립트를 다시 돌릴 것.**
 *
 * 트랙 제목은 팩이 아니라 MathGrades 의 리소스를 쓰므로 여기서 손대지 않는다.
 */
const fs = require("fs");
const path = require("path");

const PACKS = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "packs");
const TPL_JSON = path.join(__dirname, "templates.json");
const WORDS = new Set(Object.keys(require("./words_i18n.js")));

// 뼈대: 이름만 / 이름+번호
const SKELETONS = {
  t_word: "%1$s",
  t_num: "%1$s %2$s",
};

function rebuild(key, args) {
  return SKELETONS[key].replace(/%(\d+)\$s/g, (_, n) => args[Number(n) - 1]);
}

function tag(obj, title) {
  // "이름 3" 처럼 뒤에 번호가 붙은 레슨
  let m = title.match(/^(.+) (\d+)$/);
  if (m && WORDS.has(m[1])) {
    if (rebuild("t_num", [m[1], m[2]]) !== title) return false;
    obj.tk = "t_num";
    obj.ta = [m[1], m[2]];
    return true;
  }
  if (WORDS.has(title)) {
    obj.tk = "t_word";
    obj.ta = [title];
    return true;
  }
  return false;
}

// ---- templates.json 에 뼈대 등록 ----
const tpl = JSON.parse(fs.readFileSync(TPL_JSON, "utf8"));
for (const [key, ko] of Object.entries(SKELETONS)) {
  tpl[key] = { ko, args: (ko.match(/%\d+\$s/g) || []).length, n: tpl[key] ? tpl[key].n : 0 };
}
fs.writeFileSync(TPL_JSON, JSON.stringify(tpl, null, 1), "utf8");

// ---- 팩 태깅 ----
let ok = 0, skip = 0;
const skipped = new Map();
for (const f of fs.readdirSync(PACKS)) {
  if (f === "index.json") continue;
  const p = JSON.parse(fs.readFileSync(path.join(PACKS, f), "utf8"));
  for (const u of p.units || []) {
    if (tag(u, u.title)) ok++;
    else { skip++; skipped.set(u.title, (skipped.get(u.title) || 0) + 1); }
    for (const l of u.lessons || []) {
      if (tag(l, l.title)) ok++;
      else { skip++; skipped.set(l.title, (skipped.get(l.title) || 0) + 1); }
    }
  }
  fs.writeFileSync(path.join(PACKS, f), JSON.stringify(p), "utf8");
}
console.log(`태깅 ${ok}건 · 미태깅 ${skip}건`);
const un = [...skipped.entries()].sort((a, b) => b[1] - a[1]);
if (un.length) {
  console.log("미태깅 제목:");
  for (const [t, n] of un.slice(0, 12)) console.log(`  ${n}× ${t}`);
  if (un.length > 12) console.log(`  … 외 ${un.length - 12}종`);
}
