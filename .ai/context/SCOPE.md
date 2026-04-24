# SCOPE

Last Updated: 2026-04-23

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（C++ 业务骨架 + Kotlin Android 壳）。

## Scope Summary
- 当前阶段: S3 收尾（最近提交已落地后的回归与范围收敛）
- 阶段目标:
  - 基于 `2026-04-22` 最近提交簇（`476ac0e` ~ `1fa5137`）确认当前主线范围。
  - 将 `.ai/context` 从“本地未提交改动”口径切换为“已提交到 `HEAD=1fa5137`”口径。
  - 明确下一执行入口仍为 `T-S3-VAL-012`（API17 实机回归与证据回填）。
- In Scope:
  - 归纳最近提交的事实变更（UI 壳重构、glass 视觉收敛、歌词 loading 行为接线、导航/进度条细节调整）。
  - 校准当前阶段上下文文件，使其与仓库现状一致（重点 `SCOPE/CURRENT_STATUS/HANDOFF`）。
  - 收敛本阶段验收边界：以 API17 稳定性和回归证据闭环为准。
- Out of Scope:
  - 新协议/新后端接入（仍为 Emby-only）。
  - 超出 S3 的新功能扩展或大规模架构重构。
  - 绕过 `minSdk=17` 约束的依赖升级方案。
- Success Criteria:
  - `.ai/context/SCOPE.md` 已创建并明确 in-scope/out-of-scope/成功标准/约束/待确认项。
  - `.ai/context` 不再描述“Gemini 未提交改动”，改为已提交基线事实。
  - 下一步动作清晰且可执行（`T-S3-VAL-012` + UI/歌词回归证据）。
- Constraints:
  - 设备/平台红线: Android `4.2.2` / API `17`，`minSdk=17` 不可变。
  - 网络与播放口径: Emby 域名保持不变、IPv4-only、download-only 主链路保持。
  - 本地环境约束: 缺少 `gradle/gradlew.bat`，编译型验证依赖 CI 或外部实机环境。
- Open Questions:
  - `B-LRC-001`：歌词请求失败后的回退与重试策略最终口径仍待确认。
  - `T-S3-VAL-012`：API17 全量回归证据尚未完整回填（尤其覆盖 2026-04-22 UI/歌词变更）。
  - 系统首页音乐卡片第三方入口能力仍未确认（`T-BLK-001`）。

## Recent Commit Analysis
- 时间窗口: `2026-04-21` ~ `2026-04-22`
- 当前 `HEAD`: `1fa5137`
- 工作区状态: clean（无未提交改动）
- 变更集中区:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - 多个 `drawable/colors/strings` 资源文件
  - 少量 `app/build.gradle.kts` 配置收敛
- 关键特征:
  - 提交信息以“`ui优化`/`其他页面重构`/`歌词loading`”为主，且改动量集中在前台交互与视觉层。
  - `437aabf`（歌词 loading）为显著行为改动点（单提交 `+205/-2`）。
  - 近期改动已落入主干，不再属于“本地 Gemini 未提交差异”。
