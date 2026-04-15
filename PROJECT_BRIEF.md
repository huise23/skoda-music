# PROJECT_BRIEF

## Project
- Name: `skoda-music`
- Type: 个人项目 / vibe coding 项目
- One-line description: 车机端音乐播放器，优先支持 Emby，并能被 AI 持续接手迭代。

## Goal
- Primary outcome: 建立可长期演进的车机播放器与 AI 协作工作台。
- Success criteria:
  - 首版完成 Emby 在线播放、推荐展示、方向盘上下曲。
  - 歌词链路支持“嵌入优先，缺失后异步 LrcApi 拉取”。
  - 新会话在 2-3 分钟内可通过核心文档恢复上下文并继续执行。

## Confirmed Scope (Current)
- 平台环境: Android 车机（已确认）。
- 技术栈: Qt/C++（已确认）。
- 协议范围: 仅 Emby 首发（已确认）。
- 推荐策略: 服务端推荐优先（已确认）。
- 网络策略: 纯在线（已确认）。
- 控制策略: 系统媒体键映射方向盘上下曲（已确认）。
- UI方向: 横屏、左侧导航、轻玻璃拟态、低动效、中密度、深浅双主题、暖灰+橙色点缀（已确认）。

## Non-Goals (Current)
- 首版不做多协议（Jellyfin/Subsonic）。
- 首版不做离线下载与完整本地库管理。
- 不要求替换系统内置“听伴”模块；只要求播放器可独立使用，并预留被系统入口调用的能力。

## Users
- Target users: 项目维护者本人（驾驶场景）。
- Key usage scenarios:
  - 日常车机播放 Emby 媒体库音乐。
  - 方向盘快速切歌。
  - 歌词可显示，缺失时自动补全。

## Device Constraints
- CPU: ARMv7 Processor rev 3(v7), 1001MHz。
- RAM: 2G（可用约 0.95G）。
- Model/Firmware: `shoda_mqb`, `3.0.1-R-20210524.9004`, MCU `1.31release`。
- Confirmed Device Runtime: Android `4.2.2`（API 17）, 分辨率 `1024x600`, CPU `autochips ac83xx`。

## Collaboration Rules
- Markdown 文件是项目主上下文载体。
- 每次执行前必须先读取: `PROJECT_BRIEF.md` `PLAN.md` `CURRENT_STATUS.md` `DECISIONS.md` `HANDOFF.md`。
- 每轮只执行一个最小闭环任务。
- 每轮结束必须更新: `CURRENT_STATUS.md` `NEXT_STEPS.md` `HANDOFF.md` `TASK_QUEUE.md`。
- 新对话或上下文压缩后，优先依赖 HANDOFF 恢复状态，不依赖历史聊天记忆。

## Open Items
- 系统首页卡片是否可调用三方播放器: 待确认。
- Android `4.2.2` 实机回归结论: 待确认（代码已下调 `minSdk=17`，待安装验证）。

## References
- Emby: 待补充官方 API 链接。
- LrcApi: https://github.com/HisAtri/LrcApi
