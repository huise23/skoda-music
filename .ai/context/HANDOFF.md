# HANDOFF

Last Updated: 2026-06-03

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@bb12c6b`
- 当前阶段: S5 子阶段 - Kugou Source Mode & Multi-Source Discovery
- 当前执行入口: None（计划内可执行任务已完成）

## User-Confirmed Requirements (Must Keep)
- API17 / Android 4.2.2 / AC83xx / 1024x600 横屏为硬约束。
- 后台服务、后台方向盘按键、全局浮窗和前台服务通知等既有稳定性口径继续保留。
- 当前音效主线为应用内保真 DSP；native DSP 本地链已完成，实机验证另行阻塞跟踪。
- Native DSP 播放页需要用播放/暂停按钮边框显示 runtime 状态，确认未进入 fail-open；刷新需低频节流，不按音频帧更新 UI。
- S5 新口径:
  - 来源需抽象，支持后续多套来源。
  - 默认进入酷狗模式。
  - 左侧一级导航快速切换，不新增二级 tab。
  - 酷狗登录必须可用，默认扫码，同时支持手机号验证码。
  - 酷狗 session 登录成功后缓存，下次启动优先复用，直到不可用再跳转登录。
  - 酷狗相关实现全部参考 `KugouMusic.NET/`；没有依据就停下问用户。
  - 点赞先抽象，本阶段暂只支持酷狗。
  - 点赞历史与状态需要可查看。
  - 点赞后播放缓存上传到 Emby 入库先作为阻塞项。
  - 本地缓存设计目标最大不超过 `100MB`。

## Latest Delta (Full Plan Execution, 2026-06-03)
- 已完成 `T-S4-AUDIO-096`:
  - `HiFiDspController` 新增 DSP runtime state。
  - `HiFiAudioProcessor` 发布 native active/degraded/fail-open 状态。
  - `MainActivity` 通过播放/暂停按钮边框显示灰/绿/黄/红状态，并复用 1s UI tick。
- 已完成 `T-S5-KG-096`:
  - 新增 `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`。
  - 覆盖登录、推荐歌曲、电台、发现歌单、播放 URL、点赞/我喜欢参考来源。
- 已完成 `T-S5-SRC-097`:
  - `MainModels.kt` 新增 `MusicSource / SourceTrack / SourcePlaylist / SourceRadio / SourcePlaybackRef` 等最小 source-aware 模型。
  - 新增 `docs/MULTI_SOURCE_NAV_CONTRACT.md`。
- 已完成 `T-S5-KG-098`:
  - 新增 `docs/KUGOU_AUTH_SESSION_CONTRACT.md`。
- 已完成 `T-S5-SRC-099`:
  - 新增 `docs/SOURCE_AWARE_PLAYBACK_QUEUE_CONTRACT.md`。

- 已完成 `T-S5-KG-101~105`:
  - Android 端新增 Kugou WebApi session store/client 和 like status store。
  - 设置页支持 Kugou WebApi Base URL、扫码登录、手机号验证码登录、登出和状态显示。
  - 默认推荐歌曲页未登录展示扫码二维码；session 可启动复用，失效清理后回登录。
  - 推荐歌曲、电台、电台歌曲、发现歌单标签、歌单列表、歌单歌曲已按 `.NET` WebApi/model 映射展示。
  - 酷狗歌曲点赞入口调用 `/playlist/tracks/add`，目标列表 ID `2` 参考 `FavoritePlaylistService.LikeListIdForAction`。
  - 点赞/入库状态页记录 `liked/failed/pending` 与 `blocked_ingest`，不执行 Emby 上传。
  - 最终验证通过 `git diff --check`、API17 guardrails、`compileDebugKotlin`、`assembleDebug`。
- 已完成 `T-S5-UI-100`:
  - 左侧入口调整为推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态、设置。
  - 默认启动进入推荐歌曲页，复用现有播放卡片保留播放控制与 DSP 状态按钮。
  - Home 内二级 tab 入口已隐藏，Queue 改为左侧一级入口。
  - 新增推荐电台、发现歌单、点赞/入库状态骨架页。
- 当前 Ready:
  - None。
- Blocked:
  - `B-KG-EMBY-INGEST-001` 点赞后将播放缓存上传到 Emby 并纳入媒体库。
  - `T-S4-AUDIO-095` AC83xx Native DSP 实机验证。


- 已完成 `T-S5-VAL-106` 和 `T-S5-PLAY-107`:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已覆盖 S5 酷狗登录、内容页、点赞状态、播放 URL 和 100MB 缓存守卫验证项。
  - 酷狗播放 URL resolver 接入 `/song/url`，参考 `SongController.GetUrl`、`SongClient.GetPlayInfoAsync`、`PlayUrlData`。
  - 酷狗歌曲点击可解析并通过 ExoPlayer 播放远程 URL；Emby 队列/download-only 主路径保持原样。
  - 播放缓存守卫覆盖 `emby_*.cache` 与未来 `kugou_*.cache`，上限 100MB。
  - 最终验证通过 `git diff --check`、API17 guardrails、`compileDebugKotlin`、`assembleDebug`。

## Execution Entry
1. 当前 S5 计划内可执行任务已完成。
2. 若继续推进，需要先确认下一阶段需求或解除阻塞：`B-KG-EMBY-INGEST-001` / `T-S4-AUDIO-095`。
3. 酷狗字段、接口、流程仍必须引用 `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md` 和 `KugouMusic.NET/`；`KugouMusic.NET/` 仍只读，不修改。

## Review Snapshot (2026-06-03)
- Review 状态: Done，未发现阻断提交/推送的问题。
- 验证通过: `git diff --check`、API17 guardrails、`compileDebugKotlin`、`assembleDebug`。
- `KugouMusic.NET/` 已加入 `.gitignore`，仅作为本地只读参考源码，不随 Android 改动提交。
- 后续工程风险: `MainActivity` 已承载较多酷狗 source UI/client 接线；新增来源前建议拆分 adapter/view binder。

## Important Source References
- `app/src/main/java/com/skodamusic/app/audio/dsp/HiFiDspController.kt`
- `app/src/main/java/com/skodamusic/app/audio/dsp/HiFiAudioProcessor.kt`
- `app/src/main/java/com/skodamusic/app/audio/dsp/NativeHiFiDspBridge.kt`
- `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/button_control_primary.xml`
- `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`
- `docs/KUGOU_AUTH_SESSION_CONTRACT.md`
- `docs/MULTI_SOURCE_NAV_CONTRACT.md`
- `docs/SOURCE_AWARE_PLAYBACK_QUEUE_CONTRACT.md`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/LoginClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/RecommendClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/FmClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/PlaylistClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/SongClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/UserClient.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/LoginController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/CaptchaController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/DiscoveryController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/FmController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/PlayListController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/MediaCatalogController.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/DiscoverViewModel.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/DailyRecommendViewModel.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/FavoritePlaylistService.cs`

## Validation Expectations
- 酷狗接口、字段、流程必须有 `.NET` 参考定位。
- 不修改 `KugouMusic.NET/`，只读参考。
- 实现任务最终至少执行:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - 触及资源/播放/native 时优先 `gradle :app:assembleDebug --no-daemon`

## Carry Forward
- `T-S4-AUDIO-095`:
  - Native DSP 本地实现已完成。
  - 仍需 AC83xx 实机验证 30 分钟长播与听感。
  - 不阻塞 S5 本地执行。
- `B-KG-EMBY-INGEST-001`:
  - Emby 上传播放缓存并入库能力未确认。
  - 当前阶段只记录点赞和入库阻塞状态，不执行上传。

## Historical Context Summary
- S4 Native DSP 本地闭环已完成：`T-S4-AUDIO-088~094` Done。
- 本地验证曾通过：`git diff --check`、API17 guardrails、`compileDebugKotlin`、`assembleDebug`。
- 现有 Android 端仍以 `MainActivity` 单 Activity、左侧竖向导航、`EmbyTrack`、download-only 播放链为核心。
- 新阶段首先要做抽象和接口映射，避免直接把酷狗字段硬塞进旧 Emby 模型。
