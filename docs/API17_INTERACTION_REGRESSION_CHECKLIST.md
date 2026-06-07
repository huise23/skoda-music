# API17 Interaction Regression Checklist (S4/S5)

Last Updated: 2026-06-04
Scope: `T-S4-VAL-032` + `T-S4-AUDIO-060` + `T-S4-AUDIO-072` + `T-S4-AUDIO-086` + `T-S5-VAL-106` + `T-S5-VAL-113` + `T-S5-KG-119`

## Purpose
用于 Android `4.2.2`（API 17）车机实机回归，统一 S4 阶段验收口径：
- 后台服务常驻与前后台一致性
- 方向盘/通知/浮窗控制链路
- 熄火/休眠恢复自动续播
- 更新检测与下载安装触发链路
- 应用内保真 DSP 音效 fail-open 验证
- 酷狗默认来源模式、登录/session、推荐内容、点赞状态验证
- 纯酷狗播放 source 边界、普通队列、Radio/FM session 验证
- MainActivity 拆分后导航/页面壳稳定性验证
- QR refresh crash hotfix 与脱敏 PostHog 观测验证
- 关键事件与日志证据回传

## 1. Preconditions
- 设备: Android `4.2.2`（API 17），目标车机分辨率 `1024x600`。
- 构建: 标注 commit/build（使用当前 `T-S5-VAL-113` 对应 APK 或更新构建）。
- 网络: 可访问 Emby；可访问 GitHub（允许镜像回退）；可访问酷狗 direct 登录域名，不要求用户提供 WebApi 地址。
- 账号: 可用 Emby 账号；可用酷狗账号（扫码或手机号验证码）。
- 开关: 允许前台服务通知、允许悬浮窗权限。

## 2. Scope Boundaries
- In Scope:
  - `download-only` 播放链路可用性。
  - 后台命令入口一致性（`notification/overlay/media_button/audio_focus`）。
  - 浮窗策略（播放/暂停均显示；手动关闭后“进应用再切出”重显）。
  - 恢复链路（熄火/休眠恢复后自动续播）。
  - 更新链路（检查 -> 下载 -> 安装触发）。
  - 应用内保真 DSP 音效（开关/模式/持久化/PCM 处理/fail-open）。
  - S5 酷狗来源模式（登录、推荐歌曲、推荐电台、发现歌单、点赞/入库状态）。
  - 纯酷狗播放 source boundary、普通队列、Radio/FM session。
  - MainActivity Binder 拆分与页面壳拆分评估后的导航稳定性。
- Out of Scope:
  - 新需求（长标题滚动/主屏删除入口）
  - 静默安装/root 安装
  - 点赞后播放缓存上传到 Emby 并入库
  - 页面壳独立 Activity / Fragment 真实迁移实现（当前 `T-S5-MAIN-116` 结论为后续先抽 Binder 再试点）。

## 3. Checklist

### A. Build / Install / Launch Baseline
- [ ] A1 安装 APK 成功，无“解析包错误/版本过低”。
- [ ] A2 启动成功，无崩溃；首页左上角构建号 `#versionCode` 可见。
- [ ] A3 首帧后 UI 可交互，主按钮（Play/Pause/Next）可点击。

### B. Emby & Playback Baseline (`download-only`)
- [ ] B1 Emby 测试连接成功并能加载推荐/回退列表。
- [ ] B2 点击 Play 后进入可听播放，状态切换正确（buffering -> playing）。
- [ ] B3 Pause/Resume 可用；Next 可稳定切歌。
- [ ] B4 当前曲下载失败/缓存播放失败时自动切下一曲，不停在失败曲目。
- [ ] B5 连续切歌 10 次无崩溃；日志无持续异常刷屏。

### C. Background Command Matrix（S4 核心）
按 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 执行并回填。
- [ ] C1 `notification` 来源：`Prev / PlayPause / Next` 均可用。
- [ ] C2 `overlay` 来源：`Prev / PlayPause / Next` 均可用。
- [ ] C3 `media_button` 来源（方向盘或等效按键）：`Prev / PlayPause / Next` 均可用。
- [ ] C4 `audio_focus` 来源：后台失焦触发 pause 行为符合预期。
- [ ] C5 关键日志包含：
  - `service cmd result action=<...> source=<...> handled=<...> detail=<...>`

