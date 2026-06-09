# PLAN

Last Updated: 2026-06-09

## Current Stage
- Stage Name: S5 纠偏 - Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split
- Scope Source: `.ai/context/SCOPE.md`（2026-06-09）

## Planning Refresh (Dev Hygiene + Emulator Network Gate, 2026-06-09)
- Trigger:
  - 用户确认只将系统镜像目录 `3.0.1-R-20210524.1733/` 加入 git 忽略，不泛化到其它目录。
  - 用户确认 Android Studio 模拟器上不强制判断 Wi-Fi 类型，但仍需要 active network connected；真机/车机继续保持 Wi-Fi gate。
- Scope Fit:
  - 属于当前 S5 的工程治理和开发环境兼容小闭环。
  - 不改变播放、酷狗、更新、Emby 或车机真实网络门禁语义。
  - 不进入系统镜像修改、系统 app 修改或 ROM 相关实现。
- Architecture Decision:
  - 新增 `M-S5-DEV-047`: Git Ignore Hygiene & Emulator Network Gate。
  - `.gitignore` 只加精确目录项 `3.0.1-R-20210524.1733/`。
  - 模拟器识别放入 focused helper，如 `DeviceEnvironmentDetector`，不把 `Build.*` 判断散落到 `MainActivity`。
  - `WifiNetworkGate` 保持网络 gate 责任：真机要求 Wi-Fi；模拟器要求 active network connected 即放行；离线仍拦截。
  - PostHog/runtime 字段需区分 emulator bypass 和真实 Wi-Fi，且不记录敏感信息。
- Current Code Reality:
  - `.gitignore` 当前只忽略 Gradle/build/dist/`KugouMusic.NET/`，系统镜像目录仍显示为 untracked。
  - `WifiNetworkGate.kt` 当前只把 `ConnectivityManager.TYPE_WIFI` 视为 connected，并用 `WIFI_NOT_CONNECTED` 上报阻断。
  - `MainActivity.kt` 只通过 `ensureWifiConnectedForNetworkRequest()` 委托网络 gate；本模块不应修改 Activity 逻辑。
  - `PostHogTracker.resolveNetworkType()` 也读取 active network type，但本轮不改变全局观测口径，除非执行时发现必须同步。
- Workstreams:
  - `W21 Git Hygiene`: 精确忽略当前系统镜像目录，恢复工作树可读性。
  - `W22 Emulator Network Gate`: API17-safe 模拟器检测 + `WifiNetworkGate` emulator connected 分支。
  - `W23 Validation/Docs`: 验证 git 状态、guardrails、compile，并记录下一步。
- Recommended Order:
  1. `T-S5-DEV-155`: 精确忽略本地系统镜像目录。
  2. `T-S5-DEV-156`: 模拟器网络 gate 兼容实现。
  3. `T-S5-DEV-157`: 本批验证与 context/checklist 回写。
- Validation Strategy:
  - `git status --short` 不再显示 `3.0.1-R-20210524.1733/`。
  - `git diff --check`、`./scripts/check_api17_guardrails.sh`、`gradle :app:compileDebugKotlin --no-daemon`。
  - `python scripts/check_code_health.py` 预期仍因既有 `MainActivity.kt` red-line 失败；本模块不得新增 red finding。
  - 模拟器手测：active network connected 时受 gate 保护的请求不因非 Wi-Fi 类型被拦截；离线仍拦截。

## Planning Refresh (Targeted Home/Discover Feedback Fixes, 2026-06-09)
- Trigger:
  - 用户实机反馈：当前任何酷狗歌曲歌词都稳定显示“暂无歌词”，需要修真实 search/download/decode/parse/cache 链路。
  - 用户确认：点赞按钮需要可见状态变化反馈，先以按钮状态变化为验收。
  - 用户反馈：发现页一级/二级 tab 字号太小且框选占空间；一级改无边框高亮并尽量一排放下，二级也无边框但使用不同高亮样式。
  - 用户纠正：手动点击左侧“每日推荐”只切页面并显示推荐列表，不得替换当前播放列表；只有点击推荐列表歌曲时才替换现有播放列表并从点击歌曲播放。
  - 用户确认：方向盘上下曲当前全局可用；方向盘语音键覆盖、系统首页卡片、Amap 联动和系统镜像读取先写入独立讨论文件，不进入本批实现。
- Scope Fit:
  - 属于 S5 首页歌词、点赞反馈、发现页可读性和每日推荐入口语义的小批 targeted fix。
  - 不改变既有冷启动/首次首页自动加载并播放每日推荐第一首口径，除非后续用户反向确认。
  - 不进入语音助手、系统镜像、系统首页卡片或高德车机联动开发。
- Architecture Decision:
  - 新增 `M-S5-FIX-046`: Lyrics, Like Feedback, Discover Tabs & Daily Recommend List Semantics。
  - `KugouLyricClient` 负责歌词 search/download/KRC-LRC decode/parse/cache 诊断和修复；不得把协议或解析逻辑放回 `MainActivity`。
  - 播放块点赞状态反馈优先由 focused action binder 或既有小范围 UI helper 承接；复用 `LikeStatusStore` 和既有点赞请求，不新增第二套点赞协议。
  - 发现页 tab 样式由 renderer 负责；若 `KugouContentRenderer` 继续膨胀，执行时优先拆 `KugouDiscoverRenderer`。
  - 每日推荐手动入口和列表歌曲点击必须是两个独立路径：manual nav only show list；song click builds/replaces current Kugou queue and plays clicked item。
