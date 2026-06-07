# CURRENT_STATUS

Last Updated: 2026-06-07

## Stage
- 当前阶段: S5 纠偏子阶段（Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split）
- 当前主干: `master@18c4723`（本地已完成 `M-S5-KG-037` + `T-S5-KG-123`，尚未推送）

## Execution Progress (T-S5-KG-123, 2026-06-07)
- 状态: 本地完成；等待手机/API17 设备验证。
- 已完成:
  - `KugouDirectContentClient` 按 `.NET` `RawFmApi.GetRecommendAsync()` 直连 `/v1/rcmd_list` 加载 Radio 推荐。
  - `KugouDirectContentClient` 按 `.NET` `RawFmApi.GetSongsAsync()` 直连 `/v1/app_song_list_offset` 加载电台歌曲。
  - `KugouDirectContentClient` 按 `.NET` `RawPlaylistApi.GetPlaylistTagsAsync()` 直连 `/pubsongs/v1/get_tags_by_type` 加载发现标签。
  - `KugouDirectContentClient` 按 `.NET` `RawDiscoveryApi.GetRecommendedPlaylistsAsync()` 直连 `/v2/special_recommend` 加载发现歌单。
  - `KugouDirectContentClient` 按 `.NET` `RawPlaylistApi.GetPlaylistSongsAsync()` 直连 `/pubsongs/v2/get_other_list_file_nofilt` 加载歌单歌曲。
  - 点赞按 `.NET` `FavoritePlaylistService` 喜欢列表 ID `2` + `RawPlaylistApi.AddSongsToPlaylistAsync()` 直连 `/cloudlist.service/v6/add_song`。
  - `KugouContentBinder` 当前 Radio/Discover/playlist song 路径已不再依赖 `resolveBaseUrl()` 或旧 `KugouWebApiClient`。
  - 新增/复用脱敏事件：`kugou_direct_content_request`、`kugou_content_load_success/failed`、`kugou_like_request/success/failed`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，仅因既有 `MainActivity.kt` red-line：文件 5840 行、粗略大方法 4169 行；另有既有 warning/refactor 项。
- 待验证:
  - 尚未在手机/API17 设备用真实账号验证 Radio 推荐、电台歌曲、发现标签/歌单/歌曲、点赞 direct 返回字段。
- 安全结论:
  - 新 direct 请求日志只记录 label/http/异常类型；内容点击 runtime log 只保留短 hash ID；PostHog 只记录 source/feature/stage/item_count/error_code，不记录 token、userid、session、完整 URL query、body、完整 hash 或歌名。

## Requirement Confirmation (2026-06-07)
- 用户确认方案 B：酷狗登录成功后默认页优先自动加载，其他页进入时懒加载。
- 默认页优先加载对象：当前默认页为首页推荐歌曲；登录成功后直接拉取。
- 其他页策略：推荐电台、发现歌单/歌曲、点赞/相关页面进入时再加载；失败时提示并保留手动拉取/重试入口。
- token/session 运行中失效策略：不清空现有内容，弹窗登录即可；重新登录成功后隐藏弹窗并继续当前页面/当前列表流程。
- 首页登录交互：从内嵌登录面板调整为弹窗登录入口。
- 状态: 已实现并通过本地构建验证；等待手机/API17 实机验证。

## Planning Progress (M-S5-KG-037, 2026-06-07)
- 新增模块: `M-S5-KG-037` Kugou Post-login Loading & Login Recovery。
- Ready:
  - `T-S5-KG-124`: 登录弹窗与 post-login 默认页自动加载协调。
- Planned:
  - `T-S5-KG-125`: 内容页懒加载与失败可重试策略。
  - `T-S5-KG-126`: token/session 失效弹窗恢复，不清内容。
  - `T-S5-OBS-127`: 登录后加载/懒加载/token 恢复观测与回归清单。
- 架构约束:
  - `KugouAuthConfigBinder` 负责登录弹窗/QR 生命周期/session 状态 UI/登录成功回调。
  - `KugouContentBinder` 负责内容页加载状态、默认页自动加载、懒加载、失败提示和手动重试。
  - 如需跨 binder 协调，新增 `KugouLoginRecoveryCoordinator` 或等价小类；`MainActivity.kt` 只做接线。
  - `T-S5-KG-123` 已在后续本地执行中完成 direct 化；自动加载/恢复机制与 direct 内容路径仍需设备验证。

## Execution Progress (M-S5-KG-037, 2026-06-07)
- 状态: `T-S5-KG-124/125/126/127` 本地完成；尚未推送，尚未实机验证。
- 已完成:
  - 新增 `KugouLoginRecoveryCoordinator`，只保存脱敏 pending action 枚举，不保存 token/session/QR key/URL/手机号/验证码。
  - 首页登录入口改为弹窗登录；首页旧二维码图和 URL 文本隐藏，二维码只在弹窗中展示。
  - QR refresh 不再调用 `clearSessionState(true)`，连续刷新不会清空酷狗内容。
  - QR 登录成功后隐藏弹窗并触发默认页首页推荐歌曲自动加载；缓存 session 启动后也自动拉首页推荐。
  - Radio/Discover 进入页面时由 `KugouContentBinder` 懒加载；缺 session 弹登录，加载失败保留页面并显示点击重试入口。
  - Radio/Discover 旧 WebApi/baseUrl 缺口已由 `T-S5-KG-123` 本地 direct 化；失败现在记录为 `KUGOU_DIRECT_CONTENT_FAILED` 并保留重试入口。
  - 点赞/播放遇到本地登录态缺失时弹登录并保留当前内容，不清空已有列表/队列；登录后隐藏弹窗并刷新当前可见状态。
  - 新增脱敏事件：`kugou_auth_dialog_shown`、`kugou_auth_recovery_resume`、`kugou_post_login_auto_load`；继续复用 `kugou_content_load_success/failed`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，仅因既有 `MainActivity.kt` red-line：文件 5820 行、粗略大方法 4146 行。
- 未验证:
  - 尚无手机/API17 设备扫码实测。
  - 服务端明确 auth-invalid 错误码识别仍未完成；当前只对本地 session 缺失/不可用走弹窗恢复，普通网络/API 缺口失败不会误判为 token 失效。
  - Radio/发现/点赞真实数据已本地 direct 化，仍需实机/真实账号网络验证。

## Review Snapshot (2026-06-06)
- 状态: `T-S5-KG-122` 已提交并推送；功能口径为 Done for default Kugou QR login + 首页推荐 + 推荐歌曲播放最小 direct loop，Partial for full Kugou direct migration。
- 已验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，但仅因既有 `MainActivity.kt` red-line：文件 5776 行、粗略大方法 4140 行；未新增 blocking finding。
- 工程结论:
  - 新增 direct recommend/play URL 协议逻辑位于 `kugou/`，入口文件只做小范围接线，未修改 `KugouMusic.NET/`，未引入新依赖，未触碰 `minSdk=17`。
  - 新增 PostHog/runtime 事件为低频、脱敏事件；不记录 token、session key、dfid、mid、完整 URL query、cookie、手机号、验证码或认证 header。
  - `MainActivity.kt` 仍是 Needs Refactor 风险，后续功能不应继续向入口文件增加业务逻辑。
- 未验证:
  - 尚无手机/目标 API17 设备扫码实测；需要验证扫码后 UI 立即已登录、推荐歌曲加载、推荐歌曲播放 URL direct 成功。
  - Radio、发现歌单/歌单歌曲、点赞当前路径已移除旧 `KugouWebApiClient`/baseUrl gate，等待 `T-S5-KG-123` 设备验证。

## Execution Progress (T-S5-KG-121, 2026-06-05)
- 已完成:
  - QR 扫码登录失败热修：扫码返回 `userid/token` 后立即按 `.NET` 行为进入可用登录态。
  - device register/token refresh 改为增强校验；失败时记录 `kugou_session_validation_deferred`，不再把 UI 打成“酷狗登录失败/登录不可用”。
  - 默认酷狗启动 source gate：冷启动不恢复 Emby cached queue，不自动播放 Emby，不触发 Emby recommendation auto-refresh。
  - Library/Test Emby 作为显式 Emby 入口；未显式进入 Emby 前，Emby 自动恢复与自动刷新均跳过。
