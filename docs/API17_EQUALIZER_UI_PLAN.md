# API17 Equalizer UI Plan (Post-MVP)

Last Updated: 2026-05-25  
Module: `M-S4-AUDIO-011`  
Tasks Covered: `T-S4-AUDIO-061/062/063/064`

## 1. Context
- EQ MVP（开关 + 预设 + 持久化 + fail-open）已可用。
- 当前设置页控件可操作，但信息层级较弱，用户难以快速理解“当前状态是否生效/是否降级”。
- 目标是先完成界面规划和任务化，不在本轮直接改 UI 代码。

## 2. Scope
### In Scope
- EQ 入口与信息架构。
- 状态与失败降级 UI 矩阵（fail-open 可见化）。
- 低保真线框与关键交互流程。
- 实现任务拆分与验收条目。

### Out of Scope
- 本轮不实现界面代码。
- 本轮不做自定义 band 曲线编辑器。
- 本轮不接入 `BassBoost/Virtualizer`。

## 3. Information Architecture (T-S4-AUDIO-061)

### Option A (Recommended): Settings Inline EQ Card
- 位置：保留在现有设置页（`page_settings`）内，作为独立 EQ 卡片。
- 层级：`总开关 -> 当前状态 -> 预设控制 -> 说明/降级提示`。
- 优点：
  - 改动小，风险低，适配当前 API17 布局成本最低。
  - 不新增导航路径，车机触达更快。
- 缺点：
  - 设置页信息密度偏高，需要更清晰分组样式。

### Option B: Standalone EQ Subpage
- 位置：设置页只保留入口按钮，点击进入 EQ 子页。
- 优点：信息集中，可扩展空间更大。
- 缺点：需要额外导航与页面状态管理，首版成本高。

### IA Decision
- 首版采用 Option A（内嵌 EQ 卡片）。
- 首版包含：
  - EQ 总开关
  - 预设切换（上一档/下一档）
  - 当前状态文本
  - fail-open 说明
- 首版排除：
  - band 滑杆
  - 音效图形可视化
  - 多音效联动

## 4. UI State Matrix (T-S4-AUDIO-062)

| State ID | 触发条件 | 状态文本 | 可操作项 | 用户反馈 | 日志关键字 |
|---|---|---|---|---|---|
| `EQ_OFF` | 开关关闭 | 均衡器状态：关闭 | 预设可切换（持久化） | 关闭 toast + 动作反馈 | `eq config update enabled=false` |
| `EQ_ON_PENDING_SESSION` | 开启但暂无有效 session | 均衡器状态：等待播放器会话 | 允许切换预设 | 不报错，仅提示等待 | `eq config update enabled=true` |
| `EQ_ON_ACTIVE` | 开启且 init 成功 | 均衡器状态：已开启（Preset #N） | 全部可用 | 开启 toast + 状态稳定 | `eq init ok` + `eq apply preset` |
| `EQ_ON_NO_PRESETS` | 设备返回 preset 数为 0 | 均衡器状态：设备无预设，已降级 | 禁用预设切换按钮 | 说明“已降级，不影响播放” | `eq apply preset skip reason=no-presets` |
| `EQ_ON_SESSION_FUSED` | 本会话 init/apply 异常 | 均衡器状态：当前会话不可用（自动降级） | 保留开关；预设操作可保留但不承诺生效 | 明确“播放正常，EQ 降级” | `eq init fail` / `eq session fused` |

### Copy Guidelines
- 必须显式表达：EQ 问题不影响播放。
- 避免“失败/异常”恐慌词堆叠，首选“降级/当前会话不可用”。
- 所有状态文案长度控制在一行半以内（1024x600 可读性优先）。

## 5. Low-Fidelity Wireframe (T-S4-AUDIO-063)

```txt
┌──────────────────────────── 设置 ────────────────────────────┐
│ 服务配置（Emby + LrcApi）                                     │
│ ...                                                          │
│                                                              │
│ 音效（Equalizer）                                             │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ 启用均衡器                                   [   ON/OFF ] │
│ │ 状态：已开启（Preset #3） / 等待会话 / 会话降级          │
│ │                                                          │
│ │ 预设                                       [上一档][下一档]│
│ │ 当前：Preset #3                                         │
│ │                                                          │
│ │ 说明：设备不支持时会自动降级，不影响播放。                │
│ └──────────────────────────────────────────────────────────┘ │
│                                                              │
│ 下载缓存 / 检查更新 ...                                       │
└──────────────────────────────────────────────────────────────┘
```

## 6. Interaction Flows
1. 进入设置页：
- 立即刷新 EQ 状态与控件可操作性。

2. 开启 EQ：
- UI 先切到“开启/等待会话”。
- session 到来后切到“已开启”或“当前会话降级”。

3. 切换预设：
- 立即更新显示值与持久化。
- 若设备无预设，按钮禁用并显示降级文案。

4. 会话异常（fail-open）：
- 播放保持原样。
- EQ 状态从“已开启”切到“当前会话不可用（自动降级）”。

## 7. Implementation Taskization (T-S4-AUDIO-064)

### Proposed Execution Chain
1. `T-S4-AUDIO-065` EQ 卡片视觉分组与状态行重排（布局层）。
2. `T-S4-AUDIO-066` EQ 状态模型接线（active/pending/no-presets/fused）与 UI 渲染。
3. `T-S4-AUDIO-067` EQ 文案与交互反馈收口（含禁用态文案）。
4. `T-S4-AUDIO-068` EQ UI 本地回归 + API17 实机观察点补齐。

### Acceptance Additions
- 能明确区分“EQ 关闭”和“EQ 开启但当前会话降级”。
- `no-presets` 场景有稳定禁用态，不出现误导可操作状态。
- fail-open 场景下播放状态无负面变化，且 UI 可感知降级。

## 8. Risks
- 如果 `EqualizerManager` 不输出可消费状态，`EQ_ON_SESSION_FUSED` 只能靠日志推断。
- 设置页信息量继续增加，需避免视觉拥挤与误触。

## 9. Recommended Next Step
- 直接进入 `T-S4-AUDIO-065`（布局分组与状态行重排），并在实现前补最小状态模型字段。