- Current Code Reality:
  - `MainActivity.kt` 仍约 5579 行且超过 entry red-line；本批任务只允许做 wiring/delegation。
  - `HomeLyricsBinder.kt`、`KugouLyricClient.kt` 已存在，但实测结果显示链路仍全部落入暂无歌词，需要按阶段诊断修复。
  - `DailyRecommendCoordinator.kt` 已存在，但用户反馈说明手动入口仍可能影响当前播放列表语义，需要重检入口和列表点击边界。
  - `KugouContentRenderer.kt` / `KugouContentBinder.kt` 已接近大文件风险，tab UI 调整不得继续堆成 god renderer。
- Workstreams:
  - `W17 Lyrics Chain Fix`: 定位并修复酷狗歌词 search/download/decode/parse/cache 的全失败问题，补脱敏 stage 诊断。
  - `W18 Playback Action Feedback`: 播放块点赞按钮 pending/success/failure/not-likeable 状态反馈。
  - `W19 Discover Tab Readability`: 一级/二级 tab 无边框高亮、字号增大、1024x600 一排/多行布局稳定。
  - `W20 Daily Recommend Semantics`: 手动入口只展示列表，列表歌曲点击才替换播放队列。
- Recommended Order:
  1. `T-S5-FIX-150`: 酷狗歌词全为暂无歌词 targeted fix。
  2. `T-S5-FIX-153`: 每日推荐手动入口列表语义修正。
  3. `T-S5-FIX-151`: 首页播放块点赞按钮状态反馈。
  4. `T-S5-FIX-152`: 发现页一级/二级 tab 无边框高亮与字号调整。
  5. `T-S5-FIX-154`: 本批观测与 API17 回归清单更新。
- Validation Strategy:
  - 本地最小闭环：`git diff --check`、`./scripts/check_api17_guardrails.sh`、`gradle :app:compileDebugKotlin --no-daemon`。
  - 触及 XML/resource/布局后执行 `gradle :app:assembleDebug --no-daemon`。
  - `python scripts/check_code_health.py` 预期仍因既有 `MainActivity.kt` red-line 失败；本批不得新增 red finding，且不得让入口文件继续承担新逻辑。
  - 设备/手测重点：至少多首酷狗歌曲歌词能加载或明确失败 stage；点赞按钮状态变化可见；发现页 1024x600 tab 不重叠；手动每日推荐入口不改变当前播放，列表歌曲点击才替换队列。

## Execution Snapshot (2026-06-08)
- `T-S5-HOME-141/142/143/144`、`T-S5-DISC-145/146`、`T-S5-OBS-147`、`T-S5-DSP-148` 已本地完成。
- 本地验证: `git diff --check`、`./scripts/check_api17_guardrails.sh`、`gradle :app:compileDebugKotlin --no-daemon`、`gradle :app:assembleDebug --no-daemon` 通过。
- `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` 红线失败：5579 行和一个 314 行方法。
- 下一步: `T-S5-VAL-137` 设备验证；DSP 音效 targeted fix 等实机 `hifi-dsp indicator/native status` 日志。

## Planning Refresh (Home UX Correction + Compact Discover Rework, 2026-06-08)
- Trigger:
  - 用户确认：首次进入首页需要加载并播放每日推荐列表，除非手动切到其它播放列表。
  - 用户纠正：左侧每日推荐入口不应点击即播放，应展示推荐列表，点击列表歌曲才播放。
  - 用户反馈：首页右侧队列未同步滚动，甚至未跟随下一曲。
  - 用户确认：首页歌词需要恢复，直接查酷狗歌词；播放列表 tab 10s 未操作自动切歌词，空播放不处理。
  - 用户确认：播放块删除按钮前增加点赞按钮，行为同其它页面点赞。
  - 用户确认：`.NET` UI 没有独立 Scene 页面，发现页采用一级 tab + 二级 tab + 歌单网格；Android 发现页需紧凑化，去掉标题/说明/刷新占位，一二级切换自动获取歌单。
- Scope Fit:
  - 属于 S5 首页、播放队列、发现页、酷狗歌词和点赞体验纠偏范围。
  - 覆盖了 2026-06-07 已实现但实机反馈不符合预期的部分；旧 `M-S5-HOME-038/M-S5-SCENE-039` 结果作为被修正的基线，不再直接进入设备验收。
- Architecture Decision:
  - 新增 `M-S5-HOMEUX-043`: Home Startup Playback, Queue Sync, Lyrics & Like Actions，负责每日推荐启动/入口行为、首页右侧队列同步、首页歌词与播放块点赞。
  - 新增 `M-S5-DISCOVER-044`: Compact Kugou Discover Category/Grid Rework，负责发现页一级/二级 tab 与歌单网格，吸收现有 Scene 能力，移除独立 Scene 产品入口。
  - 新增 `M-S5-DSP-045`: DSP Runtime Evidence & Red Ring Correction，负责红圈/音效无效的诊断可见性和有证据的 targeted fix。
  - `MainActivity` 只能做 view 绑定、导航委托和当前播放变化通知；歌词 search/download/decode、发现页 tab 状态、队列滚动策略、点赞状态机都必须在 focused client/binder/coordinator 内。
  - 酷狗歌词直接参考 `.NET` `LyricClient` / `RawLyricApi`：`/v1/search` -> `/download` -> KRC/LRC decode/parse。
