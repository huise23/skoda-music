# MODULES

Last Updated: 2026-06-02

## Active Stage
- S4 子阶段 - AC83xx Native Hi-Fi DSP 性能优化

## M-S4-AUDIO-018
- Module ID: `M-S4-AUDIO-018`
- Name: Native DSP Bridge & Build Integration
- Goal: 在现有 `native-playback` so 中接入 Hi-Fi DSP JNI bridge，并建立安全生命周期与 fail-open 契约。
- Why it matters: 当前卡顿来自 Kotlin 热路径，native 化的第一风险是 JNI/生命周期/构建接线；这块必须先稳定。
- In Scope:
  - 设计 `nativeCreate/nativeRelease/nativeConfigure/nativeSetMode/nativeProcessPcm16` 等 buffer 级 API。
  - 更新 CMake 引入 native DSP 源文件。
  - 支持 direct `ByteBuffer` 整块处理。
  - handle 校验、格式校验、错误码返回、异常保护。
  - no-op/bypass bridge 先行，证明播放链路稳定。
- Out of Scope:
  - 完整 DSP 调音。
  - UI 改动。
  - 新增独立 native so。
- Dependencies: 现有 `app/src/main/cpp`、`NativePlaybackBridge`、ExoPlayer DSP 接入基线。
- Milestone / Done Criteria:
  - native bridge 可构建。
  - Kotlin 能创建、配置、释放 native DSP handle。
  - `queueInput()` 可调用 native 处理整块 buffer。
  - native 不可用时原声旁路且不闪退。
- Related Tasks: `T-S4-AUDIO-088`, `T-S4-AUDIO-090`
- Priority: P0
- Status: Done
- Result:
  - 新增 `NativeHiFiDspBridge` Kotlin wrapper。
  - 新增 `native_hifi_dsp.cpp` 并接入 `native-playback` CMake。
  - JNI API 覆盖 create/release/configure/setMode/flush/processPcm16。
  - native 失败、不可用、non-direct buffer 均由 Kotlin fail-open 旁路。
- Risks:
  - 仍需实机确认 ExoPlayer 输出 direct buffer 与目标车机 native so 加载行为。
- Suitable For Module Execution?: No (completed locally)

## M-S4-AUDIO-019
- Module ID: `M-S4-AUDIO-019`
- Name: Native DSP Engine & Performance Tiers
- Goal: 将五种音质模式迁入 C++，并实现 AC83xx 友好的自动性能档位。
- Why it matters: 仅迁 native 不够，必须控制滤波器数量、数学开销和超预算行为，才能真正消除卡顿。
- In Scope:
  - C++ 实现 `原声 / 保真 / 清晰 / 动感 / 柔和` 模式参数。
  - 模式切换时预计算 biquad 系数，不在热路径计算三角函数。
  - `quality / balanced / safe` 三档滤波器组合。
  - 低成本 limiter / clipping guard。
  - buffer 处理耗时统计、超预算计数、自动降档、最终旁路。
  - 日志节流，避免性能日志本身造成卡顿。
- Out of Scope:
  - 10 段 EQ 直写。
  - 混响/环绕/空间化。
  - 离线音质分析或自动调音模型。
- Dependencies: `M-S4-AUDIO-018`
- Milestone / Done Criteria:
  - native 中完成五种模式处理。
  - 各模式在三档下均有明确降级曲线。
  - 耗时超预算可自动从 quality 降到 balanced/safe。
  - 多次异常或持续超预算时 fail-open 旁路。
- Related Tasks: `T-S4-AUDIO-089`, `T-S4-AUDIO-091`, `T-S4-AUDIO-092`
- Priority: P0
- Status: Done
- Result:
  - 五种音质模式已迁入 C++。
  - 模式切换/配置阶段预计算 biquad 系数，热路径不再计算三角函数。
  - 实现 `quality / balanced / safe` 三档曲线和超预算自动降档。
  - native 返回 packed status，包含 status/tier/flags/costUs，Kotlin 节流记录日志。
- Risks:
  - AC83xx 对浮点/除法开销敏感，实机若仍卡顿，需要 fixed-point/NEON/参数二轮优化。
  - safe 档听感必须以目标车机确认。
