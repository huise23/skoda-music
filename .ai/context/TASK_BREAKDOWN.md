# TASK_BREAKDOWN

Last Updated: 2026-04-27

## Active Stage: S4 车机后台控制落地（方案1 / Legacy 稳态）

## T-S4-CORE-026A
- Task ID: `T-S4-CORE-026A`
- Title: 核心命令链路收口（Service/Activity 职责边界）
- Module ID: `M-S4-CORE-001`
- Goal: 明确并固化命令执行真源与状态上报边界，降低前后台行为不一致。
- Why: 当前仍处于“稳定补丁 + 迁移并行”状态，不先收口会持续回归。
- Dependencies: 无
- Inputs:
  - `MainActivity.kt`
  - `PlaybackService.kt`
  - `PlaybackControlBus.kt`
  - `PlaybackStateStore.kt`
- Expected Outputs:
  - 命令入口行为一致，执行失败可被真实感知（无假成功）。
  - 状态快照字段可稳定驱动通知/浮窗展示。
- Done Criteria:
  - 前后台控制不再出现“命令已收但未执行”分叉。
  - 链路日志可定位 command source 与执行结果。
- Risks:
  - 与前台稳定性热修存在策略冲突，需以实机表现裁决。
- Size: M
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-CORE-026B
- Task ID: `T-S4-CORE-026B`
- Title: 后台命令矩阵自测（notification/overlay/media_button/audio_focus）
- Module ID: `M-S4-CORE-001`
- Goal: 对四类来源命令做统一回归，确认行为和日志口径一致。
- Why: 后台入口多源，单点验证不足以证明链路稳定。
- Dependencies: 无（基于当前已接线链路可并行执行）
- Inputs:
  - 后台命令来源日志（`source`）
  - Service 命令执行结果
  - `docs/S4_BACKGROUND_COMMAND_MATRIX.md`
- Expected Outputs:
  - 一份后台命令矩阵（来源 x 命令 x 结果）。
  - 命令结果可观测字段（`handled/detail`）在运行日志可见。
  - 失败路径复现步骤与初步修复点。
- Done Criteria:
  - 四类来源均完成 `prev/play_pause/next` 验证。
  - 至少形成一次失败样本的可复现定位记录（若存在失败）。
- Risks:
  - 仅本地模拟仍可能与车机实机行为不一致。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-CORE-026C
- Task ID: `T-S4-CORE-026C`
- Title: 浮窗与通知策略闭环
- Module ID: `M-S4-CONTROL-002`
- Goal: 固化“播放/暂停显示 + 手动关闭后进应用再切出重显”策略，并验证通知兜底可用。
- Why: 浮窗策略是用户明确验收项，且最易在生命周期切换中回归。
- Dependencies: `T-S4-CORE-026A`
- Inputs:
  - `OverlayController.kt`
  - `PlaybackService.kt`
  - `AndroidManifest.xml`
- Expected Outputs:
  - 浮窗显示状态机验证结论。
  - 通知控制条与浮窗控制一致性结论。
- Done Criteria:
  - 手动关闭后仅在“进应用再切出”路径重显。
  - 通知控制在浮窗不可用场景下可稳定兜底。
- Risks:
  - 部分车机 ROM 的悬浮窗权限行为不稳定。
- Size: M
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-RESUME-020B
- Task ID: `T-S4-RESUME-020B`
- Title: 服务侧自动续播二阶段闭环
- Module ID: `M-S4-RESUME-003`
- Goal: 将恢复逻辑从“可恢复”推进到“可车机验收”的服务侧闭环。
- Why: 熄火/休眠自动续播是 S4 强需求，当前仍待实机闭环。
- Dependencies: `T-S4-CORE-026A`
- Inputs:
  - `PlaybackResumeStore.kt`
  - `PlaybackService.kt`
  - 当前恢复策略决策（12 小时窗口/同曲 seek）
- Expected Outputs:
  - 服务重建后的恢复触发与降级策略稳定。
  - 恢复失败时可直接定位的日志与状态码。
- Done Criteria:
  - 恢复成功场景可自动续播并恢复进度。
  - 失败场景不崩溃，且降级路径符合预期。
- Risks:
  - 账号会话失效和网络波动会干扰稳定复现。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-VAL-032