### D. Overlay Policy Regression
- [ ] D1 播放中显示浮窗（含歌名 + 三键）。
- [ ] D2 暂停中仍显示浮窗。
- [ ] D3 手动关闭浮窗后，不应立即自动重显。
- [ ] D4 执行“进入应用 -> 再切出”后浮窗重显。
- [ ] D5 浮窗歌名点击可拉起应用前台。
- [ ] D6 浮窗可拖动；重新显示后位置保持上次拖动结果。

### E. Resume / Recovery (Ignition-Sleep)
- [ ] E1 正在播放时进入熄火/休眠或等效场景后恢复，满足条件可自动续播。
- [ ] E2 续播后进度恢复合理（同曲目恢复，不错误 seek 到其他曲目）。
- [ ] E3 恢复失败场景不崩溃，并有可诊断日志（会话失效/网络失败）。

### F. Update Check / Download / Install Trigger
- [ ] F1 冷启动自动检测遵循节流（成功 24h，失败 30min），不阻断主流程。
- [ ] F2 设置页“检查更新”可手动触发并展示状态（最新/有新版本/失败）。
- [ ] F3 发现新版本后可下载 APK（镜像优先 + 官方回退）。
- [ ] F4 下载完成可触发系统安装器。
- [ ] F5 更新失败时可看到结构化失败信息（`failed_stage/failed_url/attempt_urls`）。

### G. Observability Evidence
- [ ] G1 `SkodaPostHog` 可见关键上报日志（含 `capture ok event=...`）。
- [ ] G2 关键节点至少覆盖：
  - `app_start/app_ready`
  - `play_start/play_success/playback_failed`
  - `background_command_received/background_command_result`
  - `update_check_* / update_download_* / update_install_*`
  - `kugou_qr_refresh_* / kugou_qr_poll_failed / kugou_session_validation_*`
  - `kugou_content_load_* / kugou_queue_start / kugou_radio_session_start`
- [ ] G3 敏感字段未明文上报（token/session key/cookie/手机号/验证码/完整 URL query/auth header/API key/response body 等）。

### H. Failure & Degrade Path
- [ ] H1 Emby 配置错误时有清晰失败反馈，不崩溃。
- [ ] H2 网络断开时播放/更新失败路径可见，恢复网络后可继续操作。
- [ ] H3 浮窗权限不可用时，通知控制条仍可兜底。

### I. Hi-Fi DSP Sound Mode Fail-Open
- [ ] I1 设置页可见“保真音效”开关与进入音效子页入口。
- [ ] I2 切换音效开关不会导致当前播放暂停、卡死或切歌。
- [ ] I3 音效子页显示音质模式：`原声 / 保真 / 清晰 / 动感 / 柔和`，不展示 ROM preset/band 数。
- [ ] I4 进入子页后，左侧展示当前听感说明，右侧展示模式按钮，整体仍保持玻璃态横屏风格。
- [ ] I5 选择 `保真 / 清晰 / 动感 / 柔和` 后开关同步开启；选择 `原声` 后进入旁路。
- [ ] I6 切换模式时当前播放尽量实时生效，不重建队列、不切歌、不停播。
- [ ] I7 运行日志包含 Native DSP 关键路径：
  - `hifi-dsp config enabled=<...> mode=<...>`
  - `hifi-dsp format sr=<...> ch=<...>`
  - `hifi-dsp native configured sr=<...> ch=<...>`
  - `hifi-dsp native active mode=<...> ... tier=quality`
  - `hifi-dsp native status=<ok|bypass|error> mode=<...> tier=<quality|balanced|safe> costUs=<...> flags=<...>`
  - 若发生 heap/direct 桥接：`hifi-dsp native direct-buffer bridge input=<...> output=<...>`
  - `hifi-dsp bypass mode=<...>`
  - 异常时：`hifi-dsp bypass reason=native-process-error`
