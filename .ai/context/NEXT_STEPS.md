# NEXT_STEPS

Last Updated: 2026-05-26

## One-Line Summary
- EQ 全屏子页（含视觉二次收口）已本地通过编译，下一步切到 API17 实机观察点与证据回填。

## Current Priority Modules
- `M-S4-CARRY-010`: 旧阶段外部任务状态迁移与边界标注（当前 Ready）。
- 外部验证链：`T-S4-REG-022` / `T-S4-VAL-033`（实机窗口到位后执行）。

## What To Start First (按顺序)
1. `T-S4-CARRY-056`
- 同步旧阶段外部任务状态，保持队列边界清晰。

2. `T-S4-REG-022`
- 在 API17 设备执行回归，重点覆盖 EQ 全屏子页交互与 fail-open 表现。

3. `T-S4-VAL-033`
- 回填实机证据与结论，更新阶段状态。

## Main Blockers
- 当前缺少稳定 API17 实机窗口，无法在本地完成最终证据闭环。

## Explicitly Deferred
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-UI-023`