- Current Code Reality:
  - `MainActivity.kt` 最新约 5579 行，首页歌词缓存、旧 LRC API 请求和歌词解析已迁出到 `HomeLyricsBinder` / `KugouLyricClient`；后续仍不能在入口文件扩写。
  - `HomeQueuePanelBinder` 已存在，但当前反馈表明“当前播放项变化”没有成为统一同步源；需要从播放切换、自动下一曲、radio advance、失败跳过、权限失败跳过等路径统一触发。
  - `DailyRecommendCoordinator` 当前支持启动/手动播放 guard，但需拆分“启动自动播放”和“左侧入口只展示列表”两种行为，并记录用户手动切换其它列表后的 auto-play lockout。
  - 现有 `KugouSceneBinder`/`KugouSceneContentClient` 可复用到发现页，但 `activity_main.xml` 仍有独立 `nav_kugou_scene/page_kugou_scene`，需隐藏或移除用户入口。
  - 发现页已有 direct tags/playlists/songs 逻辑，需改成一级/二级 tab 紧凑布局和切换即加载歌单，而不是独立刷新按钮。
  - 播放块已有删除按钮和其它页面点赞能力，需在删除按钮前增加 source-aware 点赞按钮并复用现有 `requestLikeTrack` 逻辑。
- New Workstreams:
  - `W14 Home UX Corrections`: 每日推荐启动/入口行为、首页右侧队列跟随、歌词恢复、播放块点赞。
  - `W15 Discover Rework`: 发现页一级/二级 tab、紧凑布局、歌单网格、Scene 能力合并、移除刷新/标题占位。
  - `W16 DSP Evidence/Triage`: 暴露红圈原因、采集 `hifi-dsp` runtime 证据，并根据真实原因修复。
- Recommended Order:
  1. `T-S5-HOME-141`: 每日推荐启动自动播放与左侧入口展示列表分离。
  2. `T-S5-HOME-142`: 首页右侧队列以当前播放变化为同步源，修复下一曲/跳过不跟随。
  3. `T-S5-HOME-143`: 首页歌词酷狗 direct 化并抽出 Binder，补 10s 空闲切回歌词。
  4. `T-S5-HOME-144`: 播放块删除按钮前增加点赞按钮。
  5. `T-S5-DISC-145`: 发现页一级/二级分类模型和 `.NET` 对齐。
  6. `T-S5-DISC-146`: 发现页紧凑 UI 与自动加载歌单，移除独立 Scene 用户入口。
  7. `T-S5-OBS-147`: 首页/发现页观测与 API17 回归清单更新。
  8. `T-S5-DSP-148`: DSP 红圈原因显示与日志采证；`T-S5-DSP-149` 根据证据 targeted fix。
- Validation Strategy:
  - 本地最小闭环：`git diff --check`、`./scripts/check_api17_guardrails.sh`、`gradle :app:compileDebugKotlin --no-daemon`、触及 layout 后 `gradle :app:assembleDebug --no-daemon`。
  - `python scripts/check_code_health.py` 预期仍因既有 `MainActivity` red-line 失败，但新任务必须让入口文件行数不增加或下降，且不得新增 red finding。
  - 设备/截图验收：1024x600 横屏发现页无横向 tab 滚动、无标题/刷新占位、队列跟随下一曲、歌词 10s 自动切回、DSP 红圈原因可见。

## Planning Refresh (Integrated Device Validation + MainActivity Red-Line Phase 3, 2026-06-07)
- Trigger:
  - `M-S5-KG-037`、`T-S5-KG-123`、`M-S5-HOME-038`、`M-S5-SCENE-039`、`M-S5-VIP-040` 已本地完成并通过 compile/assemble。
  - 当前剩余最高风险已从“功能未实现”转为“真实账号/API17 设备未验证 + `MainActivity.kt` 继续超过 red-line”。
- Scope Fit:
  - 设备验证属于 S5 成功标准的一部分，必须覆盖登录、direct content、每日推荐、Scene/Radio grid、队列自动滚动、VIP 与无权限播放提示。
  - `MainActivity.kt` 红线治理仍在当前 scope 内，且红线文件已回升到约 6009 行；后续开发不得继续扩大入口文件。
- Architecture Decision:
  - 新增 `M-S5-VAL-041`: S5 Integrated Device Validation & Evidence Closure，负责把设备验证、PostHog/logcat 证据和真实响应字段缺口结构化闭环。
  - 新增 `M-S5-MAIN-042`: MainActivity Red-Line Decomposition Phase 3，继续以低耦合 Binder 提取为主；首选 `RuntimeLogBinder`，其次 `EqualizerPageBinder`。
  - 设备验证失败后的协议/字段修复必须回到对应 focused client/binder；不得在 `MainActivity` 临时补协议逻辑。
  - 运行日志 UI、EQ 页面 UI、播放/service bridge 等职责不得继续留在入口文件中扩展。
- Current Code Reality:
  - `MainActivity.kt` 当前约 6009 行，`check_code_health.py` 仍报 entry red-line 与超长方法 blocking。
  - 已存在低耦合提取候选：
    - runtime log buffer/dialog/copy/clear: `MainActivity` 约 line 5517 起。
    - EQ/settings fullscreen rendering: `MainActivity` 约 line 5375 起。
  - `KugouDirectContentClient.kt` 约 762 行，处于 warning 边界但未越 red-line；后续若继续加协议，应拆分 song/fm/playlist/user client。
- New Workstreams:
  - `W12 S5 Integrated Device Validation`: 用一台手机/API17 设备跑完整 S5 验证，回填真实响应字段、截图/日志/PostHog 证据和失败分级。
  - `W13 MainActivity Red-Line Phase 3`: 提取 RuntimeLog/EQ 等低耦合页面 binder，为后续 Fragment 试点准备边界，并持续压降入口文件。
