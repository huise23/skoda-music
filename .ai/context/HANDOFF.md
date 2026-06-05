# HANDOFF

Last Updated: 2026-06-04

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@edb006e`（已推送 `Complete S5 corrective playback validation`）
- 当前阶段: S5 纠偏 - Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split
- 当前执行入口: `T-S5-KG-119` 与 `T-S5-OBS-120` 已完成；下一步恢复 API17 实机 A~N 回归并回传 QR/PostHog 证据。

## User-Confirmed Requirements (Must Keep)
- API17 / Android 4.2.2 / AC83xx / 1024x600 横屏为硬约束。
- 后台服务、后台方向盘按键、全局浮窗和前台服务通知等既有稳定性口径继续保留。
- 当前音效主线为应用内保真 DSP；native DSP 本地链已完成，实机验证另行阻塞跟踪。
- Native DSP 播放页用播放/暂停按钮边框显示 runtime 状态，刷新低频，不按 audio frame 更新 UI。
- 酷狗相关实现全部参考 `KugouMusic.NET/`；没有依据就停下问用户。
- 默认进入酷狗模式，但必须是纯酷狗播放，不叠加 Emby 播放/队列/resume/refresh。
- 酷狗普通歌曲队列参考 `.NET PlaybackQueueManager`，不是 Emby 队列。
- 酷狗电台/Radio 是独立 session，同一电台持续播放，下一曲由电台内部推进。
- 酷狗 API 不应要求用户提供地址。
- 后续新增功能必须有足够 PostHog/运行时诊断日志；日志与事件属性必须过滤 token、session、手机号、验证码、完整 URL query、认证 header 等敏感信息。
- `MainActivity.kt` 过大问题当前必须优化，不再只是记录债务。
- “保持单 Activity 外壳”不是长期硬约束；允许在 API17 兼容且不破坏左侧一级快速切换的前提下，后续拆 Activity、页面壳、Fragment、Controller 或 Binder。
- 点赞后播放缓存上传到 Emby 入库继续阻塞；本地缓存设计目标最大不超过 `100MB`。

## Latest Delta (T-S5-KG-119 + T-S5-OBS-120 Done, 2026-06-05)
- 已完成:
  - QR refresh crash hotfix：二维码图片 URL/request 构造异常 fail-soft，刷新/登出/停止后的旧异步回调被 generation guard 忽略。
  - QR 失败态可恢复：二维码 key/image 失败时显示失败/可重试，不启动无图轮询。
  - QR auth 事件：`kugou_qr_refresh_start/success/failed`、`kugou_qr_poll_failed`、`kugou_qr_login_success`、`kugou_session_validation_success/failed`。
  - S5 观测补齐：`kugou_content_load_success/failed`、`kugou_queue_start`、`kugou_radio_session_start`。
  - 新增 `docs/S5_OBSERVABILITY_COVERAGE.md`，同步 `docs/POSTHOG_EVENT_DICTIONARY.md` 与 API17 回归清单。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。
- 下一步:
  - 手机/API17 设备执行 A~N 回归。
  - 重点验证 J10/J11：连续刷新二维码 10 次、弱网/异常/切后台不崩溃；PostHog/logcat 无敏感字段。

## Latest Delta (T-S5-VAL-113 Done, 2026-06-04)
- 已完成:
  - `T-S5-VAL-113` S5 纠偏 API17 回归清单与本地验证。
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 增加 M 组 Kugou playback/queue/radio，N 组 Main split/page shell。
  - I 组增加 DSP 边框颜色语义和 direct-buffer bridge 验证。
  - 追加 T-S5-VAL-113 本地验证快照。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。
- 后续:
  - API17 目标车机执行 A~N 分组，重点 M 组 Radio session、I14/I15 DSP 边框语义、N 组导航/返回键。
  - 回传 device report、日志片段、截图/视频证据。

## Previous Delta (T-S5-MAIN-116 Done, 2026-06-04)
- 已完成:
  - `T-S5-MAIN-116` 页面壳拆分试点评估。
  - 新增 `docs/PAGE_SHELL_SPLIT_EVALUATION.md`。
  - 结论：当前阶段暂缓独立 Activity；Fragment 作为未来试点，但先抽低耦合 Binder。
  - 推荐下一轮 MainActivity 治理顺序：`RuntimeLogBinder` -> `EqualizerPageBinder` -> 用 Binder 包装 Fragment 试点。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。
- 后续状态:
  - `T-S5-VAL-113` 已完成；当前转入 API17 实机 A~N 回归执行与证据回传。

## Previous Delta (T-S4-AUDIO-097 Done, 2026-06-04)
- 已完成:
  - `T-S4-AUDIO-097` DSP 播放按钮持续红框诊断与修正。
  - `HiFiAudioProcessor` 对 API17/ExoPlayer heap `ByteBuffer` 增加 direct scratch bridge。
  - non-direct buffer 不再直接发布 `FAIL_OPEN`，避免正常播放路径常态红框。
  - 真实 unsupported/native-not-ready/native-process-error/native bypass/error 仍保持 fail-open 红色诊断。
  - 新日志：`hifi-dsp native direct-buffer bridge input=<...> output=<...>`。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。
- 后续状态:
  - `T-S5-MAIN-116` 已完成。
  - AC83xx 实机仍需观察日志；若仍红色，应按 `native status=bypass/error` 或 `bypass reason=...` 继续定位。

## Previous Delta (T-S5-PLAY-112 Done, 2026-06-04)
- 已完成:
  - `T-S5-PLAY-112` Radio/FM session。
  - 新增 `app/src/main/java/com/skodamusic/app/playback/KugouRadioSessionManager.kt`。
  - radio session 维护 current/upcoming/history；next 推进 upcoming，previous 从 history 回退。
  - radio active 时 `previous/next/completion` 先走 radio session，不走普通 Kugou queue，也不走 Emby queue。
  - `KugouContentBinder` radio 点击路径会启动 radio session，普通 Kugou queue 与 radio session 互斥。
  - 队列页 radio active 时显示“酷狗电台队列”（current + upcoming）。
- `.NET` 依据:
  - `PersonalFmService.cs`
  - `PlayerViewModel.PersonalFm.cs`
  - `PlayerViewModel.Queue.cs`
- 范围说明:
  - 当前 Android 仍用已加载的旧 WebApi `/fm/songs` 列表建立最小 radio session；未猜测 direct Personal FM raw endpoint。
  - 未实现 action report/dislike。
  - 未修改 `KugouMusic.NET/`。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking。
- 后续状态:
  - `T-S4-AUDIO-097` 与 `T-S5-MAIN-116` 已完成。
  - `MainActivity.kt` 当前约 5709 行，后续 `RuntimeLogBinder` / `EqualizerPageBinder` 仍需继续压降。

## Previous Delta (T-S5-MAIN-115 Done, 2026-06-04)
- 已完成:
  - `T-S5-MAIN-115` 内容页 Binder 拆分。
  - 新增 `app/src/main/java/com/skodamusic/app/ui/KugouContentRenderer.kt`。
  - 新增 `app/src/main/java/com/skodamusic/app/ui/KugouContentBinder.kt`。
  - 推荐歌曲、Radio 页面、发现页、普通 Kugou 队列页 UI 行构造、请求入口、loading/list/selected state 从 `MainActivity` 迁出。
  - `MainActivity` 只保留内容页委托和播放/点赞高层回调。
  - `MainActivity.kt` 约 6118 -> 5632 行。
- 后续状态:
  - `T-S5-PLAY-112` 已完成独立 Radio/FM session。
  - `MainActivity.kt` 仍超过 entry red-line；`T-S5-MAIN-116` 已完成页面壳/低耦合页面拆分评估。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。

## Latest Delta (T-S5-PLAY-111 Execution, 2026-06-04)
- 已完成:
  - `T-S5-PLAY-111` Done: 普通酷狗歌曲队列。
  - 新增 `app/src/main/java/com/skodamusic/app/playback/KugouPlaybackQueueManager.kt`。
  - 推荐歌曲和发现歌单歌曲点击会按上下文建立独立 Kugou queue，不写入 Emby `loadedTracks/currentTrackIndex`。
  - `previous/next` 在 Kugou active 时走普通 Kugou queue 循环；无普通 queue 时仍保持单曲边界。
  - Kugou 普通队列播放完成后按 queue next 推进。
  - 队列页可显示“酷狗普通队列”，并禁用 Emby 源文件删除按钮，避免误删。
  - Radio 歌曲点击路径已由后续 `T-S5-PLAY-112` 接管为独立 Radio/FM session。
- `.NET` 依据:
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；`MainActivity.kt` 约 6118 行。
- 风险/下一手:
  - 普通队列接线一度让入口文件增大；`T-S5-MAIN-115` 已完成内容页拆分，当前下一手是 `T-S5-PLAY-112` Radio/FM session。

## Latest Delta (T-S5-PLAY-110 Execution, 2026-06-04)
- 已完成:
  - `T-S5-PLAY-110` Done: 纯酷狗 playback/session 边界。
  - 新增 `app/src/main/java/com/skodamusic/app/playback/SourcePlaybackSession.kt`，用 `SourcePlaybackSnapshot` 表达当前 source 的 track id/title/artist/duration。
  - Kugou 播放请求通过本地前置校验后标记为 Kugou active；失败、暂停和单曲完成后不回落到 Emby 队列。
  - Emby 显式播放时切回 Emby active。
  - 首页 Now Playing、lyrics artist、SeekBar duration、前台 service state、progress refresh 和 resume persistence 已按 source session 分流。
  - Kugou active 时 previous/next 不触发 Emby `loadedTracks/currentTrackIndex`。
- 后续状态:
  - 普通酷狗队列已在 `T-S5-PLAY-111` 完成。
  - Radio/FM session 已在 `T-S5-PLAY-112` 完成，参考 `.NET PersonalFmService`。
  - 旧 WebApi Base URL 路径不得恢复；内容/play URL direct migration 继续逐项迁移。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败。

## Latest Delta (T-S5-KG-118 Execution, 2026-06-04)
- 已完成:
  - `T-S5-KG-118` Done: QR success 后的 direct device init / token refresh / session validation。
  - 新增 `KugouDirectCrypto.kt`：按 `.NET KgCrypto` 移植 AES-CBC、Playlist AES、RSA no-padding、RSA PKCS1，使用 API17 标准库。
  - 新增 `KugouDirectSessionClient.kt`：调用 `userservice.kugou.com/risk/v2/r_register_dev` 和 `login.user.kugou.com/v5/login_by_token`。
  - 扩展 `KugouDirectSessionStore.kt`：缓存 `dfid/mid/uuid/installGuid/installMac/installDev/vipType/t1/validationState`。
  - `KugouAuthConfigBinder` 只有在 token refresh 成功后才把酷狗视为已登录；QR token pending/blocked 都不放开内容入口。
- 未完成/后续:
  - SMS 登录仍待 `RawLoginApi.LoginByMobileAsync` direct port。
  - 内容接口 direct 化仍未完成；旧 WebApi Base URL 路径不得恢复。
  - `T-S5-PLAY-110` 已完成；下一手应进入 `T-S5-PLAY-111/112`，处理普通酷狗队列与 Radio/FM session。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍仅因既有 `MainActivity.kt` red-line 失败。

## Latest Delta (T-S5-MAIN-114 Execution, 2026-06-04)
- 已完成:
  - `T-S5-MAIN-114` Done: MainActivity 第二轮拆分，拆出 Kugou Auth/Config Binder。
  - 新增 `app/src/main/java/com/skodamusic/app/ui/KugouAuthConfigBinder.kt`。
  - `MainActivity.kt` 从约 6213 行降到 5890 行。
  - 迁出酷狗登录/配置 UI、session restore/persist、QR polling、短信登录、登出和状态文案。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，但没有新增 red finding。
- 当时任务链:
  - `T-S5-KG-109` Ready；当前已完成，后续进入 `T-S5-KG-117`。
  - `T-S4-AUDIO-097` Done: DSP 播放按钮持续红框诊断与修正已完成。

## Latest Delta (T-S5-KG-109 Execution, 2026-06-04)
- 已完成:
  - `T-S5-KG-109` Done: 移除用户必填 Kugou WebApi Base URL 产品路径并确认 direct API 可行性边界。
  - 设置页删除 `kugou_webapi_base_url_input`。
  - `KugouAuthConfigBinder` 启动/登出时清理旧 WebApi base-url/session 缓存。
  - QR/SMS 登录入口显示“酷狗直连 API 待接入”，不再触发旧 WebApi 代理请求。
  - `docs/KUGOU_AUTH_SESSION_CONTRACT.md`、`docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`、`docs/MULTI_SOURCE_NAV_CONTRACT.md`、`docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已同步新口径。
