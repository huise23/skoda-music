# HANDOFF

Last Updated: 2026-04-26

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@2d5d315`
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
1. `T-S4-CORE-026`：S4 大闭环执行（播放服务化 + 方向盘后台控制 + 浮窗 + 通知 + 恢复链路）。
2. `T-S4-REG-022`：车机实机回归与证据回填。
3. `T-S4-UI-023/024 + T-S4-AUDIO-025`：主屏删除入口、长标题滚动与音效优化。

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
  - Service 移除“无活动曲目前置过滤”，避免状态滞后导致命令被误丢弃。
  - 播放状态上报补充位置心跳（`>=2s` 位置增量或 `>=10s` 播放中心跳），提高 Service 快照位置准确度。
  - 顶部版本标识显示 `#versionCode`（如 `#79`）以便现场验包。
  - 恢复状态存储抽离为 `PlaybackResumeStore`（带 legacy 键迁移），`MainActivity` 不再直接操作恢复键。
  - `ACTION_STATE_UPDATE` 扩展 `trackId/positionMs` 上报，并写入 `PlaybackStateStore.Snapshot`。
- 待完成：
  - 服务内自动续播恢复完善（`T-S4-RESUME-020` 二阶段）；
  - 车机实测确认后台方向盘按键是否恢复；
  - 车机实测确认浮窗策略与后台通知链路稳定性；
  - 补写 `Section 4：风险控制与验收清单`（用户已明确要该章节）。

## Environment Notes
- 本地环境无 `gradlew/gradle`，编译型验证依赖 CI 或外部构建环境。
- 车机测试窗口不连续，必须优先保证上下文文档可中断续跑。