- 观测:
  - 新增脱敏事件：`kugou_session_validation_deferred`、`resume_restore_skipped`、`emby_auto_refresh_skipped`。
  - 事件与 runtime log 只记录 stage/reason/trigger/hash，不记录 token、dfid、mid、uuid、Emby URL、账号、密码或 cached queue payload。
- 待验证:
  - 手机扫码后不再提示“酷狗登录失败/登录不可用”，内容入口可用。
  - 冷启动 logcat 不再出现 Emby resume/autoplay，PostHog 可见 source gate skip 事件。

## Execution Progress (T-S5-KG-122, 2026-06-06)
- 状态: Done for default Kugou login/recommend/play minimal loop; Partial for all remaining Kugou content direct migration.
- 已完成:
  - QR success 返回 `userid/token` 后立即持久化为 `VALID` session，`hasSession()` 不再等待 device register/token refresh；该语义对齐 `.NET` `LoginClient.CheckQrStatusAsync()` token success 即 `UpdateAuth(...)`。
  - device register/token refresh 继续后台增强；失败只记录 `kugou_session_validation_deferred`，不覆盖登录态。
  - 新增 `KugouDirectContentClient`，按 `.NET` `RawDiscoveryApi.GetRecommendSongAsync()` 直连 `/everyday_song_recommend` 获取首页推荐歌曲。
  - 默认推荐歌曲点击播放改按 `.NET` `RawSongApi.GetUrlAsync()` / `RawSearchApi.GetPlayUrlAsync()` 的 `/v5/url` 直连路径获取播放 URL，不再依赖旧 WebApi `baseUrl`。
  - 新增脱敏事件：`kugou_direct_content_request`、`kugou_direct_play_url_request/success/failed`。
- 仍未完成:
  - Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞仍有旧 `KugouWebApiClient` + `resolveKugouBaseUrl()` 依赖；这些不属于本轮最小登录闭环，需后续 direct 化。
  - 尚无手机扫码实测证据；本轮只完成本地构建/护栏验证。
- `.NET` 依据:
  - `LoginClient.CheckQrStatusAsync()`
  - `LoginViewModel` QR success 后后台 InitDevice/RefreshSession
  - `RawDiscoveryApi.GetRecommendSongAsync()`
  - `RawSongApi.GetUrlAsync()` / `RawSearchApi.GetPlayUrlAsync()`

## Execution Progress (T-S5-KG-119 + T-S5-OBS-120, 2026-06-05)
- 已完成:
  - `T-S5-KG-119` QR refresh crash hotfix + fail-soft observability。
  - `T-S5-OBS-120` S5 新功能 PostHog 覆盖补齐与敏感字段审计。
- 关键改动:
  - `KugouDirectAuthClient.downloadBitmap()` 将二维码图片 URL/request 构造纳入 `runCatching`，避免无效 URL 抛未捕获异常杀进程。
  - `KugouAuthConfigBinder` 增加 QR refresh request generation，刷新/登出/停止后的旧异步结果不会再写 UI。
  - QR refresh 失败会进入失败/可重试状态，不启动无图轮询。
  - 新增 QR auth 脱敏事件：`kugou_qr_refresh_start/success/failed`、`kugou_qr_poll_failed`、`kugou_qr_login_success`、`kugou_session_validation_success/failed`。
  - `KugouContentBinder` 新增低频事件：`kugou_content_load_success/failed`、`kugou_queue_start`、`kugou_radio_session_start`。
  - `PostHogTracker` 扩展敏感属性 key 过滤，新增 `docs/S5_OBSERVABILITY_COVERAGE.md`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking finding。
- 结果:
  - 当前可执行代码任务已闭环。
  - 下一步恢复 API17 A~N 实机回归，重点验证 J10/J11 QR refresh 和 G 组 PostHog 证据。

