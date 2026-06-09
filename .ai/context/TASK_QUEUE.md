# TASK_QUEUE

Last Updated: 2026-06-09

## Ready
- None. `M-S5-UPD-046` 代码侧本地完成，下一步需要 API17 车机实机验证。

## Pending / Planned

- `T-S5-UPD-153`（P0, Module `M-S5-UPD-046`）: API17 应用内更新实机验证与证据回填，依赖 `T-S5-UPD-152` 和设备/可下载新版本 APK。
- `T-S5-DSP-149`（P0, Module `M-S5-DSP-045`）: DSP 音效无效 targeted fix，依赖 `T-S5-DSP-148` 和实机/手机 `hifi-dsp` 日志。
- `T-S5-TRIAGE-140`（P0, Module `M-S5-VAL-041`）: 真实设备失败分流与 targeted fix 计划，依赖 `T-S5-VAL-137`。
- `T-S5-VAL-137`（P0, Module `M-S5-VAL-041`）: S5 集成设备验证执行包与证据回填。原因：当前用户已提供新纠偏需求，验证清单需先由 `T-S5-OBS-147` 更新后再执行。
- API17 A~N/P 实机回归：等待本轮首页/发现页/DSP 诊断纠偏完成后，执行并回传 QR 登录、direct content、每日推荐、发现页、队列自动滚动、VIP、歌词、DSP 与 PostHog/logcat 脱敏证据。

## Blocked

### B-KG-EMBY-INGEST-001
- Priority: P2
- Module: `M-S5-INGEST-028`
- Execution Mode: Single
- Title: 点赞后将播放缓存上传到 Emby 并纳入媒体库
- Blocked By:
  - Emby 是否支持 API 上传音频并入库未确认。
  - 若不支持，需要确认服务端代理写入媒体目录 + 触发扫描方案。
  - 100MB 缓存上限与网络空闲重试策略需后续需求确认。
- Current Requirement:
  - 当前只记录点赞状态和入库阻塞状态，不执行上传。

### T-S4-AUDIO-095
- Priority: P1
- Module: `M-S4-AUDIO-021`
- Execution Mode: Single
- Title: AC83xx 实机长播与听感验证
- Blocked By: external AC83xx device window
- Current Requirement:
  - 安装 native DSP APK。
  - 开启 `保真` 连续播放，确认是否消除广播感卡顿。
  - 回传 `hifi-dsp native status=... mode=... tier=... costUs=... flags=...` 日志样本。

## In Progress
- None