- [ ] I8 若当前音频格式不支持 DSP，必须自动旁路原声并记录 `hifi-dsp bypass unsupported format`。
- [ ] I9 重启后音效配置安全恢复；旧 EQ 配置存在时不得触发 Android `audiofx` 写入失败循环。
- [ ] I10 连续播放 30 分钟无明显卡顿、爆音、破音、闪退。
- [ ] I11 切歌、seek、暂停/恢复后音效模式保持一致。
- [ ] I12 听感对比：`原声` 接近无处理；`保真` 更清楚不糊；`清晰/动感/柔和` 有方向差异但不过度。
- [ ] I13 若 AC83xx CPU 超预算，应先看到 `tier=balanced` 或 `tier=safe` 的降档日志；仍超预算时允许 `status=bypass`，但播放不能中断。
- [ ] I14 播放按钮边框颜色符合语义：关闭/未知灰色、native active 绿色、降级/超预算黄色、真实 fail-open/bypass/error 红色。
- [ ] I15 若日志出现 `hifi-dsp native direct-buffer bridge ...`，按钮不应仅因 heap buffer 桥接持续红色；后续应出现 `native status=ok` 或明确真实 bypass/error 原因。

### J. Kugou Source Mode Login / Session
- [ ] J1 冷启动默认进入左侧“推荐歌曲”入口，不显示 Home 二级 tab。
- [ ] J2 未登录时推荐歌曲页展示登录入口；点击刷新通过弹窗展示酷狗扫码登录，不后台请求推荐内容。
- [ ] J3 设置页不显示 Kugou WebApi Base URL 输入框；可见酷狗登录状态。
- [ ] J4 点击“刷新扫码登录”不会要求填写地址，会弹窗并通过 direct `/v2/qrcode` 获取二维码或二维码图片 URL；首页不直接内嵌显示二维码。
- [ ] J5 扫码轮询约 2 秒一次，走 direct `/v2/get_userinfo_qrcode`，等待扫码、等待确认、成功、过期状态文案可区分。
- [ ] J6 旧 `X-Kg-Session-Id` / `kugou_webapi_base_url` 缓存在启动后被清理，不被复用；QR success 后先进入 direct session 登录态，再执行设备/Token 增强校验。
- [ ] J7 手机号验证码按钮不会要求填写 WebApi 地址，不发起旧 `/captcha/sent` 代理请求，并显示 SMS direct pending 状态。
- [ ] J8 扫码返回 `userid/token` 后显示“已登录”；device register/token refresh 失败只记录 `kugou_session_validation_deferred`，不提示登录失败；仅缺少 `userid/token` 时阻断。
- [ ] J9 登出后清理 session、二维码和酷狗内容状态。
- [ ] J10 连续点击“刷新扫码登录”10 次不崩溃；二维码地址异常、图片下载失败、断网/弱网、切后台/返回后的旧回调都进入失败/可重试状态。
- [ ] J11 QR refresh / login recovery 相关 PostHog/logcat 证据脱敏可见：`kugou_qr_refresh_start/success/failed`、`kugou_qr_poll_failed`、`kugou_session_validation_success/deferred/failed`、`kugou_auth_dialog_shown`、`kugou_auth_recovery_resume`、`kugou_post_login_auto_load`。
- [ ] J12 运行中 session/token 不可用时不清空当前酷狗列表/队列/页面内容，只弹登录；扫码成功后弹窗隐藏并继续当前页面或默认页加载。

### K. Kugou Content Pages
- [ ] K1 推荐歌曲页未登录时展示登录/待登录状态，不后台刷失败请求。
- [ ] K2 推荐歌曲页登录后通过 Android direct `/everyday_song_recommend` 展示推荐歌曲；失败、空结果、session 失效时有明确反馈，不依赖用户填写 Kugou WebApi Base URL。
- [ ] K3 推荐电台页进入时懒加载；未登录时弹登录，失败时保留页面并显示点击重试入口。
- [ ] K4 点击电台后可加载并展示电台歌曲；加载完成后进入 radio session 首曲播放。
- [ ] K5 发现歌单页进入时懒加载分类/标签；未登录时弹登录，失败时保留页面并显示点击重试入口。
- [ ] K6 点击标签后可加载歌单。
- [ ] K7 点击歌单后可加载歌单歌曲。
- [ ] K8 三个酷狗内容页遇到网络失败、空结果或未登录时有明确反馈，不闪退。
- [ ] K9 1024x600 横屏下左侧一级导航、登录面板、列表行、点赞按钮不重叠。
- [ ] K10 `MainActivity` 拆出 `KugouContentBinder` 后，推荐歌曲、推荐电台、发现歌单页面切换和刷新不丢状态、不崩溃。