## Execution Progress (T-S5-VAL-113, 2026-06-04)
- 已完成 `T-S5-VAL-113`：
  - 更新 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`，Scope 增加 `T-S5-VAL-113`。
  - I 组补充 DSP 边框颜色语义和 direct-buffer bridge 验证项。
  - K 组更新为当前 Kugou 内容页真实口径。
  - 新增 M 组：纯酷狗播放、普通 queue、Radio session。
  - 新增 N 组：MainActivity split / page shell decision。
  - 追加 `Local Validation Snapshot (T-S5-VAL-113, 2026-06-04)`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking finding。
- 结果:
  - `T-S5-VAL-113` Done。
  - S5 纠偏本地计划已闭环；下一步需要 API17 目标车机执行 A~N 分组并回传证据。

## Execution Progress (T-S5-MAIN-116, 2026-06-04)
- 已完成 `T-S5-MAIN-116` 页面壳拆分试点评估：
  - 新增 `docs/PAGE_SHELL_SPLIT_EVALUATION.md`。
  - 结论：当前阶段暂缓独立 Activity；Fragment 可作为未来试点，但不直接迁移 raw XML 页面。
  - 推荐路线：先继续低耦合 Binder 提取，首选 `RuntimeLogBinder`，其次 `EqualizerPageBinder`；之后用已成型 Binder 包装 Fragment。
  - 理由：保持 API17、左侧一级导航、后台播放、方向盘按键、前台通知和浮窗状态稳定，避免在 S5 纠偏收尾阶段引入新生命周期风险。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking finding。
- 结果:
  - `T-S5-MAIN-116` Done。
  - 下一步主线转入 `T-S5-VAL-113`：更新 API17 回归清单并记录本地验证结果。

## Execution Progress (T-S4-AUDIO-097, 2026-06-04)
- 已完成 `T-S4-AUDIO-097`：
  - 诊断出 DSP 播放按钮持续红色的高概率误报路径：`HiFiAudioProcessor` 在非 direct `ByteBuffer` 时直接发布 `FAIL_OPEN`。
  - `HiFiAudioProcessor` 新增 direct scratch bridge；API17/ExoPlayer heap buffer 会桥接到 direct buffer 后继续调用 native DSP。
  - non-direct buffer 不再直接导致红框；真实 `unsupported-format`、`native-not-ready`、`native-process-error`、native bypass/error 仍按 fail-open 诊断保留红色。
  - 新增诊断日志：`hifi-dsp native direct-buffer bridge input=<...> output=<...>`。
- 行数变化:
  - `HiFiAudioProcessor.kt`: 约 293 -> 383 行，仍低于 general file preferred 500。
  - `MainActivity.kt`: 未因本任务继续扩大。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking finding。
- 结果:
  - `T-S4-AUDIO-097` Done。
  - 下一步主线转入 `T-S5-MAIN-116`：页面壳拆分试点评估。

## Execution Progress (T-S5-PLAY-112, 2026-06-04)
- 已完成 `T-S5-PLAY-112`：
  - 新增 `KugouRadioSessionManager`，维护 radio active session、current、upcoming、history。
  - `KugouContentBinder` 将 radio 歌曲点击和电台歌曲加载成功后的首曲播放改为启动 radio session。
  - 普通 Kugou queue 与 radio session 互斥：普通歌曲队列播放会清 radio，radio 播放会清普通 queue，切回 Emby 播放会清酷狗 queue/radio session。
  - `performNextAction`、`performPrevAction` 和 Kugou 播放 completion 在 radio active 时先走 radio session，不再落到普通 queue 或 Emby queue。
  - 队列页在 radio active 时展示“酷狗电台队列”（current + upcoming），普通 queue 仍显示“酷狗普通队列”。
- `.NET` 依据:
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PersonalFmService.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.PersonalFm.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
- 范围说明:
  - Android 当前仍基于已接入的旧 WebApi `/fm/songs` 列表构建最小 radio session。
  - 本轮没有猜测 `.NET` direct Personal FM raw endpoint，没有实现 action report/dislike。
  - 未修改 `KugouMusic.NET/`，未引入新依赖，未触碰 `minSdk=17`。
- 行数变化:
  - 新增 `KugouRadioSessionManager.kt`: 114 行。
  - `KugouContentBinder.kt`: 486 -> 493 行。
  - `KugouContentRenderer.kt`: 196 -> 198 行。
  - `MainActivity.kt`: 5632 -> 5709 行；仍低于 `T-S5-MAIN-115` 执行前约 6118 行，但本轮播放接线使入口文件回升，后续仍需 `T-S5-MAIN-116` 或播放控制 Binder 继续压降。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，未新增 blocking finding。
- 结果:
  - `T-S5-PLAY-112` Done。
  - 下一步主线转入 `T-S4-AUDIO-097`：DSP 播放按钮持续红框诊断与修正。

## Execution Progress (T-S5-MAIN-115, 2026-06-04)
- 已完成 `T-S5-MAIN-115`：
  - 新增 `KugouContentRenderer`，接管推荐歌曲、Radio 页面、发现页、普通 Kugou 队列页的 UI 行构造与状态文案渲染。
  - 新增 `KugouContentBinder`，接管推荐歌曲、推荐电台、radio songs、发现 tag/playlist/songs 的请求入口、loading/list/selected state 和页面渲染协调。
  - `MainActivity` 不再维护大批 Kugou 内容页 state，也不再承载 `requestKugouRadioSongs`、`requestKugouPlaylistsByTag`、`requestKugouPlaylistSongs` 等内容页请求主逻辑；入口文件只保留播放/点赞/页面刷新委托。
  - 未改变已完成的 Kugou source boundary、普通 queue 行为或 Radio 单曲临时行为。
  - 未修改 `KugouMusic.NET/`，未引入新依赖，未触碰 `minSdk=17`。
- 行数变化:
  - 新增 `KugouContentRenderer.kt`: 196 行。
  - 新增 `KugouContentBinder.kt`: 486 行。
  - `MainActivity.kt`: 约 6118 -> 5632 行。
  - `SourceRowRenderer.kt`: 约 192 行，未继续扩大。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，但入口文件行数继续下降。
- 结果:
  - `T-S5-MAIN-115` Done。
  - 后续 `T-S5-PLAY-112` 已完成 Radio/FM session，当前主线已转入 API17 实机 A~N 回归证据闭环。

## Execution Progress (T-S5-PLAY-111, 2026-06-04)
- 已完成普通酷狗歌曲队列最小闭环，依据 `.NET PlaybackQueueManager`：
  - 新增 `KugouPlaybackQueueManager`，实现 `setupQueue(track, contextList)`、最大 300 首截取、围绕当前歌曲截取、`getNext/getPrevious` 循环。
  - 推荐歌曲和发现歌单歌曲点击时建立独立 Kugou 普通队列，不写入 Emby `loadedTracks/currentTrackIndex`。
  - Kugou active 的 previous/next 先走普通 Kugou queue；无普通队列时保持单曲边界，不触发 Emby。
  - Kugou 普通队列播放完成时按 queue next 推进。
  - 队列页在 Kugou active/普通队列非空时展示“酷狗普通队列”，并禁用 Emby 源文件删除按钮，避免误删 Emby 当前曲。
  - Radio 歌曲点击路径已由后续 `T-S5-PLAY-112` 接管为独立 Radio/FM session。
- `.NET` 依据:
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
- 行数变化:
  - 新增 `KugouPlaybackQueueManager.kt`: 84 行。
  - `MainActivity.kt`: 当前约 6118 行；普通队列接线使入口文件 red-line 趋势变差。
  - `SourceRowRenderer.kt`: 约 192 行，新增 `active` 参数保持向后兼容。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2，但入口文件行数继续上升。
- 结果:
  - `T-S5-PLAY-111` Done。
  - 后续 `T-S5-MAIN-115` 已完成；当前主线进入 `T-S5-PLAY-112`。

## Execution Progress (T-S5-PLAY-110, 2026-06-04)
- 已完成纯酷狗 playback/session 边界：
  - 新增 `SourcePlaybackSession` 与 `SourcePlaybackSnapshot`，用当前 source session 表达 Now Playing 基础字段。
  - Kugou 播放请求通过前置校验后标记为 Kugou active；播放失败、暂停或单曲结束时保留当前 Kugou track，避免 play/pause 回落到 Emby 队列。
  - Emby 显式播放时切回 Emby active。
  - previous/next 在 Kugou active 时不再触发 Emby `loadedTracks/currentTrackIndex`。
  - 首页标题/artist、歌词 artist、SeekBar duration、前台 service state 改为读取 source playback snapshot。
  - Kugou active 时 `refreshProgressMetrics()` 跳过 Emby 下载窗口/可播放估算，`maybePersistPlaybackResumeState()` 跳过 Emby resume 写入。
  - 清理 Kugou session/content 时清掉 active Kugou source，避免登出后保留旧 Now Playing。
- 范围说明:
  - 本轮没有实现完整 Kugou 普通队列或 Radio/FM session；这些已提升为 `T-S5-PLAY-111/112`。
  - 本轮没有恢复用户填写 Kugou WebApi Base URL；旧内容/play URL direct migration 仍由后续内容/播放任务逐项迁移。
- 行数变化:
  - 新增 `SourcePlaybackSession.kt`: 77 行。
  - `MainActivity.kt`: 当前约 6018 行；本轮只做源状态接线，但入口文件仍处于既有 red-line。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数为 2。
- 结果:
  - `T-S5-PLAY-110` Done。
  - `T-S5-PLAY-111` 与 `T-S5-MAIN-115` 已完成；当前主线建议继续 `T-S5-PLAY-112` Radio/FM session。
  - 独立 Ready：`T-S4-AUDIO-097` DSP 播放按钮持续红框诊断与修正。

## Execution Progress (T-S5-KG-118, 2026-06-04)
- 已完成 Kugou direct session validation：
  - 新增 `KugouDirectCrypto`，按 `.NET` `KgCrypto` / `Constants.PublicLiteRasKey` 移植 AES-CBC、Playlist AES、RSA no-padding、RSA PKCS1。
  - 新增 `KugouDirectSessionClient`，按 `.NET` `RegisterClient.InitDeviceAsync()` / `RawDeviceApi.RegisterDevAsync(...)` 注册设备并保存 `dfid/mid/uuid`。
  - 同一 client 按 `.NET` `LoginClient.RefreshSessionAsync()` / `RawLoginApi.RefreshTokenAsync(...)` 刷新 token，并解密 `secu_params` 合并 `token/t1/is_vip`。
  - `KugouDirectSessionStore` 扩展 direct session 缓存：`dfid/mid/uuid/installGuid/installMac/installDev/vipType/t1/validationState/validationReason/validatedAtMs`。
  - `KugouAuthConfigBinder` 改为 QR success 后先进入“校验设备与 Token”；只有 refresh 成功后 `hasSession()` 才返回 true。
- `.NET` 依据:
  - `LoginViewModel.cs` QR success 后调用 `deviceClient.InitDeviceAsync()` 与 `authClient.RefreshSessionAsync()`。
  - `RegisterClient.cs`
  - `RawDeviceApi.cs`
  - `LoginClient.cs`
  - `RawLoginApi.cs`
  - `KGCrypto.cs`
  - `KgSessionManager.cs`
- 行数变化:
  - 新增 `KugouDirectCrypto.kt`: 158 行。
  - 新增 `KugouDirectSessionClient.kt`: 335 行。
  - `KugouDirectSessionStore.kt`: 49 -> 140 行。
  - `KugouAuthConfigBinder.kt`: 282 -> 345 行。
  - `MainActivity.kt`: 5889 行，未修改。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数仍为 2，未新增 red finding。
- 结果:
  - `T-S5-KG-118` Done。
  - `T-S5-PLAY-110/111` 与 `T-S5-MAIN-115` 已完成；当前主线建议继续 `T-S5-PLAY-112` Radio/FM session。
  - 独立 Ready：`T-S4-AUDIO-097` DSP 播放按钮持续红框诊断与修正。

## Execution Progress (T-S5-KG-117, 2026-06-04)
- 已完成 Kugou direct raw API 最小 QR 登录链路:
  - 新增 `KugouDirectSigner`，按 `.NET` `KgSigner.CalcWebQrSignature` 与 `KgSignatureHandler` 默认参数规则计算 Web QR signature。
  - 新增 `KugouDirectAuthClient`，调用 `https://login-user.kugou.com/v2/qrcode` 与 `/v2/get_userinfo_qrcode`。
  - 新增 `KugouDirectSessionStore`，缓存 direct QR 成功返回的 `userid/token/nickname`，不存储 WebApi Base URL。
  - `KugouAuthConfigBinder` 恢复 QR 获取、二维码图片展示、2s 轮询、成功后缓存 direct session。
  - 手机号验证码登录仍保持 pending，因 `.NET` `RawLoginApi.LoginByMobileAsync` 依赖 AES/RSA 体加密与解密，本轮不猜测。
