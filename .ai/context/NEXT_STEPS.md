# NEXT_STEPS

Last Updated: 2026-05-22

## One-Line Summary
- EQ MVP 实现链 `T-S4-AUDIO-057~060` 已完成本地收口并编译通过，下一步回到边界维护与外部验证任务。

## Current Priority Modules
- `M-S4-AUDIO-009`: 已完成本地 MVP，等待 API17 实机窗口按新条目留证。
- `M-S4-CARRY-010`: 旧阶段外部任务状态迁移与边界标注。

## What To Start First (按顺序)
1. `T-S4-CARRY-056`
- 清理并固化旧阶段外部任务状态，保持 Ready 队列干净。

2. `T-S4-REG-022` / `T-S4-VAL-033`（外部窗口到位时）
- 按 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 执行实机回归并回写证据。

## Main Blockers
- API17 车机窗口不连续，EQ fail-open 的真实 ROM 差异暂时只能先靠清单化验证。

## Explicitly Deferred
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-UI-023`
