# MODULES

Last Updated: 2026-06-03

## Active Stage
- S5 子阶段 - Kugou Source Mode & Multi-Source Discovery
- Parallel Hotfix: S4 Native DSP Playback Status Indicator

## M-S4-AUDIO-022
- Module ID: `M-S4-AUDIO-022`
- Name: Native DSP Playback Status Indicator
- Goal: 在播放页用播放/暂停按钮边框展示 Native DSP runtime 状态，让用户能快速确认当前没有进入 fail-open。
- Why it matters: native DSP 已经能工作，但目前状态主要依赖日志；车机场景需要在播放页用低成本视觉信号判断 DSP 是否仍在正常处理。
- In Scope:
  - 在 `HiFiDspController` 或等价轻量状态源中发布 runtime state。
  - `HiFiAudioProcessor` 根据 native status/tier/flags 更新状态。
  - `MainActivity` 在既有 `UI_PROGRESS_REFRESH_MS` 进度 tick 中读取状态。
  - `btn_play_pause` 使用有色边框展示：未知/关闭、正常、降级/超预算、fail-open/旁路/错误。
  - 仅视觉状态变化时更新按钮背景。
- Out of Scope:
  - 修改 DSP 算法、native 性能档位或音频处理策略。
  - 增加新播放页二级入口或常驻大面积状态面板。
  - 逐帧 UI 刷新或从 audio thread 直接操作 UI。
- Dependencies: `T-S4-AUDIO-088~094` 已完成。
- Milestone / Done Criteria:
  - 播放/暂停按钮边框可稳定反映 DSP 状态。
  - fail-open/bypass/error 有明显异常颜色。
  - 关闭 DSP、原声模式或 release player 后不会残留正常状态。
  - API17 guardrails、Kotlin compile 通过；触及资源时 assemble 通过。
- Related Tasks: `T-S4-AUDIO-096`
- Priority: P0
- Status: Done
- Risks:
  - UI 刷新过快会影响低端车机体验；必须复用 1s tick 并做状态变化去重。
- Suitable For Module Execution?: No

## M-S5-SRC-022
- Module ID: `M-S5-SRC-022`
- Name: Multi-Source Domain & Left Navigation IA
- Goal: 建立多来源统一模型和左侧一级导航结构，默认进入酷狗模式。
- Why it matters: 当前代码以 `EmbyTrack` 和 Home 二级 tab 为中心；如果不先建立来源边界，酷狗会变成临时字段堆叠，后续扩展会失控。
- In Scope:
  - 定义 `MusicSource / SourceTrack / SourcePlaylist / SourceRadio / SourcePlaybackRef` 或等价模型。
  - 定义 source-aware queue/playback/like/history 的最小字段。
  - 规划左侧一级导航：推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态、设置。
  - 默认启动进入酷狗推荐歌曲页。
  - 明确不新增 Home 内二级 tab。
- Out of Scope:
  - 真实酷狗接口请求。
  - Emby 上传入库。
  - 大规模拆分 `MainActivity` 到多 Activity。
- Dependencies: 已确认 S5 scope。
- Milestone / Done Criteria:
  - source contract 可承载 Emby 与 Kugou。
  - 页面导航契约清楚，能进入实现。
  - 不把酷狗数据塞进 `EmbyTrack` 作为长期方案。
- Related Tasks: `T-S5-SRC-097`, `T-S5-SRC-099`, `T-S5-UI-100`
- Priority: P0
- Status: Done
- Risks:
  - `MainActivity` 体量大，UI 改造时容易碰到播放链路；需先做薄契约。
- Suitable For Module Execution?: Yes

## M-S5-KG-023
- Module ID: `M-S5-KG-023`
- Name: Kugou Interface Map & Auth Session
- Goal: 将酷狗相关能力逐项映射到 `KugouMusic.NET`，并实现登录/session 生命周期。
- Why it matters: 用户明确要求酷狗内容全部参考 .NET 程序，不允许自行发挥；登录又是酷狗模式可用的前置条件。
- In Scope:
  - 建立接口映射文档，覆盖登录、推荐歌曲、推荐电台、发现歌单、播放 URL、点赞相关参考点。
  - 默认扫码登录。
  - 手机号验证码登录。
  - session 缓存、启动复用、失效跳转登录。
  - 未登录状态阻断酷狗内容加载。
- Out of Scope:
  - 猜测 `KugouMusic.NET` 未覆盖的接口。
  - 酷狗账号完整管理。
  - 修改 `KugouMusic.NET`。
- Dependencies: `M-S5-SRC-022` 的来源契约可并行推进。
- Milestone / Done Criteria:
  - 每个酷狗能力可追溯到具体 .NET client/controller/model。
  - 登录成功后能缓存并复用 session。
  - session 失效时能跳回登录。
- Related Tasks: `T-S5-KG-096`, `T-S5-KG-098`, `T-S5-KG-101`
- Priority: P0
- Status: Done
- Risks:
  - 网关地址/部署方式未固定。
  - session 失效响应不能猜，需要以 .NET 现有逻辑为准。
- Suitable For Module Execution?: Yes

## M-S5-KG-024
- Module ID: `M-S5-KG-024`
- Name: Kugou Content Pages
- Goal: 接入酷狗推荐歌曲、推荐电台和发现歌单三个一级内容页。
- Why it matters: 这是用户可感知的核心功能，也是验证来源抽象是否成立的最短路径。
- In Scope:
  - 推荐歌曲加载与列表渲染。
  - 推荐电台加载、图片、进入电台歌曲列表。
  - 发现歌单分类、子标签、歌单列表、歌单歌曲列表。
  - 空结果、网络错误、未登录、不可播状态反馈。
  - 所有字段映射参考 `KugouMusic.NET` 对应 models。