- `.NET` 依据:
  - `RawLoginApi.GetQrKeyAsync()`
  - `RawLoginApi.CheckQrStatusAsync(key)`
  - `LoginClient.CheckQrStatusAsync(key)`
  - `KgSignatureHandler`
  - `KgSigner.CalcWebQrSignature(...)`
  - `KuGouConfig.WebSignatureSalt`
- 行数变化:
  - 新增 `KugouDirectAuthClient.kt`: 162 行。
  - 新增 `KugouDirectSigner.kt`: 67 行。
  - 新增 `KugouDirectSessionStore.kt`: 49 行。
  - `KugouAuthConfigBinder.kt`: 187 -> 282 行。
  - `MainActivity.kt`: 5883 -> 5889 行（仅恢复 Binder 构造参数接线）。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数仍为 2，未新增 red finding。
- 结果:
  - `T-S5-KG-117` Done（QR direct 最小登录链路已可编译落地）。
  - 后续 `T-S5-KG-118` 已完成 direct device init / token refresh / session validation；内容接口 direct 化仍需后续拆分。

## Execution Progress (T-S5-KG-109, 2026-06-04)
- 已完成产品路径纠偏:
  - 设置页移除 `kugou_webapi_base_url_input`，不再要求用户填写 Kugou WebApi Base URL。
  - `KugouAuthConfigBinder` 启动/登出时清理旧 WebApi base-url/session 缓存。
  - 扫码登录、短信验证码和验证码登录按钮改为显示“酷狗直连 API 待接入”，不再触发旧 WebApi 代理路由。
  - `resolveKugouBaseUrl()` 当前返回空，推荐歌曲/电台/发现歌单/点赞/播放 URL 旧代理请求不会继续后台发起。
- `.NET` direct API 可行性确认:
  - `KgHttpTransport` 默认 host 是 `https://gateway.kugou.com`，但登录还依赖 `login-user.kugou.com`、`login.user.kugou.com`、`loginserviceretry.kugou.com` 等 host。
  - 登录、刷新、内容和播放 URL 不是 base URL 替换；需要移植 `KgSignatureHandler`、`KgSigner`、`KgCrypto`、`KgSessionManager`、dfid/mid/cookie/token/device/session 行为。
  - 本轮未猜测签名或加密协议，未修改 `KugouMusic.NET/`。
- 文档更新:
  - `docs/KUGOU_AUTH_SESSION_CONTRACT.md`
  - `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`
  - `docs/MULTI_SOURCE_NAV_CONTRACT.md`
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
- 行数变化:
  - `KugouAuthConfigBinder.kt`: 415 -> 187 行。
  - `KugouSessionStore.kt`: 83 -> 26 行。
  - `MainActivity.kt`: 5890 -> 5883 行。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 warning）。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败；blocking finding 数仍为 2，未新增 red finding。
- 结果:
  - `T-S5-KG-109` Done（产品路径纠偏 + direct 可行性确认完成）。
  - direct raw API 端到端接入拆为后续 P0：`T-S5-KG-117`。
  - 依赖真实酷狗内容/播放的 `T-S5-PLAY-110/111/112` 当前已由 `T-S5-KG-118` 解锁 session 前置。

## Execution Progress (T-S5-MAIN-114, 2026-06-04)
- 已完成 MainActivity 第二轮拆分：Kugou Auth/Config Binder。
- 新增:
  - `app/src/main/java/com/skodamusic/app/ui/KugouAuthConfigBinder.kt`
- 迁出范围:
  - 酷狗登录/配置 UI 控件绑定。
  - session cache restore / persist。
  - 扫码登录、QR polling、短信验证码、验证码登录、登出。
  - 登录状态文案、二维码状态文案、登录面板可见性。
- `MainActivity` 保留:
  - 薄委托：`requestKugouQrLogin`、`clearKugouSessionState`、`refreshKugouLoginUi`、`resolveKugouBaseUrl`、`hasKugouSession`。
  - 酷狗内容页、播放、点赞逻辑暂未改变，留给 `T-S5-KG-109/T-S5-MAIN-115/T-S5-PLAY-*`。
- 行数变化:
  - `MainActivity.kt`: 6213 -> 5890 行。
  - `KugouAuthConfigBinder.kt`: 415 行。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（仅既有 Kotlin warning）。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，但 blocking finding 数未增加，入口文件行数下降。
- 结果:
  - `T-S5-MAIN-114` Done。
  - `M-S5-MAIN-034` In Progress（后续仍有 `T-S5-MAIN-115/T-S5-MAIN-116`）。
  - `T-S5-KG-109` 随后进入 Ready，并已在本轮完成。

## Planning Refresh (Bootstrap Guardrail Alignment, 2026-06-04)
- 已执行 `$ai-planning`，将 bootstrap 后新增工程护栏纳入 S5 纠偏计划。
- 关键调整:
  - `MainActivity.kt` red-line 治理作为当前阶段硬前置。
  - 当时 Ready 队列收敛为 `T-S5-MAIN-114` 与 `T-S4-AUDIO-097`。
  - `T-S5-KG-109` 当时从并列 Ready 调整为 P0 Planned，依赖 `T-S5-MAIN-114` 完成后再提升。
  - 当前已完成 `T-S5-MAIN-114`、`T-S5-KG-109`、`T-S5-KG-117`、`T-S5-KG-118`、`T-S5-PLAY-110` 与 `T-S5-PLAY-111`，下一步转入 `T-S5-MAIN-115`。
- 原因:
  - `python scripts/check_code_health.py` 当前因既有 `MainActivity.kt` 约 6213 行与超长方法失败。
  - WebApi 地址纠偏会触碰酷狗登录/配置 UI，若抢先执行容易继续扩大入口文件。
- 下一步:
  - `T-S4-AUDIO-097`、`T-S5-PLAY-111/112` 与 `T-S5-MAIN-115` 已完成。
  - 当前首选入口为 API17 目标车机 A~N 回归执行与证据回写。

## Bootstrap Completion (2026-06-04)
- 已按 `$ai-bootstrap` 补齐缺失工程护栏与 code health 文件。
- 新增 guardrails:
  - `.ai/context/ARCHITECTURE.md`
  - `.ai/context/CODE_STANDARDS.md`
  - `.ai/context/RED_LINES.md`
  - `.ai/context/ENGINEERING_CHECKLIST.md`
- 新增 code health / hook 文件:
  - `.ai/code_health_config.json`
  - `scripts/check_code_health.py`
  - `.pre-commit-config.yaml`
- 已将 `minSdk = 17` 写入 red-line 约束。
- 已将 `KugouMusic.NET/` 配置为第三方参考项目，code health 扫描忽略该目录。
- 已追加 `.ai/context/PROJECT_BRIEF.md` 的 bootstrap 自动识别结果。
- 验证结果:
  - `python scripts/check_code_health.py` 可执行，但因既有 `MainActivity.kt` 红线失败。
  - Blocking findings:
    - `MainActivity.kt` 约 6213 行，超过 entry file red line。
    - `MainActivity.kt` 中约 line 2242 的方法体被粗略检测为超长方法。
  - Non-blocking findings:
    - `MainActivity.kt` 另有 refactor/warning 级方法长度问题。
    - `SourceRowRenderer.kt` 存在 warning 级方法长度问题。
    - `AppUpdateManager.kt` 文件长度 warning。
    - `activity_main.xml` declarative UI 文件长度 warning。
- 结论: bootstrap 文件生成完成；`T-S5-MAIN-114` 已完成一轮压降，后续继续执行 `T-S5-KG-109/T-S5-MAIN-115` 等任务。