- `.NET` direct API 结论:
  - `https://gateway.kugou.com` 只是 `KgHttpTransport` 默认 host，不能替代旧 `/login/qr/key` 等代理路由。
  - 登录链路还依赖 `login-user.kugou.com`、`login.user.kugou.com`、`loginserviceretry.kugou.com`、request signing、dfid/mid、cookie/session、AES/RSA 加密。
  - 后续必须移植 `KgSignatureHandler`、`KgSigner`、`KgCrypto`、`KgSessionManager` 与 raw login/content API。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，但没有新增 red finding。
- 当前任务链:
  - `T-S5-KG-117` Done: Kugou direct raw API 最小 QR 登录链路移植。
  - `T-S4-AUDIO-097` Done: DSP 播放按钮持续红框诊断与修正已完成。

## Latest Delta (T-S5-KG-117 Execution, 2026-06-04)
- 已完成:
  - `T-S5-KG-117` Done: QR direct raw 最小登录链路。
  - 新增 `KugouDirectSigner.kt`：按 `.NET` Web QR signature/default params 实现签名。
  - 新增 `KugouDirectAuthClient.kt`：调用 `login-user.kugou.com/v2/qrcode` 与 `/v2/get_userinfo_qrcode`。
  - 新增 `KugouDirectSessionStore.kt`：缓存 QR success 返回的 direct `userid/token/nickname`。
  - `KugouAuthConfigBinder` 重新接入 QR 获取、图片展示、2s 轮询和 direct session restore/logout。