- Out of Scope:
  - 搜索、排行榜、歌手、专辑、MV、听书、评论。
  - 酷狗歌单收藏/创建/删除。
- Dependencies: `M-S5-KG-023`, `M-S5-SRC-022`
- Milestone / Done Criteria:
  - 三个一级页均可加载数据并展示。
  - 发现歌单分类来自接口，不手写猜测。
  - 点击歌曲可产生 source playback ref 或明确不可播反馈。
- Related Tasks: `T-S5-KG-102`, `T-S5-KG-103`, `T-S5-KG-104`
- Priority: P0
- Status: Done
- Risks:
  - 酷狗字段结构复杂，已按 `.NET` model 做首屏映射；分页/播放仍由后续任务处理。
- Suitable For Module Execution?: Yes

## M-S5-PLAY-025
- Module ID: `M-S5-PLAY-025`
- Name: Source-Aware Playback & Cache Guard
- Goal: 让队列和播放解析支持多来源，并建立酷狗播放缓存上限策略。
- Why it matters: 列表可展示不等于能播放；播放链路当前强依赖 Emby download URL，需要抽出来源解析边界。
- In Scope:
  - source-aware queue item。
  - 酷狗播放 URL 解析契约，参考 `SongClient.GetPlayInfoAsync()` / `GetUrlAsync()`。
  - 100MB 本地缓存上限与清理策略设计。
  - 播放失败、不可播、VIP 权限反馈。
- Out of Scope:
  - 上传缓存到 Emby 入库。
  - 自研下载器大重构。
  - 非酷狗来源播放扩展。
- Dependencies: `M-S5-SRC-022`, `M-S5-KG-024`
- Milestone / Done Criteria:
  - 队列项能区分 Emby/Kugou。
  - 酷狗播放解析失败不影响 App 稳定。
  - 缓存上限策略清楚且不超过 100MB。
- Related Tasks: `T-S5-SRC-099`, `T-S5-PLAY-107`
- Priority: P1
- Status: Done
- Risks:
  - 直接改现有播放队列可能影响 Emby 已稳定链路；本轮采用酷狗点击直连 resolver 播放，Emby 队列保持原路径。
- Suitable For Module Execution?: Yes

## M-S5-LIKE-026
- Module ID: `M-S5-LIKE-026`
- Name: Like Abstraction & Status History
- Goal: 抽象点赞能力，并实现酷狗歌曲点赞状态与历史页。
- Why it matters: 用户明确要求点赞先抽象、暂时支持酷狗，同时需要查看历史和状态；这也是后续 Emby 入库任务的前置状态模型。
- In Scope:
  - source-aware like model。
  - 酷狗歌曲点赞入口。
  - 本地点赞历史/状态持久化。
  - 状态字段：pending、liked、blocked_ingest、failed、retry_deferred。
  - Emby 入库阻塞原因展示。
- Out of Scope:
  - 非酷狗来源点赞真实实现。
  - 上传到 Emby。
  - 网络空闲真实重试队列。
- Dependencies: `M-S5-SRC-022`, `M-S5-KG-024`
- Milestone / Done Criteria:
  - 酷狗歌曲能记录点赞。
  - 状态页可查看历史、来源、状态、失败原因。
  - Emby 入库显示为阻塞，不误报成功。
- Related Tasks: `T-S5-LIKE-105`
- Priority: P1
- Status: Done
- Risks:
  - 后续入库任务字段若设计不足会返工；当前已保留 source/hash/status/failure/ingest 字段。
- Suitable For Module Execution?: Yes

## M-S5-VAL-027
- Module ID: `M-S5-VAL-027`
- Name: API17 Validation & Regression Evidence
- Goal: 为 S5 新来源模式建立 API17 构建、UI、登录和网络失败路径验证闭环。
- Why it matters: 新增登录/网络/导航页改动触及用户主路径，必须有可复盘验证清单。
- In Scope:
  - 更新 API17 回归清单。
  - 本地执行 guardrails/compile。
  - 登录、session 缓存、未授权、网络失败、空结果、点赞状态验证项。
  - 横屏 1024x600 UI 验证点。
- Out of Scope:
  - AC83xx Native DSP 听感验证，仍由 `T-S4-AUDIO-095` 跟踪。
- Dependencies: `M-S5-KG-023`, `M-S5-KG-024`, `M-S5-LIKE-026`
- Milestone / Done Criteria:
  - 文档包含 S5 验证清单。
  - 本地构建验证通过。
  - 下一轮实机验证入口明确。
- Related Tasks: `T-S5-VAL-106`
- Priority: P1
- Status: Done
- Risks:
  - 真实酷狗登录需要外部账号/扫码条件，部分验证可能依赖人工。
- Suitable For Module Execution?: No

## Blocked / Deferred Modules

### M-S5-INGEST-028
- Module ID: `M-S5-INGEST-028`
- Name: Emby Upload Ingest From Playback Cache
- Goal: 点赞后将播放缓存上传到 Emby 并纳入媒体库。
- Status: Blocked / Deferred
- Blocked By:
  - Emby 是否支持 API 上传音频并入库未确认。
  - 若不支持，需要服务端代理写入媒体目录 + 触发扫描的新方案。
  - 本地缓存上限 100MB 与上传重试策略未完成需求确认。
- Related Blocker: `B-KG-EMBY-INGEST-001`
- Suitable For Module Execution?: No

## Historical Carry Forward
- `M-S4-AUDIO-021`: AC83xx Native DSP 实机验证仍等待外部设备窗口，任务 `T-S4-AUDIO-095` 保持 Blocked。
