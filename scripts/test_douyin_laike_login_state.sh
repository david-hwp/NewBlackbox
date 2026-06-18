#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
XIAOMI="${XIAOMI:-3ca26684}"
OPPO="${OPPO:-55J7JJWKTWKNHYZL}"

MAIN_PKG="com.zhirang.zhanghaoguanjia"
ENGINE_PKG="com.zhirang.zhanghaoguanjia.engine"
TARGET_PKG="com.bytedance.ls.merchant"
WRONG_PKGS=(
  "com.sankuai.meituan.meituanwaimaibusiness"
  "com.sankuai.meituan.merchant"
  "com.sankuai.meituan.meituanwaimaibusiness"
)

CLONE_ID="CLN1-15200837196-com.bytedance.ls.merchant-N1-U25-R672c8b60"
CLONE_DIR="/data/user/0/${ENGINE_PKG}/blackbox/accounts/10/cards/${CLONE_ID}/user/25/${TARGET_PKG}"
ENGINE_ROOT="/data/user/0/${ENGINE_PKG}"
V6_DIR="${V6_DIR:-$(cat /tmp/dylk-latest-v6-dir.txt 2>/dev/null || true)}"
V6_TAR="${V6_DIR}/xiaomi-restore-data.tar"

RUN_ID="$(date +%Y%m%d-%H%M%S)"
UI_XML="/tmp/dylk-ui-${RUN_ID}.xml"
RESULT_UI="/tmp/dylk-result-ui-${RUN_ID}.xml"
RESULT_LOG="/tmp/dylk-result-log-${RUN_ID}.txt"

adb_x() {
  "$ADB" -s "$XIAOMI" "$@"
}

adb_o() {
  "$ADB" -s "$OPPO" "$@"
}

dump_ui() {
  adb_x shell 'timeout 8 uiautomator dump --compressed /sdcard/window.xml >/dev/null 2>&1 || true' >/dev/null
  adb_x shell cat /sdcard/window.xml > "$UI_XML"
}

print_texts() {
  local file="${1:-$UI_XML}"
  python3 - "$file" <<'PY'
import html
import re
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except Exception:
    data = open(path, "r", encoding="utf-8", errors="ignore").read()
    for text, bounds in re.findall(r'text="([^"]*)"[^>]*bounds="([^"]*)"', data):
        text = html.unescape(text)
        if text.strip():
            print(bounds, text[:160])
    raise SystemExit

for node in root.iter("node"):
    text = html.unescape(node.attrib.get("text", ""))
    bounds = node.attrib.get("bounds", "")
    if text.strip():
        print(bounds, text[:160])
PY
}

center_for_text() {
  local text="$1"
  local xmin="${2:-0}"
  local xmax="${3:-99999}"
  local exact="${4:-1}"
  local file="${5:-$UI_XML}"
  python3 - "$file" "$text" "$xmin" "$xmax" "$exact" <<'PY'
import html
import re
import sys
import xml.etree.ElementTree as ET

path, wanted, xmin, xmax, exact = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5] == "1"
root = ET.parse(path).getroot()
pattern = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

for node in root.iter("node"):
    text = html.unescape(node.attrib.get("text", ""))
    if not text:
        continue
    matched = text == wanted if exact else wanted in text
    if not matched:
        continue
    m = pattern.fullmatch(node.attrib.get("bounds", ""))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    cx = (x1 + x2) // 2
    cy = (y1 + y2) // 2
    if xmin <= cx <= xmax:
        print(f"{cx} {cy} {x1} {y1} {x2} {y2} {text}")
        raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_center_for_text() {
  local text="$1"
  local xmin="${2:-0}"
  local xmax="${3:-99999}"
  local exact="${4:-1}"
  local line
  line="$(center_for_text "$text" "$xmin" "$xmax" "$exact")"
  local x y
  x="$(awk '{print $1}' <<<"$line")"
  y="$(awk '{print $2}' <<<"$line")"
  echo "tap '$text' at ${x},${y}"
  adb_x shell input tap "$x" "$y"
}

close_announcements() {
  for _ in 1 2 3 4 5; do
    dump_ui
    if ! grep -qE '系统公告|知道了' "$UI_XML"; then
      return 0
    fi
    if center_for_text "知道了" >/dev/null 2>&1; then
      tap_center_for_text "知道了"
      sleep 0.8
    else
      adb_x shell input keyevent BACK
      sleep 0.8
    fi
  done
  dump_ui
  if grep -qE '系统公告|知道了' "$UI_XML"; then
    echo "announcement still visible"
    print_texts "$UI_XML"
    return 1
  fi
}

