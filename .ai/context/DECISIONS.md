# DECISIONS

Last Updated: 2026-04-15 22:41:00

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

## 2026-04-15 - Emby 登录信息持久化（含密码）
- 决策: 按用户明确要求，Android 前台将 Emby `BaseURL/用户名/密码` 持久化到 `SharedPreferences` 并在启动自动回填。
- 影响: 提升实机反复测试效率，但密码为明文持久化，后续如需上线应切换加密存储方案。

## 2026-04-15 - Emby 响应统一 UTF-8 解码
- 决策: 读取 Emby HTTP 响应时显式使用 UTF-8，避免设备默认字符集导致中文乱码。
- 影响: “非中文曲名”排障时可优先判断服务端元数据本身，而非客户端默认编码。

## 2026-04-15 - Android 最小真实播放采用 MediaPlayer
- 决策: 在 API 17 兼容前提下，前台真实播放链路先采用系统 `MediaPlayer` + Emby stream URL。
- 影响: 播放能力从“仅状态切换”升级为“可实际出声”，但暂未引入完整音频焦点/后台会话策略。

## 2026-04-15 - Emby 曲目拉取策略调整为推荐优先
- 决策: 曲目列表优先请求 `Items/Latest`（音频推荐），失败或为空时回退 `Items` 全量查询。
- 影响: 当前曲目更贴近推荐场景，且可通过来源标记快速判断服务端返回路径。

## 2026-04-15 - API17 实机回归口径文档化
- 决策: 实机回归统一以 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 为执行与回传模板。
- 影响: 后续人工验证将有固定条目与结果结构，便于快速定位回归失败点与阻塞项。