### L. Kugou Like / Ingest Status
- [ ] L1 酷狗歌曲行可见点赞按钮。
- [ ] L2 点赞调用 Android direct `/cloudlist.service/v6/add_song`，目标列表 ID 为 `2`（我喜欢），成功后记录 `liked`。
- [ ] L3 点赞失败记录 `failed` 和失败原因。
- [ ] L4 点赞/入库状态页展示歌曲、来源、远端状态、入库状态和失败原因。
- [ ] L5 Emby 入库状态显示 `blocked_ingest` 或等价阻塞文案，不误报已入库。
- [ ] L6 重启后点赞历史仍可查看。

### M. Pure Kugou Playback / Queue / Radio Session
- [ ] M1 默认酷狗模式下点击推荐歌曲后通过 Android direct `/v5/url` 获取播放 URL，Now Playing 显示酷狗歌曲标题/歌手，不从 Emby 当前队列推导。
- [ ] M2 酷狗播放期间 `Prev / PlayPause / Next` 不触发 Emby `loadedTracks/currentTrackIndex` 推进。
- [ ] M2.1 默认酷狗冷启动不恢复 Emby cached queue、不自动播放 Emby、不触发 Emby recommendation auto-refresh；PostHog 可见 `resume_restore_skipped` / `emby_auto_refresh_skipped`。
- [ ] M3 酷狗播放时前台通知、浮窗、方向盘/媒体键的当前曲信息与控制结果来自 source playback session。
- [ ] M4 酷狗普通歌曲队列：推荐歌曲/发现歌单歌曲点击后建立普通 queue；队列页显示“酷狗普通队列”。
- [ ] M5 普通 queue 的 next/previous 在当前上下文内循环，不进入 Emby 队列。
- [ ] M6 点击推荐电台或电台歌曲后进入“酷狗电台队列”；队列页展示 current + upcoming。
- [ ] M7 radio active 时 next 推进 upcoming，previous 从 history 回退；不走普通 Kugou queue 或 Emby queue。
- [ ] M8 radio 当前曲自然结束后，由 radio session 推进下一首；没有 upcoming 时显示队列末尾/不可切换反馈，不崩溃。
- [ ] M9 从 radio 切到普通 Kugou queue 会清 radio session；切回 Emby 播放会清 Kugou queue/radio session。
- [ ] M10 旧 WebApi Base URL 输入路径不得恢复；如果内容 API 未可用，应给出登录/接口不可用反馈，而不是要求用户填写地址。

### N. MainActivity Split / Page Shell Decision
- [ ] N1 `KugouAuthConfigBinder` 登录/登出/QR 刷新仍可用。
- [ ] N2 `KugouContentBinder` 页面加载、渲染和播放/点赞回调仍可用。
- [ ] N3 `docs/PAGE_SHELL_SPLIT_EVALUATION.md` 结论已纳入交接：当前阶段不直接引入独立 Activity 或 raw Fragment 迁移。
- [ ] N4 左侧一级导航在 Home / 推荐电台 / 发现歌单 / 队列 / 点赞状态 / 设置 / EQ 间切换稳定。
- [ ] N5 返回键行为保持：EQ 返回设置页，其他非首页页面返回首页，首页返回后台。

## 4. Risk Control & Acceptance Checklist (Section 4)

### 4.1 Risk Gates（任一命中即 Blocker）
- [ ] R1 后台命令矩阵出现“多数 handled=false 且不可复现定位”。
- [ ] R2 浮窗策略不符合已确认规则（关闭后乱重显或无法重显）。
- [ ] R3 熄火/休眠恢复后无法自动续播且无可诊断降级路径。
- [ ] R4 更新链路在主流网络场景下持续失败且无回退解释。
- [ ] R5 回归过程中出现崩溃/ANR/连续卡死。
- [ ] R6 酷狗未登录状态仍请求内容或出现不可操作空白页。
- [ ] R7 酷狗 session 失效后没有回登录，导致持续失败刷屏。
- [ ] R8 刷新二维码导致崩溃、ANR 或不可恢复登录状态。

