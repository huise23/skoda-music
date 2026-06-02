# NEXT_STEPS

Last Updated: 2026-06-02

## One-Line Summary
- Native Hi-Fi DSP 本地实现已完成并通过构建；下一步是 AC83xx 实机验证是否解决卡顿。

## Current Highest Priority
- `T-S4-AUDIO-095`: AC83xx 实机长播与听感验证。

## What To Validate On Device
1. 安装本轮 APK 后开启 `保真` 模式连续播放，确认是否还存在“听广播一样”的轻微卡顿。
2. 切换 `原声 / 保真 / 清晰 / 动感 / 柔和`，确认不断播、不切歌、不爆音。
3. 执行 seek、切歌、暂停/恢复，确认 native DSP 状态一致。
4. 连续播放至少 30 分钟，确认无卡顿、爆音、破音、闪退。
5. 回传日志样本：`hifi-dsp native status=<...> mode=<...> tier=<...> costUs=<...> flags=<...>`。

## Expected Log Interpretation
- `tier=quality`: 正常高音效档。
- `tier=balanced`: CPU 超预算后自动降一档，仍应保持可听差异。
- `tier=safe`: CPU 压力较高，进入最低复杂度音效。
- `status=bypass` 或 `flags=bypass`: native 保护性旁路，播放应不中断。
- `reason=non-direct-buffer`: ExoPlayer 输出 buffer 不满足 native 直处理，需要后续改复制兜底或调整接入策略。

## Completed Locally
- `T-S4-AUDIO-088~094` 已完成。
- `gradle :app:compileDebugKotlin --no-daemon` 通过。
- `gradle :app:assembleDebug --no-daemon` 通过。
- `./scripts/check_api17_guardrails.sh` 通过。
- `git diff --check` 通过。

## Recommended Next Action
- 推送/打包后执行 AC83xx 实机验证。
- 若实机仍卡顿：带日志回到 `$ai-requirement` 或 `$ai-planning`，规划 fixed-point/NEON/更低复杂度参数二轮优化。
