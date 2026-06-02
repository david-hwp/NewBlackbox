#!/usr/bin/env bash
# e2e_pixel/tests/jd_captcha.sh — Pixel 模拟器京东验证码 E2E 测试
# 分辨率: 1080x2424, density: 420
# 改进版: 支持动态UI元素检测 + 截图验证 + 备选坐标

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib/utils.sh"

PACKAGE="top.niunaijun.blackbox"
JD_PACKAGE="com.jd.mrd.jingming"

# ── 坐标配置 (1080x2424) ──
# 主坐标（基于已知布局）
JD_ICON_X=135
JD_ICON_Y=394

# 验证码登录标签 — 多个备选位置
# 京东登录页通常有"密码登录"和"验证码登录"两个标签
TAP_VERIFY_TAB_X=538
TAP_VERIFY_TAB_Y=451
TAP_VERIFY_TAB_ALT_X=810    # 如果标签在右侧
TAP_VERIFY_TAB_ALT_Y=451

# 手机号输入框
PHONE_INPUT_X=603
PHONE_INPUT_Y=626

# 获取验证码按钮
GET_CODE_X=937
GET_CODE_Y=639
GET_CODE_ALT_X=850          # 备选位置
GET_CODE_ALT_Y=639

# 验证码输入框
VERIFY_CODE_INPUT_X=603
VERIFY_CODE_INPUT_Y=794

# 测试手机号
TEST_PHONE="13265710803"

# ANR 处理坐标
ANR_WAIT_X=540
ANR_WAIT_Y=1400

log_info "═══════════════════════════════════════════════════════════"
log_info "  Pixel JD 验证码 E2E 测试 (改进版)"
log_info "  Device: $($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
log_info "  Android: $($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
log_info "═══════════════════════════════════════════════════════════"

# ── 辅助函数: 带验证的点击 ──
tap_with_verify() {
    local x="$1"
    local y="$2"
    local desc="${3:-element}"
    local verify_text="${4:-}"

    log_info "点击 $desc (坐标: $x, $y)..."

    # 截图点击前
    local before
    before=$(take_screenshot "before_tap_${desc}")
    sleep 0.3

    # 执行点击
    $ADB shell input tap "$x" "$y"
    sleep 1.5

    # 截图点击后
    local after
    after=$(take_screenshot "after_tap_${desc}")

    # 比较截图
    if compare_screenshots "$before" "$after"; then
        log_warn "点击 $desc 后界面没有变化，坐标可能不准确"
        return 1
    fi

    log_ok "点击 $desc 成功，界面已变化 ✅"

    # 如果指定了验证文本，检查UI dump
    if [[ -n "$verify_text" ]]; then
        sleep 1
        local dump_xml="/sdcard/e2e_verify_dump.xml"
        $ADB shell uiautomator dump "$dump_xml" 2>/dev/null || true
        if $ADB shell cat "$dump_xml" 2>/dev/null | grep -q "$verify_text"; then
            log_ok "检测到验证文本 '$verify_text' ✅"
            $ADB shell rm -f "$dump_xml" 2>/dev/null || true
            return 0
        else
            log_warn "未检测到验证文本 '$verify_text'"
            $ADB shell rm -f "$dump_xml" 2>/dev/null || true
            return 1
        fi
    fi

    return 0
}

# ── 辅助函数: 动态查找并点击 ──
tap_element_dynamic() {
    local text_pattern="$1"
    local fallback_x="$2"
    local fallback_y="$3"
    local desc="${4:-$text_pattern}"

    log_info "尝试动态查找 '$desc'..."

    local coords
    if coords=$(find_element_by_text "$text_pattern" 2>/dev/null); then
        local ex ey
        ex=$(echo "$coords" | awk '{print $1}')
        ey=$(echo "$coords" | awk '{print $2}')
        log_ok "动态找到 '$desc' 坐标: ($ex, $ey)"
        tap_with_verify "$ex" "$ey" "$desc" && return 0
    fi

    log_warn "动态查找失败，使用固定坐标 ($fallback_x, $fallback_y)"
    tap_with_verify "$fallback_x" "$fallback_y" "$desc" && return 0

    return 1
}

