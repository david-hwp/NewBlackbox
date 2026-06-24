---
phase: 19-xpra-shop-authorization
plan: index
type: index
wave: all
depends_on: []
files_modified: []
autonomous: false
requirements:
  - PHASE-19-XPRA-SHOP-AUTHORIZATION
---

# Phase 19: Xpra 店铺授权窗口 - 标准计划索引

**Status:** Wave 5 Completed
**Updated:** 2026-06-24

## Phase Boundary

Phase 19 建立“店铺授权”远端浏览器链路：用户或超管按店铺打开绑定 profile 的服务端 Chromium，通过 Xpra HTML5 画面完成平台登录、授权状态探测和后台直达管理。Phase 19 不实现平台账号托管、自动验证码处理、自动经营动作或正式生产环境发布。

## Standard Plans

- [19-01-PLAN.md](19-01-PLAN.md) - Wave 1: 最小可行性验证
- [19-02-PLAN.md](19-02-PLAN.md) - Wave 2: 登录表单自动定位与视口对齐
- [19-03-PLAN.md](19-03-PLAN.md) - Wave 3: 授权体验优化与授权状态闭环
- [19-04-PLAN.md](19-04-PLAN.md) - Wave 4: PC 管理后台全屏远端店铺后台与可靠 resize
- [19-05-PLAN.md](19-05-PLAN.md) - Wave 5: VNC 调试通道默认关闭

## Legacy Record

- [19-LEGACY-PLAN.md](19-LEGACY-PLAN.md) 保留原叙述型计划、服务器排查记录和 Wave 1-3 验证流水。新执行和后续维护应以 `19-01-PLAN.md` 到 `19-05-PLAN.md` 为准。

## Current Environment Facts

- `zhirang-dev` 是线上/跳板环境；升级、部署、重启线上服务必须先获得明确同意。
- `192.168.0.210` 是 Phase 19 爬虫/远端浏览器服务器，不是管理后台服务器。
- `172.20.0.13` 是内网开发测试管理后台服务器，Mac，SSH 用户 `hewp`。
- 管理后台访问爬虫服务器必须经过 `zhirang-dev` nginx 代理；前端同源 `/zr-stream/<port>/` 代理到爬虫服务器动态 Xpra 端口。

## Execution Order

Wave 1-5 已完成并作为历史基线保留。下一次执行 Phase 19 时应新建后续 wave，除非用户明确要求重放旧 wave。
