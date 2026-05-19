# TASK_QUEUE

Last Updated: 2026-05-19

## Queue Usage (白话说明)
- `Ready`: 当前就能做，且在本阶段 scope 内。
- `In Progress`: 已开始、未收口。
- `Blocked`: 依赖未满足或口径待确认。
- `Done`: 已完成并达到完成标准。
- `Deferred`: 暂不优先推进，避免范围扩张。

## Ready
- [ ] `T-S4-LRC-050` 歌词三段容器交互口径与验收清单定义
- [ ] `T-S4-AUDIO-054` API17 Equalizer 可行性与生命周期接线分析
- [ ] `T-S4-CARRY-056` 旧阶段外部任务状态迁移与边界标注

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-S4-LRC-051` Home 歌词面板三段容器布局改造（依赖 `T-S4-LRC-050`）
- [ ] `T-S4-LRC-052` 歌词渲染逻辑改造（按三段容器输出，依赖 `T-S4-LRC-051`）
- [ ] `T-S4-LRC-053` 歌词改造本地回归与文档回写（依赖 `T-S4-LRC-052`）
- [ ] `T-S4-AUDIO-055` 均衡器 MVP 方案文档与任务拆分（依赖 `T-S4-AUDIO-054`）
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认（待系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径确认（待产品口径）

## Done
- [x] `T-S4-UI-024A/024B` 首页删除入口按新口径收口
- [x] `T-S4-RESUME-020C/020D` 续播口径调整代码落地
- [x] `T-S4-VAL-032` API17 回归清单升级
- [x] `T-S4-UPD-040/041/042/043` 更新链路主干能力落地

## Deferred
- [ ] `T-S4-CORE-026C-HF-20260429` 浮窗 UI 热修车机复测
- [ ] `T-S4-OBS-035/036/037/038` PostHog 在线验收链路
- [ ] `T-S4-UPD-044` 更新安装触发闭环（CI/实机）
- [ ] `T-S4-REG-022` 车机实机回归
- [ ] `T-S4-VAL-033` 实机证据回写
- [ ] `T-S4-RESUME-020B` 服务侧自动续播二阶段
- [ ] `T-S4-UI-023` 长标题滚动异常修复

## Queue Notes
- 本阶段主线聚焦“歌词中线改造 + 均衡器规划”，不把外部依赖任务放进 Ready。
- 技术红线保持不变：`minSdk=17`、Emby-only、IPv4-only、download-only 主链路。
