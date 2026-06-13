# Phase 15 Research: 滚动播报展示实践

## 结论

- 滚动播报适合“短、低打扰、持续可见”的运营公告；不适合替代必须确认的弹窗公告。
- 内容应使用单行文本，短内容静态展示，长内容才滚动，避免无意义动效。
- 滚动速度应慢，且用户触摸时暂停；系统辅助功能启用时应避免持续动画。
- 播报条作为 header 与主体之间的视觉分隔即可，不需要再叠加分割线。
- 颜色应低饱和、低对比压迫感，主色与现有绿色体系保持一致：浅绿色背景、深绿色文字。

## 采用方案

- 新公告类型：`SCROLLING_TICKER`，后台展示名称“滚动播报”。
- App 打开首页时按普通公告链路拉取 `published=true&type=SCROLLING_TICKER`，取最新一条。
- 布局位置：根布局中 header 后、主体区前，宽度 `match_parent`，高度约一行文字略高。
- 文案：只展示公告 content；title 只用于后台管理，不在首页播报条中占空间。
- 动效：Android 使用单行 `TextView` marquee；内容不溢出时不滚动。
- 可访问性：触摸探索开启时不自动滚动，仅静态省略；触摸播报条时暂停滚动。

## 参考来源

- WCAG 2.2 Success Criterion 2.2.2 Pause, Stop, Hide: 自动移动内容需要给用户暂停/停止机制。
- Material Design banner guidance: 横幅内容应明确、可见，不应过度打断上下文。
- Nielsen Norman Group animation guidance: 动效应服务于用户理解和注意力管理，避免持续无目的运动。