- Ready Queue Update:
  - `T-S5-VAL-137`: S5 集成设备验证执行包与证据回填。
  - `T-S5-MAIN-138`: `RuntimeLogBinder` 提取（低风险红线治理首刀）。
  - `T-S5-MAIN-139`: `EqualizerPageBinder` 提取（RuntimeLog 后续）。
  - `T-S5-TRIAGE-140`: 真实设备失败分流与 targeted fix 计划（依赖设备证据）。
- Recommended Next:
  - 若当前环境要继续开发，执行 `T-S5-MAIN-138` Single/Module。
  - 若设备窗口可用，执行 `T-S5-VAL-137` 并回填证据；失败后再进入 `T-S5-TRIAGE-140`。

## Planning Refresh (Home Daily Recommend, Scene Grid & Current Queue Panel, 2026-06-07)
- Trigger:
  - 用户纠正：不是把首页中间播放块改为推荐，而是在左侧按钮新增“每日推荐”。
  - 用户确认：首次进入首页加载每日推荐并播放第一首；酷狗模式隐藏 Emby 队列按钮；首页右侧展示当前播放列表/队列；Radio/Scene 网格带缩略图；Scene tab 不横向滚动；所有队列自动滚动到当前歌曲。
- Scope Fit:
  - 属于 S5 酷狗首页/content/queue/radio 体验纠偏范围。
  - Scene 来源已在 `KugouMusic.NET` 找到 `SceneClient` / `RawMediaCatalogApi` 依据，可进入规划；未确认字段解析仍需在执行时对照 `.NET` 和真实响应。
- Architecture Decision:
  - `MainActivity` 只做左侧按钮和页面委托接线，不承载每日推荐自动播放状态机、Scene 协议、网格渲染或队列自动滚动逻辑。
  - 首页右侧当前队列面板归属 `HomeQueuePanelBinder` 或等价 focused binder。
  - Scene direct 请求归属 `KugouSceneContentClient` 或 `KugouDirectContentClient` 的小型 scene 扩展，必须可追溯到 `.NET` scene raw API。
  - Radio/Scene 网格与 Scene tab 展开收缩归属 `KugouContentRenderer` 扩展或新的轻量 renderer；避免让 `KugouContentBinder` 演化成 god binder。
  - 每日推荐首次加载并播放第一首归属 `KugouContentBinder` 小范围扩展或 `DailyRecommendCoordinator`；播放仍走现有 Kugou queue/source session。
- Current Code Reality:
  - 左侧当前有 Home/Radio/Discover/Queue/Like/Settings；`nav_queue` 是独立队列页入口。
  - 首页右侧当前仍是 `home_recommend_panel`，并使用 `SwipeRefreshLayout`；新需求不需要每日推荐刷新按钮。
  - `KugouContentRenderer` 当前 Radio/Discover 仍以行列表渲染，未提供缩略图网格。
  - `KugouRadioSessionManager` 已有 current/upcoming/history；队列页目前最小展示未覆盖 history 自动滚动。
  - Scene Android direct client 尚未实现。
- New Workstreams:
  - `W10 Kugou Home Daily Recommend & Current Queue Panel`: 左侧每日推荐、隐藏酷狗模式 Emby 队列入口、首页右侧当前队列、所有队列自动滚动到当前歌曲。
  - `W11 Kugou Scene & Grid Content`: Scene direct 来源、Scene tab 展开收缩、Radio/Scene 缩略图网格。
- Ready Queue Update:
  - `T-S5-HOME-128`: 左侧每日推荐入口、酷狗模式隐藏 Emby 队列按钮、首次加载并播放每日推荐第一首。
  - `T-S5-SCENE-129`: Scene `.NET` direct 来源映射与 Android client/模型边界。
  - `T-S5-HOME-130`: 首页右侧当前队列面板与所有来源自动滚动到当前歌曲。
  - `T-S5-UI-131`: Radio/Scene 缩略图网格与 Scene tab 展开收缩。
  - `T-S5-OBS-132`: 新首页/Scene/队列交互观测与 API17 回归清单。

## Planning Refresh (Daily One-Day VIP + Permission-Aware Playback, 2026-06-07)
- Trigger:
  - 用户补充关键验收：每日首次启动还必须调用获取/领取一日 VIP 接口。
  - 用户确认：参考 `KugouMusic.NET`；能查服务端领取记录就查，不能查时本地按账号+日期记录；失败自动重试；无权限/VIP 播放失败要明确提示；PostHog 可查时应看播放 URL 失败分布。
- Scope Fit:
  - 属于 S5 酷狗 direct 登录、播放 URL 和播放失败体验纠偏范围。
  - `.NET` 依据已确认：`MainWindowViewModel.TryGetVip()`、`UserClient`、`RawUserApi`。
- Architecture Decision:
  - `KugouDirectUserClient` 负责 `/youth/v1/activity/get_month_vip_record`、`/youth/v1/recharge/receive_vip_listen_song`、`/youth/v1/listen_song/upgrade_vip_reward` direct 请求和最小字段解析。
  - `KugouDailyVipCoordinator` 负责每日触发、服务端记录优先、本地账号+日期 fallback、失败重试/退避/冷却和脱敏观测。
  - `KugouDirectContentClient` 的 `/v5/url` 返回扩展为成功 URL 或分类失败；UI 层根据分类展示“无权限/需要 VIP”。
  - `MainActivity` 只在 cached session / login success / permission failure 时做委托和 UI 状态更新，不承载 VIP 状态机。
