# -*- coding: utf-8 -*-
"""07_answer(정답 해설 화면)만 다시 찍는다.

    python tool/shoot_answer.py            # 아직 정답 화면이 아닌 언어만
    python tool/shoot_answer.py ru th      # 지정한 언어만

`shoot_all_langs.py` 안에서 이 화면만 유독 잘 실패한다 — 앞선 화면들을 오가며
앱 상태가 흐트러지기 때문이다. 그래서 **레슨에 새로 들어가** 한 화면만 노린다.
매 문제마다 화면을 다시 읽어 보기 모양(버블/목록)을 판별하고, 정답이 나올 때까지
문제를 넘긴다.
"""
import io, os, subprocess, sys, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = r"C:\Users\사용자\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PKG = "com.peep.math"
ACT = f"{PKG}/com.piyak.english.ui.MainActivity"
OUT = os.path.join(ROOT, "store", "screenshots")

LANGS = ["ko", "en", "ja", "zh", "zh_Hant", "zh_HK", "es", "fr",
         "de", "pt", "ru", "th", "vi", "id"]
TAG = {"zh": "zh-CN", "zh_Hant": "zh-TW", "zh_HK": "zh-HK", "id": "id-ID",
       "ko": "ko-KR", "en": "en-US", "ja": "ja-JP", "es": "es-ES", "fr": "fr-FR",
       "de": "de-DE", "pt": "pt-BR", "ru": "ru-RU", "th": "th-TH", "vi": "vi-VN"}

BUBBLES = [(288, 996), (786, 996), (288, 1290), (786, 1290),
           (288, 1130), (786, 1130), (288, 1420), (786, 1420)]
ROWS = [(540, 1072), (540, 1219), (540, 1366), (540, 1512)]
BTN_WRONG = (199, 2164)
BTN_CHECK = (540, 2168)
BTN_CONTINUE = (540, 2159)


def sh(*a, binary=False):
    r = subprocess.run([ADB, *a], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def cap():
    return sh("exec-out", "screencap", "-p", binary=True)


def tap(x, y, w=1.0):
    sh("shell", "input", "tap", str(x), str(y)); time.sleep(w)


def verdict(png):
    """정답 True · 오답 False · 패널 없음 None — 계속하기 버튼 색으로."""
    im = Image.open(io.BytesIO(png)).convert("RGB")
    w, h = im.size
    for f in (0.900, 0.912, 0.888, 0.925, 0.875):
        r, g, b = im.getpixel((w // 2, int(h * f)))
        if g > r + 40 and g > b + 40:
            return True
        if r > g + 60 and r > b + 60:
            return False
    return None


def is_list_layout(png):
    """보기가 가로로 긴 목록 버튼인가 (긴 낱말인 언어에서 이 모양이 된다)."""
    im = Image.open(io.BytesIO(png)).convert("RGB")
    r, g, b = im.getpixel((int(im.size[0] * 0.07), 1072))
    return not (r > 250 and g > 244 and b > 220)


def already_ok(lang):
    p = os.path.join(OUT, lang, "07_answer.png")
    if not os.path.exists(p):
        return False
    im = Image.open(p).convert("RGB")
    w, h = im.size
    for f in (0.900, 0.912, 0.888, 0.925, 0.875):
        r, g, b = im.getpixel((w // 2, int(h * f)))
        if g > r + 40 and g > b + 40:
            return True
    return False


def shoot_answer(lang, questions=45):
    sh("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", TAG[lang])
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1.2)
    sh("shell", "am", "start", "-n", ACT)
    time.sleep(5.0)
    # **복습(오답 노트)을 쓰면 안 된다** — 거기엔 숫자 키패드 문제가 섞여 있어서
    # 버블 자리를 눌러 봐야 정답이 될 수 없다(45문제를 돌려도 못 맞혔다).
    # 유치원 첫 레슨은 그림을 세는 버블 문제라 눌러서 맞힐 수 있다.
    tap(540, 1440, 3.0)      # 홈 → 첫 학년 카드
    tap(136, 512, 4.0)       # 유닛의 첫 레슨

    # 보기 중 하나가 "모르겠어요"(건너뛰기)라 그 자리를 누르면 패널 없이 넘어간다.
    # 그래서 한 번에 맞힐 확률이 낮다 — 문제를 넉넉히 넘겨 가며 시도한다.
    for q in range(questions):
        png = cap()
        base = (ROWS + BUBBLES) if is_list_layout(png) else (BUBBLES + ROWS)
        # **문제마다 시작 자리를 돌린다.** 늘 첫 번째 보기만 누르면, 정답이 그 자리가
        # 아닌 문제들에서는 영원히 못 맞힌다 (복습 문제는 순서가 되풀이된다).
        spots = base[q % 4:] + base[:q % 4]
        for x, y in spots:
            tap(x, y, 0.9)
            tap(*BTN_CHECK, 2.2)
            png = cap()
            v = verdict(png)
            if v is True:
                d = os.path.join(OUT, lang)
                os.makedirs(d, exist_ok=True)
                open(os.path.join(d, "07_answer.png"), "wb").write(png)
                print(f"  {lang}: {q + 1}번째 문제에서 확보")
                return True
            if v is False:
                tap(*BTN_CONTINUE, 1.9)
                break
        else:
            print(f"  {lang}: 보기를 못 눌렀다 (문제 {q + 1})")
            return False
    print(f"  {lang}: 끝내 정답 화면 실패")
    return False


def main():
    want = sys.argv[1:] or [l for l in LANGS if not already_ok(l)]
    if not want:
        print("전부 정답 화면이다 — 할 일 없음")
        return
    print("대상:", " ".join(want))
    bad = [l for l in want if l in TAG and not shoot_answer(l)]
    sh("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "")
    print("실패:", bad if bad else "없음")


if __name__ == "__main__":
    main()
