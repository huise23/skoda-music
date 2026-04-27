# HANDOFF

Last Updated: 2026-04-27

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@5e6f155`
- 当前阶段: S4 车机后台控制落地（方案1 / Legacy 稳态）

## User-Confirmed Requirements (Must Keep)
- 必须有后台服务，避免车机频繁切回应用。
- 需要全局小浮窗：仅歌名 + 上一曲/播放暂停/下一曲。
- 浮窗策略：播放和暂停都显示；用户手动关闭后，进入应用再切出需再次显示。
- 车机熄火/休眠恢复后自动续播。
- 第一版必须同时满足：后台服务 + 后台方向盘按键 + 浮窗控制。
- 接受前台服务常驻通知（稳定性优先）。
- 命令执行策略固定为“失败即失败”：不记录待执行命令，不做延迟重放/重试。
- 当前并行问题已记录：长标题滚动异常、删除入口需上主屏、均衡器优化。

## Technical Strategy (Confirmed)
- 采用 `ForegroundService + ACTION_MEDIA_BUTTON Receiver + AudioManager/RemoteControlClient`。
- 不把 MediaSession 作为本阶段主链路（保持 API17/车机稳定优先）。
- 命令入口统一：前台按钮 / 通知按钮 / 浮窗按钮 / 方向盘按键全部进入 Service 统一分发。

## Execution Entry
1. `T-S4-CORE-026A`（In Progress）+ `T-S4-CORE-026B`（In Progress）：继续收口命令链路并执行后台命令矩阵回填。
2. `T-S4-OBS-036`（In Progress）+ `T-S4-OBS-037`（In Progress）：继续关键节点埋点并完成实机压测。
3. `T-S4-OBS-038`：执行在线查询验证与 AI 导出模板验收。
4. `T-S4-VAL-032`（Ready）：升级 API17 回归清单并补齐 Section 4 风险控制与验收模板。
5. `T-S4-CORE-026C` + `T-S4-RESUME-020B`：完成浮窗/通知策略与恢复链路闭环。
6. `T-S4-REG-022` -> `T-S4-VAL-033`：车机实机回归后回填证据并更新 context。
7. `T-S4-UI-023/024 + T-S4-AUDIO-025`：保持 Deferred，待 S4 主验收后推进。

## WIP Code Delta (2026-04-26)
- 已创建后台控制模块文件（service/receiver/overlay/state store/command bus）。
- `MainActivity` 已接线：
  - `onStart/onStop` 通知 Service 前后台切换；
  - `render()` 上报当前播放状态给 Service；
  - 实现 `PlaybackControlBus.Controller`，可响应外部命令触发 `Prev/PlayPause/Next`。
- `Manifest` 已新增服务与媒体键接收器声明，权限已补齐。
- 本轮新增稳定化补丁（`T-S4-MEDIA-018`）：
  - `PlaybackControlBus` 使用无缓存即时分发，controller 不可用时直接返回失败。
  - `PlaybackService` 增加音频焦点请求/释放逻辑，降低后台媒体键失效概率。
  - 收敛媒体键注册路径（由 `RemoteControlClientBridge` 统一管理）。
  - `MediaButtonReceiver` 增加 `abortBroadcast()`（ordered broadcast）减少抢占。
  - `MainActivity` 将 `SERVICE_INIT` 后移到 `onStart`，降低启动首帧前负担。
  - 悬浮窗权限引导增加 `resolveActivity` 防护。
- 本轮新增恢复补丁（`T-S4-RESUME-020`）：
  - `MainActivity` 新增恢复状态持久化（队列/索引/进度/播放态 + 账号基线）。
  - 应用启动恢复上次队列和索引；满足条件时自动触发续播。
  - 播放启动后自动恢复上次进度（seek）。
  - 恢复写入加入节流（时间与进度阈值）避免高频写偏慢。
- 本轮新增命令链路增强（`T-S4-ARCH-017` 局部）：
  - `MainActivity` 将 `Prev/PlayPause/Next` 抽为统一动作函数，UI点击/外部命令/硬件键复用同一逻辑。
  - `onPlaybackCommand` 返回真实执行结果（含主线程等待），Service 基于真实结果判断执行状态。
  - Service 命令策略改为“失败即失败”：不落盘、不重放、不维护重试队列。
  - 前台 `UI` 按钮和前台硬件媒体键已回滚为本地直执路径（实机稳定优先）。
  - Service 统一分发路径保留给通知/浮窗/后台外部命令，命令执行层保留 `source/allowToast` 参数。
  - 后台命令来源标记已打通：通知/浮窗/媒体键/音频焦点均带来源进入 Service 分发。
  - 音频焦点策略改为“仅永久失焦暂停”，忽略 transient 失焦，降低车机短时停播概率。
  - Service 移除“无活动曲目前置过滤”，避免状态滞后导致命令被误丢弃。
  - 播放状态上报补充位置心跳（`>=2s` 位置增量或 `>=10s` 播放中心跳），提高 Service 快照位置准确度。
  - 顶部版本标识调整为左上角大号显示 `#versionCode`（如 `#79`）以便现场验包。
  - 恢复状态存储抽离为 `PlaybackResumeStore`（带 legacy 键迁移），`MainActivity` 不再直接操作恢复键。
  - `ACTION_STATE_UPDATE` 扩展 `trackId/positionMs` 上报，并写入 `PlaybackStateStore.Snapshot`。
