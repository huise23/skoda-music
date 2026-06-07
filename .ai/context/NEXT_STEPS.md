# NEXT_STEPS

Last Updated: 2026-06-07

## One-Line Summary
- 已完成首页/Scene/队列/VIP 本地实现，并完成 `RuntimeLogBinder` 与 `EqualizerPageBinder` 提取；下一步是手机/API17 设备回归验证，或重新规划下一批 `MainActivity` 拆分点。

## Current Highest Priority
- `T-S5-VAL-137`: 手机/API17 设备集成验证。设备窗口不可用时，`M-S5-MAIN-042` 的 RuntimeLog/EQ 提取已完成，继续开发前需规划下一批低耦合拆分任务。

## Guardrail-Adjusted Queue
- 当前 Ready:
  - `T-S5-VAL-137`: S5 集成设备验证执行包与证据回填。
- 当前 Planned:
  - `T-S5-TRIAGE-140`: 真实设备失败分流与 targeted fix 计划。
  - 后续 MainActivity 拆分任务：需重新规划。
  - API17 A~N/P 实机回归，需真实设备/手机环境回传证据。
- 当前 In Progress:
  - None
- 原因:
  - `T-S5-PLAY-110/111` 已完成 source boundary 和普通队列。
  - `KugouContentRenderer`、`KugouContentBinder`、`RuntimeLogBinder` 与 `EqualizerPageBinder` 已迁出内容页、日志和音效页职责，`MainActivity.kt` 最新约 5755 行，但仍超过 entry red-line。
  - `T-S5-PLAY-112` 已完成：radio active 时 next/previous/completion 由 `KugouRadioSessionManager` 接管，不走普通 queue 或 Emby queue。
  - `T-S4-AUDIO-097` 已完成：non-direct buffer 会走 direct scratch bridge，不再直接把按钮置红。
  - `T-S5-MAIN-116` 已完成：页面壳路线结论为先 Binder 化低耦合页面，再做 Fragment 试点。
  - `T-S5-VAL-113` 已完成：清单已覆盖 Main split、纯酷狗、普通 queue、Radio session、DSP direct-buffer bridge 和页面壳评估。
  - `T-S5-KG-119` 已完成：QR refresh 图片 URL/request 构造异常 fail-soft，旧异步回调会被 generation guard 忽略。
  - `T-S5-OBS-120` 已完成：QR/content/queue/radio 低频 PostHog 事件和敏感过滤已补齐，DSP 使用 runtime/logcat 证据。
  - `T-S5-KG-122` 已推送：QR `userid/token` 成功即有效登录态，首页推荐走 direct `/everyday_song_recommend`，推荐歌曲播放 URL 走 direct `/v5/url`。
  - `T-S5-KG-123` 本地完成：Radio、Discover、playlist songs、Like 当前路径已移除旧 `KugouWebApiClient` baseUrl gate，改走 `.NET` raw API 对齐的 direct 请求。

## Immediate Next Step

- 设备窗口可用时推荐执行:
  - 安装当前 debug APK 到手机/API17 设备，按 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 的 K/M/G/N 相关条目回归。
  - 重点回传 Scene list/song 真实响应是否匹配 Android 解析、Radio/Scene 缩略图弱网表现、首页/队列页自动滚动截图或视频、VIP record/receive/upgrade 与无权限播放提示证据。

- 设备窗口不可用时推荐执行:
  - 暂停继续堆新功能，回到 planning 为下一批 `MainActivity` 拆分建立任务。
  - 候选方向：播放/service bridge、下载控制、设置页其余低耦合区块；必须先做边界评估。

- 手机/API17 验证：
  - 未登录首页点击刷新应弹窗显示二维码。
  - 扫码成功后弹窗关闭并自动加载首页推荐。
  - 进入 Radio/Discover 时应懒加载 direct 数据；失败时显示“加载失败，点击重试”，不清已有内容。
  - 点击电台应拉取电台歌曲并进入 radio session。
  - 发现页应加载 tags -> playlists -> playlist songs。
  - 点击喜欢应记录为 pending -> success/failed，PostHog 只记录脱敏 `kugou_like_*` 事件。
  - 运行中本地登录态不可用时弹窗登录，不清当前列表/队列。
  - 冷启动或扫码登录成功后应后台触发 `kugou_daily_vip_start`，不阻塞每日推荐加载/播放。
  - 无权限/VIP 歌曲应显示“无权限播放，可能需要 VIP”，并记录 `kugou_direct_play_url_failed` 的 `failure_kind/error_code`。

推荐 logcat:

```bash
adb logcat -v time SkodaMusicEmby:D SkodaPostHog:I AndroidRuntime:E Toast:E '*:S'
```

## Planned After Ready
- `T-S5-TRIAGE-140`: 设备验证失败项分流后再进入 targeted fix。
- 下一批 MainActivity 红线治理：需先 planning，避免误拆播放/service 主链。
- 若实机发现 Scene 字段不一致，回到 `KugouMusic.NET` 和真实响应核对，修 `KugouSceneContentClient` 字段映射，不猜测协议。

## Blocked / Deferred
- `B-KG-EMBY-INGEST-001`: 点赞后将播放缓存上传到 Emby 并纳入媒体库。
  - 原因: Emby 上传并入库能力未确认。
  - 当前处理: 只记录点赞和入库阻塞状态，不执行上传。
- `T-S4-AUDIO-095`: AC83xx Native DSP 实机长播与听感验证。
  - 原因: 外部设备窗口。

## Important Notes
- 当前主干为 `master@18c4723`；本地有 `M-S5-KG-037` 与 `T-S5-KG-123` 实现改动，尚未推送。
- 2026-06-07 最新需求已规划：左侧每日推荐、酷狗隐藏 Emby 队列按钮、首页右侧当前队列、Scene `.NET` 来源、Radio/Scene 网格缩略图、Scene tab 展开收缩、所有队列自动滚动到当前歌曲。
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
- 当前 app 已接入每日一日 VIP record/receive/upgrade direct 流程；真实账号响应字段仍需设备验证。
- 新增功能必须有足够 PostHog/runtime/logcat 证据；敏感信息必须过滤，PostHog 不作为高频原始日志池。
- QR refresh 失败态应可重试；若手机仍崩溃，优先回传 `FATAL EXCEPTION` / `Caused by` 堆栈。
- DSP 红框只能代表真实 fail-open/bypass/error，正常 native active 应为绿色。
- `python scripts/check_code_health.py` 当前会因既有 `MainActivity.kt` red-line 失败；最新本地结果为 5755 行/粗略大方法 4026 行，后续拆分不得新增 red finding。
