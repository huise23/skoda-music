# HANDOFF

Last Updated: 2026-06-01

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@7cf36f3`
- 当前阶段: S4 车机后台控制落地（方案1 / Legacy 稳态）

## User-Confirmed Requirements (Must Keep)
- 必须有后台服务，避免车机频繁切回应用。
- 需要全局小浮窗：仅歌名 + 上一曲/播放暂停/下一曲。
- 浮窗策略：播放和暂停都显示；用户手动关闭后，进入应用再切出需再次显示。
- 自动续播功能先移除（体验反馈差）。
- 第一版必须同时满足：后台服务 + 后台方向盘按键 + 浮窗控制。
- 接受前台服务常驻通知（稳定性优先）。
- 命令执行策略固定为“失败即失败”：不记录待执行命令，不做延迟重放/重试。
- 音效需求已更新：系统 EQ 继承与 Android `audiofx.Equalizer` 固定 10 段直写均不再作为主线；新方向为应用内保真 DSP 音效，引擎失败必须旁路原声。

## Latest Delta (Execution Refresh, 2026-06-01)
- Full Plan Mode 已完成本地实现与验证：
  - 新增 `app/src/main/java/com/skodamusic/app/audio/dsp/HiFiDspMode.kt`。
  - 新增 `HiFiDspController.kt`。
  - 新增 `HiFiAudioProcessor.kt`。
  - 新增 `HiFiRenderersFactory.kt`。
  - `ExoPlaybackEngine` 通过自定义 renderers factory 注入 DSP processor。
  - `MainActivity` 设置页/子页改为“保真音效 / 音质模式”。
  - 新状态键：`sound_effect_enabled / sound_effect_mode`。
  - 旧 `EqualizerManager` 代码保留，但默认播放路径不再触发 Android `audiofx` 写入。
- 本地验证：
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。

## Execution Entry
1. 当前无本地 Ready 任务。
2. 下一步为 `T-S4-AUDIO-087`：API17 实机听感与稳定性验证。
3. 若用户要求推送，直接提交并推送当前实现。
4. 实机反馈后再进入调音/修复；不要在无设备反馈前继续扩大 DSP 功能。

## Device Validation Focus
- 设置页“保真音效”开关与子页入口可用。
- 子页显示 `原声 / 保真 / 清晰 / 动感 / 柔和`。
- `原声` 接近关闭音效；`保真` 更清楚不糊；其它模式有方向差异但不过度。
- 连续播放 30 分钟无卡顿、爆音、破音、闪退。
- 切歌、seek、暂停恢复后模式保持一致。
- 日志包含 `hifi-dsp config/format/active/bypass`。

## Historical Context
## Latest Delta (Requirement Refresh, 2026-05-29)
- 实机结论：系统 EQ 继承不可用，应用内 EQ 重新成为主线。
- 用户确认固定 10 段试验口径：
  - UI 固定常见 10 段与常见中文预设，不再读取/展示 ROM 内置 bands/presets。
  - 滑杆必须接近系统 EQ 截图，细轨道、窄滑块，不使用当前粗滑块。
  - 底层先强制写 Android Equalizer band `0..9`，用于实机验证。
  - band 写入必须逐段 try/catch，不能一段失败就整体不可用。
  - 不预先禁用 band；只有用户实际改动/预设写入触发底层真实异常后才提示。
  - 真实成功的段继续生效，真实失败段仅跳过本次写入；失败段不保存到持久化配置。
- 当前入口：先执行 `ai-planning`，为固定 10 段 UI、预设曲线、逐 band 写入结果模型、安全持久化拆任务。

## Latest Delta (EQ Full-screen Redesign, 2026-05-26)
- 用户确认 EQ 子页重做方向：
  - 全屏横屏子页。
  - 左侧动态 bands 滑杆。
  - 右侧 preset 按钮网格。
  - 保持玻璃态风格，但重做颜色配比和文字层级。
  - fail-open 提示不常驻，仅回退/降级时显示。
- 本轮执行结果：
  - `T-S4-AUDIO-065~068` 已完成本地闭环。
  - 本地编译通过：`gradle :app:compileDebugKotlin --no-daemon`。
  - 下一步转入 API17 实机观察点与证据回填。

## Latest Delta (EQ Visual Polish V2, 2026-05-26)
- 在既有全屏 EQ 子页上完成视觉二次收口（不改音频能力层）：
  - 新增左右分区玻璃面板与页头状态徽标。
  - 左侧动态 bands 行改为卡片化，滑杆统一使用玻璃轨道/拇指样式。
  - 右侧 preset 按钮增加激活/未激活双态视觉，提升横屏触控辨识度。
  - 开启/关闭/回退状态文案按颜色分层展示。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-26）。

## Latest Delta (Requirement Refresh, 2026-05-27)
- 用户确认“仅关闭应用 EQ 不等于系统 EQ 接管”。
- 新需求口径已收敛（Option B）：
  - 先尝试系统 EQ 接线；
  - 系统不可用时 toast 提示；
  - 允许手动开启应用内 EQ；
  - 设置页暂不重构，先不改入口信息架构。
- 已更新 `.ai/context/SCOPE.md`，等待 planning 产出任务拆分。

## Latest Delta (Planning Refresh, 2026-05-27)
- 已完成 `ai-planning` 回写：
  - `PLAN` 切换到“系统 EQ 继承接线”阶段。
  - 新增模块 `M-S4-AUDIO-013`。
  - 新增任务链 `T-S4-AUDIO-069~072` 并将 `069` 设为 Ready。
- 当前执行建议：下一轮直接用 `ai-execution` 进入 Module Mode 执行 `M-S4-AUDIO-013`。

## Latest Delta (Execution Refresh, 2026-05-27)
- `M-S4-AUDIO-013` 已完成本地闭环（`T-S4-AUDIO-069~072`）：
  - 系统 EQ 会话 open/close 接线已落地。
  - 启动默认系统优先；系统不可用时提示并允许手动应用 EQ 兜底。
  - 设置页结构保持不变，文案与反馈链路已收口。
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已新增 I7~I9 观察项。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-27）。
- 下一步入口：
  - `T-S4-REG-022` -> `T-S4-VAL-033`（API17 实机留证）。

## Historical Delta (EQ UI Planning, 2026-05-25)
- 已完成 `M-S4-AUDIO-011`（`T-S4-AUDIO-061~064`）：
  - 新增 `docs/API17_EQUALIZER_UI_PLAN.md`（IA、状态矩阵、低保真线框、实现任务化）。
  - 规划链路已收口，`061~064` 全部转 Done。
- 下一步入口：
  - 切换到 `M-S4-AUDIO-012`，执行 `T-S4-AUDIO-065~068` 实现链。

## Latest Delta (Module Execution, 2026-05-22)
- 已完成歌词模块本地收口（`T-S4-LRC-050/051/052/053`）：
  - `activity_main.xml` 歌词区已改为三段容器：上文/当前/下文。
  - `MainActivity` 歌词渲染已从整段富文本滚动切换为按 `activeIndex` 分发三段文本。
  - 旧 `centerHomeLyricsLine` 滚动居中逻辑已移除。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-22）。
  - `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md` 已补充 `G. Home Midline Container Regression` 和本地结论快照。
- 下一步入口：
  - EQ 实现链已完成，下一步切回边界维护 + 外部验证留证。

## Latest Delta (Audio Wiring, 2026-05-22)
- 已完成 `T-S4-AUDIO-057`：
  - `PlaybackEngine` 新增 `audioSessionId()`，`ExoPlaybackEngine` 已透传。
  - `MainActivity` 已接入 session 变化观测（prepared + progress tick）。
- 已完成 `T-S4-AUDIO-058`：
  - 新增 `EqualizerManager`（fail-open + 单会话熔断 + session 重绑 + 安全释放）。
  - `releasePlayer()` 已接线调用 `onPlayerReleased`，避免旧 session 持有。
- 已完成 `T-S4-AUDIO-059`：
  - 设置页新增 EQ 开关/预设切换与本地持久化。
  - 启动时恢复 EQ 配置并回写到 `EqualizerManager`。
- 已完成 `T-S4-AUDIO-060`：
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 新增 `I. Equalizer MVP Fail-Open` 条目与证据字段。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-22）。
- 下一步入口：
  - 等待 API17 实机窗口执行 EQ I 组条目并回写证据。

## Latest Delta (2026-05-08)
- 已完成 `T-S4-OBS-038` 本地模板准备：
  - 新增 `docs/POSTHOG_QUERY_EXPORT_TEMPLATE.md`，固定查询清单、导出字段和证据模板。
- 当前执行边界：
  - OBS 仍需在线 PostHog 数据窗口完成最终验收；
  - UPD 仍需 CI/实机完成安装触发闭环验证。

## Latest Delta (2026-05-12)
- 用户新增反馈与执行口径：
  - 最新版首页仍看不到删除按钮，要求先重规划删除入口位置与功能，再实施修正。
  - 续播相关先做口径规划，不立即恢复自动续播实现。
- 本轮规划调整：
  - `T-S4-UI-024A` 从已完成改为复开（历史动作，现已完成复开收口）。
  - 新增优先前置 `T-S4-UI-024B`（删除入口规则规划），并设为 `024A` 依赖。
  - `T-S4-RESUME-020D/020E` 进入 Ready；`T-S4-RESUME-020B` 下沉 Deferred。

## Latest Delta (Execution, 2026-05-12)
- 已按用户确认口径执行落地：
  - 首页删除主入口固定到播放卡片右上角按钮，且始终删除当前播放曲目。
  - 保持首页默认歌词/推荐 tab 不变（未调整默认切换）。
  - 续播持久化改为仅队列+索引（附 base/username/savedAt），移除进度/播放态持久化与 `ENABLE_AUTO_RESUME_PLAYBACK` 开关。
  - 启动恢复后自动播放恢复索引曲目；会话缺失时自动鉴权重试后续播。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
- 当前剩余：
  - `T-S4-RESUME-020E` 文档与验收清单回写；
  - 车机侧回归验证删除入口触控与续播自动鉴权行为。

## Latest Delta (2026-04-29)
- 用户验证状态更新：
  - `T-S4-CORE-026A/026B` 已车机验证通过。
  - `T-S4-CORE-026C-HF-20260429` 主流程通过，但新增 UI 问题反馈（字号体感与关闭按钮位置/大小）。
- 浮窗交互增强已落地（待车机验收）：
  - 歌名字号已上调为 `17sp`（本轮代码修正，待复测）。
  - 点击歌名拉起应用前台。
  - 拖动位置持久化，重进后台后恢复。
  - 关闭按钮改为右上角独立锚点，触控区放大到 `40dp`（本轮代码修正，待复测）。
- 更新链路诊断能力增强：
  - `update_check_failed` 现可回传 `failed_stage/failed_url/attempt_urls`。
  - 更新元数据检查已切换为 GitHub 直连；版本比较已支持 pre-release。
- OBS API 自检现状：
  - CLI 首轮直连 `https://us.i.posthog.com/capture/` 失败（`SSL_ERROR_SYSCALL`）。
  - OBS 验收口径已调整为“PostHog 查询优先”；仅在查询异常时才回查客户端与网络链路。