- Suitable For Module Execution?: No (completed locally)

## M-S4-AUDIO-020
- Module ID: `M-S4-AUDIO-020`
- Name: Kotlin AudioProcessor Native Migration
- Goal: 保留 Kotlin `HiFiAudioProcessor` 壳，但将实际 PCM 处理迁移到 native。
- Why it matters: 用户感知问题发生在播放热路径；如果 Kotlin 仍逐 sample 处理，性能问题不会根治。
- In Scope:
  - `HiFiAudioProcessor.queueInput()` 改为整块 native 调用。
  - Kotlin 仅负责配置快照、buffer 准备、错误兜底、日志转发。
  - 移除 active path 中 Kotlin `Biquad/FilterSpec/processPcm16` 逐 sample 逻辑。
  - 保持 `onConfigure/onFlush/onReset` 状态一致。
  - 保持关闭/原声/格式不支持时旁路。
- Out of Scope:
  - 大规模重构 `MainActivity` 音效 UI。
  - 改 ExoPlayer 版本。
- Dependencies: `M-S4-AUDIO-018`, `M-S4-AUDIO-019`
- Milestone / Done Criteria:
  - 开启音效时 native 是唯一 DSP 热路径。
  - native 失败时当前 buffer 原样输出。
  - 模式切换实时生效，不要求重建播放器。
  - 构建与 API17 guardrails 通过。
- Related Tasks: `T-S4-AUDIO-093`
- Priority: P0
- Status: Done
- Result:
  - `HiFiAudioProcessor.queueInput()` 已改为 direct `ByteBuffer.slice()` + native 整块调用。
  - Kotlin active path 已移除 `Biquad/FilterSpec/processPcm16` sample loop。
  - Kotlin 仅保留 ExoPlayer 接入、状态同步、buffer 转交、日志和 fail-open。
  - `onFlush/onReset` 已同步 native flush/release。
- Risks:
  - ByteBuffer position/limit 仍需实机播放确认无杂音、吞音或播放中断。
- Suitable For Module Execution?: No (completed locally)

## M-S4-AUDIO-021
- Module ID: `M-S4-AUDIO-021`
- Name: AC83xx Validation & Regression Evidence
- Goal: 建立 native DSP 的本地与实机验证闭环，确保性能优化可被复盘。
- Why it matters: 音效优化最终必须以目标车机不卡顿和听感可接受为准，不能只看本地编译。
- In Scope:
  - 更新 API17 回归清单，新增 native DSP 性能/降档/旁路观察项。
  - 本地执行构建、guardrails、diff 检查。
  - 明确实机日志字段与证据模板。
  - 记录 AC83xx 实测 mode/tier/cost/degrade/bypass 结果。
- Out of Scope:
  - 代替人工听感判断。
  - 自动化音频质量评分。
- Dependencies: `M-S4-AUDIO-018`, `M-S4-AUDIO-019`, `M-S4-AUDIO-020`
- Milestone / Done Criteria:
  - 本地构建验证通过。
  - 实机清单能直接执行。
  - 至少一次 AC83xx 30 分钟长播结果可回填。
- Related Tasks: `T-S4-AUDIO-094`, `T-S4-AUDIO-095`
- Priority: P1
- Status: Partial / Device validation blocked externally
- Result:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已补 native DSP mode/tier/cost/degrade/bypass 观察项。
  - 本地验证已通过：`git diff --check`、`./scripts/check_api17_guardrails.sh`、`gradle :app:compileDebugKotlin --no-daemon`、`gradle :app:assembleDebug --no-daemon`。
  - 剩余 `T-S4-AUDIO-095` 依赖 AC83xx 实机。
- Risks:
  - 设备窗口不连续，日志必须足够自解释。
- Suitable For Module Execution?: Yes for local validation, No for real-device execution

## Historical Completed Modules
- `M-S4-AUDIO-014~017`: 应用内保真 DSP Kotlin 版已完成本地闭环，但 AC83xx 实机反馈性能不足；本阶段以 native 优化取代后续 Kotlin 调参。