- Ready Queue Update:
  - `T-S5-VIP-133`: VIP direct user client 与记录/领取/升级解析。
  - `T-S5-VIP-134`: 每日 VIP coordinator，本地兜底记录与失败重试。
  - `T-S5-PLAY-135`: 播放 URL 失败分类与无权限/VIP UI 提示。
  - `T-S5-OBS-136`: VIP/权限失败观测、PostHog 查询限制记录与 API17 回归清单。

## Planning Refresh (Post-login Loading + Login Dialog Recovery, 2026-06-07)
- Trigger:
  - 用户反馈：车机登录酷狗成功，但未自动加载各项列表。
  - 用户确认方案 B：登录成功直接拉默认页；其它页进入时懒加载；失败提示并保留手动拉取；运行中 token 失效不清现有内容，弹窗登录，登录后隐藏并继续当前页面。
- Scope Fit:
  - 属于当前 S5 酷狗登录/content 体验纠偏范围。
  - 不需要回 requirement；scope 和 decisions 已确认。
- Architecture Decision:
  - 新增或扩展轻量 UI/协调层处理登录弹窗、post-login action、内容加载触发，不把状态机塞回 `MainActivity.kt`。
  - `KugouAuthConfigBinder` 负责登录 UI/弹窗、QR 生命周期、登录成功回调。
  - `KugouContentBinder` 负责内容页加载状态、懒加载、失败提示和手动重试。
  - 如需跨 auth/content 协调，新增 `KugouLoginRecoveryCoordinator` 或等价小类；`MainActivity` 只接线。
  - `kugou/*` client 仍只负责 direct/API 请求，不承担 UI 策略。
- Current Code Reality:
  - `KugouContentBinder.requestRecommendedSongs()` 已走 direct 推荐歌曲。
  - `requestRecommendedRadios()`、`requestDiscoverTags()`、playlist/radio song 相关路径仍有旧 `resolveBaseUrl()`/`KugouWebApiClient` gate，失败时存在 `clearSessionState(true)` + `requestQrLogin()` 行为，需改成登录弹窗恢复且保留内容。
  - `KugouAuthConfigBinder.refreshLoginUi()` 仍控制首页登录 panel 可见性，尚未弹窗化。
- Ready Queue Update:
  - `T-S5-KG-124`: 登录弹窗与 post-login 默认页自动加载协调。
  - `T-S5-KG-125`: 内容页懒加载与失败可重试策略。
  - `T-S5-KG-126`: token/session 失效恢复，不清内容并登录后恢复当前页面。
  - `T-S5-OBS-127`: 对上述流程补齐脱敏观测和回归清单。
- Relationship To `T-S5-KG-123`:
  - `T-S5-KG-124/125/126` 可先落地交互策略。
  - 旧 baseUrl gate 仍会限制 Radio/发现/点赞真实数据加载；若执行中触碰这些 API，应与 `T-S5-KG-123` 建依赖或合并到 direct 化任务。

## Planning Refresh (QR Refresh Crash + Observability, 2026-06-04)
- Trigger:
  - 用户反馈：手机打开应用后点击“刷新二维码”，约 1-2 秒后崩溃；API17 车机实机尚未测试。
  - 用户确认：后续新增功能必须有足够 PostHog/运行时诊断日志，并且必须过滤敏感信息。
- Planning Decision:
  - API17 A~N 全量实机回归暂缓，先执行 QR 刷新崩溃热修；否则登录入口不稳定会污染后续回归结论。
  - QR 登录链路属于当前 S5 酷狗 direct auth/session 范围内的稳定性修复，不需要回到大范围 requirement。
  - 新增功能验收必须包含 observability：关键动作、异步请求结果、状态机跳转和失败路径要有 PostHog 结构化事件或 runtime/logcat 证据。
  - PostHog 不作为原始日志池；禁止上报 token/session/cookie/手机号/验证码/完整 URL query/auth header/API key/可复用设备凭据。
- Ready Queue Update:
  - `T-S5-KG-119`: Done 2026-06-05。
- Planned Queue Update:
  - `T-S5-OBS-120`: Done 2026-06-05。
- Execution Result:
  - QR refresh hotfix and S5 observability catch-up are complete locally.
  - Current remaining stage work is external device validation: API17 A~N regression evidence.

## Stage Goal
- 修正 S5 首轮实现中的方向偏差：
  - 酷狗不再要求用户提供 WebApi Base URL。
  - 默认酷狗模式必须是纯酷狗播放，不叠加 Emby 队列/续播/刷新。
  - 酷狗普通歌曲队列参考 `.NET` `PlaybackQueueManager`。
  - 酷狗电台/Radio 使用独立 session，next/previous 由电台会话内部推进。
  - DSP 播放按钮持续红色需要诊断并修正。
  - `MainActivity.kt` 过大问题从“记录债务”升级为当前必须拆分的前置任务，并且不再把单 Activity 外壳作为长期硬约束。

## Planning Refresh (Bootstrap Guardrail Alignment, 2026-06-04)
- 本轮 `$ai-planning` 已读取并纳入 bootstrap 后新增工程护栏：
  - `.ai/context/ARCHITECTURE.md`
  - `.ai/context/CODE_STANDARDS.md`
  - `.ai/context/RED_LINES.md`
  - `.ai/context/ENGINEERING_CHECKLIST.md`
- Code health reality:
  - `python scripts/check_code_health.py` 当前因既有 `MainActivity.kt` 红线失败。
  - Blocking: `MainActivity.kt` 约 6213 行，超过 entry file red line 1800。
  - Blocking: `MainActivity.kt` 中约 line 2242 的粗略方法体检测超 300 行红线。
  - Warning/refactor carry-forward: `SourceRowRenderer.kt` 方法长度 warning、`AppUpdateManager.kt` 文件长度 warning、`activity_main.xml` declarative UI 长度 warning。
