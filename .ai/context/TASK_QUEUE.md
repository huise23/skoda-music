# TASK_QUEUE

Last Updated: 2026-04-16 20:47

## Ready
- [ ] （空）

## In Progress
- [ ] （空）

## Blocked
- [ ] `T-S2-004` API17 全量回归执行与证据回填（依赖人工实机）
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口接入（待系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径（待确认）

## Done
- [x] `D-BOOT-001` `.ai/context/` bootstrap 最小工作台已建立
- [x] `D-CI-001` API 17 打包链路可用
- [x] `D-CI-002` `minSdk` 校验误报修复（兼容 `sdkVersion/minSdkVersion`）
- [x] `T-S1-001` Android 前台交互壳改造（替换封面页）
- [x] `T-S1-002` Android UI 状态模型（最小 ViewState）
- [x] `T-S1-003` 播放动作最小闭环（本地 mock）
- [x] `T-S1-004` Android-C++ 桥接最小接线
- [x] `T-S1-005` Emby 连接状态可视化
- [x] `T-S1-006` API17 实机交互回归脚本化清单
- [x] `T-HF-EMBY-001` Emby 信息本地持久化与启动回填
- [x] `T-HF-EMBY-002` Emby UTF-8 解码与曲名样例诊断日志
- [x] `T-HF-PLY-001` MediaPlayer 真实播放 Emby 流
- [x] `T-HF-PLY-002` 推荐优先（Latest）+ 全量回退（Items）策略接线
- [x] `T-HF-LOG-001` 运行日志面板（底部两行 + 全屏）与完整 URL 日志
- [x] `T-HF-EMBY-003` 推荐接口固定为 `Items + Random + Limit=20 + api_key`
- [x] `T-HF-EMBY-004` 列表解析仅接收 `Type=Audio`，避免专辑 ID 入队
- [x] `T-HF-PLY-003` 流请求改为 URL-only（仅 token），失败/超时兜底 download
- [x] `T-UI-001` Android 前台 UI 首版重设计（卡片化 + 主题 + 控件皮肤）
- [x] `T-S2-IA-001` IA v2 文档清洗与单一口径固化
- [x] `T-S2-UI-006` 前台升级为 4 导航壳（Home -> Queue -> Library -> Settings）
- [x] `T-S2-SET-008` 统一服务配置区（Emby + LrcApi）与测试门禁自动保存
- [x] `T-S2-UI-007` Queue/Library 列表单击即播放（禁用长按主路径）
- [x] `T-S2-UI-008` Queue 推荐歌曲（默认20）并替换未播放段
- [x] `T-S2-003` 日志面板交互增强（复制/清理）

## Queue Notes
- 推荐执行顺序: `T-S2-004 -> T-S2-005`（`T-S2-004` 仍需人工实机）。
- `T-S2-SET-008` 与 `T-S2-UI-006` 共享 `MainActivity/activity_main.xml`，建议串行执行避免冲突。
- `T-S2-004` 保持 Blocked，避免把人工事项误放 Ready。
