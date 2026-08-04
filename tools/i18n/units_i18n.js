/**
 * 답 옆에 붙는 **단위** 번역. (cm·m·g·%·° 처럼 만국 공통인 것은 뺐다)
 *
 * 낱말 사전(words_i18n.js)과 따로 두는 이유: 겹치는 글자가 있다.
 *   그림 라벨의 "원" = 동그라미 / 단위의 "원" = 돈
 */
module.exports = {
  "개":  { en: "",        ja: "個",   zh: "个",   es: "",         fr: "",       de: "",        pt: "",        ru: "шт.",   vi: "cái",   th: "ชิ้น",     in: "buah" },
  "명":  { en: "people",  ja: "人",   zh: "人",   es: "personas", fr: "pers.",  de: "Pers.",   pt: "pessoas", ru: "чел.",  vi: "người", th: "คน",      in: "orang" },
  "분":  { en: "min",     ja: "分",   zh: "分",   es: "min",      fr: "min",    de: "Min.",    pt: "min",     ru: "мин",   vi: "phút",  th: "นาที",    in: "menit" },
  "초":  { en: "sec",     ja: "秒",   zh: "秒",   es: "s",        fr: "s",      de: "Sek.",    pt: "s",       ru: "сек",   vi: "giây",  th: "วินาที",  in: "detik" },
  "점":  { en: "pts",     ja: "点",   zh: "分",   es: "pts",      fr: "pts",    de: "Pkt.",    pt: "pts",     ru: "б.",    vi: "điểm",  th: "คะแนน",   in: "poin" },
  "원":  { en: "won",     ja: "ウォン", zh: "韩元", es: "wones",   fr: "wons",   de: "Won",     pt: "wons",    ru: "вон",   vi: "won",   th: "วอน",     in: "won" },
};