restore_v7() {
  if [[ ! -f "$V6_TAR" ]]; then
    echo "missing V6 tar: $V6_TAR" >&2
    return 1
  fi

  for pkg in "$MAIN_PKG" "$ENGINE_PKG" "$TARGET_PKG" "${WRONG_PKGS[@]}"; do
    adb_x shell am force-stop "$pkg" >/dev/null 2>&1 || true
  done
  sleep 1

  adb_x shell "run-as ${ENGINE_PKG} sh -c 'mkdir -p \"${CLONE_DIR}\" && cd \"${CLONE_DIR}\" && toybox find . -mindepth 1 -maxdepth 1 -exec rm -rf {} +'"
  adb_x exec-in run-as "$ENGINE_PKG" sh -c "cd '${CLONE_DIR}' && tar -xf -" < "$V6_TAR"
  adb_o exec-out run-as "$ENGINE_PKG" sh -c "cd '${ENGINE_ROOT}' && tar -cf - blackbox/system/accounts.conf" \
    | adb_x exec-in run-as "$ENGINE_PKG" sh -c "cd '${ENGINE_ROOT}' && tar -xf -"

  adb_x shell "run-as ${ENGINE_PKG} sh -s" <<'SH'
cd '/data/user/0/com.zhirang.zhanghaoguanjia.engine/blackbox/accounts/10/cards/CLN1-15200837196-com.bytedance.ls.merchant-N1-U25-R672c8b60/user/25/com.bytedance.ls.merchant' || exit 2
echo restored_files=$(find . -type f | wc -l)
for f in \
  'shared_prefs/cookieStore.xml' \
  'cache/ttnet_storage/prefs/local_prefs.json' \
  'files/keva/repo/lsm_account/lsm_account.blk' \
  'files/offlineX/bc7ca1d076a570ae499f02d873f8cf49/bc_account_app/7594385116021964809/res/resource/js/index.10c0d608.js'; do
  [ -e "$f" ] && stat -c '%s %y %n' "$f" || echo "missing:$f"
done
cd /data/user/0/com.zhirang.zhanghaoguanjia.engine || exit 3
stat -c '%s %y %n' blackbox/system/accounts.conf
SH

  adb_x shell am force-stop "$ENGINE_PKG" >/dev/null 2>&1 || true
}

ensure_home() {
  adb_x shell am start -n "${MAIN_PKG}/.view.login.LoginActivity" >/dev/null
  sleep 2
  close_announcements
  dump_ui
  if grep -q "算力余额" "$UI_XML"; then
    return 0
  fi
  echo "main app home is not visible"
  print_texts "$UI_XML"
  return 1
}

select_douyin_laike() {
  # Reset platform list near the top, then search downward.
  adb_x shell input swipe 130 620 130 1840 450 >/dev/null 2>&1 || true
  sleep 0.4
  adb_x shell input swipe 130 620 130 1840 450 >/dev/null 2>&1 || true
  sleep 0.4

  for _ in 1 2 3 4 5; do
    dump_ui
    if center_for_text "抖音来客" 0 320 >/dev/null 2>&1; then
      tap_center_for_text "抖音来客" 0 320
      sleep 1.2
      dump_ui
      if center_for_text "湘食味大食堂" 330 1100 >/dev/null 2>&1; then
        echo "selected Douyin Laike and verified shop card"
        return 0
      fi
      echo "Douyin Laike tapped, but target shop is not visible"
      print_texts "$UI_XML"
      return 1
    fi
    adb_x shell input swipe 130 1840 130 620 600
    sleep 0.8
  done

  echo "Douyin Laike platform not found"
  print_texts "$UI_XML"
  return 1
}

open_shop_and_collect() {
  dump_ui
  if ! center_for_text "湘食味大食堂" 330 1100 >/dev/null 2>&1; then
    echo "refusing to tap: target shop card is not visible"
    print_texts "$UI_XML"
    return 1
  fi

  adb_x logcat -c
  tap_center_for_text "湘食味大食堂" 330 1100
  sleep 40

  adb_x shell 'timeout 8 uiautomator dump --compressed /sdcard/window.xml >/dev/null 2>&1 || true'
  adb_x shell cat /sdcard/window.xml > "$RESULT_UI"
  adb_x logcat -d -v time \
    | rg -n "ActivityTaskManager|START u0|Displayed|com\\.bytedance\\.ls\\.merchant|com\\.sankuai|LoginActivity|MainActivity|SplashActivity|PhoneNumberLoginActivity|Toast|params_login_state|merchantAccountModel|uid=0|laike_id|jumpLoginUser|getLocalAccountInfo|no valid data|passport/user/logout|can_select_role|errorCode|bizCode|Account|TicketGuard|TTNetInit|iRequestTagHeaderProvider|登录失效|重新登录" \
    | rg -vi "headers|set-cookie|cookie|x-tt-token|x-ms-token|sessionid|sid_guard|odin_tt|passport_csrf|authorization|password" \
    > "$RESULT_LOG" || true

  python3 - "$RESULT_UI" "$RESULT_LOG" <<'PY'
import sys
ui = open(sys.argv[1], "r", encoding="utf-8", errors="ignore").read()
log = open(sys.argv[2], "r", encoding="utf-8", errors="ignore").read()
login_terms = any(t in ui for t in ["手机登录", "密码登录", "获取短信验证码", "未注册的手机号"])
home_terms = any(t in ui for t in ["湘食味大食堂", "订单", "门店", "首页", "经营", "数据", "核销"])
wrong_platform = "com.sankuai" in log
bytedance_seen = "com.bytedance.ls.merchant" in log
print(f"RESULT_UI={sys.argv[1]}")
print(f"RESULT_LOG={sys.argv[2]}")
print(f"LOGIN_TERMS={login_terms}")
print(f"HOME_TERMS={home_terms}")
print(f"BYTEDANCE_LOGS={bytedance_seen}")
print(f"WRONG_PLATFORM_LOGS={wrong_platform}")
if wrong_platform:
    raise SystemExit(3)
if login_terms:
    raise SystemExit(2)
if not bytedance_seen:
    raise SystemExit(4)
raise SystemExit(0)
PY
}

main() {
  restore_v7
  ensure_home
  select_douyin_laike
  open_shop_and_collect
}

main "$@"