## Requirement Refresh (S5 Corrective Scope, 2026-06-04)
- 用户新增确认：
  - 除歌曲外还有电台类型播放；电台是同一电台持续播放，下一曲由电台内部切换。
  - `MainActivity.kt` 6k 多行问题需要现在优化，不再只是记录。
  - 酷狗 API 不应要求用户提供地址；必须参考 `KugouMusic.NET`。
  - 默认酷狗模式应是纯酷狗，不叠加 Emby 播放/队列。
  - 酷狗队列和 Emby 队列不同，必须参考 `.NET`。
  - DSP 播放按钮不能持续红色；红色应只代表真实 fail-open/bypass/error。
- 状态:
  - `Scoped + Planned`：已写入 S5 纠偏 scope，并重排 plan/modules/tasks/queue。
  - `T-S5-MAIN-108` 已完成；用户随后修正拆分口径，单 Activity 外壳不再是长期硬约束。
  - Next Ready 调整为 `T-S5-MAIN-114`，先拆酷狗登录/配置 Binder；`T-S4-AUDIO-097` 可独立执行，`T-S5-KG-109` 等待 `T-S5-MAIN-114` 后提升。

## Planning Refresh (MainActivity Decomposition Phase 2, 2026-06-04)
- 用户新增确认：
  - 之前“保持单 Activity 外壳”的口径过窄。
  - 项目越来越大，如果能适当拆 Activity/页面壳/Fragment/Controller/Binder 更好。
  - 目标是避免单文件行数过多。
- 规划调整：
  - 新增模块 `M-S5-MAIN-034`：MainActivity Decomposition Phase 2。
  - 新增 Ready 任务 `T-S5-MAIN-114`：拆出 Kugou Auth/Config Binder。
  - 新增 Planned 任务 `T-S5-MAIN-115`：拆出 Kugou Content Pages Binder。
  - 新增 Planned 任务 `T-S5-MAIN-116`：页面壳拆分试点评估（Fragment / 独立 Activity）。
  - `T-S5-KG-109` 保持 P0，但推荐在 `T-S5-MAIN-114` 后执行，避免继续扩大 `MainActivity`。
- 设计口径：
  - 不再把单 Activity 作为长期硬约束。
  - 短期先用 Controller/Binder 降低风险。
  - 中期可试点 Fragment/独立 Activity，优先设置/日志/EQ 等低耦合页面。
  - 播放页、service bridge、方向盘按键和浮窗相关页面最后拆。

## Planning Refresh (S5 Corrective Scope, 2026-06-04)
- 新增/刷新模块：
  - `M-S5-MAIN-029` MainActivity Split Foundation。
  - `M-S5-KG-030` Kugou API Configuration Correction。
  - `M-S5-PLAY-031` Pure Kugou Playback Boundary。
  - `M-S5-PLAY-032` Kugou Queue and Radio Session Parity。
  - `M-S4-AUDIO-023` DSP Runtime Indicator Correction。
  - `M-S5-VAL-033` Corrective Validation。
- 新任务链：
  - `T-S5-MAIN-108` MainActivity 第一轮拆分边界落地。
  - `T-S5-KG-109` Kugou WebApi Base URL 产品路径纠偏与 direct API 可行性确认。
  - `T-S5-PLAY-110` 纯酷狗播放状态边界，切断 Emby 队列叠加。
  - `T-S5-PLAY-111` Kugou 普通歌曲队列按 `.NET PlaybackQueueManager` 实现。
  - `T-S5-PLAY-112` Kugou Radio/FM session 按 `.NET PersonalFmService` 实现。
  - `T-S4-AUDIO-097` DSP 播放按钮持续红框诊断与修正。
  - `T-S5-VAL-113` S5 纠偏 API17 回归清单与本地验证。
- 当时队列状态：
  - Ready: `T-S5-KG-109`, `T-S4-AUDIO-097`。
  - Pending: `T-S5-MAIN-115`, `T-S5-PLAY-110`, `T-S5-PLAY-111`, `T-S5-PLAY-112`, `T-S5-MAIN-116`, `T-S5-VAL-113`。
  - Blocked: `B-KG-EMBY-INGEST-001`, `T-S4-AUDIO-095`。

## Execution Progress (T-S5-MAIN-108, 2026-06-04)
- 已完成 MainActivity 第一轮低风险拆分:
  - 新增 `app/src/main/java/com/skodamusic/app/ui/SourceRowRenderer.kt`。
  - 将 source row、Kugou track row、Emby track row、空态行、分区标题、点赞/删除图标按钮构造从 `MainActivity` 迁出。
  - 接入范围覆盖：推荐歌曲、推荐电台、发现歌单、队列/库列表、点赞状态页。
  - 未改变播放、删除、点赞、登录、网络请求、导航和 service state 逻辑。
- 行数变化:
  - `MainActivity.kt`: 6443 -> 6213 行。
  - 新增 `SourceRowRenderer.kt`: 191 行。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
- 结果:
  - `T-S5-MAIN-108` Done。
  - `M-S5-MAIN-029` Done。
  - `T-S5-KG-109` 当时进入 Ready；随后被 bootstrap guardrail planning refresh 临时调整为 P0 Planned；`T-S5-MAIN-114` 完成后重新提升并已完成。

## Code Reality Notes (2026-06-04)
- `MainActivity.kt` 当前约 5883 行；已抽出 `SourceRowRenderer` 与 `KugouAuthConfigBinder`。
- 当前酷狗普通歌曲点击使用 `playKugouTrack(SourceTrack)` 单曲直连播放，未建立 `.NET` 风格 queue。
- 当前电台歌曲也被当普通 `SourceTrack` 播放，未建立同一 radio/FM session。
- 当前 Now Playing / service state / next/previous 仍大量读取 Emby `loadedTracks/currentTrackIndex`。
- 当前设置页已移除 `kugou_webapi_base_url_input`；QR direct 登录和 session validation 已落地，SMS direct 登录仍 pending。
- `.NET` 队列与电台参考：
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PersonalFmService.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
  - `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.PersonalFm.cs`

## Latest Confirmed (User)
- 新阶段切换到酷狗默认模式与多来源抽象。
- 来源需抽象，后续支持多套来源；点赞也先抽象，本阶段暂只支持酷狗。
- 酷狗模式必须登录后可用；默认扫码登录，同时支持手机号验证码登录；登录成功后缓存 session，直到下次不可用再跳转登录。
- 酷狗相关实现必须参考 `KugouMusic.NET/` 已有接口、模型和流程；没有依据就停止并询问用户。
- 左侧应能快速切换整体界面；默认进入酷狗模式；不增加二级 tab。
- 点赞后“播放缓存上传到 Emby 入库”先作为阻塞项，后续再处理；本地缓存设计目标最大不超过 `100MB`。
- Native DSP 已可用，但播放页需要可视确认未进入 fail-open；采用播放/暂停按钮有色边框方案，状态刷新要慢一点，避免卡顿。
- 路线锁定为“方案1（Legacy 稳态）”。
- 第一版必须同轮达成：后台服务 + 后台方向盘按键 + 全局浮窗。
- 浮窗策略锁定：播放/暂停均显示；手动关闭后“进应用再切出”再次显示。
- 自动续播体验差，当前口径改为“先移除自动续播”。
- 接受前台服务常驻通知。
- 音效主线切换为应用内保真 DSP：`原声 / 保真 / 清晰 / 动感 / 柔和`。

## Already Completed (Baseline)
- API17 红线与构建护栏已建立（含 CI guardrails）。
- 主播放链路为 download-only，队列尾补充与自动切歌逻辑已在主干。
- 下载缓存统计/清理与设置页入口已落地。
- 最近车机稳定性修复已合入：
  - 移除不稳定媒体会话实现（`ff52815`）。
  - 增加 API17 违规守卫（`8afea55`）。
  - 启动白屏感知优化（`6e6206c`、`2d5d315`）。

## Requirement Refresh (Kugou Source Mode, 2026-06-03)
- 用户确认新口径：
  - 多来源抽象是前置，不能继续硬绑定 Emby-only。
  - 默认进入酷狗模式，左侧一级导航快速切换，不新增二级 tab。
  - 酷狗登录必需：默认扫码，同时支持手机号验证码；session 缓存复用，失效后再登录。
  - 酷狗实现以 `KugouMusic.NET/` 为唯一参考来源，不自行发挥接口或字段。
  - 点赞先抽象，本阶段暂只支持酷狗；点赞历史和状态需要可查看。
  - 播放缓存上传到 Emby 入库能力未确认，作为阻塞项 `B-KG-EMBY-INGEST-001`。
