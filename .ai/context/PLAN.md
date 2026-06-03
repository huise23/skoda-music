# PLAN

Last Updated: 2026-06-03

## Current Stage
- Stage Name: S5 子阶段 - Kugou Source Mode & Multi-Source Discovery
- Scope Source: `.ai/context/SCOPE.md`（2026-06-03）

## Stage Goal
- 将 App 从 Emby-only 内容入口演进为多来源内容模式。
- 默认进入酷狗模式，并通过左侧一级导航快速切换推荐歌曲、推荐电台、发现歌单、播放队列、点赞/入库状态和设置。
- 酷狗模式必须登录后可用，默认扫码登录，同时支持手机号验证码登录和 session 缓存复用。
- 酷狗相关实现严格参考 `KugouMusic.NET/` 已有接口、模型与流程；没有依据则停止并向用户确认。
- 点赞能力先完成抽象和酷狗本地状态记录，Emby 上传入库作为阻塞项后续处理。
- 并行完成 S4 Native DSP 播放页状态指示：播放/暂停按钮通过有色边框展示 native 正常、降级和 fail-open 状态，UI 刷新走低频节流。

## Scope Validation

### In Scope
- 多来源抽象与现有 Emby 模型解耦。
- 酷狗接口清单与 `KugouMusic.NET` 源码映射。
- 酷狗登录、session 缓存、失效跳转登录。
- 推荐歌曲、推荐电台、发现歌单三个内容入口。
- 左侧一级导航重排，默认酷狗推荐歌曲页。
- 酷狗歌曲点赞抽象与本地历史/状态页。
- API17 兼容验证与回归清单更新。
- Native DSP 播放页状态指示热修：播放按钮边框显示 DSP runtime 状态，状态读取不按 audio frame 刷新。

### Out of Scope
- 播放缓存上传到 Emby 并真正入库。
- Emby 入库失败的真实网络空闲重试队列。
- 非酷狗来源点赞实现。
- 未在 `KugouMusic.NET` 中找到依据的酷狗能力。
- 搜索、排行榜、歌手、专辑、MV、听书、评论等完整客户端扩展。

## Reality Check
- 当前 Android 端:
  - `MainActivity` 仍以 `EmbyTrack`、`loadedTracks`、`libraryTracks` 为核心模型。
  - 左侧导航已有 86dp 竖向导航栏，但入口为 Home/Library/Settings，Queue 当前隐藏。
  - Home 内仍存在“歌词/推荐”二级 tab，需要后续改造为一级内容入口，不再新增二级 tab。
  - 已有 `AppBackgroundExecutor`、Emby session/cache、download-only 播放与 100MB 缓存约束可复用思路。
- Native DSP:
  - `HiFiAudioProcessor` 已记录 native runtime status/tier/flags，但状态目前只在日志中可见，播放页没有直观入口。
  - `NativeHiFiDspBridge` 已有 `STATUS_OK/BYPASS/ERROR` 与 `FLAG_ACTIVE/BYPASS/DEGRADED/OVER_BUDGET/ERROR` 等状态位，可作为 UI 指示来源。
  - `MainActivity` 已有 `UI_PROGRESS_REFRESH_MS = 1_000L` 的进度刷新 tick，适合低频读取 DSP 状态并避免 per-frame UI 更新。
- `KugouMusic.NET/`:
  - 已提供登录、推荐歌曲、推荐电台、歌单标签、推荐歌单、歌单歌曲、播放 URL、点赞/我喜欢等参考实现。
  - 当前目录未跟踪，作为参考源码读取，不在本阶段修改。
- 主要冲突:
  - 历史决策中“首版协议 Emby only”被本阶段显式覆盖为“多来源 + 酷狗模式”。
  - 现有 UI 与播放队列紧耦合 Emby，需要先做模型和来源边界，避免直接把酷狗字段塞进 `EmbyTrack`。

## Workstreams

### W1 Source Abstraction & IA Shell
- 目标: 建立多来源统一模型和左侧一级导航信息架构。
- 输出: source/domain 契约、默认酷狗入口、页面切换计划。

### W0 Native DSP Playback Status Hotfix
- 目标: 在播放页提供低开销 DSP fail-open 可视确认。
- 输出: DSP runtime state 发布、播放按钮有色边框、1s tick 读取与变更才重绘。

### W2 Kugou Interface Map & Auth
- 目标: 固定酷狗能力到 `KugouMusic.NET` 的源码映射，并接入登录/session 生命周期。
- 输出: 接口映射文档、扫码/验证码登录、session 缓存与失效处理任务。

### W3 Kugou Content Pages
- 目标: 接入推荐歌曲、推荐电台、发现歌单三类酷狗内容。
- 输出: 三个一级页面的数据加载、状态反馈、列表渲染和基础播放引用。