- Task ID: `T-S4-VAL-032`
- Title: 升级 API17 回归清单并补齐 Section 4 验收模板
- Module ID: `M-S4-VALID-004`
- Goal: 将 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 从 S1 口径升级到 S4 验收口径。
- Why: 当前清单停留在 S1，无法覆盖后台控制/浮窗/恢复关键场景。
- Dependencies: 无
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - `.ai/context/SCOPE.md`
  - `.ai/context/CURRENT_STATUS.md`
- Expected Outputs:
  - 覆盖 S4 场景的实机清单。
  - Section 4（风险控制与验收清单）模板。
- Done Criteria:
  - 非开发同事可按步骤执行并输出 PASS/FAIL。
  - 清单字段可直接用于 `T-S4-REG-022` 结果回传。
- Risks:
  - 条目过细会降低现场执行效率。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-REG-022
- Task ID: `T-S4-REG-022`
- Title: 车机实机回归执行（S4）
- Module ID: `M-S4-VALID-004`
- Goal: 在 API17 目标车机执行 S4 全量回归并产出证据。
- Why: S4 完成标准是实机可验证，不是代码层推断。
- Dependencies: `T-S4-CORE-026B`, `T-S4-CORE-026C`, `T-S4-RESUME-020B`, `T-S4-VAL-032`, `T-S4-OBS-036`, `T-S4-OBS-037`
- Inputs:
  - 升级后的 S4 回归清单
  - 目标构建包与构建号
  - 车机设备窗口
- Expected Outputs:
  - 按分组的 PASS/FAIL/Blocker 结果。
  - 失败项复现步骤 + 日志/截图/视频证据。
- Done Criteria:
  - 覆盖后台按键、浮窗策略、通知兜底、自动续播。
  - 至少 1 台 API17 设备完成完整执行。
  - PostHog 中可查询到对应 session 的关键事件链路。
- Risks:
  - 车机窗口不可控，可能导致执行中断。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-VAL-033
- Task ID: `T-S4-VAL-033`
- Title: 实机证据回写与阶段收口
- Module ID: `M-S4-VALID-004`
- Goal: 将 `T-S4-REG-022` 结果结构化回填到 context 文件。
- Why: 无回写就无法形成下一轮可持续执行入口。
- Dependencies: `T-S4-REG-022`
- Inputs:
  - Device Report
  - 失败条目与证据
- Expected Outputs:
  - 更新 `CURRENT_STATUS/HANDOFF/TASK_QUEUE/NEXT_STEPS`。
  - 阶段结论（继续修复 or 进入下一阶段）。
- Done Criteria:
  - 文档状态与实机结论一致。
  - 下一轮 Ready 任务明确且可执行。
- Risks:
  - 证据不完整时结论不稳，可能需要补测。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-034
- Task ID: `T-S4-OBS-034`
- Title: PostHog 事件模型与字段规范落地
- Module ID: `M-S4-OBS-006`
- Goal: 定义播放器关键节点事件、公共属性、错误码规范与命名规则。
- Why: 不先固化 schema，后续埋点会碎片化，查询与 AI 分析成本高。
- Dependencies: 无
- Inputs:
  - 用户确认的 PostHog 使用目标（关键节点观测 + 排障 + AI 分析）
  - 现有 `S4_BACKGROUND_COMMAND_MATRIX` 与 runtime log 字段
  - `docs/POSTHOG_INSTRUMENTATION_PLAN.md`
- Expected Outputs:
  - 事件字典文档（10~20 核心事件 + 公共属性 + stage/error_code 口径）。
  - “上报什么/不上报什么”边界清单（避免高频噪音）。
- Done Criteria:
  - 事件名与属性命名稳定（snake_case），覆盖 S4 主链路。
  - 字段满足 session 追踪与失败聚合分析。
  - 明确“禁止上报清单”（高频进度/频繁 buffer 状态/UI redraw/HTTP headers）。
- Risks:
  - 事件定义过细会导致埋点噪音和执行负担。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-035
- Task ID: `T-S4-OBS-035`
- Title: API17 兼容 PostHog 上报客户端（fail-open）
- Module ID: `M-S4-OBS-006`
- Goal: 基于现有网络栈实现轻量上报层，确保上报失败不影响播放链路。
- Why: 直接引入重 SDK 有 API17 风险，需先保证可控与兼容。
- Dependencies: `T-S4-OBS-034`
- Inputs:
  - `OkHttp 3.12.13` 现有依赖
  - 事件模型文档
