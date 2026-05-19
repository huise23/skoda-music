# PLAN

Last Updated: 2026-05-19

## Current Stage
- Stage Name: S4 子阶段（歌词中线改造 + 均衡器规划）
- Scope Source: `.ai/context/SCOPE.md`（2026-05-19）
- Stage Goal:
  - 完成 Home 歌词“三段容器（上文/当前行/下文）”改造规划与实现路径。
  - 完成 API17 下均衡器能力可行性与 MVP 实施规划。

## Scope Validation

### In Scope
- 歌词 UI 改造：从单 `TextView + Scroll` 迁移到“三段容器中线方案”。
- 歌词边界定义：首句/末句、超长换行、无歌词、加载中状态。
- 均衡器技术规划：`Equalizer` 可行性、音频 session 生命周期、fail-open 策略、MVP 任务拆分。

### Out of Scope
- 本阶段不做完整音效系统上线（复杂 EQ UI、自定义曲线编辑、多音效联动）。
- 不改播放主链路策略（仍保持 Exo + download-only 现状）。
- 不推进与本阶段无关的外部验收任务（车机回归、OBS 在线验收、更新安装闭环）。

## Major Work Blocks

### W1 歌词布局方案收敛
- 目标: 固化三段容器交互与验收标准，明确“绝对居中”与“视觉居中”边界。
- 结果: 可执行实现任务输入（布局与渲染逻辑边界清晰）。

### W2 歌词改造实现与本地验证
- 目标: 完成 `activity_main.xml` 与 `MainActivity.kt` 的歌词渲染改造。
- 结果: 当前行独立容器稳定居中，上下文歌词按规则显示。

### W3 均衡器可行性与接线规划
- 目标: 基于 API17 与当前 `PlaybackEngine` 设计 EQ 接线方案。
- 结果: 明确 session 获取/绑定/释放时机与异常降级策略。

### W4 均衡器 MVP 任务化
- 目标: 把 W3 结论转换成可执行任务队列（不止概念文档）。
- 结果: Ready/Blocked 明确，可直接进入 `ai-execution` 或 `ai-module-execution`。

### W5 阶段边界管理（Carry Forward）
- 目标: 将旧阶段外部验收任务从主线剥离，避免干扰当前推进。
- 结果: 保留追踪但不进入本阶段 Ready。

## Recommended Order
1. `W1`：先锁歌词三段容器的行为与验收口径。
2. `W2`：再改布局与渲染逻辑，完成本地回归。
3. `W3`：并行完成 EQ 可行性分析与接线设计。
4. `W4`：将 EQ 结论落到任务层，形成后续实现入口。
5. `W5`：持续维护旧任务边界，防止范围回弹。

## Dependency Graph
- `W1 -> W2`
- `W3 -> W4`
- `W5` 与主线并行

## Risks & Assumptions
- 风险: 三段容器若处理不当，可能引入歌词跳变感或换行错位。
- 风险: API17 设备上 `audiofx` 可用性存在 ROM 差异，需保留 fail-open。
- 假设: 当前 Exo 引擎可稳定提供可绑定的音频 session。
- 假设: 本阶段可先本地验证结构正确性，车机感知验证放后续。

## Stage Milestones
- M1: 歌词三段容器方案与验收标准确定。
- M2: 歌词 UI/逻辑改造完成并通过本地回归清单。
- M3: EQ 可行性与生命周期接线结论确定。
- M4: EQ MVP 任务完成拆分并进入可执行队列。
