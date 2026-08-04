/**
 * 삐약수학 다국어 문자열 원장.
 *
 * 기본 언어는 **영어**(values/) — 지원하지 않는 언어로 폰이 설정돼 있으면 영어가 나온다.
 * 한국어는 values-ko/ 에 들어간다.
 *
 * 문자열을 고치거나 추가한 뒤에는 반드시:
 *   node tools/i18n/gen_strings.js
 *
 * 언어 12종: en(기본) ko ja zh es fr de pt ru vi th in
 */
module.exports = {
  // zh 는 간체(본토), zh-rTW·zh-rHK 는 번체 — 번체는 원장에 안 쓰고 zh 에서 자동 변환한다
  langs: ["en", "ko", "ja", "zh", "zh-rTW", "zh-rHK", "es", "fr", "de", "pt", "ru", "vi", "th", "in"],

  strings: {
    // ---------- 공통 ----------
    app_name: {
      en: "Piyak Math", ko: "삐약수학", ja: "ピヤックさんすう", zh: "小鸡数学",
      es: "Piyak Mates", fr: "Piyak Maths", de: "Piyak Mathe", pt: "Piyak Matemática",
      ru: "Пияк Математика", vi: "Piyak Toán", th: "Piyak คณิต", in: "Piyak Matematika",
    },
    ok: { en: "OK", ko: "확인", ja: "確認", zh: "确定", es: "Aceptar", fr: "OK",
          de: "OK", pt: "OK", ru: "ОК", vi: "Xác nhận", th: "ตกลง", in: "OK" },
    cancel: { en: "Cancel", ko: "취소", ja: "キャンセル", zh: "取消", es: "Cancelar",
              fr: "Annuler", de: "Abbrechen", pt: "Cancelar", ru: "Отмена",
              vi: "Hủy", th: "ยกเลิก", in: "Batal" },
    done: { en: "Done", ko: "완료", ja: "完了", zh: "完成", es: "Listo", fr: "Terminé",
            de: "Fertig", pt: "Concluído", ru: "Готово", vi: "Xong", th: "เสร็จ", in: "Selesai" },
    start: { en: "Start", ko: "시작하기", ja: "はじめる", zh: "开始", es: "Empezar",
             fr: "Commencer", de: "Starten", pt: "Começar", ru: "Начать",
             vi: "Bắt đầu", th: "เริ่ม", in: "Mulai" },
    back: { en: "Back", ko: "뒤로", ja: "戻る", zh: "返回", es: "Atrás", fr: "Retour",
            de: "Zurück", pt: "Voltar", ru: "Назад", vi: "Quay lại", th: "กลับ", in: "Kembali" },
    chick: { en: "Piyak", ko: "삐약이", ja: "ピヤック", zh: "小鸡", es: "Piyak", fr: "Piyak",
             de: "Piyak", pt: "Piyak", ru: "Пияк", vi: "Piyak", th: "Piyak", in: "Piyak" },
    scenery: { en: "Scenery", ko: "풍경", ja: "風景", zh: "风景", es: "Paisaje", fr: "Paysage",
               de: "Landschaft", pt: "Paisagem", ru: "Пейзаж", vi: "Phong cảnh",
               th: "ทิวทัศน์", in: "Pemandangan" },

    // ---------- 홈 ----------
    home_greeting: {
      en: "Let's study together today!", ko: "오늘도 삐약삐약 공부해요!",
      ja: "きょうもいっしょにべんきょう！", zh: "今天也一起学习吧！",
      es: "¡Estudiemos juntos hoy!", fr: "Étudions ensemble aujourd'hui !",
      de: "Lass uns heute zusammen lernen!", pt: "Vamos estudar juntos hoje!",
      ru: "Позанимаемся сегодня вместе!", vi: "Hôm nay cùng học nhé!",
      th: "วันนี้มาเรียนด้วยกันนะ!", in: "Ayo belajar bersama hari ini!",
    },
    home_courses: {
      en: "Courses", ko: "학습 코스", ja: "コース", zh: "学习课程", es: "Cursos",
      fr: "Parcours", de: "Kurse", pt: "Cursos", ru: "Курсы", vi: "Khoá học",
      th: "คอร์สเรียน", in: "Kursus",
    },
    home_my_section: {
      en: "Progress · Shop · Level test", ko: "내 실력 · 상점 · 레벨테스트",
      ja: "じつりょく・ショップ・レベルテスト", zh: "我的实力 · 商店 · 水平测试",
      es: "Progreso · Tienda · Test de nivel", fr: "Progrès · Boutique · Test de niveau",
      de: "Fortschritt · Shop · Einstufungstest", pt: "Progresso · Loja · Teste de nível",
      ru: "Успехи · Магазин · Тест уровня", vi: "Tiến bộ · Cửa hàng · Kiểm tra trình độ",
      th: "ความก้าวหน้า · ร้านค้า · ทดสอบระดับ", in: "Kemajuan · Toko · Tes level",
    },
    home_my_skill: {
      en: "My progress", ko: "내 실력", ja: "じつりょく", zh: "我的实力",
      es: "Mi progreso", fr: "Mes progrès", de: "Mein Fortschritt", pt: "Meu progresso",
      ru: "Мои успехи", vi: "Tiến bộ của tôi", th: "ความก้าวหน้าของฉัน", in: "Kemajuanku",
    },
    home_overall_lv: {
      en: "Overall Lv.%s", ko: "종합 실력 Lv.%s", ja: "そうごう Lv.%s", zh: "综合水平 Lv.%s",
      es: "Nivel global Lv.%s", fr: "Niveau global Lv.%s", de: "Gesamt Lv.%s",
      pt: "Nível geral Lv.%s", ru: "Общий Ур.%s", vi: "Tổng thể Lv.%s",
      th: "ระดับรวม Lv.%s", in: "Level total Lv.%s",
    },
    home_daily_goal: {
      en: "Today's goal  %1$d / %2$d XP", ko: "오늘의 목표  %1$d / %2$d XP",
      ja: "きょうのもくひょう  %1$d / %2$d XP", zh: "今日目标  %1$d / %2$d XP",
      es: "Meta de hoy  %1$d / %2$d XP", fr: "Objectif du jour  %1$d / %2$d XP",
      de: "Tagesziel  %1$d / %2$d XP", pt: "Meta de hoje  %1$d / %2$d XP",
      ru: "Цель дня  %1$d / %2$d XP", vi: "Mục tiêu hôm nay  %1$d / %2$d XP",
      th: "เป้าหมายวันนี้  %1$d / %2$d XP", in: "Target hari ini  %1$d / %2$d XP",
    },
    home_goal_done: {
      en: "  ✅ Done!", ko: "   ✅ 달성!", ja: "  ✅ たっせい！", zh: "  ✅ 达成！",
      es: "  ✅ ¡Logrado!", fr: "  ✅ Atteint !", de: "  ✅ Geschafft!", pt: "  ✅ Concluído!",
      ru: "  ✅ Готово!", vi: "  ✅ Đạt rồi!", th: "  ✅ สำเร็จ!", in: "  ✅ Tercapai!",
    },
    home_change: {
      en: "Change", ko: "변경", ja: "へんこう", zh: "更改", es: "Cambiar", fr: "Modifier",
      de: "Ändern", pt: "Alterar", ru: "Изменить", vi: "Đổi", th: "เปลี่ยน", in: "Ubah",
    },
    home_shop_line: {
      en: "Shop · Cash out", ko: "상점 · 현금으로 바꾸기", ja: "ショップ・げんきんにかえる",
      zh: "商店 · 兑换现金", es: "Tienda · Canjear", fr: "Boutique · Échanger",
      de: "Shop · Auszahlen", pt: "Loja · Trocar", ru: "Магазин · Обмен",
      vi: "Cửa hàng · Đổi tiền", th: "ร้านค้า · แลกเงิน", in: "Toko · Tukar uang",
    },
    home_wrong_count: {
      en: "Review %d", ko: "오답 %d", ja: "まちがい %d", zh: "错题 %d",
      es: "Repasar %d", fr: "Réviser %d", de: "Fehler %d", pt: "Rever %d",
      ru: "Ошибки %d", vi: "Ôn %d", th: "ทบทวน %d", in: "Ulangi %d",
    },
    home_stats: {
      en: "Stats", ko: "통계", ja: "とうけい", zh: "统计", es: "Datos", fr: "Stats",
      de: "Statistik", pt: "Dados", ru: "Статистика", vi: "Thống kê", th: "สถิติ", in: "Statistik",
    },
    home_settings: {
      en: "Settings", ko: "설정", ja: "せってい", zh: "设置", es: "Ajustes", fr: "Réglages",
      de: "Einstellungen", pt: "Ajustes", ru: "Настройки", vi: "Cài đặt", th: "ตั้งค่า", in: "Pengaturan",
    },
    home_no_review: {
      en: "No mistakes to review! 🐥", ko: "복습할 오답이 없어요! 삐약 🐥",
      ja: "ふくしゅうするもんだいはありません！🐥", zh: "没有需要复习的错题！🐥",
      es: "¡No hay errores que repasar! 🐥", fr: "Aucune erreur à réviser ! 🐥",
      de: "Keine Fehler zum Wiederholen! 🐥", pt: "Nada para rever! 🐥",
      ru: "Нет ошибок для повторения! 🐥", vi: "Không có lỗi nào để ôn! 🐥",
      th: "ไม่มีข้อผิดที่ต้องทบทวน! 🐥", in: "Tidak ada kesalahan untuk diulang! 🐥",
    },
    home_wallet: {
      en: "My wallet", ko: "내 지갑", ja: "おさいふ", zh: "我的钱包", es: "Mi cartera",
      fr: "Mon porte-monnaie", de: "Mein Geldbeutel", pt: "Minha carteira",
      ru: "Мой кошелёк", vi: "Ví của tôi", th: "กระเป๋าเงินของฉัน", in: "Dompetku",
    },
    home_placement: {
      en: "Find your level!\n25 questions to place you just right",
      ko: "레벨테스트로 내 위치 찾기!\n25문제로 딱 맞는 레벨을 정해줘요",
      ja: "レベルテストでじぶんのいちを！\n25もんでぴったりのレベルにします",
      zh: "用水平测试找到你的位置！\n25道题定出最合适的等级",
      es: "¡Descubre tu nivel!\n25 preguntas para ubicarte bien",
      fr: "Trouve ton niveau !\n25 questions pour bien te situer",
      de: "Finde dein Level!\n25 Fragen für die richtige Stufe",
      pt: "Descubra seu nível!\n25 questões para te posicionar",
      ru: "Определи свой уровень!\n25 вопросов подберут нужный уровень",
      vi: "Tìm trình độ của bạn!\n25 câu để xếp đúng cấp độ",
      th: "หาระดับของคุณ!\n25 ข้อเพื่อจัดระดับให้พอดี",
      in: "Temukan levelmu!\n25 soal untuk menempatkanmu tepat",
    },

    // ---------- 문제 화면 ----------
    lesson_check: {
      en: "Check", ko: "확인", ja: "こたえあわせ", zh: "确认", es: "Comprobar",
      fr: "Vérifier", de: "Prüfen", pt: "Verificar", ru: "Проверить",
      vi: "Kiểm tra", th: "ตรวจคำตอบ", in: "Periksa",
    },
    lesson_continue: {
      en: "Continue", ko: "계속하기", ja: "つづける", zh: "继续", es: "Continuar",
      fr: "Continuer", de: "Weiter", pt: "Continuar", ru: "Далее",
      vi: "Tiếp tục", th: "ต่อไป", in: "Lanjut",
    },
    lesson_quit: {
      en: "Quit", ko: "그만두기", ja: "やめる", zh: "退出", es: "Salir", fr: "Quitter",
      de: "Beenden", pt: "Sair", ru: "Выйти", vi: "Thoát", th: "ออก", in: "Keluar",
    },
    lesson_quit_ask: {
      en: "Stop this lesson?\nYour progress won't be saved",
      ko: "레슨을 그만둘까요?\n진행 상황은 저장되지 않아요",
      ja: "レッスンをやめますか？\nとちゅうのきろくはのこりません",
      zh: "要退出这节课吗？\n进度不会保存",
      es: "¿Salir de la lección?\nTu progreso no se guardará",
      fr: "Quitter la leçon ?\nTa progression ne sera pas gardée",
      de: "Lektion beenden?\nDein Fortschritt wird nicht gespeichert",
      pt: "Sair da lição?\nSeu progresso não será salvo",
      ru: "Выйти из урока?\nПрогресс не сохранится",
      vi: "Dừng bài học?\nTiến trình sẽ không được lưu",
      th: "ออกจากบทเรียน?\nความคืบหน้าจะไม่ถูกบันทึก",
      in: "Keluar dari pelajaran?\nProgresmu tidak akan tersimpan",
    },
    lesson_correct: {
      en: "That's right!", ko: "삐약! 정답이에요!", ja: "せいかい！", zh: "答对了！",
      es: "¡Correcto!", fr: "Bravo, c'est juste !", de: "Richtig!", pt: "Certo!",
      ru: "Верно!", vi: "Chính xác!", th: "ถูกต้อง!", in: "Benar!",
    },
    lesson_review_mode: {
      en: "Review", ko: "복습", ja: "ふくしゅう", zh: "复习", es: "Repaso", fr: "Révision",
      de: "Wiederholung", pt: "Revisão", ru: "Повтор", vi: "Ôn tập", th: "ทบทวน", in: "Ulangan",
    },
    lesson_done: {
      en: "Lesson complete!", ko: "레슨 완료!", ja: "レッスンかんりょう！", zh: "本课完成！",
      es: "¡Lección completada!", fr: "Leçon terminée !", de: "Lektion geschafft!",
      pt: "Lição concluída!", ru: "Урок пройден!", vi: "Hoàn thành bài học!",
      th: "จบบทเรียน!", in: "Pelajaran selesai!",
    },
    lesson_answer_hint: {
      en: "Type your answer", ko: "답을 입력하세요", ja: "こたえをいれてね", zh: "请输入答案",
      es: "Escribe tu respuesta", fr: "Écris ta réponse", de: "Antwort eingeben",
      pt: "Digite a resposta", ru: "Введите ответ", vi: "Nhập câu trả lời",
      th: "พิมพ์คำตอบ", in: "Ketik jawabanmu",
    },
    lesson_count_hint: {
      en: "👆 Tap each picture to count", ko: "👆 그림을 하나씩 짚어 세어 보세요",
      ja: "👆 えをひとつずつタップしてかぞえよう", zh: "👆 逐个点击图片来数一数",
      es: "👆 Toca cada dibujo para contar", fr: "👆 Touche chaque image pour compter",
      de: "👆 Tippe jedes Bild an zum Zählen", pt: "👆 Toque em cada figura para contar",
      ru: "👆 Нажимай на каждую картинку и считай",
      vi: "👆 Chạm từng hình để đếm", th: "👆 แตะทีละรูปเพื่อนับ",
      in: "👆 Ketuk tiap gambar untuk menghitung",
    },
    lesson_count_reset: {
      en: "Count again", ko: "다시 세기", ja: "もういちどかぞえる", zh: "重新数",
      es: "Contar de nuevo", fr: "Recompter", de: "Neu zählen", pt: "Contar de novo",
      ru: "Считать заново", vi: "Đếm lại", th: "นับใหม่", in: "Hitung lagi",
    },
    lesson_read_aloud: {
      en: "Read aloud", ko: "읽어주기", ja: "よみあげ", zh: "朗读", es: "Leer en voz alta",
      fr: "Lire à voix haute", de: "Vorlesen", pt: "Ler em voz alta", ru: "Прочитать",
      vi: "Đọc to", th: "อ่านออกเสียง", in: "Bacakan",
    },

    // ---------- 연습장 ----------
    pad_open: {
      en: "Scratchpad", ko: "연습장", ja: "メモ", zh: "草稿纸", es: "Borrador",
      fr: "Brouillon", de: "Notizblock", pt: "Rascunho", ru: "Черновик",
      vi: "Giấy nháp", th: "กระดาษทด", in: "Coretan",
    },
    pad_close: {
      en: "✅ Done (close scratchpad)", ko: "✅ 다 썼어요 (연습장 닫기)",
      ja: "✅ かきおわった（メモをとじる）", zh: "✅ 写完了（关闭草稿纸）",
      es: "✅ Listo (cerrar borrador)", fr: "✅ Fini (fermer le brouillon)",
      de: "✅ Fertig (Notizblock schließen)", pt: "✅ Pronto (fechar rascunho)",
      ru: "✅ Готово (закрыть черновик)", vi: "✅ Xong (đóng giấy nháp)",
      th: "✅ เสร็จแล้ว (ปิดกระดาษทด)", in: "✅ Selesai (tutup coretan)",
    },
    pad_eraser: {
      en: "Eraser", ko: "지우개", ja: "けしゴム", zh: "橡皮", es: "Borrador",
      fr: "Gomme", de: "Radierer", pt: "Borracha", ru: "Ластик",
      vi: "Tẩy", th: "ยางลบ", in: "Penghapus",
    },
    pad_undo: {
      en: "Undo", ko: "되돌리기", ja: "もとにもどす", zh: "撤销", es: "Deshacer",
      fr: "Annuler", de: "Rückgängig", pt: "Desfazer", ru: "Отменить",
      vi: "Hoàn tác", th: "ย้อนกลับ", in: "Urungkan",
    },
    pad_clear: {
      en: "Clear all", ko: "모두 지움", ja: "ぜんぶけす", zh: "全部清除",
      es: "Borrar todo", fr: "Tout effacer", de: "Alles löschen", pt: "Limpar tudo",
      ru: "Стереть всё", vi: "Xoá hết", th: "ลบทั้งหมด", in: "Hapus semua",
    },

    // ---------- 설정 ----------
    set_title: {
      en: "Settings", ko: "설정", ja: "せってい", zh: "设置", es: "Ajustes", fr: "Réglages",
      de: "Einstellungen", pt: "Ajustes", ru: "Настройки", vi: "Cài đặt", th: "ตั้งค่า", in: "Pengaturan",
    },
    set_speed: {
      en: "Speech speed", ko: "발음 속도", ja: "よみあげのはやさ", zh: "朗读速度",
      es: "Velocidad de voz", fr: "Vitesse de lecture", de: "Sprechtempo",
      pt: "Velocidade da fala", ru: "Скорость речи", vi: "Tốc độ đọc",
      th: "ความเร็วเสียง", in: "Kecepatan suara",
    },
    set_listen: {
      en: "Listen", ko: "들어보기", ja: "きいてみる", zh: "试听", es: "Escuchar",
      fr: "Écouter", de: "Anhören", pt: "Ouvir", ru: "Прослушать",
      vi: "Nghe thử", th: "ฟังตัวอย่าง", in: "Dengar",
    },
    set_sfx: {
      en: "Sound effects", ko: "효과음 크기", ja: "こうかおんのおおきさ", zh: "音效大小",
      es: "Efectos de sonido", fr: "Effets sonores", de: "Soundeffekte",
      pt: "Efeitos sonoros", ru: "Громкость эффектов", vi: "Âm thanh hiệu ứng",
      th: "เสียงประกอบ", in: "Efek suara",
    },
    set_free_mode: {
      en: "Free play mode", ko: "자유 이동 모드", ja: "じゆうモード", zh: "自由模式",
      es: "Modo libre", fr: "Mode libre", de: "Freier Modus", pt: "Modo livre",
      ru: "Свободный режим", vi: "Chế độ tự do", th: "โหมดอิสระ", in: "Mode bebas",
    },
    set_free_mode_desc: {
      en: "Play any lesson in any order", ko: "모든 레슨을 순서 상관없이 풀 수 있어요",
      ja: "どのレッスンでもすきなじゅんばんで", zh: "所有课程都可以不按顺序做",
      es: "Haz las lecciones en cualquier orden", fr: "Fais les leçons dans n'importe quel ordre",
      de: "Lektionen in beliebiger Reihenfolge", pt: "Faça as lições em qualquer ordem",
      ru: "Проходите уроки в любом порядке", vi: "Học bài theo thứ tự bất kỳ",
      th: "เรียนบทไหนก่อนก็ได้", in: "Kerjakan pelajaran dalam urutan bebas",
    },
    set_hearts: {
      en: "Use hearts", ko: "하트 쓰기", ja: "ハートをつかう", zh: "使用爱心",
      es: "Usar corazones", fr: "Utiliser les cœurs", de: "Herzen verwenden",
      pt: "Usar corações", ru: "Использовать сердечки", vi: "Dùng tim",
      th: "ใช้หัวใจ", in: "Pakai hati",
    },
    set_hearts_desc: {
      en: "Off means wrong answers cost nothing", ko: "끄면 틀려도 하트가 줄지 않아요 (마음껏 연습)",
      ja: "オフならまちがえてもへりません", zh: "关闭后答错也不会扣爱心",
      es: "Si lo apagas, fallar no cuesta nada", fr: "Désactivé : les erreurs ne coûtent rien",
      de: "Aus: Fehler kosten nichts", pt: "Desligado: errar não custa nada",
      ru: "Выключено — ошибки ничего не стоят", vi: "Tắt thì sai không mất tim",
      th: "ปิดแล้วตอบผิดก็ไม่เสียหัวใจ", in: "Nonaktif: salah tidak mengurangi hati",
    },
    set_placement_again: {
      en: "Take the level test again", ko: "레벨테스트 다시 보기",
      ja: "レベルテストをもういちど", zh: "重新做水平测试",
      es: "Repetir el test de nivel", fr: "Refaire le test de niveau",
      de: "Einstufungstest wiederholen", pt: "Refazer o teste de nível",
      ru: "Пройти тест уровня заново", vi: "Làm lại bài kiểm tra trình độ",
      th: "ทำแบบทดสอบระดับอีกครั้ง", in: "Ulangi tes level",
    },
    set_reset: {
      en: "Reset all progress", ko: "진행도 전체 초기화", ja: "きろくをぜんぶけす",
      zh: "清除全部进度", es: "Borrar todo el progreso", fr: "Effacer toute la progression",
      de: "Gesamten Fortschritt löschen", pt: "Apagar todo o progresso",
      ru: "Сбросить весь прогресс", vi: "Xoá toàn bộ tiến trình",
      th: "ล้างความคืบหน้าทั้งหมด", in: "Hapus semua kemajuan",
    },
    set_reset_ask: {
      en: "Really reset?\nAll progress, XP, badges and mistakes will be deleted.\nThis cannot be undone!",
      ko: "정말 초기화할까요?\n모든 진행도·XP·배지·오답이 삭제돼요.\n되돌릴 수 없어요!",
      ja: "ほんとうにけしますか？\nきろく・XP・バッジ・まちがいがぜんぶきえます。\nもとにもどせません！",
      zh: "确定要清除吗？\n所有进度、XP、徽章和错题都会删除。\n无法恢复！",
      es: "¿Seguro que quieres borrar?\nSe eliminarán progreso, XP, insignias y errores.\n¡No se puede deshacer!",
      fr: "Vraiment tout effacer ?\nProgression, XP, badges et erreurs seront supprimés.\nC'est irréversible !",
      de: "Wirklich zurücksetzen?\nFortschritt, XP, Abzeichen und Fehler werden gelöscht.\nNicht umkehrbar!",
      pt: "Apagar mesmo?\nProgresso, XP, medalhas e erros serão excluídos.\nNão dá para desfazer!",
      ru: "Точно сбросить?\nПрогресс, XP, значки и ошибки будут удалены.\nЭто необратимо!",
      vi: "Xoá thật chứ?\nTiến trình, XP, huy hiệu và lỗi sẽ bị xoá.\nKhông thể hoàn tác!",
      th: "ล้างจริงหรือ?\nความคืบหน้า XP เหรียญ และข้อผิดจะถูกลบ\nย้อนกลับไม่ได้!",
      in: "Yakin hapus semua?\nKemajuan, XP, lencana, dan kesalahan akan terhapus.\nTidak bisa dibatalkan!",
    },
    set_reset_done: {
      en: "Reset! Starting fresh 🐣", ko: "초기화 완료! 처음부터 삐약 🐣",
      ja: "リセットかんりょう！さいしょから 🐣", zh: "已清除！从头开始 🐣",
      es: "¡Listo! Empezamos de nuevo 🐣", fr: "Fait ! On repart de zéro 🐣",
      de: "Zurückgesetzt! Neu anfangen 🐣", pt: "Pronto! Recomeçando 🐣",
      ru: "Сброшено! Начинаем заново 🐣", vi: "Đã xoá! Bắt đầu lại 🐣",
      th: "ล้างแล้ว! เริ่มใหม่ 🐣", in: "Terhapus! Mulai dari awal 🐣",
    },
    set_parent: {
      en: "Parent settings", ko: "부모 설정", ja: "ほごしゃせってい", zh: "家长设置",
      es: "Ajustes de padres", fr: "Réglages parents", de: "Eltern-Einstellungen",
      pt: "Ajustes dos pais", ru: "Родительские настройки", vi: "Cài đặt phụ huynh",
      th: "ตั้งค่าผู้ปกครอง", in: "Pengaturan orang tua",
    },
    set_crash_log: {
      en: "View last error log", ko: "마지막 오류 기록 보기",
      ja: "さいごのエラーきろく", zh: "查看最近的错误记录",
      es: "Ver último registro de error", fr: "Voir le dernier rapport d'erreur",
      de: "Letztes Fehlerprotokoll ansehen", pt: "Ver último registro de erro",
      ru: "Показать последнюю ошибку", vi: "Xem nhật ký lỗi gần nhất",
      th: "ดูบันทึกข้อผิดพลาดล่าสุด", in: "Lihat catatan galat terakhir",
    },

    // ---------- 지갑 · 통계 ----------
    wallet_saved: {
      en: "Allowance saved", ko: "모은 용돈", ja: "ためたおこづかい", zh: "攒下的零用钱",
      es: "Dinero ahorrado", fr: "Argent gagné", de: "Gespartes Taschengeld",
      pt: "Mesada guardada", ru: "Накопленные деньги", vi: "Tiền đã tiết kiệm",
      th: "เงินที่สะสมได้", in: "Uang saku terkumpul",
    },
    wallet_rule: {
      en: "10 per question on the first try. Replaying a lesson pays nothing",
      ko: "문제 1개를 처음 맞히면 10원! 같은 레슨을 다시 풀면 용돈은 없어요",
      ja: "はじめてせいかいで10。おなじレッスンをもういちどしてもふえません",
      zh: "第一次答对每题10。重做同一课不再有奖励",
      es: "10 por acertar a la primera. Repetir la lección no paga",
      fr: "10 par bonne réponse du premier coup. Refaire une leçon ne rapporte rien",
      de: "10 pro Aufgabe beim ersten Versuch. Wiederholte Lektionen zahlen nichts",
      pt: "10 por acertar de primeira. Refazer a lição não paga",
      ru: "10 за верный ответ с первой попытки. Повтор урока не оплачивается",
      vi: "10 cho câu đúng ngay lần đầu. Học lại bài cũ không được thưởng",
      th: "ตอบถูกครั้งแรกได้ 10 ทำบทเดิมซ้ำไม่ได้เพิ่ม",
      in: "10 untuk benar di percobaan pertama. Mengulang pelajaran tidak berbayar",
    },
    wallet_shop: {
      en: "Shop", ko: "상점", ja: "ショップ", zh: "商店", es: "Tienda", fr: "Boutique",
      de: "Shop", pt: "Loja", ru: "Магазин", vi: "Cửa hàng", th: "ร้านค้า", in: "Toko",
    },
    wallet_cash_out: {
      en: "Cash out (parent)", ko: "현금으로 바꾸기 (부모님)",
      ja: "げんきんにかえる（ほごしゃ）", zh: "兑换现金（家长）",
      es: "Canjear (padres)", fr: "Échanger (parents)", de: "Auszahlen (Eltern)",
      pt: "Trocar por dinheiro (pais)", ru: "Обменять (родители)",
      vi: "Đổi tiền mặt (phụ huynh)", th: "แลกเป็นเงิน (ผู้ปกครอง)",
      in: "Tukar uang (orang tua)",
    },
    wallet_log: {
      en: "Allowance history", ko: "용돈 기록", ja: "おこづかいのきろく", zh: "零用钱记录",
      es: "Historial", fr: "Historique", de: "Verlauf", pt: "Histórico",
      ru: "История", vi: "Lịch sử", th: "ประวัติ", in: "Riwayat",
    },
    stats_title: {
      en: "My stats", ko: "내 통계", ja: "とうけい", zh: "我的统计", es: "Mis datos",
      fr: "Mes stats", de: "Meine Statistik", pt: "Meus dados", ru: "Моя статистика",
      vi: "Thống kê của tôi", th: "สถิติของฉัน", in: "Statistikku",
    },
    stats_by_skill: {
      en: "Skills", ko: "영역별 실력", ja: "ぶんやべつ", zh: "各领域实力",
      es: "Por áreas", fr: "Par domaine", de: "Nach Bereichen", pt: "Por área",
      ru: "По разделам", vi: "Theo lĩnh vực", th: "ตามทักษะ", in: "Per bidang",
    },
    stats_streak: {
      en: "Streak calendar", ko: "스트릭 달력", ja: "れんぞくカレンダー", zh: "连续打卡日历",
      es: "Calendario de racha", fr: "Calendrier de série", de: "Streak-Kalender",
      pt: "Calendário de sequência", ru: "Календарь серии", vi: "Lịch chuỗi ngày",
      th: "ปฏิทินต่อเนื่อง", in: "Kalender rentetan",
    },
    stats_badges: {
      en: "Badges", ko: "배지", ja: "バッジ", zh: "徽章", es: "Insignias", fr: "Badges",
      de: "Abzeichen", pt: "Medalhas", ru: "Значки", vi: "Huy hiệu", th: "เหรียญ", in: "Lencana",
    },
    placement_title: {
      en: "Level test", ko: "레벨테스트", ja: "レベルテスト", zh: "水平测试",
      es: "Test de nivel", fr: "Test de niveau", de: "Einstufungstest",
      pt: "Teste de nível", ru: "Тест уровня", vi: "Kiểm tra trình độ",
      th: "แบบทดสอบระดับ", in: "Tes level",
    },
    placement_quit_ask: {
      en: "Stop the level test?", ko: "레벨테스트를 그만둘까요?",
      ja: "レベルテストをやめますか？", zh: "要退出水平测试吗？",
      es: "¿Salir del test de nivel?", fr: "Quitter le test de niveau ?",
      de: "Einstufungstest beenden?", pt: "Sair do teste de nível?",
      ru: "Выйти из теста уровня?", vi: "Dừng bài kiểm tra trình độ?",
      th: "ออกจากแบบทดสอบระดับ?", in: "Keluar dari tes level?",
    },
  // 문제문의 빈칸(___)을 소리로 읽을 때 대신 부르는 말
  tts_blank: { ko: "몇", en: "what", ja: "なに", zh: "多少", es: "cuánto", fr: "combien", de: "wie viel", pt: "quanto", ru: "сколько", vi: "bao nhiêu", th: "เท่าไร", in: "berapa" },

  // ---------- 힌트 ----------
  hint_none: { ko: "힌트권이 없어요. 상점에서 살 수 있어요! 💡", en: "No hint tickets. You can buy them in the shop! 💡", ja: "ヒント券がありません。ショップで買えます! 💡", zh: "没有提示券了。可以在商店购买! 💡", es: "No tienes pistas. ¡Puedes comprarlas en la tienda! 💡", fr: "Plus d'indices. Tu peux en acheter dans la boutique ! 💡", de: "Keine Tipp-Tickets. Im Shop erhältlich! 💡", pt: "Sem dicas. Você pode comprar na loja! 💡", ru: "Подсказок нет. Их можно купить в магазине! 💡", vi: "Hết vé gợi ý. Bạn có thể mua ở cửa hàng! 💡", th: "ไม่มีบัตรคำใบ้ ซื้อได้ที่ร้านค้า! 💡", in: "Tidak ada tiket petunjuk. Beli di toko! 💡" },
  hint_choice_only: { ko: "힌트는 4지선다 문제에서만 쓸 수 있어요", en: "Hints only work on multiple-choice questions", ja: "ヒントは4択問題でだけ使えます", zh: "提示只能用于四选一的题目", es: "Las pistas solo funcionan en preguntas de opción múltiple", fr: "Les indices ne marchent que sur les questions à choix multiples", de: "Tipps gibt es nur bei Multiple-Choice-Fragen", pt: "As dicas só funcionam em questões de múltipla escolha", ru: "Подсказки работают только в вопросах с выбором ответа", vi: "Gợi ý chỉ dùng được cho câu hỏi trắc nghiệm", th: "คำใบ้ใช้ได้เฉพาะข้อสอบแบบเลือกตอบ", in: "Petunjuk hanya untuk soal pilihan ganda" },
  hint_already: { ko: "이 문제에는 이미 힌트를 썼어요", en: "You already used a hint on this question", ja: "この問題ではもうヒントを使いました", zh: "这道题已经用过提示了", es: "Ya usaste una pista en esta pregunta", fr: "Tu as déjà utilisé un indice sur cette question", de: "Für diese Frage hast du schon einen Tipp benutzt", pt: "Você já usou uma dica nesta questão", ru: "Для этого вопроса подсказка уже использована", vi: "Bạn đã dùng gợi ý cho câu này rồi", th: "คุณใช้คำใบ้กับข้อนี้ไปแล้ว", in: "Kamu sudah memakai petunjuk di soal ini" },

  // ---------- 수학 문제 유형 배지 ----------
  mkind_clock: { ko: "🕐 시계 보기", en: "🕐 Read the clock", ja: "🕐 とけいを よむ", zh: "🕐 看时钟", es: "🕐 Leer el reloj", fr: "🕐 Lire l'heure", de: "🕐 Uhr lesen", pt: "🕐 Ler o relógio", ru: "🕐 Читаем часы", vi: "🕐 Xem đồng hồ", th: "🕐 อ่านนาฬิกา", in: "🕐 Membaca jam" },
  mkind_clock_set: { ko: "🕐 시계 바늘 돌리기", en: "🕐 Set the clock", ja: "🕐 はりを まわす", zh: "🕐 拨动指针", es: "🕐 Ajustar el reloj", fr: "🕐 Régler l'horloge", de: "🕐 Uhr einstellen", pt: "🕐 Ajustar o relógio", ru: "🕐 Ставим стрелки", vi: "🕐 Chỉnh kim đồng hồ", th: "🕐 ตั้งเวลานาฬิกา", in: "🕐 Atur jarum jam" },
  mkind_group: { ko: "🧺 끌어서 똑같이 나누기", en: "🧺 Share equally", ja: "🧺 同じに わける", zh: "🧺 平均分", es: "🧺 Repartir por igual", fr: "🧺 Partager également", de: "🧺 Gleich aufteilen", pt: "🧺 Dividir por igual", ru: "🧺 Делим поровну", vi: "🧺 Chia đều", th: "🧺 แบ่งเท่า ๆ กัน", in: "🧺 Bagi rata" },
  mkind_frac_paint: { ko: "🍰 분수만큼 색칠하기", en: "🍰 Colour the fraction", ja: "🍰 分だけ ぬる", zh: "🍰 涂出分数", es: "🍰 Colorear la fracción", fr: "🍰 Colorier la fraction", de: "🍰 Bruch ausmalen", pt: "🍰 Pintar a fração", ru: "🍰 Закрась дробь", vi: "🍰 Tô phân số", th: "🍰 ระบายเศษส่วน", in: "🍰 Warnai pecahan" },
  mkind_shape_sort: { ko: "🔺 도형 분류하기", en: "🔺 Sort the shapes", ja: "🔺 かたち わけ", zh: "🔺 图形分类", es: "🔺 Clasificar figuras", fr: "🔺 Trier les formes", de: "🔺 Formen sortieren", pt: "🔺 Separar figuras", ru: "🔺 Сортируем фигуры", vi: "🔺 Phân loại hình", th: "🔺 จำแนกรูปทรง", in: "🔺 Kelompokkan bangun" },
  mkind_numline_drag: { ko: "📏 수직선에서 찾기", en: "📏 Find on the line", ja: "📏 数直線で さがす", zh: "📏 在数轴上找", es: "📏 Buscar en la recta", fr: "📏 Trouver sur la droite", de: "📏 Auf dem Strahl finden", pt: "📏 Achar na reta", ru: "📏 Ищем на прямой", vi: "📏 Tìm trên trục số", th: "📏 หาบนเส้นจำนวน", in: "📏 Cari di garis bilangan" },
  mkind_angle_set: { ko: "📐 각도 만들기", en: "📐 Make the angle", ja: "📐 角を つくる", zh: "📐 做出角度", es: "📐 Formar el ángulo", fr: "📐 Former l'angle", de: "📐 Winkel einstellen", pt: "📐 Formar o ângulo", ru: "📐 Строим угол", vi: "📐 Tạo góc", th: "📐 สร้างมุม", in: "📐 Buat sudut" },
  mkind_balance: { ko: "⚖️ 저울 맞추기", en: "⚖️ Balance the scale", ja: "⚖️ てんびん つりあい", zh: "⚖️ 平衡天平", es: "⚖️ Equilibrar la balanza", fr: "⚖️ Équilibrer la balance", de: "⚖️ Waage ausgleichen", pt: "⚖️ Equilibrar a balança", ru: "⚖️ Уравновесь весы", vi: "⚖️ Cân thăng bằng", th: "⚖️ ทำให้ตาชั่งสมดุล", in: "⚖️ Seimbangkan timbangan" },
  mkind_bar_build: { ko: "📊 그래프 세우기", en: "📊 Build the graph", ja: "📊 グラフを つくる", zh: "📊 建立统计图", es: "📊 Construir el gráfico", fr: "📊 Construire le graphique", de: "📊 Diagramm bauen", pt: "📊 Montar o gráfico", ru: "📊 Строим диаграмму", vi: "📊 Dựng biểu đồ", th: "📊 สร้างกราฟ", in: "📊 Bangun diagram" },
  mkind_take_out: { ko: "➖ 덜어내고 세기", en: "➖ Take away and count", ja: "➖ へらして かぞえる", zh: "➖ 拿走再数", es: "➖ Quitar y contar", fr: "➖ Enlever et compter", de: "➖ Wegnehmen und zählen", pt: "➖ Tirar e contar", ru: "➖ Убери и посчитай", vi: "➖ Bớt rồi đếm", th: "➖ เอาออกแล้วนับ", in: "➖ Ambil lalu hitung" },
  mkind_gather: { ko: "➕ 모아서 세기", en: "➕ Gather and count", ja: "➕ あつめて かぞえる", zh: "➕ 收集再数", es: "➕ Juntar y contar", fr: "➕ Rassembler et compter", de: "➕ Sammeln und zählen", pt: "➕ Juntar e contar", ru: "➕ Собери и посчитай", vi: "➕ Gom rồi đếm", th: "➕ รวมแล้วนับ", in: "➕ Kumpulkan lalu hitung" },
  mkind_shapes: { ko: "🔺 도형", en: "🔺 Shapes", ja: "🔺 かたち", zh: "🔺 图形", es: "🔺 Figuras", fr: "🔺 Formes", de: "🔺 Formen", pt: "🔺 Figuras", ru: "🔺 Фигуры", vi: "🔺 Hình học", th: "🔺 รูปทรง", in: "🔺 Bangun" },
  mkind_fraction: { ko: "🍰 분수", en: "🍰 Fractions", ja: "🍰 分数", zh: "🍰 分数", es: "🍰 Fracciones", fr: "🍰 Fractions", de: "🍰 Brüche", pt: "🍰 Frações", ru: "🍰 Дроби", vi: "🍰 Phân số", th: "🍰 เศษส่วน", in: "🍰 Pecahan" },
  mkind_bar_graph: { ko: "📊 그래프", en: "📊 Graphs", ja: "📊 グラフ", zh: "📊 统计图", es: "📊 Gráficos", fr: "📊 Graphiques", de: "📊 Diagramme", pt: "📊 Gráficos", ru: "📊 Диаграммы", vi: "📊 Biểu đồ", th: "📊 กราฟ", in: "📊 Diagram" },
  mkind_numline: { ko: "📏 수직선", en: "📏 Number line", ja: "📏 数直線", zh: "📏 数轴", es: "📏 Recta numérica", fr: "📏 Droite graduée", de: "📏 Zahlenstrahl", pt: "📏 Reta numérica", ru: "📏 Числовая прямая", vi: "📏 Trục số", th: "📏 เส้นจำนวน", in: "📏 Garis bilangan" },
  mkind_geom: { ko: "📐 도형", en: "📐 Geometry", ja: "📐 図形", zh: "📐 几何", es: "📐 Geometría", fr: "📐 Géométrie", de: "📐 Geometrie", pt: "📐 Geometria", ru: "📐 Геометрия", vi: "📐 Hình học", th: "📐 เรขาคณิต", in: "📐 Geometri" },
  mkind_coord3d: { ko: "🧊 공간좌표", en: "🧊 3D coordinates", ja: "🧊 空間座標", zh: "🧊 空间坐标", es: "🧊 Coordenadas 3D", fr: "🧊 Coordonnées 3D", de: "🧊 Raumkoordinaten", pt: "🧊 Coordenadas 3D", ru: "🧊 Координаты в 3D", vi: "🧊 Tọa độ không gian", th: "🧊 พิกัดสามมิติ", in: "🧊 Koordinat 3D" },
  mkind_coord2d: { ko: "📈 좌표평면", en: "📈 Coordinate plane", ja: "📈 座標平面", zh: "📈 坐标平面", es: "📈 Plano cartesiano", fr: "📈 Plan cartésien", de: "📈 Koordinatenebene", pt: "📈 Plano cartesiano", ru: "📈 Координатная плоскость", vi: "📈 Mặt phẳng tọa độ", th: "📈 ระนาบพิกัด", in: "📈 Bidang koordinat" },
  mkind_angle: { ko: "📐 각도", en: "📐 Angles", ja: "📐 角度", zh: "📐 角度", es: "📐 Ángulos", fr: "📐 Angles", de: "📐 Winkel", pt: "📐 Ângulos", ru: "📐 Углы", vi: "📐 Góc", th: "📐 มุม", in: "📐 Sudut" },
  mkind_div: { ko: "➗ 나눗셈", en: "➗ Division", ja: "➗ わり算", zh: "➗ 除法", es: "➗ División", fr: "➗ Division", de: "➗ Division", pt: "➗ Divisão", ru: "➗ Деление", vi: "➗ Phép chia", th: "➗ การหาร", in: "➗ Pembagian" },
  mkind_mul: { ko: "✖️ 곱셈", en: "✖️ Multiplication", ja: "✖️ かけ算", zh: "✖️ 乘法", es: "✖️ Multiplicación", fr: "✖️ Multiplication", de: "✖️ Multiplikation", pt: "✖️ Multiplicação", ru: "✖️ Умножение", vi: "✖️ Phép nhân", th: "✖️ การคูณ", in: "✖️ Perkalian" },
  mkind_sub: { ko: "➖ 빼기", en: "➖ Subtraction", ja: "➖ ひき算", zh: "➖ 减法", es: "➖ Resta", fr: "➖ Soustraction", de: "➖ Subtraktion", pt: "➖ Subtração", ru: "➖ Вычитание", vi: "➖ Phép trừ", th: "➖ การลบ", in: "➖ Pengurangan" },
  mkind_add: { ko: "➕ 더하기", en: "➕ Addition", ja: "➕ たし算", zh: "➕ 加法", es: "➕ Suma", fr: "➕ Addition", de: "➕ Addition", pt: "➕ Adição", ru: "➕ Сложение", vi: "➕ Phép cộng", th: "➕ การบวก", in: "➕ Penjumlahan" },
  mkind_math: { ko: "🔢 수학", en: "🔢 Maths", ja: "🔢 さんすう", zh: "🔢 数学", es: "🔢 Matemáticas", fr: "🔢 Maths", de: "🔢 Mathe", pt: "🔢 Matemática", ru: "🔢 Математика", vi: "🔢 Toán", th: "🔢 คณิตศาสตร์", in: "🔢 Matematika" },
  mkind_picture: { ko: "🐥 그림 문제", en: "🐥 Picture question", ja: "🐥 えの もんだい", zh: "🐥 图画题", es: "🐥 Pregunta con dibujo", fr: "🐥 Question illustrée", de: "🐥 Bildaufgabe", pt: "🐥 Questão com figura", ru: "🐥 Задача с картинкой", vi: "🐥 Câu hỏi có hình", th: "🐥 โจทย์รูปภาพ", in: "🐥 Soal bergambar" },
  },
};

// 화면별 문자열은 파일을 나눠 둔다 — 한 파일이 수백 줄이 되면 손대기가 겁난다.
// 같은 키가 겹치면 나중 파일이 이긴다.
Object.assign(module.exports.strings, require("./ui_lesson"), require("./ui_wallet"));
