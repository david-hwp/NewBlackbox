# Attempt 02: Native `/proc` 伪装 — 伪装非分身环境

**Date**: 2026-06-02
**Direction**: 方向 B — 伪装非分身环境（`/proc/self/cmdline`、`/proc/self/maps`、`/proc/self/status` 伪装）
**Android Version**: 目标 Android 15/16 (Honor ABR-AN00)
**Status**: 编译通过 ✅ | E2E 待 Honor 设备验证 ⏳

---

## 背景

JD SDK 很可能通过读取 `/proc/self/cmdline`、`/proc/self/maps`、`/proc/self/status` 等文件来检测虚拟环境。在 Android 15+ 上，由于 SELinux 策略收紧，需要在 native 层 hook `open()`/`openat()`/`read()` 来过滤这些文件内容。

## 实现

### 修改文件

1. **`Bcore/src/main/cpp/Hook/ProcDisguiseHook.cpp`** (新建)
   - 使用 **xhook**（PLT/GOT hook）拦截 `open`、`openat`、`read`、`lseek`、`close`、`fstat`
   - 拦截的文件：
     - `/proc/self/cmdline` → 返回虚拟应用包名（如 `com.jd.mrd.jingming`）
     - `/proc/self/maps` → 过滤掉包含 `blackbox`、`niunaijun`、`libblackbox.so`、`libdobby.so`、`libxdl.so`、`xhook`、`NewBlackbox` 的行
     - `/proc/self/status` → 过滤掉 `Name:` 行中包含 `blackbox`/`niunaijun` 的内容
     - `/proc/self/comm` → 返回虚拟应用包名（截断至 15 字符）
   - 通过 **pipe** 创建假 fd，将伪造内容写入 pipe 返回给调用方
   - 使用 **pthread_key** 做线程局部存储，跟踪被 hook 的 fd

2. **`Bcore/src/main/cpp/Hook/ProcDisguiseHook.h`** (新建)
   - 声明 `hook_proc_disguise()`

3. **`Bcore/src/main/cpp/BoxCore.cpp`**
   - 在 `nativeHook()` 中注册 `hook_proc_disguise(env)`

4. **`Bcore/src/main/cpp/Android.mk`**
   - 添加 `xhook` 静态库编译 + 链接 `ProcDisguiseHook.cpp`

5. **`Bcore/src/main/java/top/niunaijun/blackbox/core/NativeCore.java`**
   - 新增 `setVirtualPackageName()` / `setHostPackageName()` native 方法声明

6. **`Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java`**
   - 在 `handleBindApplication()` 中调用 `NativeCore.setVirtualPackageName(packageName)`

### Java → Native 通信

- `BActivityThread.handleBindApplication()` 在绑定虚拟应用时，通过 JNI 调用 `NativeCore.setVirtualPackageName(packageName)`
- Native 层将包名存入 `g_virtual_pkg_name`，供后续伪装使用

### 编译结果

```
BUILD SUCCESSFUL — arm64-v8a + armeabi-v7a 均编译通过
APK: BlackBox_4.0.0_universal-debug.apk (45.7MB)
```

### 模拟器验证

- 模拟器 (emulator-5556, sdk_gphone64_arm64) 安装成功 ✅
- 基本启动正常 ✅

### Honor 设备 E2E

**未执行** — 无 Honor 设备连接

---

## 与方向 A 的对比

| 方向 | 覆盖的检测点 | 实现复杂度 |
|------|-------------|----------|
| A (execve hook) | `pm list packages`、`ps -A`、`cat /proc/.../cmdline`（通过 shell） | 中等 |
| B (proc disguise) | `/proc/self/cmdline`、 `/proc/self/maps`、 `/proc/self/status`、 `/proc/self/comm`（直接文件读取） | 较高 |

方向 B 比方向 A 覆盖更底层（直接文件读取 vs shell 命令），且使用了 xhook 做 PLT/GOT hook，理论上更可靠。

## 结论

- 实现完整，编译通过
- 由于缺少 Honor Android 16 测试设备，无法验证 JD captcha 效果
- **无法判定方向 B 是否有效**

## 下一步

回滚到基线，继续尝试**方向 C**（解决 WebView sandboxed process 问题）或**方向 D**（hook queryIntentActivities 等其他检测点）。

---

## 回滚命令

```bash
git checkout -- Bcore/src/main/cpp/Hook/ProcDisguiseHook.cpp \
                 Bcore/src/main/cpp/Hook/ProcDisguiseHook.h \
                 Bcore/src/main/cpp/BoxCore.cpp \
                 Bcore/src/main/cpp/Android.mk \
                 Bcore/src/main/java/top/niunaijun/blackbox/core/NativeCore.java \
                 Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java
```
