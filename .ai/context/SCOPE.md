# SCOPE

Last Updated: 2026-06-03

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（API17 基线，Kotlin 壳 + C++ 核心）

## Scope Summary
- 当前阶段: S5 子阶段（Kugou Source Mode & Multi-Source Discovery）
- 阶段背景:
  - 当前 App 主内容链路长期以 Emby 为单一来源，缺少在线推荐、推荐电台和发现歌单能力。
  - 用户确认新方向：引入多来源抽象，默认进入酷狗模式，并以 `KugouMusic.NET/` 中已有接口、模型和流程作为酷狗能力的唯一参考来源。
  - 酷狗模式必须登录后可用；默认扫码登录，同时支持手机号验证码登录；登录成功后缓存 session，直到缓存不可用或接口返回未授权时再跳转登录。
  - 点赞能力先抽象，本阶段暂只支持酷狗歌曲点赞；点赞后的“上传播放缓存到 Emby 入库”因 Emby 上传/入库能力未确认，先作为阻塞项，不进入本阶段实现范围。
- 阶段目标:
  - 建立多来源内容与播放抽象，为 `Emby / Kugou / 后续来源` 提供统一模型与页面接线边界。
  - 将左侧导航改为可快速切换的一级内容入口，默认进入酷狗模式，不再新增 Home 内二级 tab。
  - 接入酷狗推荐歌曲、推荐电台、发现歌单分类浏览，所有酷狗接口行为均参考 `KugouMusic.NET` 已有实现。
  - 建立酷狗登录、session 缓存、失效跳转登录和点赞历史/状态的本地闭环。
  - 并行补入 S4 Native DSP 播放页状态指示：在播放/暂停按钮上增加有色边框，用于快速确认当前未进入 fail-open，且状态刷新不得按音频帧驱动。

## In Scope
- 多来源抽象:
  - 新增来源维度与统一模型，例如 `MusicSource / SourceTrack / SourcePlaylist / SourceRadio / SourcePlaybackRef` 或等价结构。
  - 保持现有 Emby 链路可继续工作，但不再把 UI 和播放模型硬绑定为 `EmbyTrack` 单一来源。
  - 播放、队列、点赞、历史状态需能携带来源标识。
- 左侧一级导航与默认入口:
  - 默认启动进入酷狗模式下的推荐歌曲页。
  - 左侧导航直接提供一级入口，不新增 Home 内二级 tab。
  - 推荐入口包括：推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态、设置。
  - UI 需适配 Android 4.2.2 / 1024x600 横屏车机，优先信息密度、触控清晰和低开销。
- 酷狗登录:
  - 默认扫码登录。
  - 同时支持手机号验证码登录。
  - 登录后缓存 session；启动时优先复用缓存。
  - 当 session 缺失、过期、接口返回未授权或 `KugouMusic.NET` 对应流程判定不可用时，跳转登录。
  - 登录接口、状态判断和缓存字段必须参考 `KugouMusic.NET` 现有 `LoginClient`、`RawLoginApi`、session persistence 与 WebApi controller 实现。
- 酷狗内容:
  - 推荐歌曲：参考 `RecommendClient.GetRecommendedSongsAsync()` 与 `DiscoveryController` 中 `GET /recommend/songs`。
  - 推荐电台：参考 `FmClient.GetRecommendAsync()`、`GetSongsAsync()`、`GetImagesAsync()` 与 `FmController`。
  - 发现歌单：参考 `PlaylistClient.GetTagsAsync()`、`RecommendClient.GetRecommendedPlaylistsAsync()`、`PlaylistClient.GetInfoAsync()`、`GetSongsAsync()` 与相关 controllers。
  - 发现歌单分类需覆盖：场景、主题、语种、风格、心情、年代；分类与子标签来自 `playlist/tags` 返回，不手写猜测 tag。
  - 播放 URL 解析参考 `SongClient.GetPlayInfoAsync()` / `GetUrlAsync()` 与 `SongController`。
- 点赞与状态:
  - 点赞能力先做来源抽象，本阶段仅实现酷狗歌曲点赞入口与本地状态记录。
  - 点赞历史/状态页需展示：歌曲、来源、动作时间、当前状态、失败原因。
  - 本阶段不执行上传 Emby 入库，只记录“入库待处理/阻塞”状态。
- S4 Native DSP 播放页状态指示:
  - 在播放页 `btn_play_pause` 上增加 DSP runtime 状态边框。
  - 颜色语义需能区分：未知/关闭、native 正常处理、降档/超预算、fail-open/旁路/错误。
  - 音频线程只发布轻量状态；UI 侧在既有进度刷新节奏读取状态，不按 PCM buffer 或 audio frame 逐帧刷新。
  - 仅当视觉状态变化时重绘播放按钮，避免低端车机 UI 抖动或卡顿。
- 缓存约束:
  - 本地播放缓存与后续入库缓存设计必须考虑总量上限，目标最大不超过 `100MB`。
  - 本阶段可先规划缓存上限与清理规则；真正“播放缓存上传到 Emby”不进入 Ready。
- 参考来源约束:
  - 酷狗相关内容实现必须参考 `KugouMusic.NET/` 已有代码。
  - 不自行发挥酷狗接口、字段、流程或协议。
  - 如果 `KugouMusic.NET` 中没有对应能力或无法确认字段含义，必须停止并向用户确认。

