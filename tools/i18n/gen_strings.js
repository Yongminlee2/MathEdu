/**
 * tools/i18n/strings.js 의 원장을 읽어 언어별 strings.xml 12개를 만든다.
 *
 *   node tools/i18n/gen_strings.js
 *
 * 기본(values/)은 영어 — 지원하지 않는 언어의 폰은 여기로 떨어진다.
 * 번역이 비어 있는 항목도 영어로 채워서, 화면에 빈칸이 뜨는 일이 없게 한다.
 */
const fs = require("fs");
const path = require("path");
const { langs, strings } = require("./strings");

const RES = path.join(__dirname, "..", "..", "app", "src", "main", "res");

/** XML 특수문자 + 안드로이드가 삼키는 문자 처리 */
function esc(s) {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/'/g, "\\'")
    .replace(/"/g, "\\\"")
    .replace(/\n/g, "\\n");
}

let missing = 0;
for (const lang of langs) {
  const dir = path.join(RES, lang === "en" ? "values" : "values-" + lang);
  fs.mkdirSync(dir, { recursive: true });

  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    "<!-- 자동 생성: node tools/i18n/gen_strings.js — 직접 고치지 말 것 -->",
    "<resources>",
  ];
  for (const [key, byLang] of Object.entries(strings)) {
    let v = byLang[lang];
    if (v == null || v === "") {
      v = byLang.en;             // 번역이 없으면 영어로 (빈칸 방지)
      if (lang !== "en") missing++;
    }
    lines.push(`    <string name="${key}">${esc(v)}</string>`);
  }
  lines.push("</resources>", "");
  fs.writeFileSync(path.join(dir, "strings.xml"), lines.join("\n"), "utf8");
}

const n = Object.keys(strings).length;
console.log(`언어 ${langs.length}종 × 문자열 ${n}개 생성`);
console.log(missing === 0 ? "번역 누락 없음" : `영어로 대체된 항목: ${missing}개`);
