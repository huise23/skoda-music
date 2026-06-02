# TASK_BREAKDOWN

Last Updated: 2026-06-02

## Active Stage: S4 子阶段 - AC83xx Native Hi-Fi DSP 性能优化

## Planning Snapshot
- Previous Kotlin DSP tasks `T-S4-AUDIO-080~086`: Done locally.
- Previous real-device listening task `T-S4-AUDIO-087`: Superseded by native performance optimization before further listening validation.
- New task chain starts at `T-S4-AUDIO-088`.

## Execution Snapshot (2026-06-02)
- Done locally: `T-S4-AUDIO-088`, `089`, `090`, `091`, `092`, `093`, `094`.
- Blocked by device: `T-S4-AUDIO-095`.
- Local validation passed:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`

## T-S4-AUDIO-088
- Task ID: `T-S4-AUDIO-088`
- Module ID: `M-S4-AUDIO-018`
- Title: Native DSP JNI API 与 fail-open 契约
- Goal: 固定 Kotlin 到 C++ 的调用边界、错误码、生命周期和旁路策略。
- Why: native 化最容易出错的是 JNI 签名、buffer 所有权和异常边界；先定契约可避免实现返工。
- Dependencies: 无
- Inputs:
  - `.ai/context/SCOPE.md`
  - `HiFiAudioProcessor.kt`
  - `HiFiDspController.kt`
  - `app/src/main/cpp/CMakeLists.txt`
  - `native_playback_bridge.cpp`
- Expected Outputs:
  - JNI 方法清单与参数含义。
  - native handle 生命周期规则。
  - direct/non-direct buffer 处理规则。
  - 错误码与 fail-open 行为矩阵。
- Done Criteria:
  - 契约足够直接进入实现。
  - 明确不允许 per-sample JNI。
  - 明确 native 失败不能导致播放中断。
- Risks:
  - 契约过度复杂会增加 JNI 维护成本。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-089
- Task ID: `T-S4-AUDIO-089`
- Module ID: `M-S4-AUDIO-019`
- Title: 性能档位、预算阈值与日志字段契约
- Goal: 定义 `quality / balanced / safe` 三档策略、降档条件、恢复条件和日志字段。
- Why: 用户要求高音效，但 AC83xx 性能有限；必须先定义“何时保真、何时降档、何时旁路”。
- Dependencies: 无
- Inputs:
  - 当前 Kotlin 五种模式参数
  - `.ai/context/SCOPE.md` 性能验收标准
- Expected Outputs:
  - 三档每个模式保留的滤波器数量/方向。
  - buffer 耗时预算初值。
  - 超预算计数与降档策略。
  - 日志字段：mode/tier/cost/degrade/bypass。
- Done Criteria:
  - 三档策略可直接映射到 C++ 实现。
  - 降档优先于直接关闭。
  - 旁路只作为最终保护路径。
- Risks:
  - 阈值需要实机二次调优。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-090
- Task ID: `T-S4-AUDIO-090`
- Module ID: `M-S4-AUDIO-018`
- Title: Native bridge scaffold 与 no-op/bypass buffer 处理
- Goal: 新增 native DSP bridge 文件、CMake 接线、Kotlin bridge，并先实现安全 no-op/bypass。
- Why: 先证明 native 调用链稳定，再加入 DSP 算法，降低排障复杂度。
- Dependencies: `T-S4-AUDIO-088`
- Inputs:
  - `app/src/main/cpp/CMakeLists.txt`
  - `app/src/main/java/com/skodamusic/app/audio/dsp/`
- Expected Outputs:
  - `native_hifi_dsp` C++ 源文件/头文件或等价实现。
  - Kotlin `NativeHiFiDspBridge` 或等价封装。
  - handle create/configure/release/process no-op 路径。
- Done Criteria:
  - 构建通过。
  - native 不可用时 Kotlin 自动旁路。
  - no-op 处理不改变音频内容。
- Risks:
  - JNI 名称或包路径不一致导致运行期找不到方法。
- Size: M
- Execution Mode: Module
- Minimal Loop: Yes

## T-S4-AUDIO-091
- Task ID: `T-S4-AUDIO-091`
- Module ID: `M-S4-AUDIO-019`
- Title: C++ DSP 模式引擎与系数预计算
- Goal: 将现有五种模式的 preamp、biquad、limiter 迁入 native，并把系数计算移出热路径。
- Why: Kotlin per-sample float 处理是当前性能瓶颈；C++ 需要承担实际 DSP 热路径。
- Dependencies: `T-S4-AUDIO-090`, `T-S4-AUDIO-089`
- Inputs:
  - `HiFiAudioProcessor.kt` 当前 `ModeSpec/FilterSpec/Biquad`
  - Native bridge scaffold
- Expected Outputs:
  - C++ mode specs。
  - 每声道滤波器状态。
  - 配置时预计算系数。
  - PCM16 mono/stereo buffer 处理。
- Done Criteria:
  - `原声` 旁路，其它四种模式可处理。
  - 热路径无三角函数、无分配、少分支。
  - 输出 clamp/limiter 不产生明显爆音。
- Risks:
  - C++ 参数与 Kotlin 版听感不完全一致，需要实机调音。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-092
- Task ID: `T-S4-AUDIO-092`
- Module ID: `M-S4-AUDIO-019`
- Title: 自动降档、耗时统计与节流日志
- Goal: 实现 buffer 处理耗时统计、三档自动降级、最终旁路和日志节流。
- Why: native 化后仍可能在 AC83xx 超预算，必须自动保护播放连续性。
- Dependencies: `T-S4-AUDIO-091`
- Inputs:
  - 性能档位契约
  - native DSP engine
- Expected Outputs:
  - quality/balanced/safe runtime tier。
  - over-budget counter。
  - degrade/bypass reason。
  - throttled log callback 或 Kotlin 侧状态读取。
- Done Criteria:
  - 超预算优先降档。
  - 持续超预算或处理异常最终旁路。
  - 日志足够判断性能问题，不高频刷屏。
- Risks:
  - 日志跨 JNI 设计过重会抵消性能收益。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-093
- Task ID: `T-S4-AUDIO-093`
- Module ID: `M-S4-AUDIO-020`
- Title: `HiFiAudioProcessor` 热路径迁移到 native
- Goal: Kotlin `queueInput()` 改为整块调用 native 处理，移除 active path 中的 Kotlin sample loop。
- Why: 只有播放热路径真正离开 Kotlin，才能解决 AC83xx 卡顿根因。
- Dependencies: `T-S4-AUDIO-090`, `T-S4-AUDIO-091`, `T-S4-AUDIO-092`
- Inputs:
  - `HiFiAudioProcessor.kt`
  - Native bridge Kotlin wrapper
- Expected Outputs:
  - native process 替代 Kotlin `processPcm16`。
  - `onConfigure/onFlush/onReset` 同步 native 状态。
  - 失败时当前 buffer 旁路输出。
- Done Criteria:
  - 开启音效时不再执行 Kotlin biquad/sample loop。
  - 关闭、原声、不支持格式全部旁路。
  - 模式切换实时更新 native config。
- Risks:
  - buffer position 处理错误会造成杂音或丢帧。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-094
- Task ID: `T-S4-AUDIO-094`
- Module ID: `M-S4-AUDIO-021`
- Title: 本地验证、guardrails 与 API17 清单更新
- Goal: 完成本地构建验证，并将 native DSP 性能观察项写入回归清单。
- Why: native 改动必须同时验证构建、API17 兼容和实机可观察性。
- Dependencies: `T-S4-AUDIO-093`
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - scripts/guardrails
  - Gradle build
- Expected Outputs:
  - 构建验证结果。
  - 更新后的 AC83xx native DSP 验证条目。
  - 实机日志回传模板。
- Done Criteria:
  - `git diff --check` 通过。
  - API17 guardrails 通过。
  - compile/assemble 至少完成一个，优先 assemble。
  - 文档包含 mode/tier/cost/degrade/bypass 观察项。
- Risks:
  - 本地缺少目标设备，只能完成本地闭环。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-095
- Task ID: `T-S4-AUDIO-095`
- Module ID: `M-S4-AUDIO-021`
- Title: AC83xx 实机长播与听感验证
- Goal: 在目标车机验证 native DSP 是否消除卡顿并保留听感差异。
- Why: 这是本阶段最终验收，不能由本地构建替代。
- Dependencies: `T-S4-AUDIO-094`
- Inputs:
  - 对应 APK
  - API17 native DSP 验证清单
  - logcat/runtime logs
- Expected Outputs:
  - 30 分钟长播结果。
  - 各模式听感反馈。
  - mode/tier/cost/degrade/bypass 日志摘录。
  - 是否需要 fixed-point/NEON/参数二轮优化的结论。
- Done Criteria:
  - `保真` 模式不再有广播感卡顿。
  - 切歌、seek、暂停恢复稳定。
  - 降档/旁路行为有日志证据。
- Risks:
  - 设备窗口外部依赖，无法在本地完成。
- Size: M
- Execution Mode: Single
- Minimal Loop: No
