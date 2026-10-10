"""안드로이드 res/values*/strings.xml 의 app_name 을 <언어>.lproj/InfoPlist.strings 로 옮긴다.

홈 화면 이름도 안드로이드 한 벌만 원본으로 둔다. 빌드 단계(copy-assets.sh)가 부른다.
    python3 gen-infoplist-strings.py <res 폴더> <번들 폴더>
"""
import os
import re
import sys

# 안드로이드 values 폴더 접미사 → 아이폰 lproj 이름
LPROJ = {
    "": "en", "ko": "ko", "ja": "ja", "zh": "zh-Hans", "zh-rTW": "zh-Hant", "zh-rHK": "zh-HK",
    "es": "es", "pt": "pt", "de": "de", "fr": "fr", "ru": "ru",
    "in": "id", "vi": "vi", "th": "th",
}

res, out = sys.argv[1], sys.argv[2]
for suffix, lproj in LPROJ.items():
    path = os.path.join(res, "values" + ("-" + suffix if suffix else ""), "strings.xml")
    if not os.path.exists(path):
        continue
    text = open(path, encoding="utf-8").read()
    m = re.search(r'<string name="app_name">(.*?)</string>', text)
    if not m:
        sys.exit(f"app_name 이 없음: {path}")
    name = m.group(1).replace("\\'", "'").replace('"', '\\"')
    lines = [f'"CFBundleDisplayName" = "{name}";', f'"CFBundleName" = "{name}";']
    # 권한 설명(마이크·음성 인식)도 안드로이드 문구가 있으면 옮긴다
    for key, sname in (("NSMicrophoneUsageDescription", "ios_mic_usage"), ("NSSpeechRecognitionUsageDescription", "ios_speech_usage")):
        mm = re.search(r'<string name="%s">(.*?)</string>' % sname, text)
        if mm:
            lines.append(f'"{key}" = "{mm.group(1).replace(chr(92) + chr(39), chr(39))}";')
    d = os.path.join(out, lproj + ".lproj")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "InfoPlist.strings"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
