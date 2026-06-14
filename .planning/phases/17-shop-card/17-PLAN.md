# Phase 17: 店铺卡片新功能 - Plan Index

**Status:** In Progress
**Created:** 2026-06-14

## Goal

在现有店铺卡片和交易体系上补充 Phase 17 的三类能力：话费分钟计费、微信分身首次绑定修复、店铺卡片微信接收方绑定与一键分享。每个 wave 独立规划、独立验证，避免不同功能互相阻塞。

## Wave Index

- [Wave 1 - 话费分钟计费](17-01-PLAN.md)
- [Wave 2 - 微信分身首次绑定 clone mapping 修复](17-02-PLAN.md)
- [Wave 3 - 店铺卡片微信接收方绑定与一键分享](17-03-PLAN.md)

## Current Status

- Wave 1: Implemented, pending final regression on local server and real devices.
- Wave 2: Implemented, pending final Xiaomi regression for new clone creation/opening.
- Wave 3: Partially implemented and paused. Code is retained; top share entry is hidden. Store remark and WeChat receiver metadata are now persisted on the server/admin side, and APP card display uses remark under shop ID.

## Cross-Wave Constraints

- Java/Android 构建使用 JDK 21。
- 店铺卡片相关业务以系统店铺 ID 为唯一可信业务键。
- 引擎只提供 AIDL 能力和分身运行环境，不长期保存主 APP 业务数据。
- 主 APP 私有目录负责保存店铺卡片维度的微信接收方数据。
- 真机验证优先使用当前 OPPO / 小米测试设备和本地内网服务端。
