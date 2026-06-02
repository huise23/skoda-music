# SCOPE

Last Updated: 2026-06-02

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（API17 基线，Kotlin 壳 + C++ 核心）

## Scope Summary
- 当前阶段: S4 子阶段（AC83xx Native Hi-Fi DSP 性能优化）
- 阶段背景:
  - 应用内 Kotlin `HiFiAudioProcessor` 已实现五种音质模式，但 AC83xx 车机实测出现类似听广播的轻微卡顿。
  - 用户明确不接受简单降级为低配/关闭音效，要求保留高音效，并通过工程手段优化性能。
  - 用户确认技术方向：直接 C++ Native DSP。
- 阶段目标:
  - 将 PCM 热路径从 Kotlin sample-by-sample 处理迁移到 C++ native DSP。
  - 通过整块处理、预计算系数、减少 Kotlin/JNI 热路径开销、必要时 fixed-point/查表/NEON 条件优化，尽量在 AC83xx 上保留高音效。
  - 播放稳定优先于效果复杂度：如果 native DSP 超预算，应自动降档或旁路，不能继续卡顿。

## In Scope
- Native DSP 接入:
  - 复用现有 `app/src/main/cpp` / `native-playback` CMake 构建体系，新增 native DSP 源文件与 JNI 接口。
  - Kotlin `HiFiAudioProcessor` 保留为 ExoPlayer `AudioProcessor` 入口，但热路径调用 C++ 按 PCM buffer 整块处理。
  - JNI 设计必须避免逐 sample 跨边界；每次 `queueInput` 最多按 buffer 调用 native。
  - 支持 PCM 16-bit mono/stereo；不支持格式自动旁路。
- DSP 性能优化:
  - 模式参数和滤波器系数预计算，模式切换时更新 native state，不在每帧重算三角函数。
  - C++ 内部优先采用低开销实现：扁平数组、少分支、少函数调用、无 per-sample 分配。
  - 可使用 fixed-point 或 optimized float；执行阶段以 AC83xx 可跑稳为准。
  - limiter/防削波改为低成本实现，避免复杂除法和高开销数学函数进入热路径。
  - 增加处理耗时统计与自动性能档位：`quality / balanced / safe`。
  - 超预算时优先自动降档，不直接关闭音效；多次超预算仍必须 fail-open 旁路以保护播放。
- 音效目标保持:
  - 保留 `原声 / 保真 / 清晰 / 动感 / 柔和` 五种模式。
  - 目标仍是自然、还原、层次，不是重低音或突出人声。
  - 不因为 AC83xx 弱就默认取消音效；默认策略在 planning 中定为“高音效优先 + 自动保护”。
- 观测与验证:
  - 日志记录 native DSP 是否启用、模式、档位、耗时、降档原因、旁路原因。
  - API17 回归清单补充 AC83xx 性能验证：卡顿、长播、模式切换、降档日志。

## Out of Scope
- 本阶段不做:
  - 简单把默认模式改为关闭或原声来规避问题。
  - 系统 EQ 继承或 Android `audiofx.Equalizer` 主线恢复。
  - 高级 10 段 EQ 页面。
  - 空间音频、环绕、混响、复杂动态压缩器。
  - 重写播放器为自研解码 + `AudioTrack` 输出。
  - 影响系统其他 App 或系统全局声音。
  - 引入要求 `minSdk > 17` 的第三方音频库。

## Success Criteria
- 性能验收:
  - AC83xx 上开启 `保真` 模式不再出现“听广播一样”的轻微卡顿。
  - `清晰 / 动感 / 柔和` 至少可连续播放，若 CPU 不足应自动降档而不是卡顿。
  - 连续播放 30 分钟无爆音、破音、卡顿、闪退。
  - 切歌、seek、暂停/恢复后 native DSP 状态一致。
- 音质验收:
  - `保真` 仍应比 `原声` 更清楚、更不糊。
  - `清晰 / 动感 / 柔和` 保留可感知方向差异。
  - 自动降档不得直接变成无声、爆音或明显失真。
- 稳定性验收:
  - native 初始化失败、JNI 异常、格式不支持、处理失败时必须旁路原声。
  - native DSP 失败不得导致 app 闪退或播放中断。
  - API17 构建、NDK 构建、guardrails、打包通过。
- 可观测性验收:
  - 日志可见 native DSP mode/tier/cost/bypass/degrade 信息。
  - 实机可回传足够日志判断是性能超预算、格式旁路还是正常处理。

## Design Direction
- 采用 C++ Native DSP 作为主后端:
  - Kotlin `AudioProcessor` 只保留 ExoPlayer 接入、buffer 转交、状态同步和 fail-open 包装。
  - C++ 负责 PCM16 buffer 热路径处理。
  - 模式参数与滤波器状态放在 native 层，避免 Kotlin 每帧对象访问和函数调用。
  - 优先先实现 optimized native float 或 fixed-point；若 AC83xx 仍吃紧，再继续 NEON/查表优化。
- Kotlin/C++ 职责边界:
  - Kotlin 层后续默认只做展示、用户交互、轻量状态同步和 Android 生命周期接线。
  - 编码、解码、音频处理、DSP、批量数据处理和其它耗 CPU 热路径默认放到 C++。
  - 如未来确需 Kotlin 承担非展示型重计算，必须先说明原因、性能风险与降级/fail-open 策略。
- 性能策略:
  - `quality`: 保留当前完整模式曲线。
  - `balanced`: 减少滤波器数量但保留听感方向。
  - `safe`: 最低复杂度处理，保证不卡。
  - 自动降档优先于卡顿，最终旁路优先于停播。

## Constraints
- 平台红线: `minSdk=17` 不可变，目标设备为 Android 4.2.2 / AC83xx。
- 构建约束: 复用现有 NDK/CMake，不能引入高 API 依赖。
- JNI 约束: 不允许 per-sample JNI 调用；必须按 ByteBuffer/byte array 整块处理。
- 稳定性优先: native 层任何异常都必须 fail-open，不得拖垮播放主链路。
- 音质约束: 不能以“直接关闭 DSP”作为主要优化手段；必须优先尝试保留高音效。

## Open Questions
- AC83xx 实机上卡顿是否所有模式都出现，还是主要出现在 `保真/清晰/动感/柔和`。
- 当前音频格式的 sample rate / channel count / buffer size 需要通过日志确认。
- `quality/balanced/safe` 三档具体阈值需要执行阶段先给默认值，再由实机调优。
