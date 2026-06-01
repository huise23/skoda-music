# TASK_QUEUE

Last Updated: 2026-06-01

## Ready
- None

## In Progress
- None

## Blocked

### T-S4-AUDIO-087
- Title: API17 实机听感与稳定性验证
- Module: `M-S4-AUDIO-017`
- Blocked By: target API17 device window
- Recommended Mode: Single
- Blocking Reason: 本地实现、构建、guardrails、回归文档已完成；听感、长播、爆音/破音/卡顿只能在目标车机验证。

## Deferred

### T-S4-AUDIO-079
- Title: 固定 10 段 Android audiofx 直写长期决策
- Reason: 新 scope 已切换为应用内保真 DSP，引擎不再以 Android `audiofx.Equalizer` 为主线。

## Done

### T-S4-AUDIO-080
- Title: ExoPlayer DSP 接入落点确认
- Module: `M-S4-AUDIO-014`
- Result: 已确认通过 `SimpleExoPlayer.Builder(context, RenderersFactory)` + `DefaultAudioSink.Builder.setAudioProcessors(...)` 接入。

### T-S4-AUDIO-081
- Title: 音效模式状态模型与配置迁移
- Module: `M-S4-AUDIO-016`
- Result: 新增 `sound_effect_enabled/sound_effect_mode`，旧 `eq_enabled` 仅用于首次迁移兜底。

### T-S4-AUDIO-082
- Title: Fail-open DSP AudioProcessor 骨架实现
- Module: `M-S4-AUDIO-014`
- Result: 新增 `HiFiAudioProcessor/HiFiDspController/HiFiRenderersFactory`，支持关闭、原声、格式不支持、异常旁路。

### T-S4-AUDIO-083
- Title: 轻量保真 DSP 模式引擎实现
- Module: `M-S4-AUDIO-015`
- Result: 已实现 `原声 / 保真 / 清晰 / 动感 / 柔和` 五种模式、前级降增益、轻量 biquad、软限幅。

### T-S4-AUDIO-084
- Title: 音效子页与设置页 UI 替换
- Module: `M-S4-AUDIO-016`
- Result: 设置页与子页主线改为“保真音效 / 音质模式”，不再以 10 段 EQ 为第一入口。

### T-S4-AUDIO-085
- Title: 模式切换联动与旧 audiofx 主线下线
- Module: `M-S4-AUDIO-016`
- Result: 模式选择实时更新 DSP controller；默认路径不再触发 Android `audiofx` 写入。

### T-S4-AUDIO-086
- Title: 本地回归与 API17 清单更新
- Module: `M-S4-AUDIO-017`
- Result: 已更新 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`，本地 `compileDebugKotlin/assembleDebug/guardrails/diff-check` 通过。
