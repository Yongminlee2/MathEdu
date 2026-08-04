/**
 * 수학 생성기의 한국어 문장에 **번역 키를 심는다.**
 *
 *   node tools/i18n/keyify.js          # 미리보기 (파일 안 고침)
 *   node tools/i18n/keyify.js --write  # 실제로 고침
 *
 * numQ/choiceQ/visualQ/textQ 에 **직접 넘긴** 문제문·해설만 골라
 *
 *     `${a} + ${b} 는 얼마일까요?`
 *   → tp("3f2a91c7", [a, b], `${a} + ${b} 는 얼마일까요?`)
 *
 * 이렇게 바꾼다. 한국어 원문은 그대로 남으므로 한국어 동작은 1도 안 변한다.
 * 키는 뼈대 문장의 해시라서, 같은 문장은 늘 같은 키가 되고 순서를 바꿔도 안 흔들린다.
 *
 * 뼈대는 tools/i18n/templates.json 에 모인다 → 여기에 12개 언어 번역을 붙인다.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const DIR = path.join(__dirname, "..", "math");
const FILES = ["lib.js", "elementary.js", "middle.js", "high.js", "word.js"];
const BUILDERS = ["numQ", "choiceQ", "visualQ", "textQ"];
const ARG_SLOTS = { 0: "prompt", 2: "explain" };   // 1번은 정답이라 건드리지 않는다
const BT = String.fromCharCode(96);
const WRITE = process.argv.includes("--write");

// ---------- 소스 훑기 (따옴표·템플릿·주석을 건너뛴다) ----------
function skipQuote(s, i) {
  const q = s[i]; i++;
  while (i < s.length) {
    if (s[i] === "\\") { i += 2; continue; }
    if (s[i] === q) return i + 1;
    i++;
  }
  return i;
}

function skipTemplate(s, i) {
  i++;
  while (i < s.length) {
    if (s[i] === "\\") { i += 2; continue; }
    if (s[i] === BT) return i + 1;
    if (s[i] === "$" && s[i + 1] === "{") { i = skipExpr(s, i + 2); continue; }
    i++;
  }
  return i;
}

/** ${ 다음부터 짝이 맞는 } 다음 위치까지 */
function skipExpr(s, i) {
  let d = 1;
  while (i < s.length && d > 0) {
    const c = s[i];
    if (c === "\\") { i += 2; continue; }
    if (c === '"' || c === "'") { i = skipQuote(s, i); continue; }
    if (c === BT) { i = skipTemplate(s, i); continue; }
    if (c === "{") d++;
    if (c === "}") d--;
    i++;
  }
  return i;
}

/** 여는 괄호 위치를 주면 인자마다 [시작, 끝) 를 돌려준다 */
function splitArgs(s, open) {
  const args = [];
  let depth = 0, i = open + 1, start = i;
  while (i < s.length) {
    const c = s[i];
    if (c === "\\") { i += 2; continue; }
    if (c === '"' || c === "'") { i = skipQuote(s, i); continue; }
    if (c === BT) { i = skipTemplate(s, i); continue; }
    if (c === "/" && s[i + 1] === "/") { while (i < s.length && s[i] !== "\n") i++; continue; }
    if (c === "/" && s[i + 1] === "*") { i = s.indexOf("*/", i) + 2; continue; }
    if (c === "(" || c === "[" || c === "{") { depth++; i++; continue; }
    if (c === ")" && depth === 0) { args.push([start, i]); return { args, end: i }; }
    if (c === ")" || c === "]" || c === "}") { depth--; i++; continue; }
    if (c === "," && depth === 0) { args.push([start, i]); start = i + 1; i++; continue; }
    i++;
  }
  return null;   // 괄호가 안 닫혔다 = 우리가 찾던 호출이 아니다
}

// ---------- 템플릿 → 뼈대 + 인자식 ----------
function toSkeleton(src) {
  const isTpl = src[0] === BT;
  const body = src.slice(1, -1);
  let out = "", i = 0;
  const exprs = [];
  while (i < body.length) {
    const c = body[i];
    if (c === "\\") { out += body.substr(i, 2); i += 2; continue; }
    if (isTpl && c === "$" && body[i + 1] === "{") {
      const st = i + 2;
      const end = skipExpr(body, st);          // 닫는 } 다음
      exprs.push(body.slice(st, end - 1).trim());
      out += "%" + exprs.length + "$s";
      i = end; continue;
    }
    if (c === "%") { out += "%%"; i++; continue; }   // 안드로이드 서식문자 회피
    out += c; i++;
  }
  return { skeleton: out, exprs };
}

const keyOf = (skel) => crypto.createHash("sha1").update(skel).digest("hex").slice(0, 8);

// ---------- 본작업 ----------
const manifest = {};        // key → { ko, args, n }
let nWrapped = 0, nSkipped = 0;