## Done
- `T-S5-UPD-152`: 更新链路观测与 API17 回归清单升级；PostHog/runtime 增加安装阶段、路径/URI、pre-parse 和 installer resolve 低敏字段，并更新 F 组回归项。
- `T-S5-UPD-151`: API17-safe APK 文件位置与安装 intent handoff 修复；API17 安装前复制 APK 到公开 Downloads/`SkodaMusicUpdates`，设置可读并用 `file://` + APK MIME 拉起安装器。
- `T-S5-UPD-150`: 更新安装链路职责拆分与诊断模型；新增 `AppUpdateApkVerifier`、`AppUpdateInstaller`、`AppUpdatePackageInspector`，降低 `AppUpdateManager` 职责和行数。
- `T-S5-DSP-148`: DSP 红圈原因显示与 runtime/logcat 采证补齐；播放按钮 DSP 指示按完整 runtime state 低频刷新并记录 `hifi-dsp indicator status=... reason=...`。
- `T-S5-OBS-147`: 首页/发现页纠偏观测与 API17 回归清单更新；同步 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 与 `docs/S5_OBSERVABILITY_COVERAGE.md`。
- `T-S5-DISC-146`: 发现页紧凑 tab + 歌单网格 UI 重构；隐藏独立 Scene 左侧入口，去掉发现页标题/状态占位，一级/二级 tab 多行展示并自动加载歌单。
- `T-S5-DISC-145`: 发现页一级/二级分类模型与 `.NET` 行为对齐；保留 `categoryName/tagName` 分组，不再把标签压平成“分类 · 标签”。
- `T-S5-HOME-144`: 首页播放块增加点赞按钮；酷狗当前曲可直接复用现有 `requestLikeTrack`。
- `T-S5-HOME-143`: 首页歌词酷狗 direct 化与 10s 空闲切回歌词；新增 `KugouLyricClient` 与 `HomeLyricsBinder`，按 `.NET` lyrics search/download + KRC/LRC decode/parse。
- `T-S5-HOME-142`: 首页右侧当前队列跟随下一曲与自动滚动修复；当前播放变化、自然下一曲、权限跳过和 Emby/Kugou 切换路径刷新 Home queue。
- `T-S5-HOME-141`: 每日推荐启动自动播放与左侧入口列表展示分离；启动自动播保留，左侧入口只展示当日推荐列表，手动切其它列表后不抢播。
- `T-S5-MAIN-139`: `EqualizerPageBinder` 提取；新增 focused binder 承接音效页开关、入口、返回、模式按钮和 fullscreen page 渲染，`MainActivity` 只保留 DSP 状态 apply/persist 委托。
- `T-S5-MAIN-138`: `RuntimeLogBinder` 提取；新增 focused binder 承接 runtime log buffer、preview、fullscreen dialog、copy/clear 与 destroy dismiss，保留后台线程 append 后主线程渲染防护。
- `T-S5-VIP-133`: VIP direct user client 与记录/领取/升级解析；新增 `KugouDirectUserClient`，按 `.NET` `RawUserApi` 接入 record/receive/upgrade。
- `T-S5-VIP-134`: 每日 VIP coordinator、本地兜底记录与失败重试；新增 `KugouDailyVipCoordinator`，冷启动/登录成功触发，失败退避/冷却。
- `T-S5-PLAY-135`: 播放 URL 失败分类与无权限/VIP UI 提示；`/v5/url` 扩展为 `KugouPlayUrlResult`，分类权限/VIP/付费/试听不可用。
- `T-S5-OBS-136`: VIP/权限失败观测、PostHog 查询限制记录与 API17 回归清单；补 `kugou_daily_vip_*` 和 `kugou_direct_play_url_failed` 分类字段。
- `T-S5-HOME-128`: 左侧每日推荐入口、酷狗模式隐藏旧 Emby 队列按钮、首次/手动每日推荐自动播放；新增 `DailyRecommendCoordinator`，本地 compile/assemble 通过，等待设备验证。
- `T-S5-SCENE-129`: Scene `.NET` direct 来源映射与 Android client/模型边界；新增 `KugouSceneContentClient`，按 `SceneClient` / `RawMediaCatalogApi` scene list/module/audio/music 建立最小 direct client，等待真实账号响应验证。
- `T-S5-HOME-130`: 首页右侧当前队列面板与自动滚动；新增 `HomeQueuePanelBinder`，覆盖 Emby、普通 Kugou、Scene queue、Radio history/current/upcoming。
- `T-S5-UI-131`: Radio/Scene 缩略图网格与 Scene tab 展开收缩；新增 Scene 页面和 `KugouSceneBinder`，Radio/Scene 卡片使用轻量异步缩略图加载。
- `T-S5-OBS-132`: 首页/Scene/当前队列交互观测与 API17 回归清单；更新 `docs/S5_OBSERVABILITY_COVERAGE.md` 与 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`。
- `T-S5-KG-123`: Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞 direct 化；移除当前路径剩余旧 `KugouWebApiClient` baseUrl gate，按 `.NET` RawFmApi/RawDiscoveryApi/RawPlaylistApi/FavoritePlaylistService 迁移，本地 `compileDebugKotlin` 通过，等待设备验证。
- `T-S5-KG-124`: 登录弹窗与 post-login 默认页自动加载协调；首页 QR 改弹窗、登录成功/缓存 session 自动拉首页推荐。
- `T-S5-KG-125`: 内容页懒加载与失败可重试策略；Radio/Discover 进入时加载，失败显示点击重试且不清旧内容。
- `T-S5-KG-126`: token/session 失效弹窗恢复，不清内容；本地 session 缺失/不可用时弹窗登录，显式登出仍清内容。
- `T-S5-OBS-127`: 登录后加载/懒加载/token 恢复观测与回归清单；新增脱敏事件并更新 PostHog/观测/API17 文档。
- `T-S5-KG-122`: Android QR auth 与 `.NET` 登录态一致化 + 默认推荐/播放 direct 最小闭环；扫码 `userid/token` 成功即 `VALID`，首页推荐走 direct `/everyday_song_recommend`，推荐歌曲播放 URL 走 direct `/v5/url`，不再依赖旧 WebApi baseUrl；已提交并推送 `master@18c4723`。
- `T-S5-KG-121`: QR 扫码登录失败 + 默认酷狗启动 source gate 热修；扫码 token 成功即登录，设备/token refresh 失败只记录 deferred；默认酷狗冷启动跳过 Emby resume/autoplay/auto-refresh。
- `T-S5-OBS-120`: S5 新功能 PostHog 覆盖补齐与敏感字段审计；补齐 QR/content/queue/radio 低频事件，新增 `docs/S5_OBSERVABILITY_COVERAGE.md`，DSP 继续用 runtime/logcat 热路径证据。
- `T-S5-KG-119`: QR refresh crash hotfix + fail-soft observability；修复二维码图片 URL/request 构造未捕获异常，增加 QR refresh generation guard、失败可重试状态和脱敏 PostHog/runtime 事件。
- `T-S5-VAL-113`: S5 纠偏 API17 回归清单与本地验证完成；`docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 已补充 M/N 分组与 T-S5-VAL-113 本地验证快照。
- `T-S5-MAIN-116`: 页面壳拆分试点评估完成，新增 `docs/PAGE_SHELL_SPLIT_EVALUATION.md`；结论为当前阶段暂缓独立 Activity/直接 Fragment，先抽 `RuntimeLogBinder`/`EqualizerPageBinder`，再用 Binder 包装做 Fragment 试点。
- `T-S4-AUDIO-097`: DSP 播放按钮持续红框诊断与修正；`HiFiAudioProcessor` 对 API17/ExoPlayer heap ByteBuffer 增加 direct scratch bridge，non-direct buffer 不再直接发布 FAIL_OPEN，真实 native-not-ready/native-process-error/bypass/error 仍保持红色诊断。
- `T-S5-PLAY-112`: Kugou Radio/FM session 按 .NET PersonalFmService 实现，新增 `KugouRadioSessionManager`，radio active 时 next/previous/completion 由 radio session current/upcoming/history 推进，队列页展示 radio current+upcoming。
- `T-S5-MAIN-115`: MainActivity 第三轮拆分：新增 `KugouContentRenderer` 与 `KugouContentBinder`，迁出推荐歌曲、Radio、发现页和普通 Kugou 队列页的渲染、请求、list/loading/selected state；`MainActivity.kt` 约 6118 -> 5632 行。
- `T-S5-PLAY-111`: Kugou 普通歌曲队列按 .NET PlaybackQueueManager 实现
- `T-S5-PLAY-110`: 纯酷狗播放状态边界，切断 Emby 队列叠加
- `T-S5-KG-118`: Kugou direct device init / token refresh / session validation
- `T-S5-KG-117`: Kugou direct raw API 最小登录链路移植
- `T-S5-KG-109`: Kugou WebApi Base URL 产品路径纠偏与 direct API 可行性确认
- `T-S5-MAIN-114`: MainActivity 第二轮拆分：Kugou Auth/Config Binder
- `T-S4-AUDIO-080`: ExoPlayer DSP 接入落点确认
- `T-S4-AUDIO-081`: 音效模式状态模型与配置迁移
- `T-S4-AUDIO-082`: Fail-open DSP AudioProcessor 骨架实现
- `T-S4-AUDIO-083`: 轻量 DSP 模式引擎实现（Kotlin 版）
- `T-S4-AUDIO-084`: 音效子页与设置页体验替换
- `T-S4-AUDIO-085`: 模式切换联动与旧 EQ 主线下线
- `T-S4-AUDIO-086`: API17 回归清单更新
- `T-S4-AUDIO-088`: Native DSP JNI API 与 fail-open 契约
- `T-S4-AUDIO-089`: 性能档位、预算阈值与日志字段契约
- `T-S4-AUDIO-090`: Native bridge scaffold 与 no-op/bypass buffer 处理
- `T-S4-AUDIO-091`: C++ DSP 模式引擎与系数预计算
- `T-S4-AUDIO-092`: 自动降档、耗时统计与节流日志
- `T-S4-AUDIO-093`: `HiFiAudioProcessor` 热路径迁移到 native
- `T-S4-AUDIO-094`: 本地验证、guardrails 与 API17 清单更新
- `T-S4-AUDIO-096`: Native DSP 播放按钮 fail-open 状态指示
- `T-S5-KG-096`: KugouMusic.NET 接口能力映射与缺口检查
- `T-S5-SRC-097`: 多来源领域模型与左侧一级导航契约
- `T-S5-KG-098`: 酷狗登录与 session 缓存契约
- `T-S5-SRC-099`: Source-aware 队列与播放解析边界设计
- `T-S5-UI-100`: 左侧导航与默认酷狗模式页面骨架
- `T-S5-KG-101`: 酷狗登录 UI 与 session 缓存实现
- `T-S5-KG-102`: 酷狗推荐歌曲页面接入
- `T-S5-KG-103`: 酷狗推荐电台页面接入
- `T-S5-KG-104`: 发现歌单分类、歌单列表与歌曲列表接入
- `T-S5-LIKE-105`: 酷狗点赞抽象与历史/状态页
- `T-S5-VAL-106`: S5 API17 回归清单与本地验证
- `T-S5-PLAY-107`: 酷狗播放 URL 解析与 100MB 缓存守卫实现
- `T-S5-MAIN-108`: MainActivity 第一轮拆分边界落地

