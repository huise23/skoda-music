# CURRENT_STATUS

Last Updated: 2026-04-26

## Stage
- 当前阶段: S4（车机后台控制落地）
- 当前主干: `master@2d5d315`

## Latest Confirmed (User)
- 路线锁定为“方案1（Legacy 稳态）”。
- 第一版必须同轮达成：后台服务 + 后台方向盘按键 + 全局浮窗。
- 浮窗策略锁定：播放/暂停均显示；手动关闭后“进应用再切出”再次显示。
- 熄火/休眠恢复后自动续播为强需求。
- 接受前台服务常驻通知。

## Already Completed (Baseline)
- API17 红线与构建护栏已建立（含 CI guardrails）。
- 主播放链路为 download-only，队列尾补充与自动切歌逻辑已在主干。
- 下载缓存统计/清理与设置页入口已落地。
- 最近车机稳定性修复已合入：
  - 移除不稳定媒体会话实现（`ff52815`）。
  - 增加 API17 违规守卫（`8afea55`）。
  - 启动白屏感知优化（`6e6206c`、`2d5d315`）。

## Current Focus
- 执行 `T-S4-CORE-026`（S4 大闭环）：后台播放服务、方向盘按键、通知与浮窗控制链路稳定化。
- 推进播放真源迁移与恢复闭环：`MainActivity -> PlaybackService`，并完成熄火/休眠自动续播二阶段验证。

## Implementation Progress (2026-04-26)
- 已新增后台控制基础模块：
  - `app/src/main/java/com/skodamusic/app/playback/PlaybackService.kt`
  - `app/src/main/java/com/skodamusic/app/playback/MediaButtonReceiver.kt`
  - `app/src/main/java/com/skodamusic/app/playback/PlaybackActions.kt`
  - `app/src/main/java/com/skodamusic/app/playback/PlaybackControlBus.kt`
  - `app/src/main/java/com/skodamusic/app/playback/PlaybackStateStore.kt`
  - `app/src/main/java/com/skodamusic/app/overlay/OverlayController.kt`
- 已完成 `MainActivity` 最小接线：前后台通知 Service、播放状态上报、接收外部控制命令（方向盘/通知/浮窗统一入口）。
- 已完成 `AndroidManifest` 基础声明：前台服务、媒体键接收器、悬浮窗/前台服务权限。
- 已完成 `T-S4-MEDIA-018` 第一轮稳定化补丁（待车机验证）：
  - `PlaybackControlBus` 采用无缓存即时分发策略，controller 不可用时单次失败即返回失败。
  - `PlaybackService` 增加音频焦点请求/释放，提升后台媒体键路由命中概率。
  - 移除 Service 与 RCC 的重复媒体键注册路径，收敛到 `RemoteControlClientBridge`。
  - `MediaButtonReceiver` 对有序广播执行 `abortBroadcast()`，减少被其他接收器抢占/重复分发风险。
  - `MainActivity` 将 `ACTION_SERVICE_INIT` 移到 `onStart`，减少 `onCreate` 首帧前负担。
  - 悬浮窗权限引导增加 `resolveActivity` 防护，避免设备缺失设置页时异常跳转。
- 已完成 `T-S4-RESUME-020` 第一轮落地（待车机验证）：
  - 新增播放恢复持久化：队列、当前索引、进度、播放态、恢复基线账号信息。
  - 应用启动时恢复上次队列与索引，并尝试用缓存 token 还原会话。
  - 满足条件时自动续播；播放后自动恢复上次进度 seek。
  - 引入恢复状态写入节流，避免高频 `SharedPreferences` 写入。
- 已完成 `T-S4-ARCH-017` 局部增强（待车机验证）：
  - 外部命令不再依赖 `performClick()`，改为统一播放控制函数（UI/外部命令/硬件键共用同一执行路径）。
  - `onPlaybackCommand` 返回真实执行结果（主线程同步等待），Service 侧按结果判断是否执行成功。
  - `PlaybackService` 命令链路改为“失败即失败”：不记录待执行命令、不重放、不做延迟重试。
  - 前台 `UI` 按钮与硬件媒体键已回滚为本地直执（稳定优先），Service 路径保留给通知/浮窗/后台外部命令。
  - 执行层保留命令上下文参数（`source/allowToast`），由 Service 调度时统一传入。
  - Service 侧移除“无活动曲目前置拦截”过滤，避免状态滞后导致命令被误丢弃。
  - 播放状态上报增加“位置增量 + 播放中心跳”策略，提升 Service 侧 `positionMs` 快照时效性。
  - 右上角构建标识改为显示 `#versionCode`（如 `#79`），用于实机快速确认版本。
  - 恢复状态读写已从 `MainActivity` 抽离到 `PlaybackResumeStore`（含 legacy 键迁移），为后续 Service 真源迁移做结构准备。
  - `ACTION_STATE_UPDATE` 现已上报并持久化 `trackId/positionMs`，为 Service 侧状态机接管准备元数据基线。
- 当前状态：S4 代码已进入“可车机联调 + 问题定点修复”阶段。

## Known Constraints
- `minSdk=17` 不可突破。
- 车机环境窗口有限，回归必须可中断续跑。
- 本地无 `gradle/gradlew`，编译型验证需依赖 CI/外部构建环境。

## Follow-up Backlog (Confirmed)
- 长标题滚动异常修复。
- 删除入口迁移到主屏。
- 均衡器/音效优化。
