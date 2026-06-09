# Attempt 02: Native `/proc` 伪装 — E2E 测试结果

**Date**: 2026-06-02
**Direction**: 方向 B — 伪装非分身环境（`/proc/self/cmdline`、`/proc/self/maps`、`/proc/self/status` 伪装）
**Device**: Pixel Emulator (sdk_gphone64_arm64), Android API 35
**Status**: 编译通过 ✅ | E2E **无效** ❌

---

## 实现

- 新建 `Bcore/src/main/cpp/Hook/ProcDisguiseHook.cpp` / `.h`
- 使用 xhook（PLT/GOT hook）拦截 `open`、`openat`、`read`、`lseek`、`close`
- 伪装 `/proc/self/cmdline` / `/proc/self/maps` / `/proc/self/status`
- 修改 `Android.mk`、`BoxCore.cpp`、`NativeCore.java`、`BActivityThread.java`

## E2E 测试

### 测试步骤

1. 编译并安装方向 B APK ✅
2. 启动 BlackBox + JD 秒送 ✅
3. 进入登录页 ✅
4. 尝试切换"验证码登录"标签 ❌

### 测试结果

| 检查项 | 基线 | 方向 A | 方向 B | 变化 |
|--------|------|--------|--------|------|
| BlackBox 启动 | ✅ | ✅ | ✅ | 无 |
| JD 秒送启动 | ✅ | ✅ | ✅ | 无 |
| 登录页显示 | ✅ | ✅ | ✅ | 无 |
| 验证码登录标签切换 | ❌ | ❌ | ❌ | **无改善** |

### 日志分析

```
ProcDisguiseHook 日志：无输出
xhook 日志：无输出
/proc 访问日志：无输出
```

**ProcDisguiseHook hook 未生效** — 可能原因：
1. `ProcDisguiseHook::init()` 未被正确调用
2. xhook 注册失败（`xhook_refresh` 返回值未检查）
3. 日志级别问题（ALOGD 可能被过滤）
4. `BActivityThread.handleBindApplication()` 未调用 `NativeCore.setVirtualPackageName()`

## 结论

- 方向 B 在 Pixel 模拟器上**未产生任何可见效果**
- JD SDK 仍然检测到虚拟环境并禁用验证码登录
- **方向 B 判定为无效**

## 累计尝试

| 方向 | 检测点 | 结果 |
|------|--------|------|
| A (execve hook) | shell 命令 (`pm list`, `ps`) | ❌ 无效 |
| B (proc disguise) | `/proc/self/cmdline`, `/proc/self/maps` | ❌ 无效 |

## 下一步

回滚到基线，尝试**方向 C**（解决 WebView sandboxed process 问题）或**方向 D**（Intent 查询过滤 + 系统属性伪装）。
