# NEXT_STEPS

Last Updated: 2026-05-27

## One-Line Summary
- 系统 EQ 继承接线已本地收口，下一步切到 API17 实机留证与结果回填。

## Current Priority Modules
- 外部验证链：`T-S4-REG-022` / `T-S4-VAL-033`（当前最高优先）。
- `M-S4-CARRY-010`: 旧阶段外部任务状态迁移与边界标注（并行低优先）。

## What To Start First (按顺序)
1. `T-S4-REG-022`
- 在 API17 设备执行系统 EQ 接线专项回归，覆盖 I7~I9 观察项。

2. `T-S4-VAL-033`
- 回填实机证据与结论，更新阶段状态。

3. `T-S4-CARRY-056`
- 同步旧阶段外部任务状态，保持队列边界清晰。

## Main Blockers
- API17 ROM 对系统音效支持差异大，最终“是否可听见系统接管效果”只能通过实机窗口确认。

## Explicitly Deferred
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-UI-023`
