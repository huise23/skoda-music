# TASK_QUEUE

Last Updated: 2026-06-02

## Ready
- None

## Pending / Planned
- None

## Blocked

### T-S4-AUDIO-095
- Priority: P1
- Module: `M-S4-AUDIO-021`
- Execution Mode: Single
- Title: AC83xx 实机长播与听感验证
- Blocked By: external AC83xx device window
- Current Requirement:
  - 安装本轮 native DSP APK。
  - 开启 `保真` 连续播放，确认是否消除“听广播一样”的轻微卡顿。
  - 回传 `hifi-dsp native status=... mode=... tier=... costUs=... flags=...` 日志样本。

## In Progress
- None

## Done
- `T-S4-AUDIO-080`: ExoPlayer DSP 接入落点确认
- `T-S4-AUDIO-081`: 音效模式状态模型与配置迁移
- `T-S4-AUDIO-082`: Fail-open DSP AudioProcessor 骨架实现
- `T-S4-AUDIO-083`: 轻量 DSP 模式引擎实现（Kotlin 版）
- `T-S4-AUDIO-084`: 音效子页与设置页体验替换
- `T-S4-AUDIO-085`: 模式切换联动与旧 EQ 主线下线
- `T-S4-AUDIO-086`: 本地构建、guardrails、回归文档更新
- `T-S4-AUDIO-088`: Native DSP JNI API 与 fail-open 契约
- `T-S4-AUDIO-089`: 性能档位、预算阈值与日志字段契约
- `T-S4-AUDIO-090`: Native bridge scaffold 与 no-op/bypass buffer 处理
- `T-S4-AUDIO-091`: C++ DSP 模式引擎与系数预计算
- `T-S4-AUDIO-092`: 自动降档、耗时统计与节流日志
- `T-S4-AUDIO-093`: `HiFiAudioProcessor` 热路径迁移到 native
- `T-S4-AUDIO-094`: 本地验证、guardrails 与 API17 清单更新

## Superseded
- `T-S4-AUDIO-087`: Kotlin DSP API17 实机听感验证。原因：AC83xx 已反馈 Kotlin 热路径卡顿，已由 native 优化链取代；后续实机验证改走 `T-S4-AUDIO-095`。

## Recommended Execution Mode
- 当前本地可执行任务已完成。
- 下一步不是继续本地扩功能，而是执行 `T-S4-AUDIO-095` AC83xx 实机验证。
- 若实机仍卡顿或 tier 长期降到 `safe/bypass`，回到 `$ai-requirement` 或 `$ai-planning` 规划 fixed-point/NEON/参数降复杂度二轮优化。
