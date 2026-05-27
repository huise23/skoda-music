# PLAN

Last Updated: 2026-05-27

## Current Stage
- Stage Name: S4 子阶段（系统 EQ 继承接线）
- Scope Source: `.ai/context/SCOPE.md`（2026-05-27）
- Stage Goal:
  - 在播放链路上优先尝试系统 EQ 接线，而不是仅停用应用内 EQ。
  - 保持设置页结构不变，先不做入口改版。
  - 系统 EQ 不可用时给出 toast 提示，并允许用户手动开启应用内 EQ 兜底。
  - 保持 API17 fail-open：音效链路失败不影响播放。

## Scope Validation

### In Scope
- 系统 EQ 会话接线：随播放 session open/close 音效控制会话。
- 启动默认策略：先关闭应用内 EQ，再尝试系统 EQ。
- 不可用提示：系统 EQ 接线失败时提供 toast。
- 手动兜底：保留用户手动开启应用内 EQ 的路径。
- 本地验证 + API17 实机观察点补齐。

### Out of Scope
- `BassBoost/Virtualizer` 等附加音效扩展。
- EQ 页面入口和信息架构重做（设置页先不动）。
- 自定义曲线编辑器、导入导出、复杂 preset 管理。
- 播放主链路重构（仍保持 Exo + download-only）。
- 与本次系统 EQ 接线无关的外部验收任务。

## Major Work Blocks

### W1 系统 EQ 接线骨架
- 目标: 在 `MainActivity + PlaybackEngine` session 生命周期上接入系统音效会话 open/close。
- 结果: 不再停留于“仅关闭应用EQ”，而是实际尝试系统接管。

### W2 策略联动与兜底
- 目标: 启动时默认应用 EQ 关闭；系统失败时提示；用户可手动开启应用 EQ。
- 结果: 达到“系统优先 + 手动兜底”闭环。

### W3 交互与提示收口
- 目标: 保持设置页结构不变，仅补齐状态提示文案与触发时机。
- 结果: 用户可感知系统链路是否可用，但不引入 UI 大改。

### W4 回归与边界维护
- 目标: 补齐本地验证和 API17 实机观察点，更新外部留证入口。
- 结果: 能回答“系统 EQ 是否被尝试接管、失败时是否可兜底”。

## Recommended Order
1. `W1`：先打通系统 EQ 会话接线。
2. `W2`：再做“系统优先 + 手动兜底”策略联动。
3. `W3`：补齐 toast/反馈提示，不改设置页结构。
4. `W4`：本地回归 + API17 实机观察点整理。

## Dependency Graph
- `W1 -> W2 -> W3 -> W4`

## Risks & Assumptions
- 风险: 部分 API17 ROM 不响应系统音效会话广播，可能仍无可听差异。
- 风险: “系统不可用”判断口径不清会误导用户，需要把提示文案收敛到“可手动启用应用EQ”。
- 假设: `PlaybackEngine.audioSessionId()` 已可复用，足够支撑系统会话接线。
- 假设: 当前设置页结构保持不变，避免本轮 UI 范围膨胀。

## Stage Milestones
- M1: 系统 EQ 会话接线完成（Done）。
- M2: 启动默认关闭应用 EQ + 手动兜底链路完成（Done）。
- M3: 提示文案/反馈链路完成（Done）。
- M4: 本地验证完成，API17 观察点进入外部窗口（In Progress）。
