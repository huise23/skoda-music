# TASK_BREAKDOWN

Last Updated: 2026-06-01

## Active Stage: S4 音效子阶段 - 应用内保真 DSP 引擎

## Execution Snapshot (2026-06-01)
- Done: `T-S4-AUDIO-080`, `081`, `082`, `083`, `084`, `085`, `086`
- Blocked: `T-S4-AUDIO-087`（等待 API17 实机听感与稳定性验证）

## T-S4-AUDIO-080
- Task ID: `T-S4-AUDIO-080`
- Module ID: `M-S4-AUDIO-014`
- Title: ExoPlayer DSP 接入落点确认
- Goal: 确认并固定 ExoPlayer 2.17.1 中自定义 `AudioProcessor` 的接入方式。
- Why: 后续 DSP 需要在 PCM 输出前处理，必须先保证接入点 API17 可用且不破坏播放。
- Dependencies: 无
- Inputs:
  - `app/src/main/java/com/skodamusic/app/player/PlaybackEngine.kt`
  - ExoPlayer 2.17.1 `DefaultRenderersFactory` / `DefaultAudioSink` API
  - `.ai/context/SCOPE.md`
- Expected Outputs:
  - 明确实现方案：自定义 renderers factory + audio sink + processor chain。
  - 明确哪些播放路径受影响，哪些保持不变。
  - 明确 fail-open 策略和日志字段。
- Done Criteria:
  - 形成可直接实现的代码落点说明。
  - 不引入高 API 依赖。
  - 不需要修改播放数据源策略。
- Risks:
  - 低版本 ExoPlayer API 与预期差异。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-081
- Task ID: `T-S4-AUDIO-081`
- Module ID: `M-S4-AUDIO-016`
- Title: 音效模式状态模型与配置迁移
- Goal: 定义替代旧 EQ 配置的音效状态模型和 SharedPreferences 迁移策略。
- Why: 旧配置围绕 `eqEnabled/eqPresetIndex/eqCustomBandLevels`，继续复用会混淆 audiofx 与 DSP 主线。
- Dependencies: 无
- Inputs:
  - `MainActivity` 现有 EQ keys：`KEY_EQ_ENABLED/KEY_EQ_PRESET_INDEX/KEY_EQ_MODE/KEY_EQ_CUSTOM_LEVELS`
  - 新模式：`原声 / 保真 / 清晰 / 动感 / 柔和`
- Expected Outputs:
  - 新配置 key 设计。
  - 旧 EQ 配置安全迁移规则。
  - 默认模式：`保真`。
  - 关闭状态语义：关闭时等同 `原声/旁路`。
- Done Criteria:
  - 重启不会触发旧 audiofx 写入。
  - 旧配置存在时能安全落到新模式。
  - 状态模型可供 UI 和 DSP backend 共用。
- Risks:
  - 状态迁移不完整导致设置页显示和实际音效不一致。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-082
- Task ID: `T-S4-AUDIO-082`
- Module ID: `M-S4-AUDIO-014`
- Title: Fail-open DSP AudioProcessor 骨架实现
- Goal: 接入一个默认旁路的自定义 `AudioProcessor`，证明播放链路可安全承载 DSP。
- Why: 先验证管线，再调音；避免算法和接入问题混在一起。
- Dependencies: `T-S4-AUDIO-080`
- Inputs:
  - `PlaybackEngine.kt`
  - 新增 `app/src/main/java/com/skodamusic/app/audio/dsp/` 包
- Expected Outputs:
  - `HiFiAudioProcessor` 或等价骨架。
  - `HiFiDspController` / 共享配置对象。
  - ExoPlayer 创建时注入 processor。
  - 关闭/原声/异常/格式不支持全部旁路。
- Done Criteria:
  - App 编译通过。
  - 原声模式播放不变。
  - processor 异常不会闪退或停播。
  - 日志能看出 DSP enabled/bypass/unsupported/error。
- Risks:
  - `queueInput()` 高频路径若分配过多会卡顿。
- Size: M
- Execution Mode: Module
- Minimal Loop: Yes

## T-S4-AUDIO-083
- Task ID: `T-S4-AUDIO-083`
- Module ID: `M-S4-AUDIO-015`
- Title: 轻量保真 DSP 模式引擎实现
- Goal: 实现五种音质模式的克制 DSP 参数和处理逻辑。
- Why: 用户目标是自然还原和层次，不能继续做夸张预设 EQ。
- Dependencies: `T-S4-AUDIO-082`
- Inputs:
  - DSP processor 骨架
  - 音质模式定义
- Expected Outputs:
  - `原声`: 完全旁路。
  - `保真`: 轻微降低浑浊、补足清晰度和空气感。
  - `清晰`: 轻微提升人声/乐器存在感。
  - `动感`: 轻微提升鼓点弹性并控制低频轰头。
  - `柔和`: 降低刺耳高频。
  - 前级降增益与防削波保护。
