# CURRENT_STATUS

Last Updated: 2026-05-22

## Stage
- 当前阶段: S4（车机后台控制落地）
- 当前主干: `master@9d771f0`

## Latest Confirmed (User)
- 路线锁定为“方案1（Legacy 稳态）”。
- 第一版必须同轮达成：后台服务 + 后台方向盘按键 + 全局浮窗。
- 浮窗策略锁定：播放/暂停均显示；手动关闭后“进应用再切出”再次显示。
- 自动续播体验差，当前口径改为“先移除自动续播”。
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
- 维持播放主链路稳定，并按新口径保持“无自动续播”。
- 并行焦点：EQ MVP 本地收口已完成，下一步转入外部验证留证与队列边界维护。

## Module Execution Progress (Lyrics Midline, 2026-05-22)
- `M-S4-LRC-008` 已完成本地收口（`T-S4-LRC-050/051/052/053`）：
  - `Home` 歌词面板由 `Scroll + 单TextView` 改为“上文/当前/下文”三段容器。
  - 当前行独立使用中线容器展示；上文与下文按上下文行数分发，不再依赖滚动补偿居中。
  - `MainActivity` 已移除 `centerHomeLyricsLine(...)` 旧逻辑，改为基于 `activeIndex` 的三段文本渲染。
- 本地验证:
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-22）。
  - `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md` 已新增 `G. Home Midline Container Regression` 与本地结论快照。
- 当前边界:
  - 需在后续 API17 实机窗口复核字体渲染体感；不阻塞模块本地收口结论。

## Module Execution Progress (Audio Wiring, 2026-05-22)
- 已完成 `M-S4-AUDIO-009` 本地执行链（`T-S4-AUDIO-057/058/059/060`）：
  - `PlaybackEngine` 新增 `audioSessionId()`，`ExoPlaybackEngine` 已透传 session id。
  - `MainActivity` 新增 session 变化观测（prepared + progress tick），并在 `releasePlayer()` 释放会话绑定。
  - 新增 `app/src/main/java/com/skodamusic/app/audio/EqualizerManager.kt`：
    - 支持 `updateConfig / onSessionChanged / onPlayerReleased`
    - 支持 fail-open、单会话熔断、session 重绑与安全释放日志。
  - 设置页已接线 EQ MVP：
    - 开关 + 预设切换 + 配置持久化（`SharedPreferences`）
    - 启动恢复配置并实时回写 `EqualizerManager`
  - 回归清单已补齐 EQ 条目：
    - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 新增 `I. Equalizer MVP Fail-Open` 分组与证据字段。
- 本地验证:
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-22）。
- 当前边界:
  - API17 车机 ROM 实机窗口仍需执行 I 组条目，确认 fail-open 在目标设备稳定成立。

## Module Execution Progress (Audio Planning, 2026-05-19)
- 已完成 `M-S4-AUDIO-009` 规划层里程碑（`T-S4-AUDIO-054/055`）：
  - 新增 `docs/API17_EQUALIZER_MVP_PLAN.md`，明确 API17 Equalizer 可行性、session 生命周期接线、fail-open 硬约束与验收口径。
  - 明确结论：`audiofx` 在车机 ROM 间支持不一致，EQ 必须按“可失败能力”实现；任何失败都不得影响播放主链路。
  - 基于当前代码确认接线前置：`PlaybackEngine` 需新增 `audioSessionId()` 能力，`MainActivity` 需在 session 变化时重绑 EQ。
- 新增执行任务链（实现层）：
  - `T-S4-AUDIO-057`：音频 session 能力透传与生命周期接线。
  - `T-S4-AUDIO-058`：`EqualizerManager` fail-open + 会话熔断。
  - `T-S4-AUDIO-059`：设置页 MVP（开关 + 预设 + 持久化）。
  - `T-S4-AUDIO-060`：本地回归 + API17 实机验证条目补齐。

## Module Execution Progress (Resume + Delete Replan, 2026-05-12)
- 已完成 `T-S4-RESUME-020C`（移除自动续播）：
  - 关闭 `onStart` 自动续播触发入口。
  - 关闭恢复链路的自动起播/自动 seek/续播快照持久化。
  - 启动时发现历史续播快照则清理，避免旧行为残留。
- `T-S4-UI-024A` 已完成复开收口（历史问题已关闭）：
  - 背景: 用户曾反馈“首页仍看不到删除按钮”。
  - 结果: 已通过 `T-S4-UI-024B -> T-S4-UI-024A` 完成入口规则与实现修正。
