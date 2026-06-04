# SCOPE

Last Updated: 2026-06-04

## Project
- 名称: `skoda-music`
- 概述: Android 车机音乐播放器（API17 基线，Kotlin 壳 + C++ 核心）

## Scope Summary
- 当前阶段: S5 纠偏子阶段（Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split）
- 阶段背景:
  - S5 已完成酷狗默认入口、登录、内容页、点赞状态、播放 URL 和 DSP 播放按钮状态指示，但 review 后暴露出实现方向偏差。
  - Android 端不应要求用户提供 Kugou WebApi Base URL；酷狗接口、地址、字段与流程必须继续以 `KugouMusic.NET/` 为依据。
  - 默认酷狗模式必须是“纯酷狗播放模式”，不能叠加 Emby 队列、Emby 自动刷新、Emby resume 或 service state 污染。
  - 酷狗歌曲队列不能复用 Emby `loadedTracks/currentTrackIndex` 语义，需参考 `.NET` `PlaybackQueueManager`。
  - 电台/Radio 不是普通歌曲队列：同一电台会话持续播放，下一曲由电台/FM 会话内部推进，需参考 `.NET` `PersonalFmService` 与 `PlayerViewModel.PersonalFm.cs`。
  - DSP 播放按钮持续红色意味着当前 runtime 仍被判为 fail-open/bypass/error；需要诊断并修正，不能把红色作为常态。
  - `MainActivity.kt` 当前仍约 6213 行，之前第一轮只迁出 source/list row 构造；用户已明确纠正：不能把“单 Activity 外壳”当硬约束，项目变大后允许适当拆 Activity / 页面壳 / Controller / Binder，核心目标是避免单文件继续膨胀。

## In Scope
- MainActivity 拆分（当前阶段必须执行）:
  - 不再把“保持单 Activity 外壳”作为长期硬约束；允许在 API17 兼容、交互不回归的前提下，评估并引入页面 Fragment、独立 Activity 或等价页面壳拆分。
  - 短期仍优先拆低风险 Controller/Binder，减少 `MainActivity` 继续膨胀：
    - 酷狗登录/session 与 API 调用协调。
    - 酷狗内容页状态与渲染。
    - source row/list renderer。
    - source-aware 播放队列与电台会话管理。
    - 播放控制/service state binder。
  - 中期允许做页面壳拆分试点，候选为设置页、音效/EQ 页、日志页、酷狗内容页；左侧一级快速切换、默认酷狗模式、不新增二级 tab 必须保持。
  - 不做一次性全量重写，不引入高 API 或重型框架依赖。
- 酷狗 API 接入纠偏:
  - 移除用户必须填写 Kugou WebApi Base URL 的产品路径。
  - 优先按 `KugouMusic.NET/src/Libraries/KuGou.Net/` 的 client/raw api/transport 直接移植或封装 Android 端能力。
  - 如果某个能力只能依赖 `.NET` WebApi 服务且无法从 `.NET` raw/client 侧确认 Android 直连方案，必须停下询问用户，不能让用户临时填地址绕过。
  - 登录继续必须支持默认扫码与手机号验证码；登录成功后缓存，直到下次不可用再跳转登录。
- 纯酷狗默认模式:
  - 默认进入酷狗推荐歌曲。
  - 酷狗播放期间 Now Playing、队列页、next/previous、service state、按钮状态应来自酷狗/source session，而不是 Emby `loadedTracks`。
  - Emby 继续作为独立来源/设置能力保留，但不得在酷狗模式下自动 resume、自动刷新、尾部补队列或参与下一曲。
- 酷狗歌曲队列:
  - 参考 `.NET` `KugouAvaloniaPlayer/Services/PlaybackQueueManager.cs`。
  - 普通歌曲列表点击时使用“点击歌曲 + 当前上下文列表”建立队列。
  - 大列表按 `.NET` 规则限制窗口，最大约 300 首且围绕当前歌曲截取。
  - next/previous 在酷狗歌曲队列内循环推进。
  - add/remove/clear 先按最小可用实现；shuffle/repeat-one 可按 `.NET` 设计预留，不强行扩大本轮 UI。
- 酷狗电台/Radio 会话:
  - 电台播放必须独立于普通歌曲队列。
  - 点击电台或电台歌曲后进入同一 radio/FM session。
  - 下一曲优先调用 radio/FM session advance，不走普通歌曲队列。
  - 队列页展示 radio session 的 current/upcoming/history 或等价最小状态。
  - 参考 `.NET` `PersonalFmService.cs`、`PlayerViewModel.PersonalFm.cs`、`PlayerViewModel.Queue.cs` 中对 `IsPersonalFmSessionActive` 的分流。
- DSP 状态纠偏:
  - 播放按钮红色只能表示真实 fail-open/bypass/error。
  - 正常 native active 应显示绿色；降级/超预算显示黄色；关闭/未知/原声显示灰色。
  - 若实测持续红色，需要定位原因：native-not-ready、non-direct-buffer、native-process-error、unsupported format、native 永久 bypass 或状态未复位。
  - UI 刷新仍保持低频，不按 audio frame 刷新。
- 缓存与 Emby 入库:
  - 本地播放缓存设计仍以最大不超过 `100MB` 为目标。
  - “点赞后将播放缓存上传到 Emby 并入库”保持阻塞，不进入当前实现 Ready。