## Out of Scope
- 本阶段不做:
  - 上传播放缓存到 Emby 并纳入 Emby 媒体库。
  - Emby 入库重试队列的真实执行。
  - 未经 `KugouMusic.NET` 支撑的酷狗接口扩展。
  - 其它来源的点赞真实实现。
  - 酷狗歌单收藏、创建、删除等账号侧复杂管理能力。
  - 搜索、排行榜、歌手页、专辑页、MV、听书、评论等全量酷狗客户端能力。
  - 改变 `minSdk=17` 红线。
  - 引入要求 `minSdk > 17` 的依赖或系统能力。

## Success Criteria
- 架构验收:
  - 存在清晰的多来源抽象，现有 Emby 与新酷狗能力不互相污染。
  - 队列/播放/点赞/历史状态能携带来源标识。
  - 酷狗接口映射有可追溯文档，能指出对应 `KugouMusic.NET` client/controller/model。
- 登录验收:
  - 默认展示扫码登录路径。
  - 手机号验证码登录路径可用或已按 `KugouMusic.NET` 能力完成接线。
  - 登录 session 可缓存并在启动时复用。
  - session 不可用时进入登录，而不是展示不可操作的酷狗内容页。
- UI 验收:
  - App 默认进入酷狗推荐歌曲页。
  - 左侧一级导航可直接切换推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态、设置。
  - 不新增 Home 内二级 tab。
  - 1024x600 横屏下文本不重叠，列表可触控，状态清晰。
- 内容验收:
  - 推荐歌曲可加载并渲染关键字段。
  - 推荐电台可加载电台列表，并可进入/展示电台歌曲。
  - 发现歌单可加载分类/子标签、歌单列表、歌单歌曲。
  - 接口失败、未登录、空结果、VIP/不可播等状态有明确反馈。
- 点赞验收:
  - 酷狗歌曲可触发点赞状态记录。
  - 点赞历史/状态页可查看来源、标题、状态、失败原因。
  - Emby 入库上传明确显示为阻塞/待后续能力确认，不误导为已完成。
- 兼容性验收:
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - 若改动触及资源或 native 构建，优先执行 `gradle :app:assembleDebug --no-daemon`。
- DSP 状态指示验收:
  - 播放页播放/暂停按钮存在稳定可见的 DSP 状态边框。
  - DSP 启用且 native 正常处理时显示“正常”颜色；进入 bypass/error/fail-open 时显示“异常”颜色。
  - 降档、超预算等非 fail-open 但降级状态有独立提示颜色。
  - 停播、释放播放器、关闭 DSP 或原声模式时不会残留“正常”颜色。
  - UI 刷新节奏不快于既有 `UI_PROGRESS_REFRESH_MS`，且状态未变化不重复重绘。

## Design Direction
- 采用“多来源抽象 + 酷狗适配器”的方向:
  - 先定义 App 内统一来源模型，再将 Emby 和 Kugou 映射到统一模型。
  - 酷狗适配层只包装 `KugouMusic.NET` 已有接口语义，不创造新语义。
  - 业务 UI 面向统一模型，具体来源能力通过 source adapter 暴露。
- 酷狗接入方式:
  - 第一阶段优先按 `KugouMusic.NET` WebApi controller 形态设计 Android 调用契约，避免在 Android 端猜签名协议。
  - 如果后续要移植协议到 Kotlin/C++，必须以 `KugouMusic.NET` 对应 client/raw api 为逐项依据。
- UI 方向:
  - 左侧一级导航承载内容切换。
  - 推荐歌曲为默认页。
  - 发现页使用分类筛选控件，不使用新增二级 tab。
  - 设置页保留服务配置入口，并新增酷狗登录/状态与必要 API base 配置。
- 点赞/入库方向:
  - 本阶段完成点赞抽象和历史状态，不执行 Emby 上传。
  - `播放缓存上传到 Emby` 后续需要先验证 Emby 是否支持 API 上传并纳入媒体库；若不支持，需另行规划服务端写入媒体目录 + 触发扫描。

## Constraints
- 平台红线: `minSdk=17` 不可变，目标设备为 Android 4.2.2 / AC83xx / 1024x600 横屏。
- 酷狗实现红线: 只能参考 `KugouMusic.NET/` 已有实现；没有依据就停下问用户。
- 登录红线: 酷狗模式必须登录后可用；默认扫码，手机号验证码作为并行登录方式。
- 缓存红线: 本地缓存设计目标总量不超过 `100MB`。
- 稳定性: 网络失败、登录失效、播放 URL 解析失败、不可播均不得导致 app 闪退或主播放链路不可恢复。
- DSP UI 稳定性: DSP 状态展示不能增加音频热路径负担，不能把 runtime 状态直接按 audio frame 推到 UI。
- 工作树注意: `KugouMusic.NET/` 当前为未跟踪目录，但作为参考源码读取，不在本阶段修改。

## Open Questions
- Emby 是否支持通过 API 上传音频并纳入媒体库。
- 如果 Emby 不支持直接上传，是否接受服务端代理写入媒体目录并触发 Emby 扫描。
- 酷狗 WebApi 服务地址是否固定由设置页配置，还是后续内置默认值。
- 本阶段播放酷狗歌曲是否采用网关返回播放 URL 直接播放，还是先只完成列表/登录/点赞状态闭环后再接播放。

## Blocked / Deferred

### B-KG-EMBY-INGEST-001
- Title: 点赞后将播放缓存上传到 Emby 并入库
- Status: Blocked / Deferred
- Blocked By:
  - Emby 上传音频并纳入媒体库能力未确认。
  - 播放缓存与入库缓存的生命周期、大小上限和失败重试策略未确认。
- Current Boundary:
  - 本阶段只记录点赞与入库待处理状态。
  - 不执行上传、不建立真实重试队列、不声称入库成功。
