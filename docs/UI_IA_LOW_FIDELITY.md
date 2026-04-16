# UI_IA_LOW_FIDELITY

Last Updated: 2026-04-16 16:28
Status: Draft v2 (Clean)

## Purpose
输出车机横屏场景下的 UI 信息架构低保真说明，作为后续界面实现与联调基线。
本文件只定义页面结构、信息分区、关键交互路径，不包含视觉像素稿或前端代码。

## Global IA
- 导航模式: 左侧固定导航（已确认）
- 导航顺序: `Home -> Queue -> Library -> Settings`（已确认）
- 主题: 深浅双主题（已确认）
- 动效: 低动效（已确认）
- 风格: 轻玻璃拟态（已确认）
- 密度: 中密度（已确认）

## Navigation Structure
- `Home` 首页（当前播放 + 推荐并列）
- `Queue` 播放队列（第 2 导航）
- `Library` 搜索/媒体库（第 3 导航）
- `Settings` 服务配置与系统状态

说明: 导航项固定 4 个，避免驾驶场景路径过深。

## Page Layouts (Low Fidelity)

### Home
```
+--------------------------------------------------------------+
| NAV |                     HOME                               |
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

### Queue
```
+--------------------------------------------------------------+
| NAV | Queue Header (Current Track / 推荐歌曲 / Clear)      |
|-----+--------------------------------------------------------|
|     | Queue Items                                            |
|     | - tap item => play now                                  |
|     | - remove item                                            |
|     | - reorder (future)                                       |
+--------------------------------------------------------------+
```

交互要点:
- 队列项单击即播放（不使用长按作为主交互）。
- 当前播放项高亮。
- `推荐歌曲` 默认刷新 20 条，保留当前播放，仅替换当前后的待播项。
- 推荐失败/空结果时保留原待播队列并提示失败。

### Library
```
+--------------------------------------------------------------+
| NAV | Search Bar + Filter                                   |
|-----+--------------------------------------------------------|
|     | Result List (Title / Artist / Duration / Source)      |
|     | tap item => play now                                   |
+--------------------------------------------------------------+
```

交互要点:
- 列表项单击即播放（不使用长按作为主交互）。
- 空查询展示最近播放或常用入口。
- 搜索失败/无结果提供清晰占位态。

### Settings
```
+--------------------------------------------------------------+
| NAV | Unified Service Config                                |
|-----+--------------------------------------------------------|
|     | Emby + LrcApi 同一配置区（统一卡片/统一信息层级）     |
|     | [Test Emby] -> pass => auto-save, fail => block save  |
|     | [Test LrcApi] -> pass => auto-save, fail => block save|
|     | Runtime Status / Toast Hint / Self-heal Summary       |
+--------------------------------------------------------------+
```

交互要点:
- Emby 与 LrcApi 放在同一配置区，便于集中维护。
- 两项均采用“测试通过即自动保存，测试失败阻断对应保存”。
- 两项保存互不等待（各测各存）。
- 启动自愈后显示聚合 toast。

## Cross-Page State Model
- `Global Player State`: 当前曲目、播放状态、进度、队列引用。
- `Service Readiness State`:
  - Emby Ready / Not Ready
  - LrcApi Ready / Not Ready
- `Lyrics State`: Loading / Ready / NoLyrics（与 `LYRICS_STATE_MACHINE` 对齐）。

## Critical User Flows
1. 冷启动 -> 自愈 -> toast -> Home/Settings。
2. 首页点击推荐 -> 入队 -> 播放状态更新。
3. 方向盘下一曲 -> 播放状态切换 -> Home 与 Queue 同步。
4. Queue 点击推荐歌曲 -> 替换未播放段（20 条）-> 当前播放不中断。
5. Settings 测试 Emby 或 LrcApi:
   - 通过 -> 自动保存对应配置。
   - 失败 -> 阻断对应配置保存并提示失败。

## Error and Empty States
- Emby 未就绪: 首页播放卡显示“请先完成 Emby 配置”。
- 推荐不可用: 推荐区显示空态，不影响播放控制区域。
- LrcApi 未就绪: 歌词区域显示“远程歌词不可用”；不阻断主播放流程，但阻断 LrcApi 配置保存。

## Performance Notes
- 首屏优先渲染导航与播放卡，推荐列表延后填充。
- 避免多层半透明重叠与复杂动画。
- 列表滚动时不触发额外玻璃特效计算。

## Non-Goals
- 不定义高保真视觉细节（颜色值、像素、动效曲线）。
- 不定义组件库实现细节。
- 不实现 UI 代码。
