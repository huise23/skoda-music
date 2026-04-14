# UI_IA_LOW_FIDELITY

Last Updated: 2026-04-14 11:30:39
Status: Draft v1 (T-004)

## Purpose
输出车机横屏场景下的 UI 信息架构低保真说明，作为后续界面实现与联调基线。  
本文件只定义页面结构、信息分区、关键交互路径，不包含视觉像素稿或前端代码。

## Global IA
- 导航模式: 左侧固定导航（已确认）
- 主题: 深浅双主题（已确认）
- 动效: 低动效（已确认）
- 风格: 轻玻璃拟态（已确认）
- 密度: 中密度（已确认）

## Navigation Structure
- `Home` 首页（当前播放 + 推荐并列）
- `Library` 搜索/媒体库
- `Queue` 播放队列
- `Settings` 配置与系统状态

说明: 导航项固定 4 个，避免驾驶场景路径过深。

## Page Layouts (Low Fidelity)

### Home
```
+--------------------------------------------------------------+
| NAV |                 HOME                                   |
|-----+------------------------------+-------------------------|
|     | Current Playing Card         | Recommendation List     |
|     | - Cover / Title / Artist     | - Server-first items    |
|     | - Prev Play/Pause Next       | - fallback empty state  |
|     | - Progress / Time            | - quick play            |
+--------------------------------------------------------------+
```

交互要点:
- 上下曲按钮与方向盘媒体键行为一致。
- 推荐项点击后加入队列并切换当前播放。

### Library
```
+--------------------------------------------------------------+
| NAV | Search Bar + Filter                                   |
|-----+--------------------------------------------------------|
|     | Result List (Title / Artist / Duration / Source)      |
|     | Item Actions: Play Now / Add Queue / View Album       |
+--------------------------------------------------------------+
```

交互要点:
- 空查询展示最近播放或常用入口。
- 搜索失败/无结果提供清晰占位态。

### Queue
```
+--------------------------------------------------------------+
| NAV | Queue Header (Current Track / Clear / Shuffle)        |
|-----+--------------------------------------------------------|
|     | Queue Items                                             |
|     | - reorder (future)                                      |
|     | - remove item                                            |
+--------------------------------------------------------------+
```

交互要点:
- 当前播放项高亮。
- 切歌后队列状态与播放状态强一致。

### Settings
```
+--------------------------------------------------------------+
| NAV | Service Config                                         |
|-----+--------------------------+-----------------------------|
|     | Emby Config Form         | LrcApi Config Form         |
|     | [Test Emby] [Save]       | [Test LrcApi] (non-block)  |
|     | Runtime Status / Toast Hint / Self-heal Summary (TBD) |
+--------------------------------------------------------------+
```

交互要点:
- Emby 测试通过才允许保存（已确认）。
- LrcApi 测试失败仅告警，不阻断保存（已确认）。
- 启动自愈后显示聚合 toast（已确认）。

## Cross-Page State Model
- `Global Player State`: 当前曲目、播放状态、进度、队列引用。
- `Service Readiness State`:
  - Emby Ready / Not Ready
  - LrcApi Ready / Not Ready
- `Lyrics State`: Loading / Ready / NoLyrics（与 `LYRICS_STATE_MACHINE` 对齐）。

## Critical User Flows
1. 冷启动 -> 自愈 -> toast -> Home/Settings。
2. 首页点击推荐 -> 入队 -> 播放页状态更新。
3. 方向盘下一曲 -> 播放状态切换 -> 首页与队列同步。
4. 设置页测试 Emby 通过 -> 保存成功 -> Home 可播放。
5. 设置页 LrcApi 失败 -> 保存仍允许 -> 歌词远程补全降级。

## Error and Empty States
- Emby 未就绪: 首页播放卡显示“请先完成 Emby 配置”。
- 推荐不可用: 推荐区显示空态，不影响播放控制区域。
- LrcApi 未就绪: 歌词区域显示“远程歌词不可用”，不阻断主流程。

## Performance Notes
- 首屏优先渲染播放卡与导航，推荐列表延后填充。
- 避免多层半透明重叠与复杂动画。
- 列表滚动时不触发额外玻璃特效计算。

## Non-Goals
- 不定义高保真视觉细节（颜色值、像素、动效曲线）。
- 不定义组件库实现细节。
- 不实现 UI 代码。

## To Confirm
- 设置页是否展示“启动自愈字段明细”（与 B-004 关联）。
- 是否需要独立“Now Playing”全屏页（当前假设不需要，先在 Home 卡片承载）。