- 状态:
  - `Planned`：已写入 S5 scope 并完成 planning。

## Planning Refresh (Kugou Source Mode, 2026-06-03)
- 已按新 scope 完成规划重排：
  - 新增模块 `M-S5-SRC-022`：Multi-Source Domain & Left Navigation IA。
  - 新增模块 `M-S5-KG-023`：Kugou Interface Map & Auth Session。
  - 新增模块 `M-S5-KG-024`：Kugou Content Pages。
  - 新增模块 `M-S5-PLAY-025`：Source-Aware Playback & Cache Guard。
  - 新增模块 `M-S5-LIKE-026`：Like Abstraction & Status History。
  - 新增模块 `M-S5-VAL-027`：API17 Validation & Regression Evidence。
  - 阻塞模块 `M-S5-INGEST-028`：Emby Upload Ingest From Playback Cache。
- 新任务链：
  - `T-S5-KG-096` KugouMusic.NET 接口能力映射与缺口检查。
  - `T-S5-SRC-097` 多来源领域模型与左侧一级导航契约。
  - `T-S5-KG-098` 酷狗登录与 session 缓存契约。
  - `T-S5-SRC-099` Source-aware 队列与播放解析边界设计。
  - `T-S5-UI-100` 左侧导航与默认酷狗模式页面骨架。
  - `T-S5-KG-101` 酷狗登录 UI 与 session 缓存实现。
  - `T-S5-KG-102` 酷狗推荐歌曲页面接入。
  - `T-S5-KG-103` 酷狗推荐电台页面接入。
  - `T-S5-KG-104` 发现歌单分类、歌单列表与歌曲列表接入。
  - `T-S5-LIKE-105` 酷狗点赞抽象与历史/状态页。
  - `T-S5-VAL-106` S5 API17 回归清单与本地验证。
  - `T-S5-PLAY-107` 酷狗播放 URL 解析与 100MB 缓存守卫实现。
- 当时队列状态:
  - Ready then: `T-S5-VAL-106`。
  - Blocked then: `B-KG-EMBY-INGEST-001`, `T-S4-AUDIO-095`。

## Execution Progress (T-S5-KG-101~105, 2026-06-03)
- 已完成 `T-S5-KG-101`:
  - 新增 WebApi session store 与 client，按 `.NET` `X-Kg-Session-Id`/`kg_sid` 契约缓存 session key。
  - 设置页新增 Kugou WebApi Base URL、扫码登录、手机号验证码登录、登出和状态显示。
  - 默认推荐歌曲页未登录时展示扫码登录，扫码轮询按 `.NET LoginViewModel` 的 2s 节奏。
  - 启动时优先 `/login/token` 复用 session；失败清 session 并回登录。
- 已完成 `T-S5-KG-102`:
  - 接入 `GET /recommend/songs`，按 `DailyRecommendResponse` / `DailyRecommendViewModel` 映射为 `SourceTrack`。
  - 推荐歌曲点击当前只提示播放解析待后续任务，不改 Emby 播放链。
- 已完成 `T-S5-KG-103`:
  - 接入 `GET /fm/recommend` 与 `GET /fm/songs`，按 `FmRecommendResponse` / `FmSongResponse` 映射电台与电台歌曲。
- 已完成 `T-S5-KG-104`:
  - 接入 `GET /playlist/tags`、`GET /top/playlist`、`GET /playlist/track/all`。
  - 发现歌单分类来自接口返回，不手写场景/主题/语种/风格/心情/年代。
- 已完成 `T-S5-LIKE-105`:
  - 新增来源无关点赞历史 store。
  - 酷狗歌曲行新增点赞入口，调用 `.NET` WebApi `/playlist/tracks/add`，目标列表 ID 按 `FavoritePlaylistService` 的 `LikeListIdForAction = "2"`。
  - 点赞/入库状态页展示 source、远端点赞状态、失败原因和 `blocked_ingest`。
  - 不执行 Emby 上传入库。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
- 当前队列状态:
  - Ready: None。
  - Pending: None。
  - Blocked: `B-KG-EMBY-INGEST-001`, `T-S4-AUDIO-095`。

## Execution Progress (T-S5-VAL-106/T-S5-PLAY-107, 2026-06-03)
- 已完成 `T-S5-VAL-106`:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 升级为 S4/S5 清单。
  - 新增酷狗登录/session、内容页、点赞/入库状态和证据要求。
  - 写入本轮本地验证快照。
- 已完成 `T-S5-PLAY-107`:
  - `KugouWebApiClient` 新增 `GET /song/url` resolver，参考 `SongController.GetUrl` / `SongClient.GetPlayInfoAsync` / `PlayUrlData`。
  - 酷狗歌曲点击后解析播放 URL，并通过 ExoPlayer 直接播放远程 URL；Emby 队列与 download-only 主路径保持不变。
  - 本地播放缓存守卫覆盖 `emby_*.cache` 和未来 `kugou_*.cache`，上限 `100MB`。
  - 不执行 Emby 上传入库。
- 最终本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
- 当前队列状态:
  - Ready: None。
  - Pending: None。
  - Blocked: `B-KG-EMBY-INGEST-001`, `T-S4-AUDIO-095`。

## Requirement Refresh (Native DSP Playback Indicator, 2026-06-03)
- 用户确认新口径：
  - Native DSP 当前方案可以继续。
  - 播放页需要一个地方确认没有进入 fail-open。
  - 采用播放/暂停按钮边框上色方案。
  - 状态刷新不要过快，避免低端车机 UI 卡顿。
- 状态:
  - `Planned`：已加入并行热修模块 `M-S4-AUDIO-022` 与 Ready 任务 `T-S4-AUDIO-096`。

## Planning Refresh (Native DSP Playback Indicator, 2026-06-03)
- 新增模块:
  - `M-S4-AUDIO-022` Native DSP Playback Status Indicator。
- 新增任务:
  - `T-S4-AUDIO-096` Native DSP 播放按钮 fail-open 状态指示。
- 执行边界:
  - 不修改 DSP 算法和 native 性能策略。
  - 通过轻量 runtime state 发布 + `MainActivity` 既有 `UI_PROGRESS_REFRESH_MS = 1_000L` tick 读取。
  - 仅状态变化时重绘播放按钮边框。
  - 颜色语义：灰色未知/关闭，绿色正常，黄色降级/超预算，红色 fail-open/bypass/error。

## Execution Progress (Full Plan Mode, 2026-06-03)
- 已完成 `T-S4-AUDIO-096`:
  - `HiFiDspController` 新增轻量 runtime state。
  - `HiFiAudioProcessor` 基于 native status/tier/flags 发布 `UNKNOWN/DISABLED/ACTIVE/DEGRADED/FAIL_OPEN`。
  - `MainActivity` 在既有 `UI_PROGRESS_REFRESH_MS = 1_000L` tick 中刷新播放/暂停按钮边框，状态未变化不重绘。
  - 播放按钮边框颜色：灰色未知/关闭，绿色正常，黄色降级/超预算，红色 fail-open/bypass/error。
- 已完成 `T-S5-KG-096`:
  - 新增 `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`。
  - 覆盖扫码登录、手机号验证码、session、推荐歌曲、推荐电台、发现歌单、播放 URL、点赞/我喜欢。
  - 明确点赞没有独立 toggle route，需参考 `FavoritePlaylistService` 的“我喜欢歌单 add/remove”流程。
- 已完成 `T-S5-SRC-097`:
  - `MainModels.kt` 新增 `MusicSource`、`SourceTrack`、`SourcePlaylist`、`SourceRadio`、`SourcePlaybackRef` 等最小来源模型。
  - 新增 `docs/MULTI_SOURCE_NAV_CONTRACT.md`，固定左侧一级导航和 Emby 迁移边界。
- 已完成 `T-S5-KG-098`:
  - 新增 `docs/KUGOU_AUTH_SESSION_CONTRACT.md`，固定 WebApi session key、扫码轮询、手机号验证码、缓存复用和失效跳转口径。
- 已完成 `T-S5-SRC-099`:
  - 新增 `docs/SOURCE_AWARE_PLAYBACK_QUEUE_CONTRACT.md`，固定 Emby/Kugou resolver、source-aware queue 和 100MB 缓存守卫边界。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