- Expected Outputs:
  - `PostHogReporter`（或同等组件）支持异步上报、节流、超时与开关控制。
  - 本地缓冲/丢弃策略（有界队列，避免内存膨胀）。
  - 事件去重/合并策略（同事件短窗口 coalesce）。
- Done Criteria:
  - 上报异常不会阻断 UI/Service 业务流程。
  - 上报 payload 包含公共属性与事件属性。
- Risks:
  - endpoint/key 未配置时仅可本地验证链路，无法真实入库。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-OBS-036
- Task ID: `T-S4-OBS-036`
- Title: 关键节点埋点接线（启动/播放/后台命令/错误）
- Module ID: `M-S4-OBS-006`
- Goal: 将 PostHog 上报接入 S4 关键路径，形成可查询事件链。
- Why: 只有客户端存在不足以排障，必须在关键节点真实触发。
- Dependencies: `T-S4-OBS-035`, `T-S4-CORE-026A`
- Inputs:
  - `MainActivity.kt`, `PlaybackService.kt`, `PlaybackStateStore.kt`
  - 事件模型文档
- Expected Outputs:
  - 启动、播放成功/失败、后台命令结果、恢复失败等事件可上报。
  - 每条事件含 `session_id/device_id/app_version/build_number` 等公共属性。
- Done Criteria:
  - 关键路径事件能在调试日志中看到发送记录并可定位失败原因。
  - 高频路径具备去抖/采样，避免事件洪泛。
- Risks:
  - 埋点点位过多会引入维护成本与性能噪音。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-OBS-037
- Task ID: `T-S4-OBS-037`
- Title: 上报门禁与隐私策略（PII Guard + Config）
- Module ID: `M-S4-OBS-006`
- Goal: 明确并落地“可上报字段”和“敏感字段脱敏/禁止上报”规则。
- Why: 防止把密码/token/完整响应体等敏感内容带入事件平台。
- Dependencies: `T-S4-OBS-034`, `T-S4-OBS-035`
- Inputs:
  - 当前日志口径（已限制敏感/冗余日志）
  - 配置入口（Settings / BuildConfig / 本地开关）
- Expected Outputs:
  - PostHog 开关策略（默认值、调试启用方式、失效回退）。
  - 敏感字段黑名单与脱敏策略文档。
- Done Criteria:
  - 默认配置下不会上报敏感凭据与大体积原文。
  - 具备快速总开关（线上故障时可一键关闭上报）。
- Risks:
  - 规则遗漏会引发隐私风险或无效数据污染。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-038
- Task ID: `T-S4-OBS-038`
- Title: PostHog 查询验证与 AI 导出模板
- Module ID: `M-S4-OBS-006`
- Goal: 固化查询与导出流程，确保事件可用于实际排障和 AI 分析。
- Why: 若只接线不验证查询，埋点价值无法闭环。
- Dependencies: `T-S4-OBS-036`, `T-S4-OBS-037`
- Inputs:
  - PostHog 事件数据
  - 典型故障场景（播放失败、后台命令失败、恢复失败）
- Expected Outputs:
  - 查询清单（最近失败分布、单 session 事件流、版本对比）。
  - AI 输入模板（JSON/CSV 导出字段建议）。
  - 噪音审计结果（确认禁用高频低价值事件）。
- Done Criteria:
  - 至少 1 条完整 session 事件流可导出并复盘。
  - 可回答“失败在哪个 stage、错误码分布、版本差异”。
- Risks:
  - 无真实 endpoint/project key 时无法完成最终在线验证。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-039
- Task ID: `T-S4-OBS-039`
- Title: PostHog 接入参数确认（Host/Project Key/环境口径）
- Module ID: `M-S4-OBS-006`
- Goal: 明确云版或自建 endpoint、project key、环境隔离与保留策略。
- Why: 未确认接入参数会阻塞真实上报验证。
- Dependencies: 无
- Inputs:
  - 用户环境选择（Cloud / Self-host）
  - 数据保留与访问口径
  - `docs/POSTHOG_CONFIG_CHECKLIST.md`