- 验收入口补齐：
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已升级为 S4 版本。
  - 新增 `Section 4`：`Risk Gates + Evidence Minimum + Acceptance Decision`。
- 现场问题结论：
  - 最近失败主因集中在低版本车机 TLS 信任链不兼容（`GITHUB_RELEASE_EXCEPTION`）。

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
- 本轮新增用户故障定点热修（2026-04-27，`eb10b46`）：
  - `PostHogTracker` 增加成功上报日志：`capture ok event=... code=2xx`，用于验证事件是否真实入库。
  - `MainActivity.downloadAndPlayTrack` 在缓存下载失败/缓存播放异常时改为自动切下一曲，不再停在失败曲目。
  - `PlaybackService` 服务侧音频焦点改为 focus-neutral，并忽略服务侧焦点暂停链路，降低 Home 后悬浮窗无声/卡住概率。
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
- 本轮新增模块执行落地产物（Update, 2026-04-28）：
  - `app/src/main/java/com/skodamusic/app/update/AppUpdateManager.kt`
  - `app/src/main/res/xml/file_paths.xml`
  - `AndroidManifest.xml` 新增 `FileProvider`
  - `activity_main.xml` 设置页新增 `btn_check_update` + `update_status_value`
  - `strings.xml` 新增更新检测/下载/安装状态文案
  - `MainActivity` 已接入：
    - 冷启动自动检测与节流（成功 24h / 失败 30min）
    - 设置页手动检查 + “有新版本时按钮变为下载并安装”
    - 镜像优先下载与官方回退
    - 安装触发与 `update_check_* / update_download_* / update_install_*` PostHog 事件