- Done Criteria:
  - 模式切换能更新 processor 配置。
  - 无明显削波爆音风险。
  - 处理逻辑避免高频对象分配。
  - `原声` 与关闭状态一致。
- Risks:
  - 固定参数需要实机微调。
  - 车机喇叭限制可能导致差异不明显。
- Size: M
- Execution Mode: Module
- Minimal Loop: Yes

## T-S4-AUDIO-084
- Task ID: `T-S4-AUDIO-084`
- Module ID: `M-S4-AUDIO-016`
- Title: 音效子页与设置页 UI 替换
- Goal: 将现有 EQ 子页改为音质模式页。
- Why: 现有 10 段 EQ UI 与“保真模式优先”的目标冲突。
- Dependencies: `T-S4-AUDIO-081`, `T-S4-AUDIO-083`
- Inputs:
  - `activity_main.xml`
  - `MainActivity.kt`
  - `strings.xml`
  - 现有玻璃态资源
- Expected Outputs:
  - 设置页文案：音效/音质增强。
  - 子页按钮：`原声 / 保真 / 清晰 / 动感 / 柔和`。
  - 每个模式显示一句听感说明。
  - 选中态与开关状态同步。
  - 旧 10 段滑杆不作为第一入口展示。
- Done Criteria:
  - 横屏车机可读、可点。
  - 开关关闭时为原声旁路。
  - 选择模式自动开启音效并同步设置页。
  - 不再出现 `Preset #N`、band 写入失败等主线文案。
- Risks:
  - MainActivity 内 EQ UI 代码较多，迁移需避免残留旧逻辑。
- Size: M
- Execution Mode: Module
- Minimal Loop: Yes

## T-S4-AUDIO-085
- Task ID: `T-S4-AUDIO-085`
- Module ID: `M-S4-AUDIO-016`
- Title: 模式切换联动与旧 audiofx 主线下线
- Goal: 将 UI 模式选择联动到 DSP controller，并让旧 `EqualizerManager` 不再默认运行。
- Why: 只改 UI 不改后端会继续走不可靠的 Android audiofx。
- Dependencies: `T-S4-AUDIO-082`, `T-S4-AUDIO-084`
- Inputs:
  - `MainActivity.kt`
  - `EqualizerManager.kt`
  - DSP controller
- Expected Outputs:
  - 旧 system EQ open/close 和 audiofx 写入不再作为默认路径触发。
  - 新模式选择实时更新 DSP 配置。
  - release/player lifecycle 不泄漏 processor 状态。
  - 旧代码可保留但被明确隔离。
- Done Criteria:
  - 启动、切歌、seek、暂停恢复时 DSP 状态一致。
  - 关闭音效时完全旁路。
  - 不再因旧 EQ 配置触发 audiofx 失败提示。
- Risks:
  - 旧 EQ 状态变量仍被多个 UI 分支引用，可能遗漏清理。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-086
- Task ID: `T-S4-AUDIO-086`
- Module ID: `M-S4-AUDIO-017`
- Title: 本地回归与 API17 清单更新
- Goal: 更新回归文档并执行本地校验。
- Why: DSP 是播放链路级改动，必须有明确验证入口。
- Dependencies: `T-S4-AUDIO-085`
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - `scripts/check_api17_guardrails.sh`
  - Gradle 构建入口
- Expected Outputs:
  - DSP 回归条目：开关、模式切换、旁路、异常、长播、切歌/seek。
  - 本地构建/guardrails 结果。
  - 需要实机观察的日志字段。
- Done Criteria:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - 回归清单可直接用于车机验证。
- Risks:
  - 本地无法判断听感，只能覆盖构建和行为稳定性。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-087
- Task ID: `T-S4-AUDIO-087`
- Module ID: `M-S4-AUDIO-017`
- Title: API17 实机听感与稳定性验证
- Goal: 在目标车机验证保真 DSP 的听感、稳定性和性能。
- Why: 最终目标是车机实听自然，必须依赖实机反馈调音。
- Dependencies: `T-S4-AUDIO-086`
- Inputs:
  - Debug/release APK
  - API17 回归清单
  - 用户听感反馈
- Expected Outputs:
  - 五种模式听感反馈。
  - 长播稳定性结果。
  - 是否爆音/破音/卡顿/停播。
  - 下一轮参数微调建议。
- Done Criteria:
  - 至少完成 `原声/保真/清晰/动感/柔和` 对比。
  - 至少连续播放 30 分钟无阻断问题。
  - 若失败，能回传日志与复现路径。
- Risks:
  - 听感主观，需要多首不同风格歌曲交叉判断。
- Size: M
- Execution Mode: Single
- Minimal Loop: No

## Deferred / Historical
- `T-S4-AUDIO-079`: 固定 10 段 Android audiofx 直写长期决策。当前因新 scope 下沉为 Deferred，不进入 Ready。
