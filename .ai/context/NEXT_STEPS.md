# NEXT_STEPS

Last Updated: 2026-05-25

## One-Line Summary
- EQ 界面规划（`T-S4-AUDIO-061~064`）已完成，下一步切到 `M-S4-AUDIO-012` 的 UI 实现链。

## Current Priority Modules
- `M-S4-AUDIO-012`: EQ 界面实现与验证（当前最高优先）。
- `M-S4-CARRY-010`: 旧阶段外部任务状态迁移与边界标注（并行低优先）。

## What To Start First (按顺序)
1. `T-S4-AUDIO-065`
- 先做 EQ 卡片布局重排与视觉分组，把规划结构落地到设置页。

2. `T-S4-AUDIO-066`
- 接线状态模型与 UI 渲染，确保 `off/pending/active/no-presets/fused` 可区分。

3. `T-S4-AUDIO-067`
- 收口文案与交互反馈，统一 fail-open 表达。

4. `T-S4-AUDIO-068`
- 完成本地回归与 API17 实机观察点补齐。

## Main Blockers
- 需要在实现阶段决定是否增加最小状态透出接口，避免仅靠日志推断 `fused/no-presets`。

## Explicitly Deferred
- `T-S4-REG-022` / `T-S4-VAL-033`（外部窗口任务，非本轮规划主线）
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-UI-023`
