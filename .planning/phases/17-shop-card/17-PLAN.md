# Phase 17: 店铺卡片新功能 - Plan Index

**Status:** Completed
**Created:** 2026-06-14

## Goal

在现有店铺卡片和交易体系上补充 Phase 17 的能力：话费分钟计费、微信分身首次绑定修复、店铺卡片微信接收方绑定研究、店铺卡片排序、系统参数配置中心。每个 wave 独立规划、独立验证，避免不同功能互相阻塞。

## Wave Index

- [Wave 1 - 话费分钟计费](17-01-PLAN.md)
- [Wave 2 - 微信分身首次绑定 clone mapping 修复](17-02-PLAN.md)
- [Wave 3 - 店铺卡片微信接收方绑定与一键分享](17-03-PLAN.md)
- [Wave 4 - 店铺卡片长按拖动排序](17-04-PLAN.md)
- [Wave 5 - 系统参数配置中心](17-05-PLAN.md)

## Current Status

- Wave 1: Completed. 本地后端测试、后台前端构建、Android 编译、内网服务部署和 OPPO/小米安装已完成。
- Wave 2: Completed. clone mapping 初始化顺序修复已合并，Android 编译通过，主 APK 与引擎已安装到 OPPO/小米。
- Wave 3: Completed by scoped closure. 微信分享研究代码保留；顶部分享入口已隐藏；店铺备注和微信接收方元数据已接入服务端/后台，APP 卡片店铺 ID 下方改为展示备注。完整一键分享到微信联系人受微信分身有效登录态阻塞，作为遗留研究项记录，不阻塞本 phase 完成。
- Wave 4: Completed. 同用户同平台店铺卡片长按拖动排序已实现，排序结果用系统店铺 ID 上报服务端；长按晃动视觉恢复；内网服务、小米和 OPPO 安装验证已完成。
- Wave 5: Completed. 后台新增系统参数页面，服务端和 APP 消除可配置文案/时长硬编码；店铺卡片 5 个操作开关的标题和两行内容已纳入参数；内置参数不可删除；内网服务、小米和 OPPO 安装验证已完成。

## Cross-Wave Constraints

- Java/Android 构建使用 JDK 21。
- 店铺卡片相关业务以系统店铺 ID 为唯一可信业务键。
- 引擎只提供 AIDL 能力和分身运行环境，不长期保存主 APP 业务数据。
- 主 APP 私有目录负责保存店铺卡片维度的微信接收方数据。
- 真机验证优先使用当前 OPPO / 小米测试设备和本地内网服务端。