- 本轮新增热修（2026-04-27）：
  - `PlaybackService` 前台状态不再持有音频焦点，避免与 Activity ExoPlayer 焦点管理冲突。
  - `AUDIOFOCUS_LOSS` 自动暂停仅在后台生效，降低前台“播放 1 秒停住”风险。
  - 构建号徽标迁移到全局根布局左上角并增大字号，便于车机验包。
- 本轮新增模块执行进展（2026-04-27）：
  - `PlaybackControlBus` 增加 `DispatchResult(handled/detail)`，分发失败原因结构化。
  - `PlaybackService` 将每次命令分发结果写入 `PlaybackStateStore`（`action/source/handled/detail`）。
  - `MainActivity` 增加服务命令结果同步日志，支持后台命令矩阵快速留证。
  - 新增 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 作为 `T-S4-CORE-026B` 标准执行模板。
- 本轮新增规划产物（PostHog）：
  - `docs/POSTHOG_INSTRUMENTATION_PLAN.md`：覆盖事件模型、上报架构、验证矩阵、AI 导出模板。
  - 已明确禁报高频低价值事件：播放进度 tick、频繁 buffer 状态、UI redraw、HTTP headers。
- 本轮新增模块执行落地产物（PostHog）：
  - `app/src/main/java/com/skodamusic/app/observability/PostHogConfigStore.kt`
  - `app/src/main/java/com/skodamusic/app/observability/PostHogTracker.kt`
  - `docs/POSTHOG_EVENT_DICTIONARY.md`
  - `docs/POSTHOG_CONFIG_CHECKLIST.md`
  - `MainActivity/PlaybackService` 已接关键事件上报。
  - 已内置默认接入参数（用户提供）：
    - `host=https://us.i.posthog.com`
    - `project_id=399199`
    - `project_api_key=phc_wPMBC5C8pCscinCMjqbcFryREP5sKACufHzYiAWxtig6`
- 待完成：
  - 服务内自动续播恢复完善（`T-S4-RESUME-020` 二阶段）；
  - 车机实测确认后台方向盘按键是否恢复；
  - 车机实测确认浮窗策略与后台通知链路稳定性；
  - 补写 `Section 4：风险控制与验收清单`（用户已明确要该章节）。
  - PostHog 关键事件观测链路落地（schema -> client -> instrumentation -> verification）。

## Environment Notes
- 本地环境无 `gradlew/gradle`，编译型验证依赖 CI 或外部构建环境。
- 车机测试窗口不连续，必须优先保证上下文文档可中断续跑。
