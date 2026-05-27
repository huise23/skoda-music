# TASK_QUEUE

Last Updated: 2026-05-27

## Queue Usage (白话说明)
- `Ready`: 当前就能做，且在本阶段 scope 内。
- `In Progress`: 已开始、未收口。
- `Blocked`: 依赖未满足或口径待确认。
- `Done`: 已完成并达到完成标准。
- `Deferred`: 暂不优先推进，避免范围扩张。

## Ready
- [ ] `T-S4-CARRY-056` 旧阶段外部任务状态迁移与边界标注 `[Module: M-S4-CARRY-010 | Mode: Single | P2]`

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认（待系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径确认（待产品口径）

## Deferred
- [ ] `T-S4-CORE-026C-HF-20260429` 浮窗 UI 热修车机复测
- [ ] `T-S4-OBS-035/036/037/038` PostHog 在线验收链路
- [ ] `T-S4-UPD-044` 更新安装触发闭环（CI/实机）
- [ ] `T-S4-REG-022` 车机实机回归
- [ ] `T-S4-VAL-033` 实机证据回写
- [ ] `T-S4-RESUME-020B` 服务侧自动续播二阶段
- [ ] `T-S4-UI-023` 长标题滚动异常修复

## Done
- [x] `T-S4-UI-024A/024B` 首页删除入口按新口径收口
- [x] `T-S4-RESUME-020C/020D` 续播口径调整代码落地
- [x] `T-S4-VAL-032` API17 回归清单升级
- [x] `T-S4-UPD-040/041/042/043` 更新链路主干能力落地
- [x] `T-S4-AUDIO-054` API17 Equalizer 可行性与生命周期接线分析
- [x] `T-S4-AUDIO-055` 均衡器 MVP 方案文档与任务拆分
- [x] `T-S4-AUDIO-057` PlaybackEngine 暴露 audioSessionId 并打通生命周期接线
- [x] `T-S4-AUDIO-058` EqualizerManager 最小实现（fail-open + 会话熔断）
- [x] `T-S4-AUDIO-059` EQ MVP 设置接线（开关 + 预设 + 持久化）
- [x] `T-S4-AUDIO-060` EQ MVP 本地回归与 API17 实机验证条目补齐
- [x] `T-S4-AUDIO-061` EQ 界面信息架构与入口规划
- [x] `T-S4-AUDIO-062` EQ 状态与失败降级 UI 矩阵定义
- [x] `T-S4-AUDIO-063` EQ 界面低保真线框与交互流程图（文档化）
- [x] `T-S4-AUDIO-064` EQ 界面实现任务拆分与验收清单落地
- [x] `T-S4-AUDIO-065` EQ 全屏子页骨架与横屏布局重构
- [x] `T-S4-AUDIO-066` 左侧动态 bands 滑杆区接线
- [x] `T-S4-AUDIO-067` 右侧 preset 按钮区与联动行为收口
- [x] `T-S4-AUDIO-068` EQ 颜色样式收口与回归验证（本地）
- [x] `T-S4-AUDIO-069` 系统 EQ 会话接线实现（open/close）
- [x] `T-S4-AUDIO-070` 启动默认系统优先策略与手动应用 EQ 兜底联动
- [x] `T-S4-AUDIO-071` 系统不可用 toast 与反馈文案收口（设置页结构不变）
- [x] `T-S4-AUDIO-072` 系统 EQ 接线本地回归与 API17 观察点补齐
- [x] `T-S4-LRC-050` 歌词三段容器口径收敛（以三段容器实现口径固化）
- [x] `T-S4-LRC-051` Home 歌词面板三段容器布局改造
- [x] `T-S4-LRC-052` 歌词渲染逻辑改造（上文/当前/下文分发）
- [x] `T-S4-LRC-053` 歌词改造本地回归与文档回写

## Queue Notes
- 本阶段主线已切到“系统 EQ 继承接线（Option B）”：系统优先，手动应用 EQ 兜底。
- 当前代码现实：系统 EQ 会话 open/close 已接线，启动默认系统优先；系统不可用时提示并允许手动开启应用 EQ 兜底。
- 技术红线保持不变：`minSdk=17`、Emby-only、IPv4-only、download-only 主链路。
- `audiofx` / 系统音效在 API17 ROM 上按 fail-open 处理：音效失败不影响播放。
- 系统 EQ 接线本地验证已完成；下一步转入外部窗口任务链（`T-S4-REG-022` / `T-S4-VAL-033`）补齐实机证据。