- 上一轮停止边界:
  - `T-S5-UI-100` 曾因 UI 结构风险单独进入 Ready。
  - 本轮已执行并完成该任务。

## Execution Progress (T-S5-UI-100, 2026-06-03)
- 已完成左侧一级导航与默认酷狗模式页面骨架:
  - 左侧入口调整为：推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态、设置。
  - 默认启动进入推荐歌曲页，复用现有播放卡片保留播放控制和 DSP 状态按钮。
  - Home 内歌词/队列二级 tab 入口已隐藏，不新增二级 tab。
  - 新增推荐电台、发现歌单、点赞/入库状态骨架页。
  - Queue 从隐藏页改为左侧一级入口。
  - 旧 Library 页面代码保留但不在 S5 一级导航暴露。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
- 当时队列状态:
  - Next Ready then: `T-S5-KG-101`。
  - Done 新增: `T-S5-UI-100`。

## Requirement Refresh (AC83xx Native Hi-Fi DSP, 2026-06-02)
- 实机反馈：AC83xx 上 Kotlin DSP 播放时有轻微卡顿，听感类似广播不稳。
- 用户确认新口径：
  - 不接受简单低配降级或默认关闭 DSP。
  - 目标是保留高音效，通过工程优化解决性能问题。
  - 直接采用 C++ Native DSP，把 PCM 热路径从 Kotlin 迁移到 native。
  - 允许空间换时间：预计算、查表、fixed-point、整块处理、自动性能档位。
  - 超预算时优先自动降档，不直接关闭音效；最终仍必须 fail-open 保护播放。
- 状态:
  - `Planned`：已完成 native DSP 优化规划，Ready 入口为 `T-S4-AUDIO-088` 与 `T-S4-AUDIO-089`。

## Planning Refresh (AC83xx Native Hi-Fi DSP, 2026-06-02)
- 已按新 scope 完成规划重排：
  - 新增模块 `M-S4-AUDIO-018`：Native DSP Bridge & Build Integration。
  - 新增模块 `M-S4-AUDIO-019`：Native DSP Engine & Performance Tiers。
  - 新增模块 `M-S4-AUDIO-020`：Kotlin AudioProcessor Native Migration。
  - 新增模块 `M-S4-AUDIO-021`：AC83xx Validation & Regression Evidence。
- 新任务链：
  - `T-S4-AUDIO-088` Native DSP JNI API 与 fail-open 契约。
  - `T-S4-AUDIO-089` 性能档位、预算阈值与日志字段契约。
  - `T-S4-AUDIO-090` Native bridge scaffold 与 no-op/bypass buffer 处理。
  - `T-S4-AUDIO-091` C++ DSP 模式引擎与系数预计算。
  - `T-S4-AUDIO-092` 自动降档、耗时统计与节流日志。
  - `T-S4-AUDIO-093` `HiFiAudioProcessor` 热路径迁移到 native。
  - `T-S4-AUDIO-094` 本地验证、guardrails 与 API17 清单更新。
  - `T-S4-AUDIO-095` AC83xx 实机长播与听感验证。
- Planning 当时队列状态:
  - Initial ready: `T-S4-AUDIO-088`, `T-S4-AUDIO-089`。
  - Initial pending: `T-S4-AUDIO-090~094`。
  - Initial blocked: `T-S4-AUDIO-095`（外部实机窗口）。
  - Initial superseded: `T-S4-AUDIO-087`（需先完成 native 优化后再重新实机验证）。
- 当前队列状态已由后续执行刷新：`T-S4-AUDIO-088~094` Done，`T-S4-AUDIO-095` Blocked by device。

## Execution Progress (AC83xx Native Hi-Fi DSP, 2026-06-02)
- Full Plan Mode 已完成本地可执行链 `T-S4-AUDIO-088~094`：
  - `T-S4-AUDIO-088` Done：固定 native DSP JNI API、handle 生命周期、direct buffer 与 fail-open 契约。
  - `T-S4-AUDIO-089` Done：固定 `quality / balanced / safe` 三档、耗时预算、降档/旁路日志字段。
  - `T-S4-AUDIO-090` Done：新增 `NativeHiFiDspBridge.kt`、`native_hifi_dsp.cpp`，并接入 `native-playback` CMake。
  - `T-S4-AUDIO-091` Done：五种音质模式迁入 C++，模式切换时预计算 biquad 系数。
  - `T-S4-AUDIO-092` Done：native 层实现耗时统计、超预算计数、自动降档和最终旁路。
  - `T-S4-AUDIO-093` Done：`HiFiAudioProcessor.queueInput()` 已改为 native direct `ByteBuffer` 整块处理，Kotlin 不再逐 sample DSP。
  - `T-S4-AUDIO-094` Done：API17 回归清单已补 native DSP `mode/tier/costUs/flags` 观察项。
- 本地验证：
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过（含 C++/CMake 多 ABI 构建）。
- 剩余：
  - `T-S4-AUDIO-095` Blocked：等待 AC83xx 实机验证是否消除广播感卡顿，并回传 native DSP 日志。

## Execution Progress (App Hi-Fi DSP Engine, 2026-06-01)
- Full Plan Mode 已推进 `T-S4-AUDIO-080~086` 本地闭环：
  - `T-S4-AUDIO-080` Done：确认 ExoPlayer 2.17.1 通过 `RenderersFactory + DefaultAudioSink.setAudioProcessors` 接入 DSP。
  - `T-S4-AUDIO-081` Done：新增 `sound_effect_enabled/sound_effect_mode` 状态与旧 `eq_enabled` 迁移兜底。
  - `T-S4-AUDIO-082` Done：新增 fail-open `HiFiAudioProcessor` 骨架。
  - `T-S4-AUDIO-083` Done：实现 `原声 / 保真 / 清晰 / 动感 / 柔和` 五种轻量 DSP 模式。
  - `T-S4-AUDIO-084` Done：设置页与子页切换为“保真音效 / 音质模式”体验。
  - `T-S4-AUDIO-085` Done：模式选择实时更新 DSP controller，默认路径不再触发 Android `audiofx` 写入。
  - `T-S4-AUDIO-086` Done：API17 回归清单已更新为 Hi-Fi DSP Sound Mode。
- 本地验证：
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
- 剩余：
  - `T-S4-AUDIO-087` Blocked：等待 API17 实机听感、长播、切歌/seek/暂停恢复验证。

## Current Focus
- 当前焦点为 S5 纠偏：MainActivity 拆分、酷狗 API 地址纠偏、纯酷狗播放、普通酷狗队列、Radio/FM session、DSP 红框修正。
- 当前 Ready: `T-S5-KG-109`, `T-S4-AUDIO-097`；`T-S5-MAIN-114` 已完成。
- S4 `T-S4-AUDIO-095` 仍等待 AC83xx 实机验证，不阻塞 S5 本地 planning/execution。

## Historical Review Snapshot (2026-06-03, Superseded By 2026-06-04 Corrective Scope)
- 状态: Done，无阻断问题。
- Scope 对齐: 仍符合 S5 酷狗来源、多来源抽象、点赞状态、100MB 缓存守卫与 DSP 播放按钮状态指示口径。
- Plan/Queue 对齐: `TASK_QUEUE.md` 当前 Ready/Pending 均为 None，Blocked 仅保留 `B-KG-EMBY-INGEST-001` 与 `T-S4-AUDIO-095`。
- 本轮 review 补充:
  - `KugouMusic.NET/` 是只读参考源码，已加入 `.gitignore`，不随 Android 代码提交。
  - 静态检查未发现会阻断提交/推送的启动、播放、登录/session、缓存守卫问题。
  - 结构风险: `MainActivity` 继续膨胀，后续新增来源时建议拆分 source adapter / view binder。
