/**
 * 문제 안에 **값으로** 박혀 오는 한국어 낱말·구절의 번역.
 *
 * 뼈대(templates_i18n.js)가 문장 틀이라면, 이건 그 틀에 끼워 넣는 알맹이다.
 *   뼈대 : "%1$s 모양은 몇 번일까요?"  →  "Which one is the %1$s?"
 *   낱말 : "삼각형" → "triangle"
 *
 * 앱은 값 안의 한글 덩어리를 하나씩 이 표에서 찾아 바꾼다.
 * 표에 없으면 한국어 그대로 둔다 — 반쪽이라도 읽히는 편이 낫다.
 * 통째로 등록된 긴 구절이 낱말보다 먼저다 (문장을 낱말로 쪼개면 말이 안 되니까).
 */
module.exports = {
  // ---------- 과일·사물 ----------
  "사과":   { en: "apple",  ja: "りんご",   zh: "苹果", es: "manzana",  fr: "pomme",   de: "Apfel",     pt: "maçã",     ru: "яблоко",    vi: "táo",      th: "แอปเปิล",     in: "apel" },
  "딸기":   { en: "strawberry", ja: "いちご", zh: "草莓", es: "fresa", fr: "fraise", de: "Erdbeere", pt: "morango", ru: "клубника", vi: "dâu", th: "สตรอว์เบอร์รี", in: "stroberi" },
  "포도":   { en: "grapes", ja: "ぶどう",   zh: "葡萄", es: "uvas",     fr: "raisin",  de: "Trauben",   pt: "uvas",     ru: "виноград",  vi: "nho",      th: "องุ่น",       in: "anggur" },
  "바나나": { en: "banana",  ja: "バナナ",   zh: "香蕉", es: "plátano",  fr: "banane",  de: "Banane",    pt: "banana",   ru: "банан",     vi: "chuối",    th: "กล้วย",       in: "pisang" },
  "귤":     { en: "tangerine", ja: "みかん", zh: "橘子", es: "mandarina", fr: "mandarine", de: "Mandarine", pt: "tangerina", ru: "мандарин", vi: "quýt", th: "ส้ม", in: "jeruk" },
  "쿠키":   { en: "cookie",  ja: "クッキー", zh: "饼干", es: "galleta",  fr: "biscuit", de: "Keks",      pt: "biscoito", ru: "печенье",   vi: "bánh quy", th: "คุกกี้",      in: "kue" },
  "사탕":   { en: "candy",   ja: "あめ",     zh: "糖果", es: "caramelo", fr: "bonbon",  de: "Bonbon",    pt: "bala",     ru: "конфета",   vi: "kẹo",      th: "ลูกอม",       in: "permen" },
  "구슬":   { en: "marble",  ja: "ビー玉",   zh: "弹珠", es: "canica",   fr: "bille",   de: "Murmel",    pt: "bolinha",  ru: "шарик",     vi: "viên bi",  th: "ลูกแก้ว",     in: "kelereng" },
  "스티커": { en: "sticker", ja: "シール",   zh: "贴纸", es: "pegatina", fr: "autocollant", de: "Sticker", pt: "adesivo", ru: "наклейка", vi: "hình dán", th: "สติกเกอร์",   in: "stiker" },
  "풍선":   { en: "balloon", ja: "ふうせん", zh: "气球", es: "globo",    fr: "ballon",  de: "Luftballon", pt: "balão",   ru: "воздушный шар", vi: "bóng bay", th: "ลูกโป่ง", in: "balon" },
  "블록":   { en: "block",   ja: "ブロック", zh: "积木", es: "bloque",   fr: "bloc",    de: "Baustein",  pt: "bloco",    ru: "кубик",     vi: "khối",     th: "บล็อก",       in: "balok" },
  "도토리": { en: "acorn",   ja: "どんぐり", zh: "橡子", es: "bellota",  fr: "gland",   de: "Eichel",    pt: "bolota",   ru: "жёлудь",    vi: "hạt sồi",  th: "ลูกโอ๊ก",     in: "biji ek" },
  "색연필": { en: "crayon",  ja: "いろえんぴつ", zh: "彩色铅笔", es: "lápiz de color", fr: "crayon de couleur", de: "Buntstift", pt: "lápis de cor", ru: "карандаш", vi: "bút chì màu", th: "ดินสอสี", in: "pensil warna" },
  "상자":   { en: "box",     ja: "はこ",     zh: "盒子", es: "caja",     fr: "boîte",   de: "Kiste",     pt: "caixa",    ru: "коробка",   vi: "hộp",      th: "กล่อง",       in: "kotak" },
  "접시":   { en: "plate",   ja: "おさら",   zh: "盘子", es: "plato",    fr: "assiette", de: "Teller",   pt: "prato",    ru: "тарелка",   vi: "đĩa",      th: "จาน",         in: "piring" },
  "바구니": { en: "basket",  ja: "かご",     zh: "篮子", es: "cesta",    fr: "panier",  de: "Korb",      pt: "cesta",    ru: "корзина",   vi: "giỏ",      th: "ตะกร้า",      in: "keranjang" },
  "봉지":   { en: "bag",     ja: "ふくろ",   zh: "袋子", es: "bolsa",    fr: "sachet",  de: "Tüte",      pt: "saco",     ru: "пакет",     vi: "túi",      th: "ถุง",         in: "kantong" },
  "가방":   { en: "backpack", ja: "かばん",  zh: "书包", es: "mochila",  fr: "sac",     de: "Tasche",    pt: "mochila",  ru: "рюкзак",    vi: "cặp",      th: "กระเป๋า",     in: "tas" },

  // ---------- 도형 ----------
  "원":     { en: "circle",   ja: "円",     zh: "圆",   es: "círculo",  fr: "cercle",  de: "Kreis",     pt: "círculo",  ru: "круг",      vi: "hình tròn", th: "วงกลม",      in: "lingkaran" },
  "삼각형": { en: "triangle", ja: "三角形", zh: "三角形", es: "triángulo", fr: "triangle", de: "Dreieck", pt: "triângulo", ru: "треугольник", vi: "tam giác", th: "สามเหลี่ยม", in: "segitiga" },
  "사각형": { en: "square",   ja: "四角形", zh: "四边形", es: "cuadrado", fr: "carré",   de: "Viereck",   pt: "quadrado", ru: "квадрат",   vi: "hình vuông", th: "สี่เหลี่ยม", in: "persegi" },
  "오각형": { en: "pentagon", ja: "五角形", zh: "五边形", es: "pentágono", fr: "pentagone", de: "Fünfeck", pt: "pentágono", ru: "пятиугольник", vi: "ngũ giác", th: "ห้าเหลี่ยม", in: "segilima" },
  "육각형": { en: "hexagon",  ja: "六角形", zh: "六边形", es: "hexágono", fr: "hexagone", de: "Sechseck", pt: "hexágono", ru: "шестиугольник", vi: "lục giác", th: "หกเหลี่ยม", in: "segienam" },
  "예각":   { en: "acute angle", ja: "鋭角", zh: "锐角", es: "ángulo agudo", fr: "angle aigu", de: "spitzer Winkel", pt: "ângulo agudo", ru: "острый угол", vi: "góc nhọn", th: "มุมแหลม", in: "sudut lancip" },
  "직각":   { en: "right angle", ja: "直角", zh: "直角", es: "ángulo recto", fr: "angle droit", de: "rechter Winkel", pt: "ângulo reto", ru: "прямой угол", vi: "góc vuông", th: "มุมฉาก", in: "sudut siku-siku" },
  "둔각":   { en: "obtuse angle", ja: "鈍角", zh: "钝角", es: "ángulo obtuso", fr: "angle obtus", de: "stumpfer Winkel", pt: "ângulo obtuso", ru: "тупой угол", vi: "góc tù", th: "มุมป้าน", in: "sudut tumpul" },
  "평각":   { en: "straight angle", ja: "平角", zh: "平角", es: "ángulo llano", fr: "angle plat", de: "gestreckter Winkel", pt: "ângulo raso", ru: "развёрнутый угол", vi: "góc bẹt", th: "มุมตรง", in: "sudut lurus" },

  // ---------- 운동 (그래프 항목) ----------
  "축구":   { en: "soccer",   ja: "サッカー", zh: "足球", es: "fútbol",   fr: "football", de: "Fußball",  pt: "futebol",  ru: "футбол",    vi: "bóng đá",  th: "ฟุตบอล",     in: "sepak bola" },
  "야구":   { en: "baseball", ja: "野球",     zh: "棒球", es: "béisbol",  fr: "baseball", de: "Baseball", pt: "beisebol", ru: "бейсбол",   vi: "bóng chày", th: "เบสบอล",    in: "bisbol" },
  "농구":   { en: "basketball", ja: "バスケ", zh: "篮球", es: "baloncesto", fr: "basket", de: "Basketball", pt: "basquete", ru: "баскетбол", vi: "bóng rổ", th: "บาสเกตบอล", in: "bola basket" },
  "수영":   { en: "swimming", ja: "水泳",     zh: "游泳", es: "natación", fr: "natation", de: "Schwimmen", pt: "natação", ru: "плавание",  vi: "bơi lội",  th: "ว่ายน้ำ",     in: "renang" },
  "달리기": { en: "running",  ja: "かけっこ", zh: "跑步", es: "correr",   fr: "course",  de: "Laufen",    pt: "corrida",  ru: "бег",       vi: "chạy",     th: "วิ่ง",        in: "lari" },

  // ---------- 등장인물 ----------
  "삐약이": { en: "Peep",    ja: "ピープ", zh: "啾啾",  es: "Peep",   fr: "Peep",   de: "Peep",     pt: "Peep",    ru: "Пип",      vi: "Peep",    th: "พียัค",       in: "Peep" },
  "토끼":   { en: "Rabbit",   ja: "うさぎ",   zh: "兔子",  es: "Conejo",  fr: "Lapin",   de: "Hase",      pt: "Coelho",   ru: "Кролик",    vi: "Thỏ",      th: "กระต่าย",     in: "Kelinci" },
  "펭귄":   { en: "Penguin",  ja: "ペンギン", zh: "企鹅",  es: "Pingüino", fr: "Pingouin", de: "Pinguin", pt: "Pinguim",  ru: "Пингвин",   vi: "Chim cánh cụt", th: "เพนกวิน", in: "Penguin" },
  "고양이": { en: "Cat",      ja: "ねこ",     zh: "小猫",  es: "Gato",    fr: "Chat",    de: "Katze",     pt: "Gato",     ru: "Кот",       vi: "Mèo",      th: "แมว",         in: "Kucing" },
  "곰돌이": { en: "Bear",     ja: "くまさん", zh: "小熊",  es: "Osito",   fr: "Ourson",  de: "Bärchen",   pt: "Ursinho",  ru: "Мишка",     vi: "Gấu",      th: "หมี",         in: "Beruang" },
  "다람쥐": { en: "Squirrel", ja: "りす",     zh: "松鼠",  es: "Ardilla", fr: "Écureuil", de: "Eichhörnchen", pt: "Esquilo", ru: "Белка",  vi: "Sóc",      th: "กระรอก",      in: "Tupai" },

  // 아이 이름은 로마자로 통일한다 (읽는 사람이 사람 이름임을 바로 알 수 있게)
  "서연":   { en: "Seoyeon", ja: "ソヨン",   zh: "序妍",  es: "Seoyeon", fr: "Seoyeon", de: "Seoyeon",   pt: "Seoyeon",  ru: "Соён",      vi: "Seoyeon",  th: "ซอยอน",       in: "Seoyeon" },
  "수아":   { en: "Sua",     ja: "スア",     zh: "秀雅",  es: "Sua",     fr: "Sua",     de: "Sua",       pt: "Sua",      ru: "Суа",       vi: "Sua",      th: "ซูอา",        in: "Sua" },
  "시우":   { en: "Siu",     ja: "シウ",     zh: "施宇",  es: "Siu",     fr: "Siu",     de: "Siu",       pt: "Siu",      ru: "Сиу",       vi: "Siu",      th: "ซีอู",        in: "Siu" },
  "채원":   { en: "Chaewon", ja: "チェウォン", zh: "彩园", es: "Chaewon", fr: "Chaewon", de: "Chaewon",  pt: "Chaewon",  ru: "Чэвон",     vi: "Chaewon",  th: "แชวอน",       in: "Chaewon" },
  "도윤":   { en: "Doyun",   ja: "ドユン",   zh: "道润",  es: "Doyun",   fr: "Doyun",   de: "Doyun",     pt: "Doyun",    ru: "Тоюн",      vi: "Doyun",    th: "โดยุน",       in: "Doyun" },
  "예린":   { en: "Yerin",   ja: "イェリン", zh: "艺琳",  es: "Yerin",   fr: "Yerin",   de: "Yerin",     pt: "Yerin",    ru: "Йерин",     vi: "Yerin",    th: "เยริน",       in: "Yerin" },
  "은우":   { en: "Eunu",    ja: "ウヌ",     zh: "恩宇",  es: "Eunu",    fr: "Eunu",    de: "Eunu",      pt: "Eunu",     ru: "Ыну",       vi: "Eunu",     th: "อึนอู",       in: "Eunu" },
  "지우":   { en: "Jiu",     ja: "ジウ",     zh: "智宇",  es: "Jiu",     fr: "Jiu",     de: "Jiu",       pt: "Jiu",      ru: "Чиу",       vi: "Jiu",      th: "จีอู",        in: "Jiu" },
  "민수":   { en: "Minsu",   ja: "ミンス",   zh: "敏秀",  es: "Minsu",   fr: "Minsu",   de: "Minsu",     pt: "Minsu",    ru: "Минсу",     vi: "Minsu",    th: "มินซู",       in: "Minsu" },
  "하준":   { en: "Hajun",   ja: "ハジュン", zh: "河俊",  es: "Hajun",   fr: "Hajun",   de: "Hajun",     pt: "Hajun",    ru: "Хаджун",    vi: "Hajun",    th: "ฮาจุน",       in: "Hajun" },

  // ---------- 요일 (막대그래프 항목) ----------
  "월": { en: "Mon", ja: "月", zh: "周一", es: "lun", fr: "lun", de: "Mo", pt: "seg", ru: "пн", vi: "T2", th: "จ.",  in: "Sen" },
  "화": { en: "Tue", ja: "火", zh: "周二", es: "mar", fr: "mar", de: "Di", pt: "ter", ru: "вт", vi: "T3", th: "อ.",  in: "Sel" },
  "수": { en: "Wed", ja: "水", zh: "周三", es: "mié", fr: "mer", de: "Mi", pt: "qua", ru: "ср", vi: "T4", th: "พ.",  in: "Rab" },
  "목": { en: "Thu", ja: "木", zh: "周四", es: "jue", fr: "jeu", de: "Do", pt: "qui", ru: "чт", vi: "T5", th: "พฤ.", in: "Kam" },
  "금": { en: "Fri", ja: "金", zh: "周五", es: "vie", fr: "ven", de: "Fr", pt: "sex", ru: "пт", vi: "T6", th: "ศ.",  in: "Jum" },

  // ---------- 세는 말 (유치원 "하나, 둘, 셋…") ----------
  "영":   { en: "zero",  ja: "ゼロ",   zh: "零", es: "cero",   fr: "zéro",   de: "null",   pt: "zero",  ru: "ноль",     vi: "không", th: "ศูนย์",  in: "nol" },
  "하나": { en: "one",   ja: "いち",   zh: "一", es: "uno",    fr: "un",     de: "eins",   pt: "um",    ru: "один",     vi: "một",   th: "หนึ่ง",  in: "satu" },
  "둘":   { en: "two",   ja: "に",     zh: "二", es: "dos",    fr: "deux",   de: "zwei",   pt: "dois",  ru: "два",      vi: "hai",   th: "สอง",    in: "dua" },
  "셋":   { en: "three", ja: "さん",   zh: "三", es: "tres",   fr: "trois",  de: "drei",   pt: "três",  ru: "три",      vi: "ba",    th: "สาม",    in: "tiga" },
  "넷":   { en: "four",  ja: "し",     zh: "四", es: "cuatro", fr: "quatre", de: "vier",   pt: "quatro", ru: "четыре",  vi: "bốn",   th: "สี่",     in: "empat" },
  "다섯": { en: "five",  ja: "ご",     zh: "五", es: "cinco",  fr: "cinq",   de: "fünf",   pt: "cinco", ru: "пять",     vi: "năm",   th: "ห้า",     in: "lima" },
  "여섯": { en: "six",   ja: "ろく",   zh: "六", es: "seis",   fr: "six",    de: "sechs",  pt: "seis",  ru: "шесть",    vi: "sáu",   th: "หก",     in: "enam" },
  "일곱": { en: "seven", ja: "なな",   zh: "七", es: "siete",  fr: "sept",   de: "sieben", pt: "sete",  ru: "семь",     vi: "bảy",   th: "เจ็ด",   in: "tujuh" },
  "여덟": { en: "eight", ja: "はち",   zh: "八", es: "ocho",   fr: "huit",   de: "acht",   pt: "oito",  ru: "восемь",   vi: "tám",   th: "แปด",    in: "delapan" },
  "아홉": { en: "nine",  ja: "きゅう", zh: "九", es: "nueve",  fr: "neuf",   de: "neun",   pt: "nove",  ru: "девять",   vi: "chín",  th: "เก้า",    in: "sembilan" },
  "열":   { en: "ten",   ja: "じゅう", zh: "十", es: "diez",   fr: "dix",    de: "zehn",   pt: "dez",   ru: "десять",   vi: "mười",  th: "สิบ",    in: "sepuluh" },

  // ---------- 선택지 ----------
  "왼쪽":       { en: "Left",     ja: "左",       zh: "左边", es: "Izquierda", fr: "Gauche", de: "Links",  pt: "Esquerda", ru: "Слева",  vi: "Bên trái", th: "ซ้าย",   in: "Kiri" },
  "오른쪽":     { en: "Right",    ja: "右",       zh: "右边", es: "Derecha",  fr: "Droite",  de: "Rechts",  pt: "Direita",  ru: "Справа", vi: "Bên phải", th: "ขวา",    in: "Kanan" },
  "같아요":     { en: "The same", ja: "同じ",     zh: "一样", es: "Iguales",  fr: "Pareil",  de: "Gleich",  pt: "Iguais",   ru: "Поровну", vi: "Bằng nhau", th: "เท่ากัน", in: "Sama" },
  "모르겠어요": { en: "Not sure", ja: "わからない", zh: "不知道", es: "No sé", fr: "Je ne sais pas", de: "Weiß nicht", pt: "Não sei", ru: "Не знаю", vi: "Không biết", th: "ไม่รู้", in: "Tidak tahu" },
  "없어요":     { en: "None",     ja: "ない",     zh: "没有", es: "Ninguno",  fr: "Aucun",   de: "Keins",   pt: "Nenhum",   ru: "Нет",    vi: "Không có", th: "ไม่มี",   in: "Tidak ada" },

  // ---------- 그림 속 바구니 이름 ----------
  "모으는 상자": { en: "Collect", ja: "あつめる", zh: "收集", es: "Juntar",  fr: "Rassembler", de: "Sammeln", pt: "Juntar", ru: "Собрать", vi: "Gom lại", th: "รวบรวม", in: "Kumpulkan" },
  "집으로":     { en: "Go home",  ja: "おうちへ", zh: "回家", es: "A casa",  fr: "À la maison", de: "Nach Hause", pt: "Para casa", ru: "Домой", vi: "Về nhà", th: "กลับบ้าน", in: "Pulang" },

  // ---------- 붙어 오는 꼬리말 ----------
  // "C반" → "C" / "40대" → "40s" / "10시 30분" → "10h 30m"
  "반": { en: "",  ja: "組", zh: "班",   es: "",  fr: "",  de: "",  pt: "",  ru: "",  vi: "",  th: "",  in: "" },
  "대": { en: "s", ja: "代", zh: "多岁", es: "s", fr: "s", de: "er", pt: "s", ru: "-е", vi: "tuổi", th: "ปี", in: "-an" },
  "시": { en: "h", ja: "時", zh: "点",   es: "h", fr: "h", de: "Uhr", pt: "h", ru: "ч", vi: "giờ", th: "น.", in: "j" },
  "분": { en: "m", ja: "分", zh: "分",   es: "m", fr: "m", de: "min", pt: "m", ru: "м", vi: "phút", th: "นาที", in: "m" },

  // ---------- 도형 분류 안내문 (줄 단위로 갈아 끼운다) ----------
  "원은 뾰족한 곳이 없고 동그란 모양이에요.": {
    en: "A circle has no corners and is perfectly round.",
    ja: "円は とがった ところが なくて まるい かたちです。",
    zh: "圆没有尖角，是圆圆的形状。",
    es: "El círculo no tiene esquinas y es completamente redondo.",
    fr: "Le cercle n'a pas de coins, il est tout rond.",
    de: "Der Kreis hat keine Ecken und ist ganz rund.",
    pt: "O círculo não tem cantos e é todo redondo.",
    ru: "У круга нет углов, он совсем круглый.",
    vi: "Hình tròn không có góc nhọn và tròn đều.",
    th: "วงกลมไม่มีมุมแหลม เป็นรูปกลม ๆ",
    in: "Lingkaran tidak punya sudut dan bentuknya bulat.",
  },
  "삼각형은 뾰족한 곳이 3개 모양이에요.": {
    en: "A triangle has 3 corners.",
    ja: "三角形は とがった ところが 3つ あります。",
    zh: "三角形有3个尖角。",
    es: "El triángulo tiene 3 esquinas.",
    fr: "Le triangle a 3 coins.",
    de: "Das Dreieck hat 3 Ecken.",
    pt: "O triângulo tem 3 cantos.",
    ru: "У треугольника 3 угла.",
    vi: "Hình tam giác có 3 góc nhọn.",
    th: "สามเหลี่ยมมีมุมแหลม 3 มุม",
    in: "Segitiga punya 3 sudut.",
  },
  "사각형은 뾰족한 곳이 4개 모양이에요.": {
    en: "A square has 4 corners.",
    ja: "四角形は とがった ところが 4つ あります。",
    zh: "四边形有4个尖角。",
    es: "El cuadrado tiene 4 esquinas.",
    fr: "Le carré a 4 coins.",
    de: "Das Viereck hat 4 Ecken.",
    pt: "O quadrado tem 4 cantos.",
    ru: "У квадрата 4 угла.",
    vi: "Hình vuông có 4 góc nhọn.",
    th: "สี่เหลี่ยมมีมุมแหลม 4 มุม",
    in: "Persegi punya 4 sudut.",
  },

  // ---------- 판별식 결론 ----------
  "D > 0 이므로 서로 다른 두 실근": {
    en: "D > 0, so there are two different real roots",
    ja: "D > 0 なので異なる2つの実数解", zh: "D > 0，所以有两个不同的实根",
    es: "D > 0, así que hay dos raíces reales distintas", fr: "D > 0, donc deux racines réelles distinctes",
    de: "D > 0, also zwei verschiedene reelle Lösungen", pt: "D > 0, então há duas raízes reais distintas",
    ru: "D > 0, значит два различных действительных корня", vi: "D > 0 nên có hai nghiệm thực phân biệt",
    th: "D > 0 จึงมีรากจริงสองค่าที่ต่างกัน", in: "D > 0, jadi ada dua akar real berbeda",
  },
  "D = 0 이므로 중근": {
    en: "D = 0, so there is a repeated root",
    ja: "D = 0 なので重解", zh: "D = 0，所以是重根",
    es: "D = 0, así que hay una raíz doble", fr: "D = 0, donc une racine double",
    de: "D = 0, also eine doppelte Lösung", pt: "D = 0, então há uma raiz dupla",
    ru: "D = 0, значит корень кратный", vi: "D = 0 nên có nghiệm kép",
    th: "D = 0 จึงมีรากซ้ำ", in: "D = 0, jadi akarnya kembar",
  },
  "D < 0 이므로 실근이 없어요": {
    en: "D < 0, so there are no real roots",
    ja: "D < 0 なので実数解はありません", zh: "D < 0，所以没有实根",
    es: "D < 0, así que no hay raíces reales", fr: "D < 0, donc pas de racine réelle",
    de: "D < 0, also gibt es keine reellen Lösungen", pt: "D < 0, então não há raízes reais",
    ru: "D < 0, значит действительных корней нет", vi: "D < 0 nên không có nghiệm thực",
    th: "D < 0 จึงไม่มีรากจริง", in: "D < 0, jadi tidak ada akar real",
  },

  // ---------- 해설 중간에 갈아 끼우는 구절 ----------
  "빼면":  { en: "Subtract", ja: "引く",  zh: "减去", es: "Resta",   fr: "Soustrais", de: "Subtrahiere", pt: "Subtraia", ru: "Вычтем", vi: "Trừ",  th: "ลบ",   in: "Kurangi" },
  "더하면": { en: "Add",     ja: "足す",  zh: "加上", es: "Suma",    fr: "Ajoute",    de: "Addiere",     pt: "Some",     ru: "Прибавим", vi: "Cộng", th: "บวก",  in: "Tambah" },

  "(10 넘으면 십의 자리로 받아올림!)": {
    en: "(over 10 — carry 1 to the tens place!)",
    ja: "(10 をこえたら十のくらいにくり上がり!)",
    zh: "(超过10就向十位进1!)",
    es: "(¡pasa de 10, lleva 1 a las decenas!)",
    fr: "(plus de 10 — retiens 1 aux dizaines !)",
    de: "(über 10 — 1 zum Zehner übertragen!)",
    pt: "(passou de 10 — vai 1 para as dezenas!)",
    ru: "(больше 10 — переносим 1 в десятки!)",
    vi: "(quá 10 — nhớ 1 sang hàng chục!)",
    th: "(เกิน 10 ทดไปหลักสิบ 1!)",
    in: "(lebih dari 10 — simpan 1 ke puluhan!)",
  },
  "일의 자리를 뺄 수 없으니 십의 자리에서 10을 빌려와요.": {
    en: "The ones place is too small, so borrow 10 from the tens place.",
    ja: "一のくらいが引けないので、十のくらいから 10 を借ります。",
    zh: "个位不够减，所以从十位借10。",
    es: "Las unidades no alcanzan, así que pide 10 prestado a las decenas.",
    fr: "Les unités ne suffisent pas : emprunte 10 aux dizaines.",
    de: "Die Einer reichen nicht, also borge 10 von den Zehnern.",
    pt: "As unidades não bastam, então pegue 10 emprestado das dezenas.",
    ru: "В разряде единиц не хватает — занимаем 10 у десятков.",
    vi: "Hàng đơn vị không đủ trừ, nên mượn 10 từ hàng chục.",
    th: "หลักหน่วยลบไม่ได้ จึงยืม 10 จากหลักสิบ",
    in: "Satuan tidak cukup, jadi pinjam 10 dari puluhan.",
  },
  "부호가 다르면 절댓값이 큰 쪽 부호를 따라요.": {
    en: "When the signs differ, the answer takes the sign of the bigger absolute value.",
    ja: "符号がちがうときは、絶対値が大きいほうの符号になります。",
    zh: "符号不同时，取绝对值较大的那个符号。",
    es: "Si los signos son distintos, se toma el signo del mayor valor absoluto.",
    fr: "Si les signes diffèrent, on garde le signe de la plus grande valeur absolue.",
    de: "Bei verschiedenen Vorzeichen gilt das Vorzeichen des größeren Betrags.",
    pt: "Com sinais diferentes, vale o sinal do maior valor absoluto.",
    ru: "Если знаки разные, берём знак числа с большим модулем.",
    vi: "Khi khác dấu, kết quả mang dấu của số có trị tuyệt đối lớn hơn.",
    th: "ถ้าเครื่องหมายต่างกัน ให้ใช้เครื่องหมายของจำนวนที่มีค่าสัมบูรณ์มากกว่า",
    in: "Jika tandanya berbeda, hasilnya mengikuti tanda nilai mutlak yang lebih besar.",
  },
  "둘 다 음수면 절댓값을 더하고 음수 부호를 붙여요.": {
    en: "When both are negative, add the absolute values and keep the minus sign.",
    ja: "どちらも負のときは、絶対値をたしてマイナスをつけます。",
    zh: "两个都是负数时，把绝对值相加并加上负号。",
    es: "Si ambos son negativos, suma los valores absolutos y pon el signo menos.",
    fr: "Si les deux sont négatifs, additionne les valeurs absolues et garde le signe moins.",
    de: "Sind beide negativ, addiere die Beträge und setze ein Minus davor.",
    pt: "Se os dois são negativos, some os valores absolutos e mantenha o sinal de menos.",
    ru: "Если оба отрицательные, складываем модули и ставим знак минус.",
    vi: "Khi cả hai đều âm, cộng trị tuyệt đối rồi giữ dấu trừ.",
    th: "ถ้าทั้งคู่เป็นจำนวนลบ ให้บวกค่าสัมบูรณ์แล้วใส่เครื่องหมายลบ",
    in: "Jika keduanya negatif, jumlahkan nilai mutlaknya lalu beri tanda minus.",
  },
};

// 팩 유닛 제목 — 파일 3개로 나눠 두었다 (자동 병합)
Object.assign(module.exports, require("./units_title"), require("./units_title2"), require("./units_title3"));
