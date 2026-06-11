# Phase 12: 美团差评顾客信息采集

**Status:** In Progress
**Created:** 2026-06-11

## Goal

在用户成功进入美团外卖商家版分身后，引擎自动获取差评数据，并尽量关联顾客信息（用户 ID、手机号或虚拟号），按平台和店铺 ID 写入引擎自己的目录，便于主 APP 或 ADB 读取结果。

## Guardrails

- 不删除 OPPO 真机上的任何数据。
- 不删除目标账号下的任何店铺卡片。
- 接口请求优先由已登录的分身 APP 自己发起，引擎做被动监听、缓存扫描和结果落盘。
- 输出文件不得包含 token、cookie、ticket、session、完整手机号等认证敏感字段；手机号/虚拟号如必须输出，默认脱敏或单独标注来源。

## Fastest Path

1. 先用 ADB 在可 root 的小米真机上探真实链路：导入同一店铺分身数据，打开分身，触发订单、顾客评价、顾客消息、差评联系和联系顾客入口。
2. 每次探路后马上拉取 logcat、uiautomator、screenshot、引擎输出文件和虚拟目录缓存，确认字段来源。
3. 一旦某条链路稳定产出差评、订单、IM 会话或虚拟号线索，就固化到 Bcore 的采集器中。
4. 构建安装最新引擎到小米真机，用同一分身数据复测；只有 ADB 证据稳定后再扩展到 Pixel 或 OPPO。

## Implementation Tasks

1. 强化 `NetworkProbeManager` 和 `MeituanWaimaiNetworkProbePolicy`，覆盖美团订单详情、订单列表、IM rights、匿名会话、差评联系相关日志。
2. 扩展 `ReviewDataCollector` / `CustomerInfoResolver`，把 `orderViewId`、IM 会话 ID、顾客 ID、虚拟号候选与差评记录关联。
3. 扩展虚拟缓存扫描，增加订单、IM、聊天、联系人、rights 相关 SQLite/文本缓存关键字。
4. 输出结构按 `review-data/meituan-waimai/{wmPoiId}/reviews_yyyy-MM-dd.jsonl` 保持稳定，并为顾客信息增加 `source`、`confidence`、`associationStatus`。
5. 保留 ADB 探路脚本和临时 Activity，只作为调试入口；最终采集应在用户进入分身后自动开始。

## Verification

- 小米真机能打开导入的粉面先生分身，并进入已登录店铺。
- 引擎自动写出至少 1 条差评数据。
- 触发“差评联系”或订单详情后，输出中能看到可解释的顾客关联字段，或明确记录无法关联的原因和可用候选。
- 复测不产生 H403，不依赖 OPPO 设备 root，不修改 OPPO 原始数据。