# ── 辅助函数: 带验证的文本输入 ──
input_text_with_verify() {
    local x="$1"
    local y="$2"
    local text="$3"
    local desc="${4:-input}"
    local verify_hint="${5:-}"

    log_info "在 ($x, $y) 输入文本到 $desc..."

    # 先点击输入框获取焦点
    $ADB shell input tap "$x" "$y"
    sleep 0.5

    # 截图输入前
    local before
    before=$(take_screenshot "before_input_${desc}")

    # 清空并输入
    $ADB shell input keyevent 29   # KEYCODE_A (select all)
    sleep 0.2
    $ADB shell input keyevent 112  # KEYCODE_FORWARD_DEL
    sleep 0.2
    $ADB shell input text "$text"
    sleep 0.5

    # 截图输入后
    local after
    after=$(take_screenshot "after_input_${desc}")

    # 比较截图
    if compare_screenshots "$before" "$after"; then
        log_warn "输入文本后界面没有变化"
        return 1
    fi

    log_ok "文本输入成功 ✅"
    return 0
}

# ── 步骤 1: 启动 BlackBox ──
log_info "【步骤 1/7】启动 BlackBox..."
start_app
sleep 3
screenshot_file=$(take_screenshot "01_blackbox_main")
log_info "截图: $screenshot_file"

# ── 步骤 2: 检查京东秒送分身 ──
log_info "【步骤 2/7】检查京东秒送分身..."
if ! $ADB shell pm list packages | grep -q "$JD_PACKAGE"; then
    log_warn "京东秒送分身不存在，请在 BlackBox 中手动添加"
    log_warn "测试终止，请先安装京东秒送分身"
    exit 1
fi
log_ok "京东秒送分身已安装"

# ── 步骤 3: 启动京东秒送 ──
log_info "【步骤 3/7】启动京东秒送..."

# 辅助函数：检测 JD 是否在前台
is_jd_foreground() {
    # 方式1: 检查 mFocusedWindow（比 mCurrentFocus 更可靠）
    if $ADB shell dumpsys window | grep "mFocusedWindow=" | grep -q "$JD_PACKAGE"; then
        return 0
    fi
    # 方式2: 检查所有 mCurrentFocus 行（不只用 head -1）
    if $ADB shell dumpsys window | grep "mCurrentFocus=" | grep -v "mCurrentFocus=null" | grep -q "$JD_PACKAGE"; then
        return 0
    fi
    # 方式3: 检查 activity stack
    if $ADB shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topActivity" | grep -q "$JD_PACKAGE"; then
        return 0
    fi
    return 1
}

# 先检查 JD 是否已经在运行且在前台
if is_jd_foreground; then
    log_ok "JD 秒送已在运行且在前台，跳过启动"
else
    # 尝试动态找到京东秒送图标
    jdx=$JD_ICON_X
    jdy=$JD_ICON_Y
    if jd_coords=$(find_element_by_text "京东秒送" 2>/dev/null); then
        jdx=$(echo "$jd_coords" | awk '{print $1}')
        jdy=$(echo "$jd_coords" | awk '{print $2}')
        log_ok "动态找到京东秒送图标坐标: ($jdx, $jdy)"
    else
        log_warn "使用固定坐标点击京东秒送图标 ($jdx, $jdy)"
    fi

    $ADB shell input tap "$jdx" "$jdy"
    log_info "点击图标，等待启动..."

    # 等待启动，检测 ANR
    STARTED=0
    for i in $(seq 1 20); do
        sleep 3

        # 检查 ANR 对话框
        $ADB shell uiautomator dump /sdcard/anr_check.xml 2>/dev/null || true
        $ADB shell cat /sdcard/anr_check.xml 2>/dev/null | grep -q "没有响应" && {
            log_warn "ANR 对话框检测到，点击'等待'..."
            $ADB shell input tap "$ANR_WAIT_X" "$ANR_WAIT_Y"
        }
        $ADB shell rm -f /sdcard/anr_check.xml 2>/dev/null || true

        # 检查 JD 是否已进入焦点
        if is_jd_foreground; then
            log_ok "JD 秒送启动成功 ✅"
            STARTED=1
            break
        fi

        log_info "  [$i/20] 等待 JD 启动..."
    done

    if [ "$STARTED" -eq 0 ]; then
        log_warn "JD 秒送启动超时或失败 ⚠️"
        log_warn "Pixel 模拟器上 BlackBox 启动虚拟应用可能存在兼容性问题"
        log_warn "继续尝试盲操作（基于已知坐标）..."
    fi
