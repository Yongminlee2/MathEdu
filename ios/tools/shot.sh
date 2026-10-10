#!/bin/sh
# 시뮬레이터에서 화면 하나를 실행 인자로 띄워 찍는다 (탭 주입이 안 되는 맥이라서).
#   tools/shot.sh <저장할.png> -shot lesson -track math_g1 -lesson ml133
set -e
OUT="$1"; shift
DEV="${DEVICE:-booted}"
xcrun simctl terminate "$DEV" com.peep.math >/dev/null 2>&1 || true
xcrun simctl launch "$DEV" com.peep.math "$@" >/dev/null
sleep "${WAIT:-2.5}"
xcrun simctl io "$DEV" screenshot "$OUT" >/dev/null 2>&1
