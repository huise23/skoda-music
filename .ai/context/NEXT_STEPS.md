# NEXT_STEPS

Last Updated: 2026-05-19

## One-Line Summary
- 当前最短路径是先完成歌词三段容器改造闭环，再落地均衡器 MVP 规划输入。

## Current Priority Modules
- `M-S4-LRC-008`（最高优先）: 歌词中线容器改造。
- `M-S4-AUDIO-009`（并行）: Equalizer API17 规划与 MVP 拆分。
- `M-S4-CARRY-010`（低优先）: 旧阶段任务边界维护。

## Recommended Execution Mode
- 当前推荐: 模块推进（`ai-module-execution`）
- 原因: 歌词改造涉及布局 + 逻辑 + 本地回归，连续推进效率高于碎片化微任务切换。

## What To Start First (按顺序)
1. `T-S4-LRC-050` + `T-S4-LRC-051`
- 先定义规则再改布局，避免先改 UI 后返工。

2. `T-S4-LRC-052`
- 在三段容器已就位后改渲染逻辑，确保当前行稳定居中。

3. `T-S4-LRC-053`
- 立刻做本地回归并回写结果，锁定边界行为。

4. `T-S4-AUDIO-054`
- 并行完成 EQ 可行性与 session 生命周期分析。

5. `T-S4-AUDIO-055`
- 将 EQ 规划结果转成后续实现任务队列。

## Main Blockers
- “永远绝对居中”在首句/末句/超长换行场景可能不可严格满足，需要口径确认为“视觉居中优先”。
- API17 车机 ROM 的 `audiofx` 支持存在不确定性，需按 fail-open 设计。

## Need Confirmation
- EQ 首版是否只做预设，还是同步支持自定义 band 增益。
- EQ 设置生效范围：全局持久化还是会话级。
- 是否把 `BassBoost/Virtualizer` 纳入首版，还是仅 Equalizer MVP。

## Explicitly Deferred
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-REG-022` / `T-S4-VAL-033`
- `T-S4-UI-023`
