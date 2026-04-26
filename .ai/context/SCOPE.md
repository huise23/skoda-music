# SCOPE

Last Updated: 2026-04-26

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（C++ 业务骨架 + Kotlin Android 壳）

## Scope Summary
- 当前阶段: S4 车机后台控制（方案1）
- 阶段目标:
  - 在 API17 红线下实现后台服务化播放。
  - 打通后台方向盘媒体按键（上一曲/下一曲/播放暂停）。
  - 提供全局小浮窗控制（歌名 + 三键控制）。
  - 支持熄火/休眠恢复后自动续播。

## In Scope
- `ForegroundService` 承载播放状态与生命周期。
- `ACTION_MEDIA_BUTTON` 接入与 Service 命令分发统一。
- `AudioManager/RemoteControlClient` legacy 媒体键桥接（API17 友好）。
- 悬浮窗权限、显示策略、手动关闭后再显示策略。
- 前台通知控制条作为兜底控制入口。
- 车机实机回归与证据回填（后台按键/浮窗/自动续播）。

## Out of Scope
- 新协议/新后端接入（仍为 Emby-only）。
- 重新切换到 MediaSession 作为主链路。
- 与车机无关的任务划掉行为讨论。
- 大规模视觉改版。

## Success Criteria
- 退到后台后播放不断，服务稳定常驻。
- 方向盘按键在后台可控且行为一致。
- 浮窗满足确认规则：播放/暂停可见；手动关闭后“进应用再切出”自动再显示。
- 熄火/休眠恢复后自动续播成功（失败时可诊断、有降级）。

## Constraints
- 平台红线: Android `4.2.2` / API `17`，`minSdk=17` 不可变。
- 网络口径: Emby-only、IPv4-only、业务 Host 保持 Emby 域名。
- 稳定性口径: 允许前台服务常驻通知。
- 本地环境: 缺少 `gradle/gradlew`，编译验证依赖 CI 或外部设备。

## Open Questions
- 长标题滚动异常修复具体策略（Marquee/Fade/滚动速度）。
- 主屏删除入口的具体交互位置与防误触细节。
- 均衡器/音效优化路径（系统 EQ / Exo 音频处理 / 预设策略）。
- 系统首页音乐卡片第三方入口能力确认（`T-BLK-001`）。