### 4.2 Evidence Minimum（最小回传集）
- [ ] EVD1 设备信息 + 构建号（`#versionCode`）。
- [ ] EVD2 背景命令矩阵表（四来源）。
- [ ] EVD3 至少 1 条失败样本（含复现步骤 + 日志关键片段）。
- [ ] EVD4 更新链路样本（至少 1 次检测结果；若失败附 `failed_stage`）。
- [ ] EVD5 至少 1 份截图或短视频说明关键现象。
- [ ] EVD6 DSP fail-open 样本（至少 1 条 `hifi-dsp bypass` / `hifi-dsp native status=... flags=...` 日志 + 对应播放不中断证据）。
- [ ] EVD7 Native DSP 性能样本（至少 3 条不同时间点 `mode/tier/costUs/flags` 日志，覆盖长播或模式切换）。
- [ ] EVD8 Kugou 登录样本（扫码或验证码路径，含 session 复用/失效观察）。
- [ ] EVD9 Kugou 内容样本（推荐歌曲、电台、发现歌单各至少 1 张截图或日志）。
- [ ] EVD10 点赞状态样本（成功或失败均可，需包含状态页截图/日志）。

### 4.3 Acceptance Decision
- `PASS`: 无 Blocker，且 A~N 关键项通过。
- `PASS with Risks`: 无 Blocker，但存在可接受风险并已有追踪项。
- `FAIL`: 命中任一 Blocker，或关键链路不可复现/不可诊断。

## 5. Result Template
每台设备回传一份，可直接复制填写。

```md
### Device Report
- Device: <品牌/型号>
- Android: 4.2.2 (API 17)
- APK: <文件名/commit/build>
- Build Badge: <#versionCode>
- Test Time: <YYYY-MM-DD HH:mm>
- Tester: <姓名或角色>

### Group Result
- A Build/Launch: PASS/FAIL
- B Playback Baseline: PASS/FAIL
- C Background Matrix: PASS/FAIL
- D Overlay Policy: PASS/FAIL
- E Resume/Recovery: PASS/FAIL
- F Update Chain: PASS/FAIL
- G Observability: PASS/FAIL
- H Failure/Degrade: PASS/FAIL
- I Hi-Fi DSP Sound Mode: PASS/FAIL
- J Kugou Login/Session: PASS/FAIL
- K Kugou Content Pages: PASS/FAIL
- L Kugou Like/Status: PASS/FAIL
- M Kugou Playback/Queue/Radio: PASS/FAIL
- N Main Split/Page Shell: PASS/FAIL

### Section 4 Decision
- Risk Gate Triggered: YES/NO
- Triggered Risk IDs: <R1/R2/... 或 None>
- Evidence Minimum Complete: YES/NO
- Final Decision: PASS / PASS with Risks / FAIL

### Failed Items
1. <条目ID + 现象>
2. <条目ID + 现象>

### Key Logs / Evidence
- command_result: <action/source/handled/detail>
- playback_error: <error_code/stage/request_id>
- update_failed: <failed_stage/failed_url/attempt_urls>
- posthog: <capture ok 或失败样本>
- hifi_dsp: <hifi-dsp config/format/active/bypass/fail 日志样本>
- kugou: <qr/session/content/like 日志样本，需包含 QR refresh crash hotfix 证据>
- kugou_queue_radio: <普通 queue/radio session next/previous/completion 日志样本>
- main_split: <页面切换/返回键/页面壳评估验证说明>
- screenshot/video: <说明或路径>

### Conclusion
- Blocker: YES/NO
- Must Fix Before Next Round: <列表>
- Notes: <补充>
```

## 6. Local Validation Snapshot (T-S4-AUDIO-086, 2026-06-01)
- 构建验证:
  - `gradle :app:compileDebugKotlin --no-daemon` 通过
