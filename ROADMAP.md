# NewBlackbox 路线图

## Phase 1: 单实例运行模式
**状态**: 📝 已规划
**目标**: 实现"同时只运行一个分身应用"功能，在打开新分身应用时自动杀掉之前运行的其他分身应用，以节省系统内存。

**关键交付物**:
- `BProcessManagerService.killAllOtherProcesses()` — 批量杀掉其他应用进程
- `BlackBoxCore.launchApk()` 单实例集成 — 启动前自动清理
- UI 设置开关 — 用户可控制开启/关闭
- ActivityStack 清理（可选增强）

**计划文档**: [.planning/PLAN.md](.planning/PLAN.md)
**调研文档**: [.planning/research/RESEARCH.md](.planning/research/RESEARCH.md)

## Phase 2: TBD