- 续播链路已改为“先规划后实现”：
  - `T-S4-RESUME-020D/020E` 进入 Ready，先对齐策略与验收口径。
  - `T-S4-RESUME-020B`（服务侧自动续播二阶段）下沉 Deferred，等待 020D/020E 结论。

## Module Execution Progress (UI/Resume Execution, 2026-05-12)
- 已完成 `T-S4-UI-024A` + `T-S4-UI-024B` 联合收口（按用户最终口径）：
  - 首页删除主操作入口固定为“播放卡片右上角删除按钮”。
  - 删除对象固定为当前播放曲目（`currentTrackIndex`），复用既有双确认删除链路。
  - 未调整首页默认歌词/推荐 tab 逻辑（仍保持当前默认）。
- 已完成 `T-S4-RESUME-020D` 代码侧落地：
  - 续播持久化仅保留队列与索引（附带 base/username/savedAt），移除进度与播放态持久化字段。
  - 移除 `ENABLE_AUTO_RESUME_PLAYBACK` 标记与相关分支。
  - 启动恢复后自动播放恢复索引曲目；会话缺失时自动尝试鉴权并在成功后自动续播。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-12）。

## User Verification Update (2026-04-29)
- 用户已确认 `T-S4-CORE-026A/026B` 车机验证通过。
- 用户已确认 `T-S4-CORE-026C-HF-20260429` 主流程通过，但新增 UI 反馈：
  - 浮窗歌名字号疑似未生效；
  - 关闭按钮过小，且需要固定在右上角而非“歌名后”。
- 用户要求 `T-S4-OBS-035/036/037` 进入“调用 API 自行检验”模式，而非仅本地日志判断。
- 用户进一步确认 OBS 验收口径：先查 PostHog 已上报事件流，查不到再回查客户端上报链路。
- 已执行首轮 API 自检探针（CLI 直连 `https://us.i.posthog.com/capture/`），当前环境返回 TLS 握手失败（`SSL_ERROR_SYSCALL`），需在车机或可用网络环境复测确认。

## Module Execution Progress (Validation, 2026-04-29)
- 已完成 `T-S4-VAL-032`（`M-S4-VALID-004`）：
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已从 S1 升级到 S4 验收口径。
  - 已补齐 `Section 4` 风险控制与验收模板（Risk Gates + Evidence Minimum + Final Decision）。
  - 清单已覆盖后台命令矩阵、浮窗策略、熄火/休眠恢复、更新链路、PostHog 关键证据字段。
- 当前影响：
  - `T-S4-REG-022` 的执行入口已标准化，现场可直接按模板回传 PASS/FAIL/Blocker。
  - 当前阶段“本地可直接完成项”已收口，后续主要依赖车机窗口与 CI/实机环境。

## Module Execution Progress (Control + Update Hotfix, 2026-04-29)
- 已按 `M-S4-CONTROL-002` 落地浮窗交互增强（代码完成，待车机验收）：
  - 浮窗歌名字号改为更大层级（当前代码 `17sp`，待车机复测确认体感）。
  - 点击浮窗歌名可拉起应用前台（`MainActivity`，`NEW_TASK + SINGLE_TOP + CLEAR_TOP`）。
  - 浮窗支持拖动，拖动后位置写入本地并在下次显示时恢复（`x/y` 持久化）。
  - 关闭按钮已改为右上角独立锚点并放大触控区（`40dp`），待车机复测确认。
- 已按 `M-S4-UPD-007` 完成更新链路热修：
  - 更新元数据检查改为 GitHub 直连（排除代理链路干扰）。
  - 版本检测支持 pre-release（只要 `non-draft + 有 APK` 即纳入比较）。
  - 更新失败埋点增加结构化字段：`failed_stage/failed_url/attempt_count/attempt_urls`。
- 已从 PostHog 验证到车机失败主因：低版本系统 TLS 证书链信任问题（`GITHUB_RELEASE_EXCEPTION + CertPathValidatorException`）。

## Planning Refresh (2026-04-27)
- 已按 `ai-planning` 重排为模块化执行：`M-S4-CORE-001/CONTROL-002/RESUME-003/VALID-004`。
- 任务粒度从单个 `T-S4-CORE-026` 调整为 `026A/026B/026C + 020B + 032/022/033`，用于区分可代码推进与实机阻塞任务。
- 当前状态：`T-S4-CORE-026A` In Progress，`T-S4-VAL-032` Done。