- 规划调整:
  - `MainActivity` 红线治理变成当前阶段的硬前置，不再只是质量建议。
  - `T-S5-KG-109` 不再与 `T-S5-MAIN-114` 并列作为下一手首选 Ready；它保持 P0，但必须在 `T-S5-MAIN-114` 之后再执行，避免 WebApi 地址纠偏继续扩大入口文件。
  - Ready 队列收敛为：
    - `T-S5-MAIN-114`：入口文件红线拆分前置。
    - `T-S4-AUDIO-097`：与 `MainActivity` 红线基本解耦的 DSP 状态纠偏。
  - `T-S5-MAIN-115` 是第二个 MainActivity 红线治理任务，推荐在 `T-S5-KG-109` 前后根据实际改动面选择，但不得让 API/播放语义纠偏向 `MainActivity` 新增大块逻辑。

## Scope Validation

### In Scope
- MainActivity 持续可编译拆分，包含 Controller/Binder 拆分与页面壳拆分评估。
- 允许在 API17 兼容且不破坏左侧一级快速切换的前提下，后续试点 Fragment 或独立 Activity。
- 移除用户必填 Kugou WebApi Base URL 路径。
- 酷狗登录成功后默认页优先自动加载，其它页进入时懒加载。
- 左侧新增每日推荐入口；首次进入首页自动加载每日推荐并播放第一首，不提供刷新按钮。
- 首页右侧展示当前播放队列；酷狗模式隐藏旧 Emby 队列按钮。
- Radio/Scene 网格缩略图；Scene tab 非横向滚动，支持展开/收缩，点击后收缩。
- 所有队列/播放列表自动滚动到当前歌曲；Radio 展示 current/upcoming/history。
- token/session 运行中失效时弹窗登录，不清空现有内容，登录成功后恢复当前页面/列表。
- 纯酷狗 source playback state。
- 酷狗歌曲队列 `.NET` parity。
- 酷狗 radio/FM session `.NET` parity。
- DSP 持续 fail-open 红框诊断与修复。
- API17 / 1024x600 / 构建验证闭环。

### Out of Scope
- Emby 上传缓存入库。
- 非酷狗来源点赞真实实现。
- 没有 `KugouMusic.NET` 依据的酷狗能力扩展。
- 一次性全量 Activity 重写。
- 为减少行数牺牲后台控制、浮窗、方向盘按键、默认酷狗入口或 API17 兼容。
- 提升 minSdk 或引入高 API 依赖。

## Reality Check
- `MainActivity.kt` 当前约 6213 行，第一轮只迁出 source/list row UI 构造，仍集中承担导航、播放控制、Emby 队列、酷狗登录/content/like、DSP 状态、设置页、缓存清理和 service state。
- 酷狗歌曲当前通过 `playKugouTrack(SourceTrack)` 单曲直连远程 URL，未建立 `.NET` 风格队列。
- 电台歌曲当前也作为普通 `SourceTrack` 点击播放，未建立同一电台持续播放 session。
- `reportPlaybackStateToService()`、Now Playing artist/title、next/previous 等仍大量依赖 Emby `loadedTracks/currentTrackIndex`。
- 设置页的 `kugou_webapi_base_url_input` 已在 `T-S5-KG-109` 移除；QR direct 登录链路已在 `T-S5-KG-117` 落地，session validation 仍待 `T-S5-KG-118`。
- `.NET` 参考已确认：
  - 普通队列: `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`
  - 队列分流: `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.Queue.cs`
  - 电台/FM: `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/PersonalFmService.cs`
  - 电台/FM 分流: `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/PlayerViewModel.PersonalFm.cs`
  - SDK 直连 transport/raw api: `KugouMusic.NET/src/Libraries/KuGou.Net/Infrastructure/Http/` 与 `Protocol/Raw/`
  - Scene: `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/SceneClient.cs` 与 `RawMediaCatalogApi.cs` scene list/audio/module/music raw API。

## Architecture Plan

### Module / Layer Direction
- `MainActivity`: launcher、生命周期、左侧一级导航、播放/service bridge 的薄协调层。
- `ui/*Binder`: 页面或控件绑定、渲染协调、低频 UI 状态刷新；不得承载协议猜测或播放队列核心语义。
- `ui/*Coordinator`: 登录成功后动作、登录弹窗恢复、内容页懒加载触发等跨 binder 协调；保持小而聚焦。
- `ui/*QueuePanelBinder`: 当前播放列表/队列展示、选中项和自动滚动；不负责 API 请求或队列算法。
- `kugou/*`: 酷狗 API/session/client/store，所有行为必须可追溯到 `KugouMusic.NET/`。
- `playback/source session`: Emby、KugouSong、KugouRadio 三类播放 session 分流，不复用 Emby queue 语义表达酷狗队列。
- `audio/dsp`: DSP runtime 状态和 native 处理保持独立，UI 只低频读取状态。

### Entry File Responsibility
- `MainActivity` 只能接线 Android 生命周期、导航、顶层回调、后台服务/方向盘按键/浮窗桥接。
- 不能继续把酷狗登录、酷狗内容页、队列算法、电台 session、歌词解析、下载控制和 DSP 页面细节堆回入口文件。
- 不应把登录弹窗状态机、post-login pending action、懒加载判断、token 失效恢复策略写入 `MainActivity`。
- 不应把每日推荐自动播放、Scene 协议字段解析、tab 展开收缩、网格卡片构造、队列自动滚动策略写入 `MainActivity`。