- 本地回归结论:
  - 音效主线已切换为应用内保真 DSP，不再默认触发 Android `audiofx`。
  - ExoPlayer 2.17.1 通过自定义 `AudioProcessor` 接入 PCM 处理链。
  - 失败策略保持 fail-open：DSP 关闭/原声/格式不支持/运行异常均旁路原始 PCM，不中断播放。
- 待外部验证:
  - API17 目标车机需要验证五种模式听感差异、长播稳定性、切歌/seek 后状态一致性。

## 7. Local Validation Snapshot (T-S5-VAL-106, 2026-06-03)
- 构建验证:
  - `git diff --check` 通过
  - `./scripts/check_api17_guardrails.sh` 通过
  - `gradle :app:compileDebugKotlin --no-daemon` 通过
  - `gradle :app:assembleDebug --no-daemon` 通过
- 本地回归结论:
  - 酷狗登录、session cache、推荐歌曲、电台、发现歌单、点赞状态页均已完成编译级闭环。
  - 酷狗内容接口字段均按 `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md` 和 `KugouMusic.NET/` 对应 controller/client/model 映射。
  - 点赞后 Emby 入库保持阻塞状态，不执行上传。
- 待外部验证:
  - 真实 Kugou WebApi 地址、扫码/验证码账号、session 失效和内容接口返回需在目标设备或同网环境验证。

## 8. Local Validation Snapshot (T-S5-VAL-113, 2026-06-04)
- 覆盖变更:
  - `T-S5-MAIN-115`: `KugouContentRenderer` / `KugouContentBinder` 拆分。
  - `T-S5-PLAY-110/111/112`: 纯酷狗 source playback、普通 queue、Radio/FM session。
  - `T-S4-AUDIO-097`: DSP non-direct buffer direct scratch bridge，避免误报持续红框。
  - `T-S5-MAIN-116`: 页面壳拆分评估结论。
- 构建验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，blocking finding 数为 2，未新增 blocking。
- 本地回归结论:
  - 默认酷狗播放、普通队列和 Radio session 已有独立 source/session 边界，不复用 Emby 队列。
  - Radio active 时 next/previous/completion 由 `KugouRadioSessionManager` 的 current/upcoming/history 推进。
  - DSP heap `ByteBuffer` 不再直接触发 `FAIL_OPEN`；若仍红色，应按 `native-not-ready/native-process-error/native status=bypass|error` 继续定位。
  - 页面壳拆分当前选择“先低耦合 Binder，再 Fragment 试点”，不在本阶段直接引入独立 Activity。
- 待外部验证:
  - API17 目标车机需执行 A~N 分组，尤其 M 组 Radio session、I14/I15 DSP 边框语义和 N 组导航/返回键。
  - 真实酷狗登录、内容返回、播放 URL 和 radio 列表仍依赖账号与网络环境。

## 9. Local Validation Snapshot (T-S5-KG-119 / T-S5-OBS-120, 2026-06-05)
- 覆盖变更:
  - `T-S5-KG-119`: QR refresh crash hotfix + fail-soft observability。
  - `T-S5-OBS-120`: S5 新功能 PostHog 覆盖补齐与敏感字段审计。
- 构建验证:
  - `git diff --check` 通过。
  - `./scripts/check_api17_guardrails.sh` 通过。
  - `gradle :app:compileDebugKotlin --no-daemon` 通过。
  - `gradle :app:assembleDebug --no-daemon` 通过。
  - `python scripts/check_code_health.py` 仍因既有 `MainActivity.kt` red-line 失败，blocking finding 数为 2，未新增 blocking。
- 本地回归结论:
  - QR image URL/request 构造异常已 fail-soft，不应再因无效二维码图片 URL 抛未捕获异常导致闪退。
  - QR refresh 使用 generation guard 忽略刷新/登出/停止后的旧异步回调。
  - QR/content/queue/radio 低频 PostHog 事件已补齐，敏感字段过滤规则已扩展。
- 待外部验证:
  - 手机/API17 设备连续点击“刷新扫码登录”10 次，覆盖弱网、断网、切后台/返回和接口失败。
  - 回传 `SkodaPostHog capture ok/failed/exception event=kugou_qr_refresh_*` 或等价日志样本，确认无 token/session/cookie/手机号/验证码/完整 URL query/auth header/API key。