## Superseded
- `T-S4-AUDIO-087`: Kotlin DSP API17 实机听感验证。原因：AC83xx 已反馈 Kotlin 热路径卡顿，已由 native 优化链取代；后续实机验证改走 `T-S4-AUDIO-095`。

## Recommended Execution Mode
- 当前最高优先级是 `T-S5-VAL-137` 设备验证闭环；设备窗口不可用时，`M-S5-MAIN-042` 已完成 RuntimeLog/EQ 两个低耦合 Binder 提取，下一轮需重新规划后续 MainActivity 拆分目标。
- 设备验证仍保留：手机/模拟器确认 `T-S5-KG-122` 的扫码、首页推荐、推荐歌曲播放 direct 最小闭环。
- 设备验证新增：每日推荐自动播放、首页当前队列、Radio/Scene 网格缩略图、Scene tab 展开收缩、各类队列自动滚动当前歌曲。
- 设备验证新增：每日 VIP record/receive/upgrade、record fallback、失败重试、无权限/VIP 播放提示。
- Radio/发现/点赞当前路径已 direct 化；若实机发现协议字段不一致，必须回到 `KugouMusic.NET/` 继续核对，不得猜测。
- `T-S5-MAIN-114` 已完成；后续 API/config 改动必须走 `KugouAuthConfigBinder` 与 `kugou/*`，不要把逻辑加回 `MainActivity`。
- 后续新增功能必须有足够 PostHog/runtime/logcat 证据，并进行敏感字段审计。