for (const f of FILES) {
  const p = path.join(DIR, f);
  let src = fs.readFileSync(p, "utf8");
  const edits = [];         // [시작, 끝, 새 텍스트]

  const call = new RegExp("\\b(" + BUILDERS.join("|") + ")\\s*\\(", "g");
  let m;
  while ((m = call.exec(src))) {
    // 함수 정의(function numQ(...))는 건너뛴다
    const before = src.slice(Math.max(0, m.index - 12), m.index);
    if (/function\s+$/.test(before)) continue;

    const open = m.index + m[0].length - 1;
    const parsed = splitArgs(src, open);
    if (!parsed) continue;

    for (const slot of Object.keys(ARG_SLOTS)) {
      const range = parsed.args[Number(slot)];
      if (!range) continue;
      let [a, b] = range;
      while (a < b && /\s/.test(src[a])) a++;
      while (b > a && /\s/.test(src[b - 1])) b--;
      const text = src.slice(a, b);
      if (!text) continue;
      if (text.startsWith("tp(")) continue;                    // 이미 처리됨

      const q = text[0];
      if (q !== BT && q !== '"' && q !== "'") { nSkipped++; continue; }   // 통짜 문장이 아님
      // 문자열 하나로 끝나는지 확인 (+ 로 이어붙인 식이면 건드리지 않는다)
      const close = q === BT ? skipTemplate(src, a) : skipQuote(src, a);
      if (close !== b) { nSkipped++; continue; }

      const { skeleton, exprs } = toSkeleton(text);
      if (!/[가-힣]/.test(skeleton)) continue;                 // 번역할 한글이 없다

      const key = keyOf(skeleton);
      const prev = manifest[key];
      if (prev) prev.n++;
      else manifest[key] = { ko: skeleton, args: exprs.length, n: 1 };

      edits.push([a, b, `tp("${key}", [${exprs.join(", ")}], ${text})`]);
      nWrapped++;
    }
    call.lastIndex = parsed.end;
  }

  edits.sort((x, y) => y[0] - x[0]);                            // 뒤에서부터 고쳐야 위치가 안 밀린다
  for (const [a, b, t] of edits) src = src.slice(0, a) + t + src.slice(b);

  // ---- 2차: 이미 tp(...) 로 감싸진 문장은 3번째 인자에서 키를 다시 계산해 박는다.
  // 손으로 tp("?", [...], `...`) 라고 써 두면 여기서 진짜 키가 채워진다.
  // 문장을 고치면 키도 따라 바뀌므로, 번역과 원문이 어긋나는 일이 없다.
  const fixes = [];
  const tpCall = /\btp\s*\(/g;
  let t;
  while ((t = tpCall.exec(src))) {
    // 주석 속 사용 예시(/** … tp("...", …) … */)는 진짜 호출이 아니다
    const open2 = src.lastIndexOf("/*", t.index);
    if (open2 !== -1 && src.indexOf("*/", open2) > t.index) continue;
    const open = t.index + t[0].length - 1;
    const parsed = splitArgs(src, open);
    if (!parsed || parsed.args.length < 3) continue;
    tpCall.lastIndex = parsed.end;
    if (/function\s+$/.test(src.slice(Math.max(0, t.index - 12), t.index))) continue;

    const [ta, tb] = parsed.args[2];
    const body = src.slice(ta, tb).trim();
    if (!body || (body[0] !== BT && body[0] !== '"' && body[0] !== "'")) continue;
    // 여기서는 한글이 있는지 따지지 않는다.
    // 뼈대에 한글이 없어도 **값 쪽에 한국어가 실려 오는** 경우가 있기 때문이다.
    //   `D = b² - 4ac = ${...}\n${D > 0 ? "서로 다른 두 실근" : ...}`
    // 뼈대만 보면 수식뿐이지만, 키가 없으면 앱이 조립을 못 해 한국어가 그대로 뜬다.
    // 손으로 tp() 를 씌웠다는 것 자체가 "이 문장은 번역 대상"이라는 뜻이다.
    const { skeleton, exprs } = toSkeleton(body);

    const key = keyOf(skeleton);
    if (manifest[key]) manifest[key].n++;
    else { manifest[key] = { ko: skeleton, args: exprs.length, n: 1 }; nWrapped++; }

    let [ka, kb] = parsed.args[0];
    while (ka < kb && /\s/.test(src[ka])) ka++;
    while (kb > ka && /\s/.test(src[kb - 1])) kb--;
    if (src.slice(ka, kb) !== `"${key}"`) fixes.push([ka, kb, `"${key}"`]);
  }
  fixes.sort((x, y) => y[0] - x[0]);
  for (const [a, b, v] of fixes) src = src.slice(0, a) + v + src.slice(b);
  if (fixes.length) console.log(`${f}: 키 ${fixes.length}개 갱신`);
  if (!edits.length && !fixes.length) continue;

  // tp 를 쓸 수 있게 require 목록에 넣어 준다
  if (f !== "lib.js" && !/[{,]\s*tp\s*[,}]/.test(src)) {
    src = src.replace(/const\s*\{\s*rint/, "const { tp, rint");
  }
  if (f === "lib.js" && !/^\s*const tp2 =/m.test(src)) {
    // lib.js 안에서는 tp 가 이미 같은 파일에 정의돼 있어 그대로 쓸 수 있다
  }

  console.log(`${f}: ${edits.length}곳`);
  if (WRITE) fs.writeFileSync(p, src, "utf8");
}

const keys = Object.keys(manifest);
console.log(`\n감싼 문장 ${nWrapped}곳 / 뼈대 ${keys.length}종 / 통짜가 아니라 건너뜀 ${nSkipped}곳`);
if (WRITE) {
  fs.writeFileSync(
    path.join(__dirname, "templates.json"),
    JSON.stringify(manifest, null, 1), "utf8"
  );
  console.log("→ tools/i18n/templates.json 저장");
} else {
  console.log("(미리보기입니다. 실제로 고치려면 --write)");
}
