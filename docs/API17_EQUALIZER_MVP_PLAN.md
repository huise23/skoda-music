# API17 Equalizer MVP Plan (Fail-Open)

Last Updated: 2026-05-19

## 1. Background
- 目标设备是 API17 车机，ROM 差异明显，`audiofx` 在不同设备上可能出现“可创建但不生效”或“直接抛异常”。
- 当前主播放引擎为 `ExoPlaybackEngine`（ExoPlayer 2.17.1），还没有 Equalizer 接线。
- 本文只定义 MVP 方案与任务拆分，不直接上线完整音效系统。

## 2. Current Baseline (Code Observation)
- `PlaybackEngine` 目前只暴露 `prepare/play/pause/seek/release`，未暴露 `audioSessionId`。
- `ExoPlaybackEngine` 在 `prepare()` 内创建 `SimpleExoPlayer`，在 `release()` 释放。
- `MainActivity` 在每次 `playTrackAtCurrentIndex()` 前会先 `releasePlayer()`，意味着会话与引擎可能频繁重建。

## 3. Feasibility Conclusion (API17)
- 结论: `Equalizer` 在 API17 可用，但必须按“可失败能力”处理，不能视为主链路硬依赖。
- 关键约束:
  - `Equalizer(int priority, int audioSession)` 依赖有效 session，且可能抛出 `IllegalArgumentException/IllegalStateException/RuntimeException/UnsupportedOperationException`。
  - 后续 `get/set/usePreset` 也可能抛出运行时异常，且不同 ROM 行为不一致。
- 设计原则:
  - 所有 EQ 操作都要 try/catch，失败只记录日志并降级，不影响播放状态机。
  - EQ 生命周期跟随播放器 session，不能假设跨 session 永久有效。

## 4. Session Lifecycle Wiring
- 接线点定义:
  - `prepare()` 后（`STATE_READY` 前后均可）读取 `audioSessionId`。
  - 当 session 变化时，EQ 重新绑定新 session。
  - `releasePlayer()` / `onDestroy()` 时释放 EQ。
- 建议改造:
  - 在 `PlaybackEngine` 新增 `audioSessionId(): Int`。
  - 在 `ExoPlaybackEngine` 中透传 `SimpleExoPlayer.getAudioSessionId()`。
  - `MainActivity` 在 `playTrackAtCurrentIndex()` 的引擎准备成功回调中触发 `eqManager.bindSession(sessionId)`。

## 5. Fail-Open Contract (Hard Requirement)
- 开关逻辑:
  - 用户开启 EQ -> 尝试创建与应用。
  - 任何阶段失败 -> 自动置为“不可用态（本会话）”，主播放继续。
- 禁止行为:
  - 禁止因为 EQ 初始化失败而中断播放。
  - 禁止因为 EQ 调参失败而触发暂停/切歌。
  - 禁止把 EQ 错误上抛到 UI 主流程导致崩溃。
- 最小日志:
  - `eq_init_ok/eq_init_fail`
  - `eq_apply_ok/eq_apply_fail`
  - `eq_release`

## 6. MVP Scope
- In:
  - `EQ 开关`
  - `预设选择`（优先用系统 presets）
  - `基础持久化`（开关 + preset index）
  - `会话重绑`（换曲/重建播放器后自动重试绑定）
  - `fail-open` 全链路
- Out:
  - 自定义 5 段/10 段曲线 UI
  - `BassBoost/Virtualizer` 实装
  - 高级音效联动和导入导出

## 7. Risk Matrix
- 风险: 部分 ROM 返回有效 session 但 EQ 无感知效果。
  - 策略: 允许“功能可开但无体感”，记录 capability 状态并保持 fail-open。
- 风险: 同一机型偶发 `UnsupportedOperationException`。
  - 策略: 单会话熔断，下一次新 session 再尝试，不做无限重试。
- 风险: 播放器重建频繁导致 EQ 重建抖动。
  - 策略: 仅在 session 变化时重绑，session 不变不重复创建。

## 8. Acceptance Checklist (MVP)
- 播放正常时开启/关闭 EQ 不影响播放连续性。
- 创建失败时不闪退、不停播，且日志可见失败原因。
- 切歌后若 session 变化，EQ 可自动重绑或自动降级。
- 退出页面/销毁播放器后 EQ 资源可释放，无重复持有。

## 9. Taskization Output
- `T-S4-AUDIO-057`：播放引擎暴露 `audioSessionId` 与 session 生命周期接线。
- `T-S4-AUDIO-058`：实现 `EqualizerManager`（创建/应用/释放 + fail-open 熔断）。
- `T-S4-AUDIO-059`：设置页最小交互（开关 + preset）与持久化接线。
- `T-S4-AUDIO-060`：本地回归与 API17 车机验证清单补充（含失败注入）。

