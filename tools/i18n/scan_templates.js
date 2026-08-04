/**
 * 수학 생성기(tools/math/*.js)에서 문제·해설로 쓰이는 한글 템플릿을 뽑아 센다.
 *
 *   node tools/i18n/scan_templates.js
 *
 * 번역해야 할 대상이 정확히 몇 개인지, 어떤 것들인지 보기 위한 도구.
 */
const fs = require("fs");
const path = require("path");

const DIR = path.join(__dirname, "..", "math");
const FILES = ["lib.js", "elementary.js", "middle.js", "high.js", "word.js"];
const BT = String.fromCharCode(96);   // 백틱

const found = new Map();

for (const f of FILES) {
  const src = fs.readFileSync(path.join(DIR, f), "utf8");
  let i = 0;
  while (i < src.length) {
    if (src[i] !== BT) { i++; continue; }
    let j = i + 1, depth = 0, s = "";
    while (j < src.length) {
      const c = src[j];
      if (c === "\\") { s += src.substr(j, 2); j += 2; continue; }
      if (c === "$" && src[j + 1] === "{") depth++;
      if (c === "}" && depth > 0) depth--;
      if (c === BT && depth === 0) break;
      s += c; j++;
    }
    if (/[가-힣]/.test(s)) found.set(s, (found.get(s) || 0) + 1);
    i = j + 1;
  }
}

const arr = [...found.keys()];
console.log("한글 템플릿:", arr.length, "개");
console.log("평균 길이:", Math.round(arr.reduce((a, b) => a + b.length, 0) / arr.length), "자");
fs.writeFileSync(
  path.join(__dirname, "templates_found.txt"),
  arr.map((s, i) => "[" + (i + 1) + "] " + s.replace(/\n/g, " ⏎ ")).join("\n"),
  "utf8"
);
console.log("→ tools/i18n/templates_found.txt 에 저장");