- 未完成/后续:
  - SMS 登录仍待 `RawLoginApi.LoginByMobileAsync` 的 AES/RSA 移植。
  - QR success 后的 device init / token refresh 已在 `T-S5-KG-118` 完成。
  - 内容接口仍未 direct 化，旧 WebApi Base URL 路径不得恢复。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，但没有新增 red finding。
- 当前任务链:
  - `T-S5-MAIN-115` Done: Kugou 内容页请求/状态所有权已迁出。
  - `T-S5-PLAY-112` Done: Radio/FM session 已完成。
  - `T-S4-AUDIO-097` Done: DSP 播放按钮持续红框诊断与修正已完成。

## Previous Delta (T-S5-MAIN-108 Execution, 2026-06-04)
- 已完成:
  - `T-S5-MAIN-108` Done: MainActivity 第一轮拆分边界落地。
  - 新增 `app/src/main/java/com/skodamusic/app/ui/SourceRowRenderer.kt`。
  - `MainActivity.kt` 从 6443 行降到 6213 行。
  - 迁出 source/list 行构造：空态行、分区标题、source row、Kugou track row、Emby track row、点赞/删除按钮。
  - 覆盖页面：推荐歌曲、推荐电台、发现歌单、队列/库列表、点赞状态页。
- 验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
## Code Reality Notes
- `app/src/main/java/com/skodamusic/app/MainActivity.kt` 当前约 5632 行。
- `python scripts/check_code_health.py` 当前因既有 `MainActivity.kt` entry red-line 与超长方法失败；后续拆分任务必须让趋势改善，不得新增入口文件职责。
- `app/src/main/java/com/skodamusic/app/ui/SourceRowRenderer.kt` 已承接 source/list row UI 构造。
- `app/src/main/java/com/skodamusic/app/ui/KugouContentRenderer.kt` 已承接 Kugou 内容页与普通队列页 UI 渲染。
- `app/src/main/java/com/skodamusic/app/ui/KugouContentBinder.kt` 已承接 Kugou 内容页请求、loading/list/selected state 和渲染协调。
- `app/src/main/java/com/skodamusic/app/ui/KugouAuthConfigBinder.kt` 已承接酷狗登录状态 UI；QR 已走 direct key/check + device init + token refresh，SMS 仍 pending。
- `app/src/main/java/com/skodamusic/app/playback/SourcePlaybackSession.kt` 已承接当前播放 source snapshot。
- `app/src/main/java/com/skodamusic/app/playback/KugouPlaybackQueueManager.kt` 已承接普通酷狗歌曲队列；推荐歌曲/发现歌单歌曲点击会建立普通 queue。
- 当前电台歌曲已进入 `KugouRadioSessionManager` radio session；radio active 时不走普通 queue。
- `reportPlaybackStateToService()`、Now Playing artist/title、next/previous 已按 source session 分流；普通 Kugou queue 与 radio session 均已建立。
- 设置页已无 `kugou_webapi_base_url_input`，不得恢复用户填写地址路径。

## Critical .NET References
- 普通队列:
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
- 电台/FM:
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PersonalFmService.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.PersonalFm.cs`
- Direct API / transport:
  - `KugouMusic.NET/src/Libraries/KuGou.Net/Infrastructure/Http/KgHttpTransport.cs`
  - `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/`
  - `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/`

## Execution Guidance
- 下一轮优先执行 API17 实机回归，按 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` A~N 分组回传证据。
- 后续继续拆 `MainActivity` 时，下一刀建议为 `RuntimeLogBinder`，再评估 `EqualizerPageBinder` 和 Fragment 包装试点。

## Validation Expectations
- 每个实现任务至少执行:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
- 触及资源/播放/native 时执行:
  - `gradle :app:assembleDebug --no-daemon`

## Blocked Carry Forward
- `B-KG-EMBY-INGEST-001`: Emby 上传播放缓存并入库能力未确认。
- `T-S4-AUDIO-095`: AC83xx Native DSP 实机长播与听感验证。
