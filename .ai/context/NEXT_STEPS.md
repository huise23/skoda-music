# NEXT_STEPS

Last Updated: 2026-06-01

## One-Line Summary
- 应用内保真 DSP 音效本地实现已完成，剩余工作是 API17 车机实机听感与稳定性验证。

## Current Highest Priority
- `T-S4-AUDIO-087`: API17 实机听感与稳定性验证。

## Completed Locally
1. ExoPlayer 2.17.1 已通过自定义 `RenderersFactory` 注入 `HiFiAudioProcessor`。
2. 新增 DSP controller 与五种模式：`原声 / 保真 / 清晰 / 动感 / 柔和`。
3. 设置页和音效子页已切换为“保真音效 / 音质模式”体验。
4. 新配置键：`sound_effect_enabled / sound_effect_mode`；旧 `eq_enabled` 只做首次迁移参考。
5. 默认路径不再触发 Android `audiofx.Equalizer` 写入。
6. API17 回归清单已切换为 Hi-Fi DSP Sound Mode 验证。

## Validation Already Done
- `git diff --check` 通过。
- `./scripts/check_api17_guardrails.sh` 通过。
- `gradle :app:compileDebugKotlin --no-daemon` 通过。
- `gradle :app:assembleDebug --no-daemon` 通过。

## Device Validation Focus
1. 设置页“保真音效”开关可用，进入音效子页正常。
2. 子页显示 `原声 / 保真 / 清晰 / 动感 / 柔和`，不再显示 10 段 EQ 主界面。
3. `原声` 与关闭音效接近。
4. `保真` 更清楚、更不糊，但不偏重低音或突出人声。
5. `清晰/动感/柔和` 有方向差异但不过度。
6. 连续播放 30 分钟无卡顿、爆音、破音、闪退。
7. 切歌、seek、暂停恢复后模式仍一致。
8. 日志可见 `hifi-dsp config/format/active/bypass`。

## Blocked / Waiting
- `T-S4-AUDIO-087` 等待 API17 实机窗口。

## Recommended Next Action
- 推送当前版本供实机验证；实机反馈后进入调音或修复闭环。
