# SCOPE

Last Updated: 2026-04-29

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（C++ 业务骨架 + Kotlin Android 壳）

## Scope Summary
- 当前阶段: S4 本地实现收口（车机验收前）
- 阶段目标:
  - 收口 S4 已落地代码与文档，形成“可直接进入 CI/车机验收”的本地交付基线。
  - 明确当前仍未完成项，并区分“本地可完成”与“依赖车机/CI”任务。
  - 不扩展新需求，优先消除上下文中的范围歧义与验收入口缺口。

## In Scope
- 本地实现收口与缺口补齐：
  - `T-S4-VAL-032`：升级 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 到 S4 口径，并补齐 Section 4 风险控制与验收模板。
  - 对当前 WIP 任务给出“本地已完成 / 待外部验收”边界：
    - `T-S4-CORE-026A/026B/026C-HF-20260429`
    - `T-S4-OBS-035/036/037`
    - `T-S4-UPD-044`
- 统一“未完成项”口径，作为下一步执行入口（`TASK_QUEUE/NEXT_STEPS` 对齐）。

## Out of Scope
- 需要车机窗口或外部环境才能完成的终验动作：
  - `T-S4-REG-022` 车机全量回归
  - `T-S4-VAL-033` 实机证据回写收口
  - `T-S4-OBS-038` 在线查询验证（依赖真实数据）
- 新需求扩展与未来优化项：
  - `T-S4-UI-023/024`、`T-S4-AUDIO-025`
  - 新协议/新后端接入、MediaSession 主链路切换、自建 OTA/静默安装

## Success Criteria
- 产出一个可执行、可验证的“本地收口清单”，明确以下未完成项：
  - 本地可直接完成：`T-S4-VAL-032`（当前唯一 Ready 且不依赖车机窗口）。
  - 代码已落地但待外部验收：`T-S4-CORE-026A/026B/026C-HF-20260429`、`T-S4-UPD-044`、`T-S4-OBS-035/036/037`。
  - 外部依赖阻塞：`T-S4-REG-022`、`T-S4-VAL-033`、`T-S4-OBS-038`、`T-S4-RESUME-020B`、`T-S4-CORE-026C`。
- `SCOPE` 内容与当前上下文口径一致（不再包含已确认但仍写为 open 的事项）。

## Constraints
- 平台红线: Android `4.2.2` / API `17`，`minSdk=17` 不可变。
- 网络口径: Emby-only、IPv4-only、业务 Host 保持 Emby 域名。
- 稳定性口径: 允许前台服务常驻通知。
- 本地环境: 缺少 `gradle/gradlew`，编译验证依赖 CI 或外部设备。
- 更新源口径: 版本元数据与安装包来源于 GitHub Releases；镜像仅做加速与回退，不改版本真源。
- 风险口径: 更新检测与下载必须 fail-open，失败不得影响播放主链路。

## Open Questions
- 长标题滚动异常修复具体策略（Marquee/Fade/滚动速度）。
- 主屏删除入口的具体交互位置与防误触细节。
- 均衡器/音效优化路径（系统 EQ / Exo 音频处理 / 预设策略）。
- 系统首页音乐卡片第三方入口能力确认（`T-BLK-001`）。
- 更新检测策略细节：冷启动检查周期（建议 12h/24h）与是否仅 Wi-Fi 检测。
- GitHub 镜像默认优先级与备用域名列表是否允许用户在设置中自定义。