### Page Shell Split Direction
- 单 Activity 外壳不是长期硬约束。
- 短期通过 Controller/Binder 降风险，避免在 6k 行 Activity 中继续修改。
- 中期通过 `T-S5-MAIN-116` 评估 Fragment/独立 Activity 试点，优先设置、日志、EQ 等低耦合页面。
- 播放页、service bridge、方向盘按键和浮窗相关拆分最后处理。

### File Size Guardrails
- `MainActivity.kt` 短期目标压到 5500 行以下，中期继续向 3000 行级别收敛。
- 新增 binder/controller 不应演化成新的 god file；单个新类应保持职责单一。

## Workstreams

### W1 MainActivity Decomposition
- 目标: 继续拆出低风险 controller/binder，并评估页面壳拆分路径，停止在 6k 行 Activity 中继续堆酷狗播放纠偏。
- 输出: 多轮可编译拆分，短期压到 5500 行以下；中期形成 Fragment/独立 Activity 试点或明确暂缓原因。

### W2 Kugou API Configuration Correction
- 目标: 移除用户必填 WebApi Base URL，按 `.NET` direct client/raw api 依据处理酷狗能力。
- 输出: 地址/config 策略、阻塞缺口清单或 Android 直连实现入口。

### W3 Pure Source Playback Boundary
- 目标: 将 Now Playing、service state、next/previous 从 Emby-only 状态中拆出来。
- 输出: source session state，酷狗播放不污染 Emby 队列。

### W4 Kugou Queue & Radio Session
- 目标: 普通歌曲走 `.NET` queue manager 语义；电台走独立 radio/FM session。
- 输出: `KugouQueueManager`、`KugouRadioSessionManager` 或等价实现。

### W5 DSP Red State Correction
- 目标: 找出持续红色原因并修正状态映射/复位/native bypass 策略。
- 输出: 红/黄/绿/灰状态可解释且不误报。

### W6 Validation
- 目标: 构建、guardrails、交互清单和回归证据闭环。
- 输出: 本地验证通过，清单覆盖纯酷狗、队列、电台、DSP。

### W7 QR Auth Stability & Observability
- 目标: 修复 QR 刷新崩溃，补齐 QR 登录/轮询/session validation 的脱敏 PostHog 与 runtime/logcat 证据。
- 输出: 刷新二维码失败可恢复、不闪退；PostHog 可看到 start/success/failure/error stage；敏感字段不进入日志。

### W8 Observability Catch-up
- 目标: 对 S5 新增酷狗内容、普通 queue、radio session、DSP direct-buffer bridge 等低频关键路径补齐结构化观测。
- 输出: 事件字典/代码埋点/回归清单一致，避免后续新功能无日志完成。

### W9 Kugou Post-login Loading & Recovery
- 目标: 登录成功后默认页自动加载，其它页懒加载；token/session 失效弹窗登录且不清内容，登录后恢复当前页面。
- 输出: 登录弹窗 UI 协调、post-login action、内容页懒加载状态、失败可重试和脱敏观测。

### W10 Kugou Home Daily Recommend & Current Queue Panel
- 目标: 左侧每日推荐入口、首次自动加载并播放第一首、酷狗模式隐藏 Emby 队列入口、首页右侧展示当前队列并自动滚动当前歌曲。
- 输出: Home/current queue focused binder、每日推荐自动播放协调、source-aware queue display adapter。

### W11 Kugou Scene & Grid Content
- 目标: 按 `.NET` Scene raw API 接入 Scene 内容，并将 Radio/Scene 入口改为带缩略图的网格和非横向滚动 tab。
- 输出: Scene direct client/model、Scene tab state、Radio/Scene grid renderer、失败可重试和脱敏观测。

### W12 S5 Integrated Device Validation
- 目标: 在真实手机/API17 环境验证 S5 已完成链路，收集可复盘证据并形成失败分流。
- 输出: Device Report、真实响应字段缺口、PostHog/logcat 证据、下一轮 targeted fix 任务。

### W13 MainActivity Red-Line Phase 3
- 目标: 继续从 `MainActivity.kt` 迁出低耦合页面/诊断职责，优先 RuntimeLog，其次 EQ 页面，为后续 Fragment 试点准备 Binder 边界。
- 输出: `RuntimeLogBinder` / `EqualizerPageBinder` 或等价 focused binder，入口文件行数下降且功能不回归。

## Dependency Graph
- `W1/T-S5-MAIN-114 -> W2/T-S5-KG-109 -> W3 -> W4 -> W6`
- `W1/T-S5-MAIN-114 -> W1/T-S5-MAIN-115 -> W1/T-S5-MAIN-116`
- `W2 -> W3`
- `W5` 可与 `W1/W2` 并行；`T-S4-AUDIO-097` 依赖已满足，可作为并行 Ready，但最终进入 `W6`。
- `W7/T-S5-KG-119 -> W6/API17 real-device regression`
- `W7/T-S5-KG-119 -> W8/T-S5-OBS-120`
- `W9/T-S5-KG-124 -> W9/T-S5-KG-125 -> W9/T-S5-KG-126 -> W9/T-S5-OBS-127`
- `W9` 与 `T-S5-KG-123` 存在接口依赖：旧 baseUrl 页可先接入懒加载/恢复策略，但真实 direct 数据能力仍由 `T-S5-KG-123` 完成。
- `W10/T-S5-HOME-128 -> W10/T-S5-HOME-130 -> W11/T-S5-UI-131 -> W11/T-S5-OBS-132`
- `W11/T-S5-SCENE-129 -> W11/T-S5-UI-131`
- `T-S5-HOME-130` 依赖现有 `KugouPlaybackQueueManager` / `KugouRadioSessionManager`，并需适配 Emby 显式队列。
- `W10/W11/M-S5-VIP-040/M-S5-KG-037/T-S5-KG-123 -> W12/T-S5-VAL-137`
- `W12/T-S5-VAL-137 -> T-S5-TRIAGE-140`
- `W13/T-S5-MAIN-138 -> W13/T-S5-MAIN-139 -> Fragment pilot re-evaluation`
- `B-KG-EMBY-INGEST-001` 继续阻塞，不参与当前 Ready。

