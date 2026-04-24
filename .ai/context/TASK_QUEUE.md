# TASK_QUEUE

Last Updated: 2026-04-23 09:25

## Ready
- [ ] `T-S3-VAL-014` API17 回归清单升级（覆盖 S3 新行为）
- [ ] `T-S3-VAL-015` 设备报告模板固化（依赖 `T-S3-VAL-014`，可同轮闭环）

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-S3-VAL-012` API17 实机专项回归执行（依赖人工设备）
- [ ] `T-S3-VAL-016` 回归结果复盘与 Context 回写（依赖 `T-S3-VAL-012` 证据）
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口接入（待系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径（待确认）

## Done
- [x] `T-S3-UI-013` Home 播放模块重排 + 可拖动进度 + 解码失败自动切歌
- [x] `T-S3-RB-008` 回滚错误实现并恢复基线
- [x] `T-S3-NET-009` CF 优选 IPv4 解析链路接线（Emby 域名保持不变）
- [x] `T-S3-DL-010` 30s 下载窗口调度状态机
- [x] `T-S3-LOG-011` 下载调度与优选 IP 诊断日志补齐
- [x] `T-S3-PLY-001` ExoPlayer 2.x 版本探针与依赖固定（历史）
- [x] `T-S3-PLY-002` 播放引擎抽象与 MediaPlayer 主路径替换（历史）
- [x] `T-S3-DL-003` Download-only 边下边播链路落地（历史）

## Queue Notes
- Ready 仅放入无需人工设备的最小闭环任务。
- `T-S3-VAL-012` 仍是阶段主目标，但执行前置为回归清单与报告模板标准化。
- 网络与平台硬约束保持不变：`minSdk=17`、`IPv4 only`、业务 Host 保持 Emby 域名。
