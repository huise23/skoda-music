# TASK_QUEUE

Last Updated: 2026-05-29

## Queue Usage (白话说明)
- `Ready`: 当前就能做，且在本阶段 scope 内。
- `In Progress`: 已开始、未收口。
- `Blocked`: 依赖未满足或口径待确认。
- `Done`: 已完成并达到完成标准。
- `Deferred`: 暂不优先推进，避免范围扩张。

## Ready
- [ ] （空）

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-S4-AUDIO-079` 实机结果后决策：保留 10 段直写或切换映射方案（依赖 API17 实机证据）
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认（待系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径确认（待产品口径）

## Deferred
- [ ] `T-S4-CARRY-056` 旧阶段外部任务状态迁移与边界标注
- [ ] `T-S4-CORE-026C-HF-20260429` 浮窗 UI 热修车机复测
- [ ] `T-S4-OBS-035/036/037/038` PostHog 在线验收链路
- [ ] `T-S4-UPD-044` 更新安装触发闭环（CI/实机）
- [ ] `T-S4-REG-022` 车机实机回归
- [ ] `T-S4-VAL-033` 实机证据回写
- [ ] `T-S4-RESUME-020B` 服务侧自动续播二阶段
- [ ] `T-S4-UI-023` 长标题滚动异常修复

## Done
- [x] `T-S4-AUDIO-073` 固定 10 段 EQ 模型、预设曲线与结果契约
- [x] `T-S4-AUDIO-074` EqualizerManager 逐 band 10 段直写与 fail-open 结果返回
- [x] `T-S4-AUDIO-075` EQ 配置安全持久化与提交顺序改造
- [x] `T-S4-AUDIO-076` EQ 子页固定 10 段系统式窄滑杆 UI 重做
- [x] `T-S4-AUDIO-077` 预设/自定义/真实失败提示联动收口
- [x] `T-S4-AUDIO-078` 固定 10 段 EQ 本地验证与 API17 实机清单更新
- [x] `T-S4-AUDIO-054~060` Equalizer MVP 能力、session、fail-open 与设置页 MVP
- [x] `T-S4-AUDIO-061~064` EQ 界面规划
- [x] `T-S4-AUDIO-065~068` EQ 全屏动态 bands 子页与视觉收口（已被固定 10 段新需求覆盖）
- [x] `T-S4-AUDIO-069~072` 系统 EQ 继承接线与手动兜底（实机结论：系统 EQ 不可用，已不作为当前主线）
- [x] `T-S4-LRC-050~053` 歌词三段容器本地闭环（后续曾回滚为单容器，当前非 EQ 主线）
- [x] `T-S4-UI-024A/024B` 首页删除入口按新口径收口
- [x] `T-S4-RESUME-020C/020D` 续播口径调整代码落地
- [x] `T-S4-VAL-032` API17 回归清单升级
- [x] `T-S4-UPD-040/041/042/043` 更新链路主干能力落地

## Queue Notes
- `M-S4-AUDIO-014` 当前本地实现与验证已完成。
- 剩余 EQ 决策任务只有 `T-S4-AUDIO-079`，依赖 API17 实机回传哪些 band 真实可写/失败。
- 技术红线保持不变：`minSdk=17`、Emby-only、IPv4-only、download-only 主链路。
- EQ 新硬约束已落地：UI 固定 10 段；底层逐 band 真实写入；不预先禁用；真实失败才提示；失败配置不落盘。
