# NEXT_STEPS

Last Updated: 2026-06-07

## One-Line Summary
- 已完成酷狗登录后自动加载/弹窗登录恢复，以及 Radio/Discover/Like direct 化；下一步执行手机/API17 实机验证。

## Current Highest Priority
- 手机/API17 环境验证 `M-S5-KG-037` 与 `T-S5-KG-123`。

## Guardrail-Adjusted Queue
- 当前 Ready:
  - 设备验证：手机/API17 环境验证 `T-S5-KG-122` 登录、首页推荐、推荐歌曲播放。
  - 设备验证：手机/API17 环境验证弹窗登录、登录后首页自动加载、Radio/Discover 懒加载失败重试、缺登录态不清内容。
  - 设备验证：手机/API17 环境验证 Radio 推荐/电台歌曲、发现歌单/歌单歌曲、点赞 direct 路径。
- 当前 Planned:
  - API17 A~N 实机回归，需真实设备/手机环境回传证据。
- 当前 In Progress:
  - None
- 原因:
  - `T-S5-PLAY-110/111` 已完成 source boundary 和普通队列。
  - `KugouContentRenderer` 与 `KugouContentBinder` 已迁出内容页渲染、请求和状态所有权，`MainActivity.kt` 约 6118 -> 5632 行，但仍超过 entry red-line。
  - `T-S5-PLAY-112` 已完成：radio active 时 next/previous/completion 由 `KugouRadioSessionManager` 接管，不走普通 queue 或 Emby queue。
  - `T-S4-AUDIO-097` 已完成：non-direct buffer 会走 direct scratch bridge，不再直接把按钮置红。
  - `T-S5-MAIN-116` 已完成：页面壳路线结论为先 Binder 化低耦合页面，再做 Fragment 试点。
  - `T-S5-VAL-113` 已完成：清单已覆盖 Main split、纯酷狗、普通 queue、Radio session、DSP direct-buffer bridge 和页面壳评估。
  - `T-S5-KG-119` 已完成：QR refresh 图片 URL/request 构造异常 fail-soft，旧异步回调会被 generation guard 忽略。
  - `T-S5-OBS-120` 已完成：QR/content/queue/radio 低频 PostHog 事件和敏感过滤已补齐，DSP 使用 runtime/logcat 证据。
  - `T-S5-KG-122` 已推送：QR `userid/token` 成功即有效登录态，首页推荐走 direct `/everyday_song_recommend`，推荐歌曲播放 URL 走 direct `/v5/url`。
  - `T-S5-KG-123` 本地完成：Radio、Discover、playlist songs、Like 当前路径已移除旧 `KugouWebApiClient` baseUrl gate，改走 `.NET` raw API 对齐的 direct 请求。

## Immediate Next Step

- 手机/API17 验证：
  - 未登录首页点击刷新应弹窗显示二维码。
  - 扫码成功后弹窗关闭并自动加载首页推荐。
  - 进入 Radio/Discover 时应懒加载 direct 数据；失败时显示“加载失败，点击重试”，不清已有内容。
  - 点击电台应拉取电台歌曲并进入 radio session。
  - 发现页应加载 tags -> playlists -> playlist songs。
  - 点击喜欢应记录为 pending -> success/failed，PostHog 只记录脱敏 `kugou_like_*` 事件。
  - 运行中本地登录态不可用时弹窗登录，不清当前列表/队列。

推荐 logcat:

```bash
adb logcat -v time SkodaMusicEmby:D SkodaPostHog:I AndroidRuntime:E Toast:E '*:S'
```

## Planned After Ready
- None

## Blocked / Deferred
- `B-KG-EMBY-INGEST-001`: 点赞后将播放缓存上传到 Emby 并纳入媒体库。
  - 原因: Emby 上传并入库能力未确认。
  - 当前处理: 只记录点赞和入库阻塞状态，不执行上传。
- `T-S4-AUDIO-095`: AC83xx Native DSP 实机长播与听感验证。
  - 原因: 外部设备窗口。

## Important Notes
- 当前主干为 `master@18c4723`；本地有 `M-S5-KG-037` 与 `T-S5-KG-123` 实现改动，尚未推送。
- `M-S5-KG-037` 已新增：Kugou Post-login Loading & Login Recovery。
- `T-S5-MAIN-108` 已完成：`MainActivity.kt` 由 6443 行降到 6213 行，新增 `SourceRowRenderer`。
- `T-S5-MAIN-115` 已完成：`KugouContentRenderer`/`KugouContentBinder` 承接内容页渲染、请求和状态，`MainActivity.kt` 当前约 5632 行。
- `T-S5-PLAY-112` 已完成：`KugouRadioSessionManager` 承接 radio current/upcoming/history，队列页可显示酷狗电台队列。
- `T-S4-AUDIO-097` 已完成：non-direct buffer 走 direct scratch bridge，红色保留给真实 native fail-open/bypass/error。
- `T-S5-MAIN-116` 已完成：页面壳路线文档见 `docs/PAGE_SHELL_SPLIT_EVALUATION.md`。
- “单 Activity 外壳”不再是长期硬约束；但页面壳拆分必须保持 API17、左侧一级快速切换、默认酷狗模式、后台 service/方向盘按键/浮窗稳定。
- 短期先做 Controller/Binder 拆分，页面壳试点优先设置/日志/EQ 等低耦合页面，播放页最后拆。
- 电台不是普通歌曲队列；radio active 时 next/previous/completion 由 radio/FM session 接管。
- 酷狗普通歌曲队列需参考 `.NET` `PlaybackQueueManager`，不要复用 Emby `loadedTracks/currentTrackIndex`。
- 酷狗 API 不应继续要求用户填地址；没有 `KugouMusic.NET` 依据就停下问。
- 当前 app 已不再要求用户填写 Kugou WebApi Base URL；QR 扫码、首页推荐、播放 URL、Radio、Discover、playlist songs、Like 当前路径均已接入 direct；SMS 仍待 AES/RSA direct port。
- 新增功能必须有足够 PostHog/runtime/logcat 证据；敏感信息必须过滤，PostHog 不作为高频原始日志池。
- QR refresh 失败态应可重试；若手机仍崩溃，优先回传 `FATAL EXCEPTION` / `Caused by` 堆栈。
- DSP 红框只能代表真实 fail-open/bypass/error，正常 native active 应为绿色。
- `python scripts/check_code_health.py` 当前会因既有 `MainActivity.kt` red-line 失败；拆分任务应记录行数下降趋势，不得新增 red finding。
