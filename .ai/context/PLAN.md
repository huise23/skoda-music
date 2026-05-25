# PLAN

Last Updated: 2026-05-25

## Current Stage
- Stage Name: S4 子阶段（EQ 界面规划完成，进入 UI 实现）
- Scope Source: `.ai/context/SCOPE.md`（2026-05-19）
- Stage Goal:
  - 在不破坏 API17 fail-open 前提下，落地 EQ 界面实现并完成本地验证。
  - 保持界面状态可解释（off/pending/active/no-presets/fused）并可回归。

## Scope Validation

### In Scope
- EQ 卡片布局实现：视觉分组、状态行位置、触控可读性。
- EQ 状态渲染实现：`off/pending/active/no-presets/fused`。
- 文案与反馈收口 + 本地回归 + API17 观察点补齐。

### Out of Scope
- 本轮不做 `BassBoost/Virtualizer` 实装与联动。
- 不改播放主链路策略（仍保持 Exo + download-only 现状）。

## Major Work Blocks

### W1 EQ 布局与结构实现
- 目标: 把规划里的 EQ 卡片结构落地到设置页。
- 结果: 视觉分组清晰、触控可读性达标。

### W2 EQ 状态渲染与文案收口
- 目标: 落地状态渲染与降级反馈，确保 fail-open 可见且不误导。
- 结果: 状态与日志主路径一致，用户可理解当前能力状态。

### W3 回归与观察点闭环
- 目标: 完成本地回归并补齐 API17 观察点。
- 结果: 形成可复盘验证记录，作为后续实机回写输入。

### W4 阶段边界管理（Carry Forward）
- 目标: 将旧阶段外部验收任务从主线剥离，避免干扰当前推进。
- 结果: 保留追踪但不进入本阶段 Ready。

## Recommended Order
1. `W1`：先落地卡片布局与状态行重排。
2. `W2`：接线状态渲染和文案反馈。
3. `W3`：完成回归与观察点补齐。
4. `W4`：并行维护旧主线任务边界。

## Dependency Graph
- `W1 -> W2 -> W3`
- `W4` 与主线并行

## Risks & Assumptions
- 风险: 若界面范围扩张到“完整音效中心”，会拉长周期并偏离当前目标。
- 风险: API17 ROM 差异导致部分 UI 状态在不同设备触发频率不同。
- 假设: EQ MVP 代码与 fail-open 行为已稳定可复用。
- 假设: 当前阶段优先做界面规划文档与任务，不立即改动 UI 代码。

## Stage Milestones
- M1: EQ 界面规划闭环（已完成：`T-S4-AUDIO-061~064`）。
- M2: EQ UI 实现闭环（进行中：`T-S4-AUDIO-065~068`）。
