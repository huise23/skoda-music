# TASK_BREAKDOWN

Last Updated: 2026-05-29

## Active Stage
- S4 子阶段（应用内 EQ 固定 10 段直写试验）

## T-S4-AUDIO-073
- Task ID: `T-S4-AUDIO-073`
- Module ID: `M-S4-AUDIO-014`
- Title: 固定 10 段 EQ 模型、预设曲线与结果契约
- Goal: 定义固定 10 段频点、dB 范围、中文预设曲线、逐 band 应用结果结构，为实现层提供稳定契约。
- Why: 当前代码依赖 ROM 返回 bands/presets；不先建立应用自己的 EQ 模型，后续 UI 与底层会继续被设备能力牵引。
- Dependencies: 无
- Inputs:
  - `.ai/context/SCOPE.md`
  - `app/src/main/java/com/skodamusic/app/audio/EqualizerManager.kt`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/values/strings.xml`
- Expected Outputs:
  - 固定 10 段频点常量与显示名。
  - 固定预设列表与每个预设的 10 段 level 曲线。
  - `ApplyResult` / band result 契约：成功、真实失败、跳过、错误信息、失败频点名。
  - 安全持久化策略说明：哪些 level 可以落盘，哪些失败 level 不能落盘。
- Done Criteria:
  - 后续任务无需再讨论频点、预设名称、预设曲线、结果语义。
  - 明确“不预先禁用 band，真实写入异常才提示”。
- Risks:
  - 预设曲线主观性强，但本轮目标是常见可用而非精细调音。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## T-S4-AUDIO-074
- Task ID: `T-S4-AUDIO-074`
- Module ID: `M-S4-AUDIO-014`
- Title: EqualizerManager 逐 band 10 段直写与 fail-open 结果返回
- Goal: 改造 `EqualizerManager`，支持按 fixed 10-band 配置逐段尝试 `setBandLevel(0..9)`，并返回真实写入结果。
- Why: 这是本轮实机验证的核心，必须知道每个 band 是真实成功还是底层抛错。
- Dependencies: `T-S4-AUDIO-073`
- Inputs:
  - `EqualizerManager.kt`
  - 现有 session 绑定与 `applyToActiveSession` 逻辑
- Expected Outputs:
  - 新的 fixed 10-band 配置应用入口。
  - 每个 band 独立 try/catch，不因单段失败影响其他段。
  - 真实异常被记录到 log/result，供 UI toast/提示区消费。
  - 初始化失败仍整体 fail-open，不影响播放。
- Done Criteria:
  - 预设或自定义写入时，band `0..9` 逐段尝试。
  - 单段失败不会抛出到 UI 主流程，不会导致闪退/停播。
  - 不做预判禁用，不持久化“永久不可用 band”。
- Risks:
  - `Equalizer` 可能在某些异常后进入不可用状态；实现需尽量隔离异常并必要时安全释放当前实例。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-075
- Task ID: `T-S4-AUDIO-075`
- Module ID: `M-S4-AUDIO-014`
- Title: EQ 配置安全持久化与提交顺序改造
- Goal: 将 EQ 操作改为先尝试应用，再保存成功/有效配置；真实失败 band 不落盘。
- Why: 当前路径存在先 `persistEqualizerConfig()` 再 `applyEqualizerConfig()` 的行为，若失败配置落盘，可能导致下次启动反复失败。
- Dependencies: `T-S4-AUDIO-074`
- Inputs:
  - `MainActivity.kt` 中 `persistEqualizerConfig/applyEqualizerConfig/applyPresetSelectionFromEqPage/applyCustomBandSelectionFromEqPage`
  - `SharedPreferences` 现有 EQ keys
- Expected Outputs:
  - 预设切换、滑杆拖动、开关切换都走安全提交路径。
  - 应用成功或部分成功后再保存有效配置。
  - 真实失败 band 不写入持久化配置；UI 保持上次有效值或本次成功值。
  - 设置页开关与子页状态保持一致。
- Done Criteria:
  - 人为触发 band 写入失败时，下次启动不会重复应用失败值。
  - 开启/关闭 EQ 不破坏默认值恢复逻辑。
- Risks:
  - 部分成功时 UI 当前值、持久化值和实际音效值容易不一致，需要明确以“真实成功写入值”为准。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-076
- Task ID: `T-S4-AUDIO-076`
- Module ID: `M-S4-AUDIO-014`
- Title: EQ 子页固定 10 段系统式窄滑杆 UI 重做
- Goal: 将 EQ 子页左侧改为固定 10 段窄竖滑杆，右侧固定中文预设按钮，视觉接近系统 EQ 截图。
- Why: 当前滑块太粗，且 UI 分区仍带明显应用卡片感，不符合用户期望的系统 EQ 形态。
- Dependencies: `T-S4-AUDIO-073`
- Inputs:
  - `activity_main.xml`
  - `MainActivity.kt` 中 `renderEqualizerFullscreenPage/renderEqualizerBandRows/renderEqualizerPresetButtons/VerticalSeekBar`
  - drawable 资源：seekbar/eq panel/button
- Expected Outputs:
  - 固定 10 段渲染，不再按 `capabilities.bands` 生成。
  - 窄轨道、窄 thumb、底部频点标签、左侧 dB 标尺或等价视觉。
  - 右侧固定中文预设按钮，选中态清晰。
  - 移除/隐藏设备 bands/presets 空态文案。
- Done Criteria:
  - 1024x600 横屏下 10 段与右侧预设可同时显示，不明显拥挤。
  - 滑块宽度明显小于当前实现，接近系统 EQ 细滑杆观感。
- Risks:
  - 触控面积和视觉细度存在冲突；可保留较宽触控列，但可见轨道必须窄。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-077
- Task ID: `T-S4-AUDIO-077`
- Module ID: `M-S4-AUDIO-014`
- Title: 预设/自定义/真实失败提示联动收口
- Goal: 接通固定预设、滑杆拖动、自定义状态、真实失败 toast/提示区与设置页状态。
- Why: 只完成 UI 和底层还不够，用户验收点集中在“切预设滑杆变化、拖动变自定义、真实失败才提示”。
- Dependencies:
  - `T-S4-AUDIO-074`
  - `T-S4-AUDIO-075`
  - `T-S4-AUDIO-076`
- Inputs:
  - `MainActivity.kt`
  - `strings.xml`
  - `EqualizerManager` apply result
- Expected Outputs:
  - 切换预设后 10 个滑杆立即同步到预设曲线。
  - 拖动任一滑杆后进入“自定义”。
  - 真实失败时 toast/提示区汇总失败频点，不预先提示、不预先禁用。
  - 设置页入口值显示当前预设/自定义状态。
- Done Criteria:
  - 用户可通过 UI 明确看到当前预设、当前自定义状态与真实失败提示。
  - 失败提示不刷屏，推荐单次操作汇总失败频点。
- Risks:
  - 拖动滑杆会高频触发写入，需避免每个 move 都 toast；失败提示应节流或在 stop/preset 操作后汇总。
- Size: M
- Execution Mode: Module
- Minimal Loop: No

## T-S4-AUDIO-078
- Task ID: `T-S4-AUDIO-078`
- Module ID: `M-S4-AUDIO-014`
- Title: 固定 10 段 EQ 本地验证与 API17 实机清单更新
- Goal: 完成本地构建/护栏验证，并把 10 段直写实机观察点补入回归清单。
- Why: 本轮核心价值是实机确认 band `0..9` 哪些真实可写，必须有现场可执行检查项。
- Dependencies: `T-S4-AUDIO-077`
- Inputs:
  - `gradle :app:assembleDebug`
  - `./scripts/check_api17_guardrails.sh`
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
- Expected Outputs:
  - 本地验证结论。
  - 回归清单新增固定 10 段 EQ 观察项。
  - 明确实机需要记录：成功频段、失败频段、toast/log、播放是否不中断、重启后是否安全。
- Done Criteria:
  - 构建通过，guardrails 通过。
  - 实机测试人员可以按清单直接验证，无需再问开发“要看什么”。
- Risks:
  - 本地无法证明车机真实 band 写入效果，只能完成可测性准备。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes

## Blocked / Future Candidates

## T-S4-AUDIO-079
- Task ID: `T-S4-AUDIO-079`
- Module ID: `M-S4-AUDIO-014`
- Title: 实机结果后决策：保留 10 段直写或切换映射方案
- Goal: 基于 API17 实机结果决定最终产品策略。
- Why: 如果 band `5..9` 大量失败，最终产品版可能需要回到最近频点/插值映射，而不是长期保留直写试验。
- Dependencies: `T-S4-AUDIO-078` + 用户实机证据
- Inputs:
  - API17 实机验证记录
- Expected Outputs:
  - 最终策略决策：保留直写 / 部分回退 / 最近频点映射 / 插值映射。
- Done Criteria:
  - 有真实证据支撑下一阶段方向。
- Risks:
  - 无实机证据时无法做产品化判断。
- Size: S
- Execution Mode: Single
- Minimal Loop: Yes
