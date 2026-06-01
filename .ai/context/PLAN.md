# PLAN

Last Updated: 2026-06-01

## Current Stage
- Stage Name: S4 音效子阶段 - 应用内保真 DSP 引擎
- Scope Source: `.ai/context/SCOPE.md`（2026-06-01）

## Stage Goal
- 将音效主线从系统 EQ / Android `audiofx.Equalizer` 切换为 App 播放链路内的保真 DSP。
- 基于 ExoPlayer 2.17.1 自定义 `AudioProcessor`，在 PCM 输出前做轻量处理。
- 第一版目标是自然、还原、低失真、可旁路，不追求夸张 EQ。
- 保持 API17 兼容与播放稳定：DSP 失败必须 fail-open 自动旁路原声播放。

## Scope Validation

### In Scope
- 音质模式：`原声 / 保真 / 清晰 / 动感 / 柔和`。
- 默认推荐模式：`保真`。
- 设置页保留音效开关和进入子页入口。
- 子页改为“音质模式优先”，不再以 10 段滑杆为第一入口。
- ExoPlayer 音频链路接入自定义 `AudioProcessor`。
- 轻量 DSP：前级降增益、少量 biquad 滤波器、防削波/限幅保护。
- DSP fail-open：初始化、格式、运行异常时旁路原声。
- 日志与 API17 实机回归清单更新。

### Out of Scope
- 系统 EQ 继承继续推进。
- Android `audiofx.Equalizer` 固定 10 段直写作为主线。
- 系统全局音效或影响其它 App 声音。
- 重写播放器为自研解码 + `AudioTrack` 输出。
- 第一版高级 10 段 EQ、强度滑杆、多套用户自定义曲线。
- 混响、环绕、空间音频、复杂动态压缩器。
- 改变 Emby-only、IPv4-only、download-only 播放策略。

## Workstreams

### W1 Audio Pipeline 接入
- 目标: 在 ExoPlayer 2.17.1 中接入自定义 `AudioProcessor`，并保证旁路路径稳定。
- 输出: 可配置的 DSP 处理链、旁路状态、日志。

### W2 DSP 模式引擎
- 目标: 实现轻量保真 DSP 参数模型和模式曲线。
- 输出: `原声/保真/清晰/动感/柔和` 对应的稳定处理参数。

### W3 UI 与状态迁移
- 目标: 将现有 EQ UI/文案/持久化迁移为音质模式体验。
- 输出: 设置页入口、音效子页、模式持久化、旧 EQ 配置安全降级。

### W4 验证与回归
- 目标: 建立本地构建、API17 guardrails、实机听感与稳定性验证闭环。
- 输出: 回归清单、日志观察项、实机报告模板。

## Dependency Graph
- `W1 -> W2 -> W3 -> W4`
- `W3` 可在 `W1` 骨架稳定后并行推进。
- `W4` 依赖前三者，但回归清单可先行更新。

## Recommended Order
1. `T-S4-AUDIO-080`: ExoPlayer DSP 接入方案落点确认。
2. `T-S4-AUDIO-081`: 音效状态模型与持久化迁移设计落地。
3. `T-S4-AUDIO-082`: Fail-open `AudioProcessor` 骨架接入。
4. `T-S4-AUDIO-083`: 轻量 DSP 模式引擎实现。
5. `T-S4-AUDIO-084`: 音效子页与设置页体验替换。
6. `T-S4-AUDIO-085`: 模式切换联动与旧 EQ 主线下线。
7. `T-S4-AUDIO-086`: 本地构建、guardrails、回归文档更新。
8. `T-S4-AUDIO-087`: API17 实机验证与调音反馈闭环。

## Milestones
- M1: ExoPlayer 自定义 `AudioProcessor` 可构建、可旁路、不会影响播放。`Done locally`
- M2: 五种音质模式在本地可切换，处理异常自动旁路。`Done locally`
- M3: UI 从 EQ 专家页切换为音质模式页，配置可安全持久化。`Done locally`
- M4: API17 实机验证确认无闪退、无停播、无明显爆音破音，并能听出保真/清晰/动感/柔和差异。`Blocked by device`

## Validation Strategy
- 本地:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - 必要时 `gradle :app:assembleDebug`
- 代码审查重点:
  - 不引入 `minSdk > 17` 依赖。
  - `AudioProcessor.queueInput()` 不做高频分配。
  - 格式不支持和异常路径必须旁路。
  - 模式切换不应要求重建播放主链路，优先通过共享配置实时生效。
- 实机:
  - 连续播放 30 分钟，无卡顿、爆音、破音、闪退。
  - 对比 `原声/保真/清晰/动感/柔和` 的可感知差异。
  - 重点观察低端 CPU 压力与 seek/切歌/暂停恢复。

## Execution Snapshot (2026-06-01)
- `T-S4-AUDIO-080~086` 已完成。
- 本地验证通过：
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`
- 剩余 `T-S4-AUDIO-087` 等待 API17 实机窗口。

## Risks & Assumptions
- 风险: ExoPlayer 2.17.1 的 `AudioProcessor` 接入需要自定义 `DefaultRenderersFactory` / `DefaultAudioSink`，改错会影响播放。
- 风险: API17 设备 CPU 弱，DSP 必须极轻量并避免每帧对象分配。
- 风险: 车机喇叭/功放染色明显，固定模式参数需要实机调音，不能仅靠本地判断。
- 风险: 当前 `MainActivity` EQ 代码较集中，UI 迁移要避免引入状态错乱。
- 假设: 当前 ExoPlayer 2.17.1 在项目内已稳定播放，适合作为 DSP 接入基座。
- 假设: 第一版不做强度滑杆，可降低状态和调音复杂度。