## Planning Refresh (Update, 2026-04-27)
- 已将“自动检测并更新”纳入当前阶段范围，新增模块 `M-S4-UPD-007`。
- 新增任务链：`T-S4-UPD-040/041/042/043/044`，覆盖版本源解析、冷启动检测、设置手动检测、镜像下载与安装触发闭环。
- 当前状态：
  - `T-S4-UPD-040` Done（已落地 GitHub Releases 解析 + 版本比较规则）。
  - `T-S4-UPD-041/042/043` Done（已落地冷启动检测/设置手动检测/镜像回退下载）。
  - `T-S4-UPD-044` In Progress（已完成安装触发与事件接线，待 CI/实机验证）。

## Module Execution Progress (Update, 2026-04-28)
- 已按 `M-S4-UPD-007` 落地更新模块代码主链路：
  - 新增 `app/src/main/java/com/skodamusic/app/update/AppUpdateManager.kt`。
  - 新增 `GitHub Releases` 拉取与解析：过滤 `draft/prerelease`，选择 APK 资产，按 `versionCode/tag` 比较版本。
  - 冷启动自动检测：`MainActivity` 在首帧后异步触发，命中冷却自动跳过（成功 24h、失败 30min）。
  - 设置页手动检测：新增“检查更新”按钮与状态文本，支持重复触发。
  - 下载策略：镜像优先（`ghfast.top`、`mirror.ghproxy.com`、`ghproxy.net`）并回退官方 GitHub 下载链接。
  - 安装触发：新增 `FileProvider` + `res/xml/file_paths.xml`，API17/24+ 路径兼容处理。
  - 观测接线：新增 `update_check_* / update_download_* / update_install_*` 事件与 runtime log。
- 当前验证状态：
  - `scripts/check_api17_guardrails.sh` 已通过。
  - 本地可使用系统 `gradle` 编译；车机实机验收仍待外部窗口。

## Local Validation (2026-05-08)
- `gradle :app:compileDebugKotlin --no-daemon` 通过。
- `T-S4-UPD-044` 代码侧已收口，剩余仅 CI / 实机验证。
- `T-S4-OBS-035/036/037` 仍依赖 PostHog 在线查询或外部网络环境，不存在新的本地闭环点。

## Module Execution Progress (OBS Query Template, 2026-05-08)
- 已完成 `T-S4-OBS-038-PREP`（本地模板层）：
  - 新增 `docs/POSTHOG_QUERY_EXPORT_TEMPLATE.md`。
  - 固化 Query Checklist（session timeline / error_code 分布 / stage 分布 / 版本对比）。
  - 固化 AI 导出 payload 字段与证据回传模板。
- 当前边界：
  - `T-S4-OBS-038` 已具备执行模板，剩余为在线查询与实机数据导出。

## Planning Refresh (PostHog, 2026-04-27)
- 已新增观测模块 `M-S4-OBS-006`：将 PostHog 作为“结构化事件链路”并行接入，不替代全量原始日志。
- 新增任务链：`T-S4-OBS-034/035/036/037/038/039`，覆盖 schema、API17 兼容上报、关键节点埋点、隐私门禁、查询验证与接入参数确认。
- 当前状态：`T-S4-OBS-034/039` Done，`T-S4-OBS-035/036/037/038` In Progress（外部查询验收阶段）。
- 已新增详细规划文档：`docs/POSTHOG_INSTRUMENTATION_PLAN.md`（含事件预算、节流策略与禁报清单）。

## Module Execution Progress (PostHog, 2026-04-27)
- 已完成 `T-S4-OBS-034`：
  - 新增 `docs/POSTHOG_EVENT_DICTIONARY.md`（核心事件、公共属性、错误码、禁报清单）。
  - 新增 `docs/POSTHOG_CONFIG_CHECKLIST.md`（接入参数确认模板）。
- 已完成 `T-S4-OBS-035-PREP` 与 `T-S4-OBS-037-PREP`：
  - 新增 `PostHogConfigStore`（运行时开关/host/key/environment 读取）。
  - 新增 `PostHogTracker`（API17 兼容、异步上报、fail-open）。
  - 落地节流与预算：默认 10s coalesce、错误 30s、session 预算 `80/150`。
  - 落地隐私门禁：敏感键过滤（password/token/header/response_body）+ 字段长度截断。
