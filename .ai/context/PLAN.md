# PLAN

Last Updated: 2026-06-04

## Current Stage
- Stage Name: S5 纠偏 - Kugou Pure Source Playback, Queue/Radio Parity & MainActivity Split
- Scope Source: `.ai/context/SCOPE.md`（2026-06-04）

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

## Architecture Plan

### Module / Layer Direction
- `MainActivity`: launcher、生命周期、左侧一级导航、播放/service bridge 的薄协调层。
- `ui/*Binder`: 页面或控件绑定、渲染协调、低频 UI 状态刷新；不得承载协议猜测或播放队列核心语义。
- `kugou/*`: 酷狗 API/session/client/store，所有行为必须可追溯到 `KugouMusic.NET/`。
- `playback/source session`: Emby、KugouSong、KugouRadio 三类播放 session 分流，不复用 Emby queue 语义表达酷狗队列。
- `audio/dsp`: DSP runtime 状态和 native 处理保持独立，UI 只低频读取状态。

### Entry File Responsibility
- `MainActivity` 只能接线 Android 生命周期、导航、顶层回调、后台服务/方向盘按键/浮窗桥接。
- 不能继续把酷狗登录、酷狗内容页、队列算法、电台 session、歌词解析、下载控制和 DSP 页面细节堆回入口文件。

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

## Dependency Graph
- `W1/T-S5-MAIN-114 -> W2/T-S5-KG-109 -> W3 -> W4 -> W6`
- `W1/T-S5-MAIN-114 -> W1/T-S5-MAIN-115 -> W1/T-S5-MAIN-116`
- `W2 -> W3`
- `W5` 可与 `W1/W2` 并行；`T-S4-AUDIO-097` 依赖已满足，可作为并行 Ready，但最终进入 `W6`。
- `B-KG-EMBY-INGEST-001` 继续阻塞，不参与当前 Ready。

## Recommended Order
1. `T-S5-MAIN-114`: MainActivity 第二轮拆分：Kugou Auth/Config Binder，先把下一步 API 地址纠偏会触碰的登录/配置 UI 迁出入口文件。
2. `T-S4-AUDIO-097`: DSP 持续红框诊断与修正（可与 MainActivity 拆分并行，但建议独立单任务完成）。
3. `T-S5-KG-109`: Kugou WebApi Base URL 产品路径纠偏与 `.NET` direct API 可行性确认；执行时不得向 `MainActivity` 添加新大块逻辑。
4. `T-S5-MAIN-115`: MainActivity 第三轮拆分：Kugou 内容页 Binder；若 `T-S5-KG-109` 实际需要大面积触碰内容页，先执行本任务。
5. `T-S5-PLAY-110`: 纯酷狗播放状态边界，切断 Emby 队列叠加。
6. `T-S5-PLAY-111`: Kugou 普通歌曲队列按 `.NET PlaybackQueueManager` 实现。
7. `T-S5-PLAY-112`: Kugou Radio/FM session 按 `.NET PersonalFmService` 实现。
8. `T-S5-MAIN-116`: 页面壳拆分试点评估，优先设置/日志/EQ 或酷狗内容页，不影响左侧一级导航。
9. `T-S5-VAL-113`: API17 回归与构建验证。

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

## Validation Strategy
- 本地:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `python scripts/check_code_health.py`（当前允许因既有 `MainActivity` 红线失败，但每个拆分任务必须证明红线趋势改善，不得新增 red findings）
  - `gradle :app:compileDebugKotlin --no-daemon`
  - 触及资源/播放/native 时执行 `gradle :app:assembleDebug --no-daemon`
- 行为:
  - 默认进入酷狗推荐歌曲。
  - 酷狗播放不自动恢复/刷新 Emby 队列。
  - 酷狗 next/previous 在酷狗队列或 radio session 内推进。
  - 电台 session active 时普通队列不接管下一曲。
  - DSP 红框只对应真实 fail-open/bypass/error。
- 文档:
  - 所有酷狗队列/电台/API 行为标注 `.NET` 参考文件。
  - 未确认能力必须标记 Blocked，不进入实现。

## Risks & Assumptions
- 风险: `MainActivity` 体量大，拆分时容易误动生命周期和 service bridge；后续先拆 Controller/Binder，再评估页面壳拆分。
- 风险: Fragment/多 Activity 拆分若过早触碰播放页和 service bridge，可能影响后台按键、浮窗和 resume；页面壳试点优先低耦合页面。
- 风险: Android 直接移植 Kugou raw API 可能遇到签名/设备字段/加密细节；必须以 `.NET` 为准，不能猜。
- 风险: Pure Kugou playback 会触碰 Now Playing、队列页、后台控制和浮窗状态，需小步验证。
- 风险: Radio/FM 与普通队列切换如果没有明确 active session，会再次出现播放叠加。
- 假设: `KugouMusic.NET/` 本地参考源码保持可读。
