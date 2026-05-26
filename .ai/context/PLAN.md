# PLAN

Last Updated: 2026-05-26

## Current Stage
- Stage Name: S4 子阶段（EQ 全屏子页重做）
- Scope Source: `.ai/context/SCOPE.md`（2026-05-26）
- Stage Goal:
  - 将 EQ 页面重做为横屏全屏子页，左侧动态 bands 滑杆，右侧预设按钮。
  - 保持现有玻璃态视觉语言，但重新校正颜色配比、文字层级与触控面积。
  - 继续保持 API17 fail-open：EQ 失败不影响播放，状态提示只在回退/降级时出现。

## Scope Validation

### In Scope
- EQ 全屏子页布局重构：双栏结构、标题/返回入口、左右区域分工。
- 左侧 bands 滑杆按设备能力动态生成。
- 右侧预设按钮按设备能力动态生成并可直接触发。
- 交互联动：选预设/拖滑杆 -> 自动开启 EQ -> 外部开关同步开启。
- 玻璃态风格收口：颜色、层级、按钮、间距、触控命中。
- 回退/降级时的提示策略，不做常驻 fail-open 文案。

### Out of Scope
- `BassBoost/Virtualizer` 等附加音效扩展。
- 自定义曲线编辑器、导入导出、复杂 preset 管理。
- 播放主链路重构（仍保持 Exo + download-only）。
- 与本次 EQ UI 重做无关的外部验收任务。

## Major Work Blocks

### W1 全屏子页骨架
- 目标: 把 EQ 从当前设置页里的轻量入口，重做为全屏横屏子页。
- 结果: 页面结构、返回路径、基础视觉分区清楚。

### W2 动态控制区
- 目标: 左侧 bands 滑杆和右侧 preset 按钮都按设备能力动态生成并联动。
- 结果: 控件可用、状态同步正确、不会靠固定死值硬写。

### W3 颜色与交互收口
- 目标: 重做颜色配比、选中态、禁用态、提示态，保证车机可读性。
- 结果: 玻璃态保留，但文字和按钮不再“灰成一片”。

### W4 回归与边界维护
- 目标: 把最小验证清单补齐，保留 fail-open 观察点与实机留证入口。
- 结果: UI 改造可复盘，且不误伤播放主链路。

## Recommended Order
1. `W1`：先搭出全屏子页骨架。
2. `W2`：再接动态 bands 与 preset 联动。
3. `W3`：最后收颜色、文案、触控和状态可读性。
4. `W4`：做本地回归与 API17 观察点补齐。

## Dependency Graph
- `W1 -> W2 -> W3 -> W4`

## Risks & Assumptions
- 风险: 右侧 preset 数量在不同 ROM 上不一致，布局必须自适应。
- 风险: 1024x600 横屏下若信息密度过高，会挤压滑杆触控面积。
- 风险: 颜色只做“好看”会牺牲车机读数性，必须先保可读再谈质感。
- 假设: 当前 `EqualizerManager` / session 接线已可复用，不需要重做音频能力层。
- 假设: 全屏子页仍在现有 `MainActivity` / view 体系内完成，不引入额外架构负担。

## Stage Milestones
- M1: 全屏子页骨架完成（Done）。
- M2: 动态 bands 与 preset 联动完成（Done）。
- M3: 颜色/交互收口完成（Done）。
- M4: 本地回归完成，API17 观察点待外部窗口补齐（In Progress）。