### W4 Playback, Cache & Like State
- 目标: 将酷狗曲目接入统一队列/播放解析，并实现酷狗点赞与历史状态。
- 输出: source-aware queue/playback resolver、100MB 缓存守卫、点赞状态页。

### W5 Validation & Evidence
- 目标: 确保 API17 构建、低版本资源、横屏 UI 与登录/内容失败路径可验证。
- 输出: 回归清单、构建验证、实机检查点。

### W6 Deferred Emby Ingest Research
- 目标: 后续确认 Emby 上传播放缓存入库能力。
- 输出: 当前只保留阻塞项，不进入 Ready。

## Dependency Graph
- `W0` 与 S5 主线并行，可先执行，不阻塞酷狗接口映射。
- `W1 -> W2 -> W3 -> W4 -> W5`
- `W2` 的接口映射可与 `W1` 契约设计并行。
- `W3` 依赖登录/session 和基础 source contract。
- `W4` 依赖至少一个可加载的酷狗曲目列表。
- `W6` 被阻塞，不依赖主线，不阻断本阶段可验证结果。
- S4 `T-S4-AUDIO-095` 仍为外部实机阻塞项，与 S5 本地规划并行。

## Recommended Order
1. `T-S4-AUDIO-096`: Native DSP 播放按钮 fail-open 状态指示。
2. `T-S5-KG-096`: KugouMusic.NET 接口能力映射与缺口检查。
3. `T-S5-SRC-097`: 多来源领域模型与左侧一级导航契约。
4. `T-S5-KG-098`: 酷狗登录/session 缓存契约。
5. `T-S5-SRC-099`: Source-aware 队列/播放边界设计。
6. `T-S5-UI-100`: 左侧导航与默认酷狗模式页面骨架。
7. `T-S5-KG-101`: 酷狗登录 UI 与 session 缓存实现。
8. `T-S5-KG-102`: 推荐歌曲页面接入。
9. `T-S5-KG-103`: 推荐电台页面接入。
10. `T-S5-KG-104`: 发现歌单分类与歌单歌曲接入。
11. `T-S5-LIKE-105`: 酷狗点赞抽象与历史/状态页。
12. `T-S5-VAL-106`: API17 回归清单与本地验证。

## Milestones
- M1: 酷狗接口映射完整，确认本阶段所有酷狗行为均可追溯到 `KugouMusic.NET`。
- M2: App 启动默认进入酷狗推荐歌曲页，左侧一级导航可切换核心页面。
- M3: 酷狗扫码/验证码登录可用，session 可缓存并在失效时跳转登录。
- M4: 推荐歌曲、推荐电台、发现歌单可加载并展示。
- M5: 酷狗点赞状态可记录和查看，Emby 入库明确显示为阻塞/待处理。
- M6: API17 guardrails 与 Kotlin 编译通过。
- M0: 播放页可通过播放/暂停按钮边框确认 Native DSP 是否处于正常、降级或 fail-open 状态。

## Validation Strategy
- 文档/契约:
  - 每个酷狗接口必须标注对应 `KugouMusic.NET` 文件与方法。
  - 没有来源依据的能力不得进入 Ready。
- 本地:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - 触及资源/播放/native 时执行 `gradle :app:assembleDebug --no-daemon`
- UI:
  - 1024x600 横屏下导航、列表、登录、状态页不重叠。
  - 不新增 Home 二级 tab。
  - DSP 状态指示只更新播放/暂停按钮边框，随既有 1s progress tick 刷新且状态未变不重绘。
- 行为:
  - 未登录进入酷狗内容页时跳登录。
  - session 缓存可复用；失效后能清理/重登。
  - 网络失败、空结果、不可播均有明确反馈。

## Risks & Assumptions
- 风险: `KugouMusic.NET` WebApi 服务地址与部署方式未固定；若 Android 直接调网关，需要设置项或默认地址。
- 风险: 酷狗 session 失效码需要严格按 `KugouMusic.NET` 行为识别，不能猜。
- 风险: 现有 `MainActivity` 很大，直接硬改容易引入回归；需要先抽模型和页面边界。
- 风险: 酷狗播放 URL/VIP 权限可能导致可展示但不可播，必须有状态反馈。
- 风险: Emby 上传入库能力未确认，不能把点赞状态伪装为入库成功。
- 风险: 若 DSP runtime 状态直接从音频线程触发 UI，会增加低端车机卡顿风险；必须采用轻量发布 + UI 低频读取。
- 假设: `KugouMusic.NET/` 会作为本阶段稳定参考源码存在。
- 假设: 第一版可通过 WebApi 形态接入酷狗能力，后续再决定是否移植协议到 Android 本地。

## Carry Forward
- `T-S4-AUDIO-095`: AC83xx Native DSP 实机长播与听感验证仍保持 Blocked by external device，不进入 S5 Ready。
- `B-KG-EMBY-INGEST-001`: 播放缓存上传到 Emby 入库保持 Blocked / Deferred。
