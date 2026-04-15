# DECISIONS

Last Updated: 2026-04-15 19:10:40

## Confirmed Collaboration Decisions
- 决策: AI 协作上下文统一写入 `.ai/context/`。
- 决策: bootstrap 最小工作集仅保留 4 个文件：
  - `PROJECT_BRIEF.md`
  - `CURRENT_STATUS.md`
  - `DECISIONS.md`
  - `HANDOFF.md`
- 决策: 其余任务型上下文文件在 bootstrap 阶段不保留，后续按需要再扩展。

## Confirmed Technical Decisions
- 决策: 业务核心继续使用 C++ 模块，Android 壳承担打包与前台入口。
- 决策: 当前目标设备基线为 Android `4.2.2`（API 17），`minSdk` 固定为 `17`。
- 决策: CI `minSdk` 校验接受 `aapt` 的两种输出格式：`sdkVersion:'17'` 或 `minSdkVersion:'17'`。
- 决策: 首版协议维持 Emby only，网络策略维持纯在线。

## Pending (Not Yet Confirmed)
- 待确认: 系统首页卡片第三方入口能力。
- 待确认: 歌词失败回退策略最终口径。
- 待确认: Android 前台从占位页切换到播放交互页的分阶段范围。

## 2026-04-15 - Planning Scope Assumption
- 决策: 当前 `SCOPE.md` 缺失，规划阶段以 `PROJECT_BRIEF + CURRENT_STATUS + HANDOFF + 最新用户指令` 作为阶段范围输入。
- 影响: 若后续补充 `SCOPE.md` 与当前计划冲突，以显式 `SCOPE.md` 为准并重排任务队列。

## 2026-04-15 - Android 前台移除 mock 曲目源
- 决策: Android 前台曲目来源不再使用本地 mock 列表，改为通过 Emby 实时请求拉取。
- 影响: 必须提供 Emby `BaseURL/UserId/Token` 才能启用播放与下一曲交互；后续优先推进 Android-C++ 桥接。

## 2026-04-15 - Android 最小桥接采用 JNI + playback_queue
- 决策: Android 前台通过 `NativePlaybackBridge`（JNI）直接调用 `src/playback/playback_queue` 实现最小接线。
- 影响: Android 构建新增 CMake/NDK 依赖，CI 需安装匹配 NDK（`26.3.11579264`）。
