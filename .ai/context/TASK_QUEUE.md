# TASK_QUEUE

Last Updated: 2026-06-05

## Ready
- 设备验证：手机/API17 环境验证 `T-S5-KG-122` 登录、首页推荐、推荐歌曲播放。

## Pending / Planned

- `T-S5-KG-123`: Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞 direct 化，移除剩余旧 `KugouWebApiClient` baseUrl gate。
- API17 A~N 实机回归：`T-S5-KG-119` 已本地完成，等待手机/API17 设备执行并回传 QR refresh 与 PostHog 证据。

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
- `T-S5-KG-122`: Android QR auth 与 `.NET` 登录态一致化 + 默认推荐/播放 direct 最小闭环；扫码 token 成功即 `VALID`，首页推荐走 direct `/everyday_song_recommend`，推荐歌曲播放 URL 走 direct `/v5/url`，不再依赖旧 WebApi baseUrl。
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
- 当前可执行代码任务已完成；下一步恢复 API17 实机按 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 执行 A~N 分组并回传证据。
- `T-S5-MAIN-114` 已完成；后续 API/config 改动必须走 `KugouAuthConfigBinder` 与 `kugou/*`，不要把逻辑加回 `MainActivity`。
- 后续新增功能必须有足够 PostHog/runtime/logcat 证据，并进行敏感字段审计。