## Out of Scope
- 上传播放缓存到 Emby 并纳入媒体库。
- Emby 入库失败的真实网络空闲重试队列。
- 未经 `KugouMusic.NET` 支撑的酷狗接口、字段、流程或协议扩展。
- 非酷狗来源点赞真实实现。
- 搜索、排行榜、歌手页、专辑页、MV、听书、评论等全量酷狗客户端能力。
- 改变 `minSdk=17` 红线。
- 引入要求 `minSdk > 17` 的依赖或系统能力。
- 一次性全量重写 `MainActivity`。
- 为减少行数而牺牲左侧一级快速切换、默认酷狗模式、后台控制、浮窗、前台服务通知或 API17 兼容。
- 引入要求 `minSdk > 17` 的 Fragment/Activity/导航依赖或高 API 页面能力。

## Success Criteria
- MainActivity 拆分验收:
  - 继续完成可编译拆分，`MainActivity.kt` 行数持续下降，新增职责类有清晰边界。
  - 短期目标：后续拆分任务不得继续扩大 `MainActivity`，优先将其压到 5500 行以下；中期目标评估页面壳拆分并继续压到 3000 行级别。
  - 拆分后现有左侧导航、播放按钮、设置页、登录页、后台 service 状态不回归。
  - 新增类不引入 API17 违规。
- Kugou API 验收:
  - App 不再要求用户填写 Kugou WebApi Base URL 才能使用酷狗。
  - 每个酷狗能力仍能追溯到 `KugouMusic.NET/` 的 client/raw/controller/model。
  - 如果发现无法直接按 `.NET` 参考落地 Android 端，任务停在 Blocked 并给出具体缺口。
- 纯酷狗播放验收:
  - 默认酷狗模式下，点击酷狗歌曲后 Now Playing 显示酷狗曲目，不再从 Emby `loadedTracks` 推导 artist/title/hasTrack。
  - 酷狗播放时 next/previous 不触发 Emby 队列。
  - 酷狗模式不自动恢复 Emby 播放，不触发 Emby 队列尾补充。
- 酷狗队列验收:
  - 推荐歌曲/发现歌单歌曲点击后建立酷狗歌曲队列。
  - next/previous 按 `.NET` `PlaybackQueueManager` 语义循环。
  - 队列页能显示当前酷狗队列状态。
- 电台验收:
  - 推荐电台进入 radio session。
  - radio session 激活时 next/previous 走 radio session，而不是普通歌曲队列或 Emby 队列。
  - 同一电台持续播放，下一曲由电台会话内部推进。
- DSP 验收:
  - 正常播放且 native DSP active 时按钮不持续红色。
  - 红色状态能对应明确诊断原因。
  - 关闭 DSP/原声/release 后状态回灰，降级/超预算为黄色。
- 兼容性验收:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - 触及资源/播放/native 时执行 `gradle :app:assembleDebug --no-daemon`。

## Design Direction
- 当前纠偏按“先继续拆 MainActivity，再修 source 播放语义”的顺序推进。
- `MainActivity` 不再被定义为必须长期承载所有页面容器；短期保留为 launcher/生命周期协调者，页面与业务逻辑下沉到小类，后续可演进为 Fragment host 或将低耦合页面迁到独立 Activity：
  - `KugouSourceController` 或等价类：登录/session/API 协调。
  - `KugouPagesBinder` 或等价类：推荐歌曲/电台/发现页渲染。
  - `SourcePlaybackQueue` / `KugouQueueManager`：普通酷狗歌曲队列。
  - `KugouRadioSessionManager`：电台/FM 会话。
  - `PlaybackControlsBinder`：播放按钮、DSP 状态、service state 桥接。
- 页面壳拆分优先级：设置/日志/EQ 等低耦合页面优先；播放页、后台控制、方向盘按键、浮窗/service bridge 最后拆。
- 酷狗普通歌曲队列和 radio session 是并列播放会话；当 radio session active 时，next/previous 由 radio session 接管。
- Emby 作为独立来源保留；切换到 Emby 时才启用 Emby 队列、download-only、resume/refresh 等旧链路。

## Constraints
- 平台红线: `minSdk=17` 不可变，目标设备为 Android 4.2.2 / AC83xx / 1024x600 横屏。
- 酷狗实现红线: 只能参考 `KugouMusic.NET/` 已有实现；没有依据就停下问用户。
- 登录红线: 酷狗模式必须登录后可用；默认扫码，手机号验证码并行支持。
- 缓存红线: 本地缓存设计目标总量不超过 `100MB`。
- 稳定性: 网络失败、登录失效、播放 URL 解析失败、不可播均不得导致 app 闪退或主播放链路不可恢复。
- DSP UI 稳定性: DSP 状态展示不能增加音频热路径负担，不能按 audio frame 推 UI。
- 工作树注意: `KugouMusic.NET/` 当前为未跟踪目录，只读参考，不修改。

## Open Questions
- Emby 是否支持通过 API 上传音频并纳入媒体库。
- 如果 Emby 不支持直接上传，是否接受服务端代理写入媒体目录并触发 Emby 扫描。
- 酷狗某些能力若 Android 直连移植需要签名/加密/设备字段，需以 `KugouMusic.NET` 代码定位后再决定是否阻塞。

## Blocked / Deferred

### B-KG-EMBY-INGEST-001
- Title: 点赞后将播放缓存上传到 Emby 并入库
- Status: Blocked / Deferred
- Blocked By:
  - Emby 上传音频并纳入媒体库能力未确认。
  - 播放缓存与入库缓存的生命周期、大小上限和失败重试策略未确认。
- Current Boundary:
  - 当前只记录点赞与入库待处理状态。
  - 不执行上传、不建立真实重试队列、不声称入库成功。