- 验证通过:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`

## Historical Requirement Capture (MainActivity Engineering Debt, 2026-06-03, Superseded By 2026-06-04)
- 用户要求记录 `MainActivity` 过大问题。
- 当时写入 `SCOPE.md` 的 `D-MAINACTIVITY-001`：
  - 状态: Pending Planning。
  - 目标: 后续逐步拆分 UI/controller/binder 边界。
  - 边界: 当前不直接执行重构，不改变 S5 已完成行为，不进入当前 Ready 队列。
- 2026-06-04 更新:
  - 用户已明确要求现在优化，当前 Ready 已变更为 `T-S5-MAIN-108`。

## Historical Notes
## Requirement Refresh (App EQ Fixed 10-band Trial, 2026-05-29)
- 实机已确认系统 EQ 继承不可用，EQ 主线切回应用内 EQ。
- 用户确认新口径：
  - EQ 页面固定常见 10 段，不再读取/展示 ROM 内置 bands/presets。
  - 视觉按系统 EQ 截图重做：横屏、一排很窄的竖滑杆、右侧中文预设按钮。
  - 底层先强制尝试 Android Equalizer band `0..9` 直接写入，用于实机验证。
  - 10 段必须分开处理：单 band 独立 try/catch、独立提示，不能一段失败就整体不可用。
  - 不预先禁用 band；只有实际改动/预设写入时底层真实抛错才提示。
  - 真实写入失败的 band 不写入持久化配置，避免下次启动反复失败或无法启动。
- 状态:
  - `Pending Planning`：已写入 scope，下一步进入 planning 拆任务。

## Historical Current Focus (2026-05-29)
- 执行 `T-S4-CORE-026`（S4 大闭环）：后台播放服务、方向盘按键、通知与浮窗控制链路稳定化。
- 维持播放主链路稳定，并按新口径保持“无自动续播”。
- 并行焦点：音效目标切到“应用内保真 DSP 引擎”，不再沿 Android `audiofx` 10 段直写作为主线推进。

## Requirement Refresh (System EQ Inherit, 2026-05-27)
- 用户确认新口径（Option B）：
  - 仅关闭应用 EQ 不等于系统 EQ 已接管，需要补系统接线验证。
  - 系统不可用时：toast 提示，并允许手动开启应用内 EQ。
  - 设置页暂不改信息架构；启动时先关闭应用 EQ 并尝试系统 EQ。
- 状态:
  - `Pending Confirmation -> Done`（需求确认完成，待进入 planning 拆任务）。

## Planning Refresh (System EQ Inherit, 2026-05-27)
- 已按新 scope 完成规划重排：
  - 新增模块 `M-S4-AUDIO-013`（系统 EQ 继承接线与手动兜底）。
  - 新任务链：`T-S4-AUDIO-069 -> 070 -> 071 -> 072`。
  - Ready 入口切换为 `T-S4-AUDIO-069`（系统 EQ 会话接线）。
- 当前状态：
  - `Done`: 需求确认 + planning 回写。
  - `Planned`: 系统 EQ 接线实现与回归验证。

## Module Execution Progress (System EQ Inherit, 2026-05-27)
- 已按 `M-S4-AUDIO-013` 完成本地执行链（`T-S4-AUDIO-069~072`）：
  - 系统 EQ 会话 open/close 已接线（基于 `audioSessionId` 生命周期）。
  - 启动默认“系统优先 + 应用 EQ 关闭”已落地。
  - 系统不可用时 toast + 反馈文案已收口，并保留手动开启应用 EQ 兜底。
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已补 I7~I9 观察项。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-27）。
- 当前边界：
  - 是否真实由系统音效接管仍需 API17 实机留证确认（`T-S4-REG-022` / `T-S4-VAL-033`）。

## Module Execution Progress (EQ Full-screen, 2026-05-26)
- `M-S4-AUDIO-012` 本地执行链完成（`T-S4-AUDIO-065~068`）：
  - EQ 页面改为全屏横屏子页。
  - 左侧按设备能力动态生成 bands 滑杆。
  - 右侧 preset 按钮网格可直接切换，保留“自动开启 + 外部开关同步”。
  - 玻璃态视觉保留并重做层级；常驻 fail-open 提示已移除，仅在回退/降级触发提示。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 通过（2026-05-26）。
- 当前边界：
  - API17 实机观察点与证据回填仍依赖外部窗口（`T-S4-REG-022` / `T-S4-VAL-033`）。

## Module Execution Progress (EQ Visual Polish V2, 2026-05-26)
- 在 `M-S4-AUDIO-012` 范围内完成一轮页面质感收口（不改音频链路）：
  - EQ 子页左右分区改为差异化玻璃面板，页头状态改为胶囊徽标。
  - 左侧 bands 行改为卡片化展示，并统一玻璃滑杆轨道/拇指样式。
  - 右侧 preset 按钮改为“激活/未激活”两套视觉态，提升可读性与触控辨识。
  - 状态文案颜色分层：开启/关闭/回退三态区分。
- 本地验证：
  - `gradle :app:compileDebugKotlin --no-daemon` 再次通过（2026-05-26）。

## Planning Refresh (EQ Full-screen Redesign, 2026-05-26)
- 用户已确认：
  - EQ 改为全屏横屏子页。
  - 左侧动态 bands 滑杆，右侧 preset 按钮网格。
  - 继续保留自动开启 + 外部开关同步开启。
  - 保留玻璃态风格，但重新调整颜色配比与文字层级。
  - fail-open 提示不常驻，仅在回退/降级时显示。
- 当前执行模块：
  - `M-S4-AUDIO-012`（EQ 全屏子页重做与验证）
- 当前 Ready：
  - `T-S4-AUDIO-065`：EQ 全屏子页骨架与横屏布局重构

## Historical Planning Refresh (EQ UI, 2026-05-25)
- 用户确认“EQ 已验证可用”，本轮切换为“先规划界面，不直接实现”。
- 新增模块 `M-S4-AUDIO-011`（EQ 界面规划与任务化）：
  - `T-S4-AUDIO-061` 入口/信息架构
  - `T-S4-AUDIO-062` 状态与 fail-open 反馈矩阵
  - `T-S4-AUDIO-063` 低保真线框与流程
  - `T-S4-AUDIO-064` 实现任务拆分与验收清单

## Module Execution Progress (EQ UI Planning, 2026-05-25)
- `M-S4-AUDIO-011` 已完成规划闭环（`T-S4-AUDIO-061~064`）：
  - 新增 `docs/API17_EQUALIZER_UI_PLAN.md`，覆盖：
    - 入口与信息架构（推荐保留设置页内嵌 EQ 卡片）
    - `off/pending/active/no-presets/fused` 状态矩阵
    - 低保真线框与交互流程
    - 实现任务化输出（`T-S4-AUDIO-065~068`）
- 队列状态变化：
  - `061~064` 已转 Done。
  - 新实现链 `065~068` 已进入 `Ready/Blocked` 序列。
- 当前边界：
  - `M-S4-AUDIO-011` 规划模块完成，下一步切换到 `M-S4-AUDIO-012` 实现模块。

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

## Planning Refresh (App EQ Fixed 10-band Trial, 2026-05-29)
- 已按新 scope 完成规划重排：
  - 新增模块 `M-S4-AUDIO-014`（应用内 EQ 固定 10 段直写试验）。
  - 新任务链：`T-S4-AUDIO-073 -> 074 -> 075/076 -> 077 -> 078`。
  - Ready 入口切换为 `T-S4-AUDIO-073`，推荐后续用 Module Mode 连续推进 `073~078`。
- 当前状态：
  - `Done`: 需求确认 + planning 回写。
  - `Ready`: 固定 10 段模型、逐 band 直写、安全持久化、窄滑杆 UI、验证清单。


## Module Execution Progress (App EQ Fixed 10-band Trial, 2026-05-29)
- `M-S4-AUDIO-014` 已完成本地闭环（`T-S4-AUDIO-073~078`）：
  - EQ UI 固定 10 段，不再读取/展示 ROM bands/presets。
  - 预设固定中文，切换预设会同步 10 个滑杆。
  - 滑杆视觉改为窄轨道/窄 thumb，接近系统 EQ 形态。
  - `EqualizerManager` 已改为 band `0..9` 逐段直写，单段失败不影响其它段。
  - 不预先禁用 band；只有真实写入异常后才提示。
  - 真实失败 band 回退到上一次持久化值，不写入失败配置。
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已补固定 10 段实机观察项。
- 本地验证：
  - `gradle :app:assembleDebug` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `git diff --check` 通过。
- 当前边界：
  - 是否长期保留 10 段直写，等待 API17 实机验证后进入 `T-S4-AUDIO-079` 决策。
