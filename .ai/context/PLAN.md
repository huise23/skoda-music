# PLAN

Last Updated: 2026-06-02

## Current Stage
- Stage Name: S4 子阶段 - AC83xx Native Hi-Fi DSP 性能优化
- Scope Source: `.ai/context/SCOPE.md`（2026-06-02）

## Stage Goal
- 将当前 Kotlin sample-by-sample DSP 热路径迁移到 C++ native buffer 级处理。
- 保留 `原声 / 保真 / 清晰 / 动感 / 柔和` 五种音质模式与高音效目标。
- 在 AC83xx / Android 4.2.2 / API17 上优先保证不卡顿、不断播、不闪退。
- 通过 `quality / balanced / safe` 自动性能档位实现“高音效优先 + 自动保护”。

## Scope Validation

### In Scope
- 复用现有 `app/src/main/cpp/CMakeLists.txt` 与 `native-playback` so。
- 新增 native DSP JNI 接口，按 PCM16 `ByteBuffer` 整块处理，不做 per-sample JNI。
- Kotlin `HiFiAudioProcessor` 保留为 ExoPlayer `AudioProcessor` 接入层、状态同步层、fail-open 包装层。
- C++ 管理模式参数、滤波器状态、系数预计算、buffer 处理、耗时统计与降档状态。
- 支持 PCM 16-bit mono/stereo；其它格式自动旁路。
- native 初始化失败、配置失败、处理失败、直接 buffer 不可用时必须旁路原声。
- 日志补齐 native enabled、mode、tier、cost、degrade reason、bypass reason。
- 更新 API17 / AC83xx 验证清单。

### Out of Scope
- 通过默认关闭 DSP 或默认原声规避性能问题。
- 恢复系统 EQ 继承或 Android `audiofx.Equalizer` 作为主线。
- 高级 10 段 EQ 页面、强度滑杆、多套用户曲线。
- 空间音频、环绕、混响、复杂动态压缩器。
- 重写播放器为自研解码 + `AudioTrack`。
- 引入要求 `minSdk > 17` 的音频库或系统能力。

## Workstreams

### W1 Native Bridge & Build Integration
- 目标: 固定 JNI API、native 生命周期、CMake 接线和 fail-open 契约。
- 输出: 可构建的 native DSP bridge，先支持 no-op/bypass buffer 处理。

### W2 Native DSP Engine & Performance Tiers
- 目标: 将现有五种模式迁入 C++，并实现 quality/balanced/safe 三档。
- 输出: native 模式参数、预计算系数、低开销 limiter、耗时统计和自动降档。

### W3 Kotlin AudioProcessor Migration
- 目标: 将 `HiFiAudioProcessor.queueInput()` 热路径改为 native 整块调用。
- 输出: Kotlin 不再执行逐 sample DSP；异常与不支持路径继续 fail-open。

### W4 Validation & AC83xx Evidence
- 目标: 建立本地构建、API17 guardrails、实机性能日志和长播验证闭环。
- 输出: 更新验证清单、日志观察项、实机报告模板与调优输入。

## Dependency Graph
- `W1 -> W2 -> W3 -> W4`
- `W2` 可在 `W1` no-op bridge 可构建后推进。
- `W4` 文档可提前更新，但实机结论依赖 `W2/W3` 完成。

## Recommended Order
1. `T-S4-AUDIO-088`: Native DSP JNI API 与 fail-open 契约。
2. `T-S4-AUDIO-089`: 三档性能策略、预算阈值与日志字段契约。
3. `T-S4-AUDIO-090`: native bridge scaffold + no-op/bypass buffer 处理。
4. `T-S4-AUDIO-091`: C++ DSP 模式引擎与系数预计算。
5. `T-S4-AUDIO-092`: 自动降档、耗时统计、throttled logs。
6. `T-S4-AUDIO-093`: Kotlin `HiFiAudioProcessor` 热路径迁移到 native。
7. `T-S4-AUDIO-094`: 本地构建、guardrails、API17 清单更新。
8. `T-S4-AUDIO-095`: AC83xx 实机长播与听感验证。

## Milestones
- M1: native bridge 可构建，可处理整块 PCM buffer，失败时旁路原声。`Done locally`
- M2: 五种音质模式在 native 中实现，Kotlin 热路径不再逐 sample 处理。`Done locally`
- M3: 自动档位和耗时日志可观察，超预算先降档再旁路。`Done locally`
- M4: AC83xx 实机开启 `保真` 连续播放不再出现广播感卡顿。`Blocked by device`

## Validation Strategy
- 本地:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`
- 代码审查重点:
  - JNI 不跨 sample 调用。
  - `queueInput()` 不做高频分配，不吞 buffer，不破坏 position/limit。
  - `ByteBuffer` direct 地址不可用时必须旁路。
  - native handle 生命周期与 `onFlush/onReset` 一致。
  - 所有 native/JNI 异常路径 fail-open。
- 实机:
  - AC83xx 连续播放 30 分钟，无卡顿、爆音、破音、闪退。
  - 切歌、seek、暂停/恢复后 mode/tier/state 正常。
  - 对比 `原声/保真/清晰/动感/柔和`，保留可感知差异。
  - 日志能解释正常处理、降档、旁路三类状态。

## Risks & Assumptions
- 风险: AC83xx 浮点性能不足，optimized float 仍可能超预算，需要 fixed-point 或进一步降滤波器数量。
- 风险: ExoPlayer 输出 buffer 是否始终 direct 需实测；非 direct 必须有安全旁路或低频复制兜底。
- 风险: native 崩溃不可被 Kotlin catch 捕获，因此 C++ 代码必须避免越界、空指针、未校验 handle。
- 风险: 自动降档过激会削弱听感，阈值需要实机调优。
- 假设: 现有 `native-playback` so 可继续承载 DSP JNI，不需要新 so。
- 假设: 当前 PCM 格式以 16-bit mono/stereo 为主，足够覆盖目标播放链路。

## Execution Snapshot (2026-06-02)
- Full Plan Mode 已完成本地可执行链 `T-S4-AUDIO-088~094`。
- 代码结果:
  - 新增 `NativeHiFiDspBridge.kt`，封装 native create/release/configure/setMode/flush/processPcm16。
  - 新增 `native_hifi_dsp.cpp`，实现五种音质模式、预计算 biquad、`quality/balanced/safe` 三档和耗时统计。
  - `HiFiAudioProcessor` 热路径已从 Kotlin sample loop 切换为 native direct `ByteBuffer` 整块处理。
  - `CMakeLists.txt` 已将 native DSP 编入 `native-playback`。
  - API17 回归清单已补充 native DSP 性能日志与降档/旁路证据字段。
- 本地验证通过:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`
- 剩余:
  - `T-S4-AUDIO-095` AC83xx 实机长播与听感验证。