## Recommended Order
1. `T-S5-VAL-137`: 设备窗口可用时先跑 S5 集成设备验证，回传证据。
2. `T-S5-MAIN-138`: 设备窗口不可用时，先提取 `RuntimeLogBinder`，降低入口文件红线。
3. `T-S5-MAIN-139`: RuntimeLog 稳定后提取 `EqualizerPageBinder`。
4. `T-S5-TRIAGE-140`: 基于设备验证结果拆 targeted fix，不在证据不足时猜协议。

## Completed Historical Order
- `T-S5-KG-119`、`T-S5-OBS-120`、`T-S5-MAIN-114`、`T-S4-AUDIO-097`、`T-S5-KG-109`、`T-S5-MAIN-115`、`T-S5-PLAY-110/111/112`、`T-S5-MAIN-116`、`T-S5-VAL-113` 已完成；保留为历史依据。

## Milestones
- M1: `MainActivity` 首轮拆分完成并可编译。
- M1.1: 酷狗登录/配置 UI 迁出 `MainActivity`，入口文件不再承载 WebApi 地址纠偏的主要 UI 状态。
- M1.2: 形成页面壳拆分试点结论，明确 Fragment/独立 Activity 的 API17 可行路径或暂缓理由。
- M2: 酷狗配置不再要求用户填写 WebApi 地址；若 direct API 存在缺口，形成明确阻塞。
- M3: 酷狗 Now Playing/service state/next/previous 不再依赖 Emby `loadedTracks`。
- M4: 普通酷狗歌曲列表有独立队列，next/previous 循环。
- M5: 电台有独立 session，下一曲由 radio session 内部推进。
- M6: DSP 正常播放不持续红色，异常状态有明确原因。
- M7: guardrails、compile、assemble 和回归清单完成。
- M8: QR refresh 在手机/模拟环境中重复点击、弱网/断网、接口异常时不崩溃，并能在 PostHog/logcat 中看到脱敏诊断链。
- M9: S5 新功能具备最低可用 PostHog 覆盖，后续 API17 回归可直接采集 `capture ok event=...` 证据。
- M10: 酷狗登录成功后首页推荐自动加载；其它酷狗页进入时懒加载；token 失效时弹窗登录且不清内容；登录后恢复当前页面。
- M11: 左侧每日推荐入口可用，首次进首页自动加载并播放第一首；酷狗模式隐藏 Emby 队列按钮。
- M12: 首页右侧当前队列覆盖每日推荐、歌单、Radio、Scene、Emby 显式来源，并自动滚动到当前歌曲。
- M13: Scene direct 来源可追溯到 `.NET`，Radio/Scene 网格缩略图和 Scene tab 展开收缩在 1024x600/API17 上可验收。

## Validation Strategy
- 本地:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `python scripts/check_code_health.py`（当前允许因既有 `MainActivity` 红线失败，但每个拆分任务必须证明红线趋势改善，不得新增 red findings）
  - `gradle :app:compileDebugKotlin --no-daemon`
  - 触及资源/播放/native 时执行 `gradle :app:assembleDebug --no-daemon`
  - 触及 PostHog/日志时检查敏感字段：不得包含 token、session key、cookie、手机号、验证码、完整 URL query、auth header、API key 或可复用设备凭据。
- 行为:
  - QR 刷新按钮连续点击 10 次不崩溃；断网、接口失败、图片下载失败、Activity 生命周期切换都进入可恢复状态。
  - 登录成功后首页推荐自动加载，失败提示且可手动重试。
  - 推荐电台/发现页进入时触发懒加载；旧内容在失败或 token 失效时不被清空。
  - token/session 失效时出现登录弹窗，登录成功后弹窗隐藏并继续当前页面。
  - 默认进入酷狗推荐歌曲。
  - 酷狗播放不自动恢复/刷新 Emby 队列。
  - 酷狗 next/previous 在酷狗队列或 radio session 内推进。
  - 电台 session active 时普通队列不接管下一曲。
  - DSP 红框只对应真实 fail-open/bypass/error。
- 文档:
  - 所有酷狗队列/电台/API 行为标注 `.NET` 参考文件。
  - 未确认能力必须标记 Blocked，不进入实现。
  - 新增/变更 PostHog event 名和关键属性同步到回归清单或事件说明，避免代码与验收脱节。

## Risks & Assumptions
- 风险: `MainActivity` 体量大，拆分时容易误动生命周期和 service bridge；后续先拆 Controller/Binder，再评估页面壳拆分。
- 风险: Fragment/多 Activity 拆分若过早触碰播放页和 service bridge，可能影响后台按键、浮窗和 resume；页面壳试点优先低耦合页面。
- 风险: Android 直接移植 Kugou raw API 可能遇到签名/设备字段/加密细节；必须以 `.NET` 为准，不能猜。
- 风险: Pure Kugou playback 会触碰 Now Playing、队列页、后台控制和浮窗状态，需小步验证。
- 风险: Radio/FM 与普通队列切换如果没有明确 active session，会再次出现播放叠加。
- 假设: `KugouMusic.NET/` 本地参考源码保持可读。