- `T-S4-OBS-036` 已启动：
  - `MainActivity` 接入 `app_start/app_ready/foreground/background/play_start/play_success/playback_failed/pause/resume/resume_restore_*`。
  - `PlaybackService` 接入 `background_command_received/background_command_result`。
- 当前边界：
  - `T-S4-OBS-038` 已具备模板与入口，剩余为在线查询执行与证据回传。

## Config Update (2026-04-27)
- 已按用户提供信息内置 PostHog 默认配置：
  - `host=https://us.i.posthog.com`（US Cloud）
  - `project_api_key=phc_wPMBC5C8pCscinCMjqbcFryREP5sKACufHzYiAWxtig6`
  - `project_id=399199`
  - `environment=prod`

## Module Execution Progress (2026-04-27)
- 已推进 `M-S4-CORE-001` 子阶段：
  - `PlaybackControlBus` 新增结构化 `DispatchResult(handled/detail)`，避免仅凭布尔值排障。
  - `PlaybackService` 分发命令后持久化 `action/source/handled/detail` 到 `PlaybackStateStore`。
  - `MainActivity` 增加服务命令结果同步日志（`service cmd result ...`），用于矩阵验证证据收集。
  - 统一前台/硬件键 source 标识（`ui`、`hardware_key`）。
  - 新增 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 作为 `T-S4-CORE-026B` 执行模板。
- 当前状态调整：
  - `T-S4-CORE-026A` 继续 In Progress（链路收口未完全结束）。
  - `T-S4-CORE-026B` 进入 In Progress（模板与观测已就绪，待设备执行矩阵并回填）。

## Module Execution Progress (Hotfix Round, 2026-04-27)
- 已按用户现场故障反馈完成一轮定点修复并推送：
  - 提交：`eb10b46`（`origin/master`）。
  - 问题 1（PostHog 上报是否成功）：`PostHogTracker` 新增 2xx 成功日志 `capture ok event=...`，可直接在 logcat 验证。
  - 问题 2（未自动跳下一曲）：缓存回退下载失败/缓存播放异常分支改为统一走 `handlePlaybackErrorAutoSkip(...)`。
  - 问题 3（Home 后悬浮窗有但无声/卡住）：`PlaybackService` 服务侧音频焦点改为 focus-neutral，不再触发服务侧焦点暂停链路干扰。
- 当前判断：
  - `M-S4-CORE-001` 已完成“故障定点热修”子阶段，待车机窗口完成行为验收后再评估是否可收口 `T-S4-CORE-026A`。
  - `M-S4-OBS-006` 已具备“上报成功可见性”最小联调条件，`T-S4-OBS-038` 仍需实机在线查询完成闭环。

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
  - 后台命令来源已细化并透传（`notification/overlay/media_button/audio_focus`），便于实机日志定位触发链路。
  - `PlaybackService` 音频焦点策略调整为“仅 `AUDIOFOCUS_LOSS` 暂停”，忽略 transient loss，规避车机 1 秒停播回归。
  - Service 侧移除“无活动曲目前置拦截”过滤，避免状态滞后导致命令被误丢弃。
  - 播放状态上报增加“位置增量 + 播放中心跳”策略，提升 Service 侧 `positionMs` 快照时效性。
  - 构建标识调整为左上角大号显示 `#versionCode`（如 `#79`），用于实机快速确认版本。
  - 恢复状态读写已从 `MainActivity` 抽离到 `PlaybackResumeStore`（含 legacy 键迁移），为后续 Service 真源迁移做结构准备。
  - `ACTION_STATE_UPDATE` 现已上报并持久化 `trackId/positionMs`，为 Service 侧状态机接管准备元数据基线。
  - 新增热修：前台场景下 Service 不再管理音频焦点，避免与 Activity ExoPlayer 重复抢焦点导致“播放 1 秒后停住”。
  - 构建号徽标改为全局左上角显示并进一步放大，格式保持 `#versionCode`。
- 当前状态：S4 代码已进入“可车机联调 + 问题定点修复”阶段。

## Known Constraints
- `minSdk=17` 不可突破。
- 车机环境窗口有限，回归必须可中断续跑。
- 仓库无 `gradlew`，需依赖系统 `gradle` 或 CI 进行编译验证。

## Follow-up Backlog (Confirmed)
- 长标题滚动异常修复。
- 均衡器/音效优化。
