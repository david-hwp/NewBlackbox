# NewBlackbox 路线图

## Phase 1: 单实例运行模式 ✅ 已完成 (2026-05-30)
**目标**: 实现"同时只运行一个分身应用"功能，在打开新分身应用时自动杀掉之前运行的其他分身应用，以节省系统内存。

**关键交付物**:
- ✅ `ClientConfiguration.isSingleInstanceMode()` — 配置接口，默认关闭
- ✅ `BProcessManagerService.killAllOtherProcesses()` — 批量杀掉其他应用进程 + 通知清理
- ✅ `BlackBoxCore.launchApk()` 单实例集成 — 启动前自动清理（try-catch 保护）
- ✅ UI 设置开关 — Settings 页面添加 Single Instance Mode 开关
- ✅ `ActivityStack.finishAllActivitiesExcept()` + `BActivityManagerService` 暴露接口 — Activity 记录清理

**验证**: `./gradlew :app:compileDebugJavaWithJavac :app:compileDebugKotlin` — BUILD SUCCESSFUL

**计划文档**: [.planning/PLAN.md](.planning/PLAN.md)
**调研文档**: [.planning/research/RESEARCH.md](.planning/research/RESEARCH.md)

## Phase 2: TBD