fi

screenshot_file=$(take_screenshot "03_jd_launched")
log_info "截图: $screenshot_file"

# ── 步骤 4: 切换到验证码登录 ──
log_info "【步骤 4/7】切换到验证码登录..."

VERIFY_TAB_CLICKED=0

# 策略1: 动态查找"验证码登录"并点击
if tap_element_dynamic "验证码登录" "$TAP_VERIFY_TAB_X" "$TAP_VERIFY_TAB_Y" "验证码登录标签"; then
    VERIFY_TAB_CLICKED=1
# 策略2: 尝试备选坐标（右侧标签位置）
elif tap_with_verify "$TAP_VERIFY_TAB_ALT_X" "$TAP_VERIFY_TAB_ALT_Y" "验证码登录(备选)" "获取验证码"; then
    VERIFY_TAB_CLICKED=1
# 策略3: 尝试点击"验证码"（部分UI只显示"验证码"）
elif tap_element_dynamic "验证码" "$TAP_VERIFY_TAB_X" "$TAP_VERIFY_TAB_Y" "验证码标签"; then
    VERIFY_TAB_CLICKED=1
fi

if [ "$VERIFY_TAB_CLICKED" -eq 0 ]; then
    log_error "无法点击验证码登录标签 ❌"
    log_warn "可能原因:"
    log_warn "  1. JD 应用未正确加载（Pixel 模拟器启动问题）"
    log_warn "  2. 登录界面布局与预期不同"
    log_warn "  3. 当前已是验证码登录模式"
    log_info "继续尝试后续步骤..."
fi

sleep 2
screenshot_file=$(take_screenshot "04_verify_tab_clicked")
log_info "截图: $screenshot_file"

# ── 步骤 5: 输入手机号 ──
log_info "【步骤 5/7】输入手机号 $TEST_PHONE..."

PHONE_ENTERED=0

# 策略1: 使用主坐标输入
if input_text_with_verify "$PHONE_INPUT_X" "$PHONE_INPUT_Y" "$TEST_PHONE" "手机号输入框"; then
    PHONE_ENTERED=1
# 策略2: 尝试动态查找手机号输入框并输入
else
    phone_coords=""
    if phone_coords=$(find_element_by_text "请输入手机号" 2>/dev/null) || \
       phone_coords=$(find_element_by_text "手机号码" 2>/dev/null); then
        px=$(echo "$phone_coords" | awk '{print $1}')
        py=$(echo "$phone_coords" | awk '{print $2}')
        log_info "动态找到手机号输入框: ($px, $py)"
        if input_text_with_verify "$px" "$py" "$TEST_PHONE" "手机号输入框(动态)"; then
            PHONE_ENTERED=1
        fi
    fi
fi

if [ "$PHONE_ENTERED" -eq 0 ]; then
    log_error "无法输入手机号 ❌"
    log_warn "可能原因:"
    log_warn "  1. 输入框未获取焦点"
    log_warn "  2. 当前页面不是登录页"
fi

screenshot_file=$(take_screenshot "05_phone_entered")
log_info "截图: $screenshot_file"

# ── 步骤 6: 点击获取验证码 ──
log_info "【步骤 6/7】点击'获取验证码'按钮..."
clear_logcat

GET_CODE_CLICKED=0

# 策略1: 动态查找"获取验证码"
if tap_element_dynamic "获取验证码" "$GET_CODE_X" "$GET_CODE_Y" "获取验证码按钮"; then
    GET_CODE_CLICKED=1
# 策略2: 使用备选坐标
elif tap_with_verify "$GET_CODE_ALT_X" "$GET_CODE_ALT_Y" "获取验证码(备选)"; then
    GET_CODE_CLICKED=1
