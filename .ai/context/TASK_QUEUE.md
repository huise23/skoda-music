# TASK_QUEUE

Last Updated: 2026-04-15 22:41:00

## Ready
- [ ] （空）

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口接入（待系统能力确认）
- [ ] `B-PLY-001` Android 4.2.2 实机功能回归结果（待人工安装/回传）
- [ ] `B-LRC-001` 歌词失败回退策略口径（待确认）

## Done
- [x] `D-BOOT-001` `.ai/context/` bootstrap 最小工作台已建立
- [x] `D-CI-001` API 17 打包链路可用，`Package MVP` 成功
- [x] `D-CI-002` `minSdk` 校验误报已修复（兼容 `sdkVersion/minSdkVersion`）
- [x] `T-S1-001` Android 前台交互壳改造（替换封面页）
- [x] `T-S1-002` Android UI 状态模型（最小 ViewState）
- [x] `T-S1-003` 播放动作最小闭环（本地 mock）
- [x] `T-S1-005` Emby 连接状态可视化
- [x] `T-S1-004` Android-C++ 桥接最小接线
- [x] `T-HF-EMBY-001` Emby 信息本地持久化（含密码）与启动回填
- [x] `T-HF-EMBY-002` Emby 曲名显示诊断增强（UTF-8 解码 + 曲名样例日志）
- [x] `T-HF-PLY-001` Android 实际播放接线（MediaPlayer + Emby Stream URL）
- [x] `T-HF-PLY-002` Emby 推荐拉取优先与全量回退策略接线
- [x] `T-S1-006` API 17 实机交互回归脚本化清单

## Queue Notes
- `Ready` 仅包含当前阶段可独立闭环并可验证的任务。
- Android 前台已移除本地 mock 曲目源，当前曲目来源改为 Emby 实时请求结果。
- `T-HF-EMBY-001/002` 为用户显式指令触发的最小热修复，已并入 Done 供后续追踪。
- `T-HF-PLY-001/002` 为用户显式指令触发的最小播放集成闭环，已并入 Done 供后续追踪。