- 待完成：
  - 更新链路 CI 编译与 API17/车机实机验收（当前环境无 `adb`）；
  - 服务内自动续播恢复完善（`T-S4-RESUME-020` 二阶段）；
  - 车机实测确认后台方向盘按键是否恢复；
  - 车机实测确认浮窗策略与后台通知链路稳定性；
  - PostHog 关键事件观测链路落地（schema -> client -> instrumentation -> verification）。

## Environment Notes
- 本地仓库无 `gradlew`，可使用系统 `gradle` 编译；车机验证仍依赖外部环境。
- 车机测试窗口不连续，必须优先保证上下文文档可中断续跑。
## Latest Delta (Audio Planning, 2026-05-19)
- 已完成 `T-S4-AUDIO-054/055` 规划闭环：
  - 新增 `docs/API17_EQUALIZER_MVP_PLAN.md`。
  - 明确技术结论：API17 车机 ROM 的 `audiofx` 支持不一致，EQ 必须 fail-open。
  - 明确接线前置：`PlaybackEngine` 增加 `audioSessionId()`，基于 session 做创建/重绑/释放。
- 任务队列调整：
  - `T-S4-AUDIO-054/055` -> Done。
  - 新增 `T-S4-AUDIO-057/058/059/060` 进入后续实现链。

