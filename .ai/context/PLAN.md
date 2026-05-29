# PLAN

Last Updated: 2026-05-29

## Current Stage
- Stage Name: S4 子阶段（应用内 EQ 固定 10 段直写试验）
- Scope Source: `.ai/context/SCOPE.md`（2026-05-29）
- Stage Goal:
  - 系统 EQ 继承实机不可用后，应用内 EQ 重新作为主线。
  - EQ 页面固定为常见 10 段系统式窄竖滑杆，不再按 ROM/API 返回能力生成 UI。
  - 预设固定为常见中文预设，不读取/展示 ROM preset。
  - 底层强制尝试 Android `Equalizer` band `0..9` 直接写入，用于目标 API17 车机验证。
  - 每个 band 独立 try/catch；不预先禁用，不因历史失败永久禁用，只有真实写入异常才提示。
  - 失败不影响播放，真实失败 band 不落盘，避免下次启动循环失败。

## Scope Validation

### In Scope
- 固定 10 段 EQ 模型：`31Hz / 62Hz / 125Hz / 250Hz / 500Hz / 1kHz / 2kHz / 4kHz / 8kHz / 16kHz`。
- 固定中文预设：`默认 / 流行 / 摇滚 / 爵士 / 古典 / 舞曲 / 人声 / 低音增强 / 高音增强 / 自定义`。
- 固定 10 段 UI：横屏全屏，左侧窄竖滑杆，右侧预设按钮。
- 10 段直写试验：底层逐段尝试写入 Equalizer band `0..9`。
- 逐 band 结果模型：成功/失败分段记录，失败提示基于真实异常。
- 安全持久化：先应用，后保存；真实失败 band 不写入持久化配置。
- 设置页 EQ 开关与子页自动开启/同步行为保持。
- 本地构建、API17 guardrails、实机验证清单更新。

### Out of Scope
- 系统 EQ 继承继续推进。
- 最近频点映射或曲线插值映射。
- 读取/展示 ROM 内置 bands/presets。
- `BassBoost/Virtualizer/Reverb/LoudnessEnhancer` 等附加音效。
- 多套自定义曲线保存、导入导出、复杂音效中心。
- 播放主链路重构。

## Current Reality
- `EqualizerManager` 当前会刷新 `numberOfPresets/numberOfBands`，并按真实 bands 应用 preset/custom。
- `MainActivity` 当前 EQ 子页仍从 `capabilitiesSnapshot()` 获取 preset/band 并动态渲染。
- 当前 `persistEqualizerConfig()` 在 UI 操作中先保存，再调用 `applyEqualizerConfig()`，与新 scope 的“先应用后保存”冲突。
- 当前已有 `VerticalSeekBar`，但视觉仍偏粗，需要新窄轨/窄 thumb 样式。
- 当前构建入口是 `gradle :app:assembleDebug`，API17 护栏脚本为 `./scripts/check_api17_guardrails.sh`。

## Work Blocks

### W1 固定 10 段模型与预设曲线
- 目标: 从“ROM 能力驱动”切换为“应用固定逻辑 EQ 模型”。
- 输出: 10 段频点、dB 范围、预设曲线、显示名称、持久化字段策略。

### W2 逐 band 直写与结果模型
- 目标: `EqualizerManager` 支持 band `0..9` 逐段真实写入、逐段捕获异常、逐段返回结果。
- 输出: 应用结果对象、失败 band 列表、日志、fail-open 行为。

### W3 安全持久化与 UI 联动
- 目标: 操作从“先保存后应用”改为“先尝试应用，再保存成功/有效配置”。
- 输出: 预设/滑杆/开关行为闭环，失败 band 不落盘，设置页同步不回归。

### W4 系统式窄滑杆 UI 重做
- 目标: EQ 子页接近用户提供的系统 EQ 截图，固定 10 段窄竖滑杆 + 右侧预设。
- 输出: 新布局/样式/提示区，移除 ROM 能力空态文案。

### W5 本地验证与实机清单
- 目标: 确保构建通过、API17 护栏通过，并给实机明确验证项。
- 输出: 本地验证记录、回归清单更新、实机观测字段。

## Dependency Graph
- `W1 -> W2 -> W3 -> W4 -> W5`
- `W2` 与 `W4` 可部分并行，但最终必须在 `W3` 汇合。
- `W5` 依赖实现完成。

## Recommended Order
1. `T-S4-AUDIO-073`：固定 10 段模型、预设曲线与结果契约。
2. `T-S4-AUDIO-074`：EqualizerManager 逐 band 直写与 fail-open 结果返回。
3. `T-S4-AUDIO-075`：安全持久化与交互提交路径改造。
4. `T-S4-AUDIO-076`：EQ 子页固定 10 段窄滑杆 UI 重做。
5. `T-S4-AUDIO-077`：预设/自定义/提示联动收口。
6. `T-S4-AUDIO-078`：本地构建、guardrails、实机验证清单更新。

## Risks & Assumptions
- 风险: API17 目标 ROM 可能对 band `5..9` 抛异常；必须逐段处理，不能整体崩溃。
- 风险: 预设一次性写 10 段时可能部分成功、部分失败；UI 与持久化必须能表达“部分应用”。
- 风险: 先应用后保存会影响当前设置页开关逻辑，需要避免外部开关显示与真实状态不一致。
- 风险: 1024x600 上 10 段 + 右侧预设可能拥挤，需要严格控制滑杆宽度、字号和间距。
- 假设: 仍使用 Android `Equalizer` 公共 API，不引入新依赖。
- 假设: 实机验证由用户执行，本地只能验证构建、无崩溃路径和日志/文案。

## Milestones
- M1: 代码支持固定 10 段模型并能生成预设曲线。
- M2: 逐 band 直写结果可被 UI 消费，失败不影响播放。
- M3: 失败配置不会落盘，重启安全。
- M4: EQ 页面视觉符合系统式窄滑杆方向。
- M5: 本地验证通过，实机清单可直接执行。
