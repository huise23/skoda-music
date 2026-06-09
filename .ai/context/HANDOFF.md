# HANDOFF

Last Updated: 2026-06-09

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master`（本轮 Home/Discover/DSP review 后提交推送；具体 commit 以 `git log -1` 为准）
- 当前阶段: S5 纠偏 - Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split
- 当前执行入口: `M-S5-FIX-046` 本地完成。下一步优先执行 `T-S5-VAL-137` 手机/API17 设备验证与证据回填。

## Latest Delta (Targeted Feedback Fixes, 2026-06-09)
- 本地 Done:
  - `T-S5-FIX-150`: 酷狗歌词链路补 `.NET` Default signature 参数/header、候选容错解析、KRC parse-empty 后 LRC fallback，并记录脱敏 search/download/decode/parse stage。
  - `T-S5-FIX-153`: 每日推荐手动入口改为列表语义；点击左侧入口取消 pending auto-play，只展示当日列表，列表歌曲点击才替换当前播放列表并播放。
  - `T-S5-FIX-151`: 首页播放块点赞按钮新增状态反馈，覆盖 disabled/ready/pending/liked/failed。
  - `T-S5-FIX-152`: 发现页一级/二级 tab 改为无边框高亮并增大字号，一级/二级使用不同高亮样式。
  - `T-S5-FIX-154`: 更新 API17 回归清单与 S5 观测覆盖。
- 新增文件:
  - `app/src/main/java/com/skodamusic/app/ui/HomePlaybackActionsBinder.kt`
  - `app/src/main/java/com/skodamusic/app/ui/HomeTabsBinder.kt`
  - `app/src/main/res/drawable/discover_primary_tab_active.xml`
  - `app/src/main/res/drawable/discover_primary_tab_idle.xml`
  - `app/src/main/res/drawable/discover_secondary_tab_active.xml`
  - `app/src/main/res/drawable/discover_secondary_tab_idle.xml`
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败：既有 `MainActivity.kt` entry file red-line 和一个 314 行方法；本批将 `MainActivity.kt` 从约 5579 行降到 5568 行，未新增 blocking finding。
- Ready:
  - `T-S5-VAL-137`: 安装当前 debug APK，执行手机/API17 集成验证并回填证据；优先覆盖歌词多曲、每日推荐手动入口、点赞状态和发现页 1024x600 tab。
- Deferred:
  - 方向盘语音按钮覆盖、语音助手、系统首页卡片、系统镜像进一步分析、高德地图车机调用联动均不进入本批实现；详见 `.ai/context/SYSTEM_IMAGE_DISCUSSION_NOTES.md`。
- 工程注意:
  - `MainActivity.kt` 仍约 5568 行且超过 red-line；后续只能 wiring/delegation，继续优先拆 focused binder。
  - 歌词协议和解析归 `KugouLyricClient`，歌词 UI 归 `HomeLyricsBinder`。
  - 每日推荐必须保留冷启动自动播口径，同时手动入口不得改变当前播放列表。
  - 发现页 UI 如需较大调整，优先拆 `KugouDiscoverRenderer`，不要继续堆大 `KugouContentRenderer`。

## Latest Delta (Home UX + Discover + DSP Diagnostics, 2026-06-08)
- 本地 Done:
  - `T-S5-HOME-141/142/143/144`: 每日推荐入口列表化、首页队列跟随、酷狗 direct 歌词 + 10s idle 切回、播放块点赞按钮。
  - `T-S5-DISC-145/146`: 发现页恢复 `.NET` category/tag 分组，一级/二级 tab 多行展示，歌单网格卡片，隐藏独立 Scene 入口。
  - `T-S5-OBS-147`: 更新 API17 回归清单与 S5 观测覆盖。
  - `T-S5-DSP-148`: 播放按钮 DSP 指示记录完整 runtime state 和 reason。
- 新增文件:
  - `app/src/main/java/com/skodamusic/app/kugou/KugouLyricClient.kt`
  - `app/src/main/java/com/skodamusic/app/ui/HomeLyricsBinder.kt`
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败：既有 `MainActivity.kt` entry file 5579 行和一个 314 行方法。

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
- 酷狗登录成功后默认页优先自动加载，其它页进入时懒加载；token/session 运行中失效不得清空现有内容，应弹窗登录，登录成功后隐藏并继续当前页面。
- 后续新增功能必须有足够 PostHog/运行时诊断日志；日志与事件属性必须过滤 token、session、手机号、验证码、完整 URL query、认证 header 等敏感信息。
- `MainActivity.kt` 过大问题当前必须优化，不再只是记录债务。
- “保持单 Activity 外壳”不是长期硬约束；允许在 API17 兼容且不破坏左侧一级快速切换的前提下，后续拆 Activity、页面壳、Fragment、Controller 或 Binder。
- 点赞后播放缓存上传到 Emby 入库继续阻塞；本地缓存设计目标最大不超过 `100MB`。
- 首页中间播放块不改成推荐；左侧新增“每日推荐”按钮。
- 首次进入首页自动加载每日推荐并播放第一首；每日推荐当天一批，不提供刷新按钮。
- 左侧“每日推荐”入口只展示当日推荐列表，不直接播放；点击列表歌曲才播放。
- 用户手动切到任意其它播放列表后，每日推荐不得再自动抢播。
- 酷狗模式隐藏旧 Emby 队列按钮；Emby 显式入口仍保留。
- 首页右侧展示当前播放列表/队列，不再作为推荐列表；每日推荐、普通歌单、Radio、发现页场景/歌单、Emby 显式队列均需在当前曲目变化时更新选中态并自动滚动到当前歌曲。
- Radio 队列展示 current/upcoming/history。
- Radio/Scene 入口使用带缩略图网格。
- `.NET` UI 没有独立 Scene 页面；Android 不继续扩展独立 Scene 左侧入口，场景能力并入发现页。
- 发现页按一级 tab + 二级 tab + 歌单网格展示，去掉“发现歌单”标题行、“二级分类：xxx”说明行和刷新按钮；切换一/二级分类自动获取歌单。
- Scene 来源仍必须追溯到 `.NET` `SceneClient` / `RawMediaCatalogApi`；发现页 tab 不横向滚动，有图默认两行、无图默认三行，多余展开/收缩，点击后收缩。
- 首页歌词需要恢复，直接查酷狗歌词；参考 `.NET` `LyricClient` / `RawLyricApi`，`lyrics.kugou.com/v1/search` -> `/download` -> KRC/LRC decode/parse。
- 首页当前在播放列表 tab 且正在播放时，10s 无操作自动切回歌词 tab；空播放不处理。
- 播放块删除按钮前增加点赞按钮，行为同其它页面酷狗点赞。
- 每日首次启动/登录成功后必须按 `.NET` 自动领取一日 VIP；服务端记录优先，本地账号+日期记录兜底；失败自动重试但不阻塞启动/推荐/播放。
- 酷狗播放 URL 因无权限/VIP/付费不可用时必须明确提示“无权限/需要 VIP”，并记录脱敏 PostHog/runtime error_code。

## Latest Planning (Home UX + Discover + DSP, 2026-06-08)
- 已更新:
  - `SCOPE.md`
  - `DECISIONS.md`
  - `PLAN.md`
  - `MODULES.md`
  - `TASK_BREAKDOWN.md`
  - `TASK_QUEUE.md`
  - `NEXT_STEPS.md`
  - `CURRENT_STATUS.md`
- Ready:
  - `T-S5-HOME-141`: 每日推荐启动自动播放与左侧入口列表展示分离。
  - `T-S5-HOME-142`: 首页右侧当前队列跟随下一曲与自动滚动修复。
  - `T-S5-HOME-143`: 首页歌词酷狗 direct 化与 10s 空闲切回歌词。
  - `T-S5-HOME-144`: 首页播放块增加点赞按钮。
  - `T-S5-DISC-145`: 发现页一级/二级分类模型与 `.NET` 行为对齐。
  - `T-S5-DISC-146`: 发现页紧凑 tab + 歌单网格 UI 重构。
  - `T-S5-OBS-147`: 首页/发现页纠偏观测与 API17 回归清单更新。
  - `T-S5-DSP-148`: DSP 红圈原因显示与 runtime/logcat 采证补齐。
- Planned/Pending:
  - `T-S5-DSP-149`: 等 `T-S5-DSP-148` 和实机/手机 `hifi-dsp` 日志后做 targeted fix。
  - `T-S5-VAL-137`: 等本轮纠偏和回归清单更新后再执行设备验证闭环。
- 工程注意:
  - `MainActivity.kt` 当前仍约 5579 行，仍是 red-line 文件。后续不得把歌词解析、发现页状态机、队列滚动策略、DSP reason 解释堆回入口文件。
  - `activity_main.xml` 约 1452 行，已是 layout warning；发现页 UI 优先复用容器 + 程序化 renderer，避免 XML 急剧膨胀。
  - `KugouMusic.NET/` 只读参考，不修改。

## Latest Delta (MainActivity RuntimeLog/EQ Binder Extraction, 2026-06-07)
- 本地 Done:
  - `T-S5-MAIN-138`: 新增 `RuntimeLogBinder`，迁出 runtime log buffer、preview、fullscreen dialog、copy/clear 和 destroy dismiss。
  - `T-S5-MAIN-139`: 新增 `EqualizerPageBinder`，迁出音效页开关、入口、返回、模式按钮和 fullscreen rendering。
  - `MainActivity` 保留 runtime log append 薄委托、DSP apply/persist、feedback/toast 和页面切换。
- 文件规模:
  - `MainActivity.kt`: 约 `6009 -> 5755` 行。
  - `RuntimeLogBinder.kt`: 143 行。
  - `EqualizerPageBinder.kt`: 207 行。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，仅因既有 `MainActivity.kt` red-line：5579 行、一个 314 行方法。
- Historical next recommendation:
  - 当时建议设备验证优先；该入口已被 2026-06-08 用户反馈覆盖。
  - 当前下一步以 `Latest Delta (Targeted Feedback Fixes, 2026-06-09)` 为准，即 `T-S5-VAL-137` 设备验证闭环。

## Latest Requirement (Daily One-Day VIP + Permission-Aware Playback, 2026-06-07)
- 用户确认:
  - 参考 `KugouMusic.NET` 实现每日 VIP 领取。
  - 能查服务端领取记录就查；不能查就本地存记录兜底。
  - 失败要自动重试，避免大量歌曲因无 VIP 无法播放。
  - 无权限播放失败必须提示无权限。
- `.NET` 依据:
  - `MainWindowViewModel.TryGetVip()`：本地 session 启动和登录成功后后台调用。
  - `RawUserApi.GetVipRecordAsync()`：`GET /youth/v1/activity/get_month_vip_record?latest_limit=100`。
  - `RawUserApi.GetOneDayVipAsync()`：`POST /youth/v1/recharge/receive_vip_listen_song?source_id=90139&receive_day=yyyy-MM-dd`。
  - `RawUserApi.UpgradeVipAsync(userid)`：`POST /youth/v1/listen_song/upgrade_vip_reward?kugouid=<userid>&ad_type=1`。
- 当前 Android 状态:
  - 已新增 `KugouDirectUserClient`：VIP record/receive/upgrade direct client。
  - 已新增 `KugouDailyVipCoordinator`：服务端记录优先、本地账号+日期兜底、失败自动重试/退避/冷却。
  - 已扩展播放 URL 失败分类，支持 `KUGOU_PLAY_PERMISSION_DENIED` / `KUGOU_PLAY_VIP_REQUIRED` 等 UI 与 PostHog error_code。
- PostHog 查询状态:
  - 仓库内置的是 PostHog capture/project key，可用于上报。
  - 当前环境和仓库未发现读取事件所需 personal API key / query token；因此本轮无法直接在线查询 PostHog 事件流。
  - 后续如提供可读 token，可按 `docs/POSTHOG_QUERY_EXPORT_TEMPLATE.md` 查询 `kugou_direct_play_url_failed` / `playback_failed` 最近 7 天 failure distribution。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，仅因既有 `MainActivity.kt` red-line。
- Next recommended execution:
  - 手机/API17 设备验证 `M-S5-VIP-040`：冷启动/登录成功触发 VIP、record 不可用 fallback、失败重试、无权限/VIP 播放提示和脱敏事件。

## Latest Delta (Home Daily Recommend + Scene/Grid + Queue Panel Done Locally, 2026-06-07)
- 新增模块:
  - `M-S5-HOME-038`: Kugou Home Daily Recommend & Current Queue Panel。
  - `M-S5-SCENE-039`: Kugou Scene & Grid Content。
- 本地 Done:
  - `T-S5-HOME-128`: 左侧每日推荐入口、酷狗模式隐藏旧 Emby 队列按钮、首次/手动每日推荐自动播放。
  - `T-S5-SCENE-129`: Scene `.NET` direct 来源映射与 Android client/model，新增 `KugouSceneContentClient`。
  - `T-S5-HOME-130`: 首页右侧当前队列面板，覆盖 Emby、普通 Kugou、Scene queue、Radio history/current/upcoming，并自动滚动当前曲。
  - `T-S5-UI-131`: Radio/Scene 缩略图网格，Scene tab 默认两到三排、展开/收缩、点击后收缩。
  - `T-S5-OBS-132`: 更新观测覆盖与 API17 回归清单。
- `.NET` 依据:
  - Scene: `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/SceneClient.cs`
  - Raw scene APIs: `RawMediaCatalogApi.GetSceneListsAsync()`, `GetSceneAudiosAsync()`, `GetSceneModulesAsync()`, `GetSceneModuleInfoAsync()`, `GetSceneMusicAsync()`
  - Daily recommend: `RawDiscoveryApi.GetRecommendSongAsync()` `/everyday_song_recommend`
  - Radio: `RawFmApi.GetRecommendAsync()` `/v1/rcmd_list`, `RawFmApi.GetSongsAsync()` `/v1/app_song_list_offset`
- Next recommended execution:
  - 手机/API17 设备验证每日推荐自动播放、Scene direct 真实字段、Radio/Scene 缩略图、Scene tab 展开收缩、当前队列自动滚动。
  - 若设备验证通过，继续 MainActivity 红线拆分；若 Scene 字段不一致，先核对 `.NET` 和真实响应再修字段映射。
- Architecture reminder:
  - `MainActivity.kt` is wiring-only for these tasks.
  - Do not modify `KugouMusic.NET/`.
  - Do not introduce high API dependencies; keep API17/1024x600 in mind.
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败：剩余 blocking 为既有 `MainActivity.kt` entry file 5952 行和粗略大方法 4188 行；本轮新增 `onCreate` blocking 已拆分降级。

## Latest Requirement (2026-06-07)
- 用户确认方案 B：
  - 酷狗登录成功后直接拉默认页，当前默认页为首页推荐歌曲。
  - 推荐电台、发现歌单/歌曲、点赞/相关页面进入时懒加载。
  - 列表加载失败时提示失败，并保留手动拉取/重试入口。
  - 运行中 token/session 失效时不清空现有内容，只弹窗登录。
  - 重新登录成功后隐藏弹窗，并继续当前页面/当前列表流程。
  - 首页登录入口改为弹窗登录，不再以内嵌登录面板作为主要交互。
- 状态:
  - `SCOPE.md`、`DECISIONS.md`、`PLAN.md`、`MODULES.md`、`TASK_BREAKDOWN.md`、`TASK_QUEUE.md` 已更新。
  - 本地实现已完成并通过构建验证；已纳入后续提交批次，尚未手机/API17 实机验证。
- 下一手:
  - 手机/API17 验证：弹窗登录、登录后首页自动加载、Radio/Discover 懒加载失败重试、缺登录态不清内容。
  - 手机/API17 验证：Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞 direct 路径。

## Latest Delta (T-S5-KG-123 Done Locally, 2026-06-07)
- 已完成:
  - Radio 推荐按 `.NET` `RawFmApi.GetRecommendAsync()` 直连 `/v1/rcmd_list`。
  - 电台歌曲按 `.NET` `RawFmApi.GetSongsAsync()` 直连 `/v1/app_song_list_offset`。
  - 发现标签按 `.NET` `RawPlaylistApi.GetPlaylistTagsAsync()` 直连 `/pubsongs/v1/get_tags_by_type`。
  - 发现歌单按 `.NET` `RawDiscoveryApi.GetRecommendedPlaylistsAsync()` 直连 `/v2/special_recommend`。
  - 歌单歌曲按 `.NET` `RawPlaylistApi.GetPlaylistSongsAsync()` 直连 `/pubsongs/v2/get_other_list_file_nofilt`。
  - 点赞按 `.NET` `FavoritePlaylistService` 喜欢列表 ID `2` + `RawPlaylistApi.AddSongsToPlaylistAsync()` 直连 `/cloudlist.service/v6/add_song`。
  - `KugouContentBinder` 当前 Radio/Discover/playlist song 路径不再依赖 `resolveBaseUrl()` 或旧 `KugouWebApiClient`。
  - 新增脱敏点赞事件：`kugou_like_request`、`kugou_like_success`、`kugou_like_failed`；内容页继续复用 direct content request/success/failed。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍失败，仅因既有 `MainActivity.kt` red-line：文件 5840 行、粗略大方法 4169 行。
- 未验证:
  - 手机/API17 真实账号网络返回字段；如果某 direct endpoint 字段不一致，继续核对 `KugouMusic.NET/`，不要猜。

## Latest Delta (M-S5-KG-037 Done Locally, 2026-06-07)
- 已完成:
  - 首页登录入口改为弹窗 QR 登录，首页旧二维码图/URL 文本隐藏。
  - QR refresh 不再清 session/内容；登录成功或缓存 session 会自动拉首页推荐歌曲。
  - Radio/Discover 进入时懒加载；失败时保留页面并显示“加载失败，点击重试”。
  - 本地登录态缺失/不可用时弹窗登录，不清已有列表/队列；显式登出仍清内容。
  - 新增 `KugouLoginRecoveryCoordinator`，pending action 只保存枚举，不保存 token/session/QR key/URL/手机号/验证码。
  - 新增事件：`kugou_auth_dialog_shown`、`kugou_auth_recovery_resume`、`kugou_post_login_auto_load`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败：文件 5820 行、粗略大方法 4146 行。
- 仍未完成:
  - 手机/API17 实机扫码验证。
  - Radio/Discover/Like direct 化已在 `T-S5-KG-123` 本地完成，仍待实机验证。
  - 服务端 auth-invalid 精确识别；当前避免把普通网络/API 缺口误判为 token 失效。

## Latest Delta (T-S5-KG-121 Done, 2026-06-05)
- 已完成:
  - QR 扫码登录失败修复：扫码返回 `userid/token` 后立即进入酷狗登录态，贴近 `KugouMusic.NET` 登录流程。
  - device register/token refresh 变为后台增强校验；失败只记录 `kugou_session_validation_deferred`，不再提示“酷狗登录失败/登录不可用”。
  - 默认酷狗启动禁止 Emby 自动行为：不恢复 Emby cached queue、不自动播放、不触发 Emby recommendation auto-refresh。
  - `navLibrary` 和 `testEmbyButton` 是显式 Emby 激活入口。
- 新增观测:
  - `kugou_session_validation_deferred`
  - `resume_restore_skipped`
  - `emby_auto_refresh_skipped`
- 下一步重点验证:
  - 手机扫码成功后登录态可用，内容页能加载。
  - 冷启动 logcat 无 Emby resume/autoplay；PostHog/logcat 仅记录脱敏 stage/reason，不出现 token/session/URL/账号/密码。

## Latest Delta (T-S5-KG-122 Pushed, 2026-06-06)
- 已完成:
  - QR success 有 `userid/token` 时立即持久化为 `VALID` session，`hasSession()` 不再等待 device register/token refresh。
  - QR success 条件与本地 session gate 对齐：必须有有效 `userid` 和 `token`，避免假登录态。
  - device register/token refresh 仍在后台增强，失败只记录 `kugou_session_validation_deferred`，不覆盖登录态。
  - 新增 `KugouDirectContentClient`，按 `.NET` `RawDiscoveryApi.GetRecommendSongAsync()` 直连 `/everyday_song_recommend`。
  - 推荐歌曲播放 URL 按 `.NET` `RawSongApi.GetUrlAsync()` / `RawSearchApi.GetPlayUrlAsync()` 直连 `/v5/url`。
  - 新增事件：`kugou_direct_content_request`、`kugou_direct_play_url_request/success/failed`。
- 本地验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍仅因既有 `MainActivity.kt` red-line 失败：文件 5776 行、粗略大方法 4140 行。
- 仍未完成:
  - Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞仍依赖旧 `KugouWebApiClient` + `resolveKugouBaseUrl()`，需 `T-S5-KG-123` direct 化。
  - 尚无手机扫码实测证据。
- 工程质量:
  - 新 direct 协议逻辑在 `kugou/`，`MainActivity.kt` 只做小范围接线；但入口文件仍是 Needs Refactor，不应继续承载新业务逻辑。
  - 新增 PostHog/runtime 诊断仅记录 stage/error code/http code/exception type，不记录 token、dfid、mid、完整 query、cookie、手机号、验证码或 auth header。
- 下一手:
  - 先手机/模拟器验证扫码、首页推荐、推荐歌曲播放。
  - 若通过，再执行 `T-S5-KG-123` 或恢复 API17 A~N 回归。
  - 推荐 logcat: `adb logcat -v time SkodaMusicEmby:D SkodaPostHog:I AndroidRuntime:E Toast:E '*:S'`

## Previous Delta (T-S5-KG-119 + T-S5-OBS-120 Done, 2026-06-05)
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