# 策略3: 尝试查找"发送验证码"
elif tap_element_dynamic "发送验证码" "$GET_CODE_X" "$GET_CODE_Y" "发送验证码按钮"; then
    GET_CODE_CLICKED=1
fi

sleep 3
screenshot_file=$(take_screenshot "06_get_code_clicked")
log_info "截图: $screenshot_file"

# 等待安全验证对话框出现（轮询检查）
log_info "等待安全验证对话框..."

PASS=0
FAIL_REASON=""
UI_DUMP="/sdcard/jd_final_dump.xml"

for check_round in $(seq 1 6); do
    sleep 3

    screenshot_file=$(take_screenshot "07_check_${check_round}")
    log_info "截图: $screenshot_file"

    $ADB shell uiautomator dump "$UI_DUMP" 2>/dev/null || true
    DUMP_CONTENT=$($ADB shell cat "$UI_DUMP" 2>/dev/null || echo "")
    $ADB shell rm -f "$UI_DUMP" 2>/dev/null || true

    # 检查多种可能的验证对话框文本
    if echo "$DUMP_CONTENT" | grep -q "安全验证"; then
        log_ok "✅ 检测到'安全验证'对话框 — 图片验证码已出现！"
        PASS=1
        break
    elif echo "$DUMP_CONTENT" | grep -q "captcha"; then
        log_ok "✅ 检测到 captcha 相关元素 — 验证组件已出现！"
        PASS=1
        break
    elif echo "$DUMP_CONTENT" | grep -q "滑动验证"; then
        log_ok "✅ 检测到'滑动验证' — 验证组件已出现！"
        PASS=1
        break
    elif echo "$DUMP_CONTENT" | grep -q "请向右滑动"; then
        log_ok "✅ 检测到'请向右滑动' — 滑块验证已出现！"
        PASS=1
        break
    elif echo "$DUMP_CONTENT" | grep -q "验证中"; then
        log_ok "✅ 检测到'验证中' — 验证流程已触发！"
        PASS=1
        break
    elif echo "$DUMP_CONTENT" | grep -q "智能检测"; then
        log_ok "✅ 检测到'智能检测' — 安全验证已出现！"
        PASS=1
        break
    fi

    log_info "  [$check_round/6] 未检测到安全验证组件，继续等待..."
done

# ── 步骤 7: 检查结果 ──
log_info "【步骤 7/7】检查结果..."

if [ "$PASS" -eq 0 ]; then
    log_warn "❌ 未检测到安全验证对话框"
    FAIL_REASON="未检测到安全验证组件"
fi

# 检查 logcat 中的相关日志
capture_logcat "jd_captcha"

# 检查 Toast 提示
TOAST_LOG=$($ADB logcat -d -t 100 2>/dev/null | grep -iE "toast|验证失败|网络异常|请重试" | tail -5 || true)
if [ -n "$TOAST_LOG" ]; then
    log_warn "检测到 Toast/提示信息:"
    echo "$TOAST_LOG"
fi

# 最终截图
screenshot_file=$(take_screenshot "08_final")
log_info "最终截图: $screenshot_file"

# 报告详细结果
log_info "═══════════════════════════════════════════════════════════"
log_info "  测试详情:"
log_info "    验证码登录标签点击: $([ $VERIFY_TAB_CLICKED -eq 1 ] && echo '✅' || echo '❌')"
log_info "    手机号输入: $([ $PHONE_ENTERED -eq 1 ] && echo '✅' || echo '❌')"
log_info "    获取验证码点击: $([ $GET_CODE_CLICKED -eq 1 ] && echo '✅' || echo '❌')"
log_info "    安全验证检测: $([ $PASS -eq 1 ] && echo '✅' || echo '❌')"
log_info "═══════════════════════════════════════════════════════════"

if [ "$PASS" -eq 1 ]; then
    log_info "═══════════════════════════════════════════════════════════"
    log_ok "  测试通过：安全验证组件已出现 ✅"
    log_info "═══════════════════════════════════════════════════════════"
    exit 0
else
    log_info "═══════════════════════════════════════════════════════════"
    log_warn "  测试未通过：$FAIL_REASON ⚠️"
    log_info "═══════════════════════════════════════════════════════════"
    exit 1
fi
