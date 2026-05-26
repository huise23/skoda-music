# SCOPE

Last Updated: 2026-05-26

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（API17 基线，Kotlin 壳 + C++ 核心）

## Scope Summary
- 当前阶段: S4 子阶段（EQ 全屏子页重做）
- 阶段目标:
  - 将 EQ 页面重做为适配 1024x600 横屏的全屏子页，左侧动态 bands 滑杆，右侧预设按钮。
  - 保持现有玻璃态视觉语言，但显著提升文字可读性、颜色配比和整体操作效率。
  - 保留 API17 fail-open：EQ 失败不影响主播放；仅在回退/降级时显示状态提示。

## In Scope
- EQ 全屏子页重做（UI/交互层）:
  - 采用横屏全屏子页布局，不再使用小弹窗样式。
  - 左侧按设备能力动态生成 bands 滑杆。
  - 右侧以按钮形式展示预设。
  - 保持“选预设/拖滑杆 -> 自动开启 EQ -> 外部开关同步开启”。
  - 保持玻璃态风格一致，但重新校正颜色比例、文字层级与按钮触控面积。
  - 状态提示仅在回退/降级时显示，默认不占用常驻区域。
- 既有 EQ 能力约束保持不变:
  - 继续基于 `android.media.audiofx.Equalizer` 与 audio session 生命周期。
  - 继续保持 fail-open，不影响主播放。

## Out of Scope
- 本阶段不做:
  - 全量音效系统上线（导入导出、自定义曲线编辑器、复杂预设管理）。
  - `BassBoost/Virtualizer` 等附加音效扩展。
  - 跨协议/跨后端改造（仍保持 Emby-only）。
  - 播放主链路重构（不替换当前 Exo 引擎、不改 download-only 主策略）。
  - 与本次 EQ UI 重做无关的 S4 外部验收任务。

## Success Criteria
- EQ 页面:
  - 全屏横屏布局在 1024x600 下可直接操作，无明显拥挤或遮挡。
  - 左侧滑杆与右侧预设按钮都能清晰识别并准确操作。
  - 颜色与文字层级更协调，具备可读性和车机可用性。
  - EQ 开关、预设、滑杆联动行为正确，fail-open 不影响播放。

## Design Direction
- 全屏横屏子页。
- 左侧动态 bands 滑杆区。
- 右侧预设按钮网格区。
- 维持玻璃态视觉语言，但重做颜色配比与文字层级。
- 不常驻显示 fail-open 提示，仅在回退/降级时提示。

## Constraints
- 平台红线: `minSdk=17` 不可变，目标设备为 Android 4.2.2 车机。
- 稳定性优先: 任何音效能力都不得阻塞主播放链路；失败必须 fail-open。
- 现有基线约束: 保持 Emby-only、IPv4-only、download-only 主链路。
- 车机场景约束: UI 必须适配 1024x600 横屏，触控命中与可读性优先。

## Open Questions
- 无，当前设计方向已收敛。