## Latest Delta (Planning Refresh, 2026-05-29)
- 已完成固定 10 段应用内 EQ 的 planning 回写。
- 新增模块 `M-S4-AUDIO-014`：应用内 EQ 固定 10 段直写试验。
- 新任务链：
  - `T-S4-AUDIO-073` 固定 10 段模型、预设曲线与结果契约。
  - `T-S4-AUDIO-074` EqualizerManager 逐 band 10 段直写与 fail-open 结果返回。
  - `T-S4-AUDIO-075` EQ 配置安全持久化与提交顺序改造。
  - `T-S4-AUDIO-076` EQ 子页固定 10 段系统式窄滑杆 UI 重做。
  - `T-S4-AUDIO-077` 预设/自定义/真实失败提示联动收口。
  - `T-S4-AUDIO-078` 固定 10 段 EQ 本地验证与 API17 实机清单更新。
- 下一轮建议：直接用 `$ai-execution` Module Mode 执行 `M-S4-AUDIO-014`。


## Latest Delta (Execution Refresh, 2026-05-29)
- `M-S4-AUDIO-014` 已完成 Full Plan 本地执行。
- 代码结果：
  - 固定 10 段 EQ 模型与中文预设已落地。
  - `EqualizerManager` 改为 band `0..9` 逐段直写并返回逐段结果。
  - UI 操作改为先应用后保存；真实失败 band 不落盘。
  - EQ 子页改为固定 10 段窄竖滑杆 + 右侧固定中文预设。
  - 真实失败提示基于底层异常汇总频点。
- 验证：
  - `gradle :app:assembleDebug` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `git diff --check` 通过。
- 下一步：
  - 推送版本后执行 API17 实机验证；回传成功/失败 band 列表，再处理 `T-S4-AUDIO-079`。