- Expected Outputs:
  - 可执行的接入参数清单（dev/prod）。
  - 环境隔离规则（避免测试数据污染生产）。
- Done Criteria:
  - `T-S4-OBS-035/036/038` 可使用真实参数完成联调。
- Risks:
  - 参数长期缺失会使埋点仅停留在本地假联调。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UI-023
- Task ID: `T-S4-UI-023`
- Title: 长标题滚动异常修复
- Module ID: `M-S4-UX-005`
- Goal: 修复车机上长标题不可读问题并形成可验证策略。
- Why: 用户已明确反馈，但当前口径细节未确认。
- Dependencies: 需求口径确认
- Inputs:
  - `activity_main.xml`
  - 文案长度样本
- Expected Outputs:
  - 可配置的滚动/截断方案与验收标准。
- Done Criteria:
  - 标题可读性明显提升且不引入抖动回归。
- Risks:
  - 不同分辨率/字体缩放下表现不一致。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: No

## T-S4-UI-024
- Task ID: `T-S4-UI-024`
- Title: 删除入口迁移到主屏
- Module ID: `M-S4-UX-005`
- Goal: 在主屏提供“不好听立即删”的安全入口（含防误触）。
- Why: 用户明确要求，但交互口径尚未最终确认。
- Dependencies: 交互方案确认
- Inputs:
  - 主屏布局与队列行为
- Expected Outputs:
  - 删除入口位置与交互规则。
- Done Criteria:
  - 删除动作可用且不会误触破坏当前播放链路。
- Risks:
  - 误触与撤销策略不清会导致体验风险。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: No

## T-S4-AUDIO-025
- Task ID: `T-S4-AUDIO-025`
- Title: 均衡器/音效优化
- Module ID: `M-S4-UX-005`
- Goal: 形成 API17 可行的听感优化基线与默认参数。
- Why: 用户有明确优化诉求，但不应阻塞 S4 主验收。
- Dependencies: `T-S4-REG-022`
- Inputs:
  - 车机音频栈能力
  - 听感对比样本
- Expected Outputs:
  - 可执行的优化方案或不可行结论。
- Done Criteria:
  - 至少 1 组参数在目标车机有正向听感反馈。
- Risks:
  - 车机音频栈差异导致结论不可迁移。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: No

## T-S4-UPD-040
- Task ID: `T-S4-UPD-040`
- Title: 更新源与版本比较规则落地（GitHub Releases）
- Module ID: `M-S4-UPD-007`
- Goal: 定义并实现版本元数据读取、tag 解析、版本比较与稳定版本过滤规则。
- Why: 无稳定规则会导致误报更新或漏检，后续下载链路不可控。
- Dependencies: 无
- Inputs:
  - `.github/workflows/package-mvp.yml`
  - `docs/CI_SIGNING_RELEASE_RUNBOOK.md`
  - 当前版本号来源（`versionName/versionCode`）
- Expected Outputs:
  - 更新元数据模型（tag/versionName/versionCode/apk asset/url）。
  - 版本比较策略（优先 `versionCode`，兼容 tag 解析）。
  - prerelease/draft 过滤策略与失败回退行为。
- Done Criteria:
  - 可稳定得到“是否有更新 + 目标下载链接 + 展示文案”。
  - 解析失败不会影响主流程（fail-open）。
- Risks:
  - release 资产命名不一致导致匹配失败。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UPD-041
- Task ID: `T-S4-UPD-041`
- Title: 冷启动自动检测与节流策略
- Module ID: `M-S4-UPD-007`
- Goal: 在冷启动流程接入自动检测，并加入检测周期节流与网络失败回退。
- Why: 自动检测是核心诉求，但必须避免每次冷启动都触发重网络请求。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - `MainActivity.kt` 启动链路
  - 本地持久化策略（SharedPreferences）
- Expected Outputs:
  - 冷启动检测触发点（不阻断播放初始化）。
  - 检测节流参数（建议 24h，可配置）。
  - 失败回退与状态缓存（避免频繁重试）。
- Done Criteria:
  - 冷启动可自动触发检测，且命中节流时不重复请求。
  - 检测失败仅记录状态，不影响播放功能。
