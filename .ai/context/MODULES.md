# MODULES

Last Updated: 2026-05-29

## Active Module

## M-S4-AUDIO-014
- Module ID: `M-S4-AUDIO-014`
- Name: 应用内 EQ 固定 10 段直写试验
- Goal: 将应用内 EQ 从“设备能力动态 UI”改为“固定 10 段系统式 UI + 逐 band 直写试验”，并保证失败不影响播放、不写入失败配置。
- Why It Matters: 实机已确认系统 EQ 继承不可用，应用内 EQ 必须成为可用主线；当前动态 bands UI 与用户期望的系统 EQ 形态不一致，且无法验证厂商 10 段能力是否对第三方开放。
- In Scope:
  - 固定 10 段频点与固定中文预设。
  - 窄竖滑杆横屏 UI，接近系统 EQ 截图。
  - Android Equalizer band `0..9` 逐段直写试验。
  - 每个 band 独立 try/catch，真实失败才提示，不预先禁用。
  - 可写入 band 继续生效，真实失败 band 跳过本次写入。
  - 先应用后保存，真实失败 band 不落盘。
  - 设置页开关与子页自动开启同步保持。
  - 本地验证与 API17 实机清单更新。
- Out of Scope:
  - 系统 EQ 继承继续推进。
  - 最近频点/插值映射。
  - 读取或展示 ROM bands/presets。
  - BassBoost/Virtualizer/Reverb/LoudnessEnhancer。
  - 多套自定义曲线保存和复杂音效中心。
- Dependencies:
  - `M-S4-AUDIO-009` 已完成：session 能力与 `EqualizerManager` fail-open 基线。
  - `M-S4-AUDIO-012` 已完成：EQ 全屏子页与竖滑杆基础。
  - `M-S4-AUDIO-013` 已完成但实机否定：系统 EQ 继承不可作为当前主线。
- Related Files / Areas:
  - `app/src/main/java/com/skodamusic/app/audio/EqualizerManager.kt`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable/*eq*` / `*seekbar*`
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
- Milestone / Done Criteria:
  - EQ 子页固定显示 10 段窄竖滑杆和固定中文预设。
  - UI 不再依赖 ROM bands/presets，也不出现设备能力空态。
  - 预设切换和滑杆拖动都会逐 band 尝试真实写入。
  - 任一 band 写入失败不闪退、不停播、不导致整体不可用。
  - 失败提示只由真实写入异常触发，不预先禁用 band。
  - 真实失败 band 不写入持久化配置，下次启动安全。
  - `gradle :app:assembleDebug` 与 `./scripts/check_api17_guardrails.sh` 通过。
  - `T-S4-AUDIO-073~078` 已完成并回写实机验证清单。
- Related Tasks:
  - `T-S4-AUDIO-073`
  - `T-S4-AUDIO-074`
  - `T-S4-AUDIO-075`
  - `T-S4-AUDIO-076`
  - `T-S4-AUDIO-077`
  - `T-S4-AUDIO-078`
- Priority: P0
- Status: Done（本地实现与验证完成，待 API17 实机验证结果）
- Risks:
  - API17 ROM 可能对高序号 band 抛异常或无效，需靠实机确认。
  - 部分成功/部分失败会让“当前配置”语义复杂，必须明确持久化规则。
  - 页面空间有限，10 段窄滑杆与右侧预设按钮需要压缩但不能影响触控。
- Suitable For Module Execution?: Yes

## Historical EQ Modules Summary
- `M-S4-AUDIO-009`: Equalizer MVP 能力接线，Done。
- `M-S4-AUDIO-011`: EQ UI 规划，Done。
- `M-S4-AUDIO-012`: EQ 全屏子页与动态 bands UI，Done but superseded by fixed 10-band requirement。
- `M-S4-AUDIO-013`: 系统 EQ 继承接线，Done locally but superseded by实机结论（系统 EQ 不可用）。


## Progress Update (2026-05-29)
- `M-S4-AUDIO-014` 本地闭环完成：
  - 固定 10 段 EQ 模型与中文预设已落地。
  - `EqualizerManager` 已改为 band `0..9` 逐段直写，真实失败逐段返回，不预先禁用。
  - `MainActivity` 已改为先应用后保存，真实失败 band 回退到上一次持久化值并跳过落盘。
  - EQ 子页已固定为 10 段窄竖滑杆 + 右侧固定中文预设。
  - 真实失败提示基于 apply result 汇总失败频点。
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已补固定 10 段实机观察项。
- 本地验证：
  - `gradle :app:assembleDebug` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
- 剩余：
  - `T-S4-AUDIO-079` 等待 API17 实机证据后决策是否保留 10 段直写。