- Risks:
  - 与现有启动优化链路冲突，影响首帧体验。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-UPD-042
- Task ID: `T-S4-UPD-042`
- Title: 设置页手动检查更新入口与结果展示
- Module ID: `M-S4-UPD-007`
- Goal: 在 Settings 页面增加“检查更新”入口，并展示明确状态。
- Why: 自动检测需要手动兜底入口，便于现场排障与用户主动升级。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - `activity_main.xml`（Settings 区域）
  - `MainActivity.kt` 设置页事件绑定
  - `strings.xml`
- Expected Outputs:
  - 设置页按钮与状态文本（检测中/已最新/发现更新/失败）。
  - 手动检查触发逻辑（可绕过冷启动节流）。
- Done Criteria:
  - 设置页可重复手动检查并得到可理解反馈。
  - UI 状态变化与日志一致。
- Risks:
  - 状态文案不清晰会误导用户。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UPD-043
- Task ID: `T-S4-UPD-043`
- Title: GitHub 镜像加速下载与官方回退链路
- Module ID: `M-S4-UPD-007`
- Goal: 对更新 APK 下载实现“镜像优先 + 官方回退”的容错下载链路。
- Why: 车机网络环境复杂，单一路径下载失败率高。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - GitHub release 资产链接
  - 镜像域名策略（默认内置，可后续配置化）
  - 下载与文件落地路径策略
- Expected Outputs:
  - 镜像 URL 构造与优先级策略（例如 `ghproxy` / `github.moeyy` 等）。
  - 下载失败自动回退到下一镜像或官方链接。
  - 下载进度/结果记录（日志 + 事件）。
- Done Criteria:
  - 至少 1 条镜像链路与官方链路可完成下载。
  - 任一镜像失败不导致整体更新流程中断。
- Risks:
  - 镜像服务可用性与合规性存在波动。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-UPD-044
- Task ID: `T-S4-UPD-044`
- Title: 安装触发与更新链路观测闭环
- Module ID: `M-S4-UPD-007`
- Goal: 下载完成后触发系统安装，并补齐更新链路关键观测事件。
- Why: 没有安装触发和观测，更新能力无法形成可验证闭环。
- Dependencies: `T-S4-UPD-041`, `T-S4-UPD-042`, `T-S4-UPD-043`, `T-S4-OBS-036`
- Inputs:
  - 下载完成的 APK 文件
  - 安装触发 Intent/FileProvider 配置
  - PostHog 事件上报组件
- Expected Outputs:
  - 安装触发逻辑（兼容 API17 文件 URI 约束）。
  - 关键事件：`update_check_*`, `update_download_*`, `update_install_triggered/failed`。
  - 最小验收文档（手动回归步骤与故障排查指引）。
- Done Criteria:
  - 下载完成后可触发系统安装器。
  - 链路关键节点均可在日志/事件中看到。
- Risks:
  - 不同 ROM 对安装权限和未知来源策略差异较大。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## Blocked Candidates (Carry Forward)

### T-BLK-001
- Task ID: `T-BLK-001`
- Title: 系统首页音乐卡片第三方入口能力确认
- Module ID: `M-S4-UX-005`
- Goal: 明确系统首页音乐卡片是否支持第三方播放器入口。
- Dependencies: 车机系统能力确认
- Size: M

### B-LRC-001
- Task ID: `B-LRC-001`
- Title: 歌词失败回退策略口径确认
- Module ID: `M-S4-UX-005`
- Goal: 明确远程歌词失败时提示/重试/缓存回退规则。
- Dependencies: 产品口径确认
- Size: S

## Done (History Snapshot)
- [x] `T-S4-ARCH-017H` 前台停播热修（前台禁用 Service 焦点干预 + 构建号徽标优化）
- [x] `T-S4-ARCH-017A` 移除命令持久化重试（失败即失败）
- [x] `T-S3-UI-013` Home 播放模块重排 + 可拖动进度 + 解码失败自动切歌
- [x] `T-S3-RB-008` 回滚错误实现并恢复基线
- [x] `T-S3-NET-009` CF 优选 IPv4 解析链路接线（Emby 域名保持不变）
- [x] `T-S3-DL-010` 30s 下载窗口状态机
- [x] `T-S3-LOG-011` 下载调度与优选 IP 诊断日志补齐
