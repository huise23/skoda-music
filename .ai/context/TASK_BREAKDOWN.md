# TASK_BREAKDOWN

Last Updated: 2026-04-29

## Active Stage
- S4 本地收口（车机验收前）
- 说明: 保持任务 ID/依赖不变，补充白话说明，便于非开发同事快速理解。

## T-S4-CORE-026A
- Task ID: `T-S4-CORE-026A`
- Title: 核心命令链路收口（Service/Activity 职责边界）
- Module ID: `M-S4-CORE-001`
- 这任务在做什么（白话）: 把“命令谁来执行、失败怎么反馈、状态谁来记”统一下来。
- Goal: 固化命令执行真源与状态上报边界，降低前后台行为不一致。
- Why: 不先收口，后续媒体键/浮窗/恢复会持续回归。
- Dependencies: 无
- Inputs:
  - `MainActivity.kt`
  - `PlaybackService.kt`
  - `PlaybackControlBus.kt`
  - `PlaybackStateStore.kt`
- Expected Outputs:
  - 命令结果真实可感知（无假成功）。
  - 状态快照可稳定驱动通知/浮窗。
- Done Criteria:
  - 前后台不再出现“收到命令但没执行”的分叉。
  - 日志可定位 source 与执行结果。
- Risks:
  - 与既有稳定补丁可能冲突，需实机裁决。
- Size: M
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-CORE-026B
- Task ID: `T-S4-CORE-026B`
- Title: 后台命令矩阵自测（notification/overlay/media_button/audio_focus）
- Module ID: `M-S4-CORE-001`
- 这任务在做什么（白话）: 把四类命令来源逐项测一遍，确认行为和日志都一致。
- Goal: 对多来源命令做统一回归，形成可复盘矩阵。
- Why: 单点验证不足以证明后台链路稳定。
- Dependencies: 无
- Inputs:
  - `docs/S4_BACKGROUND_COMMAND_MATRIX.md`
  - Service 命令执行结果日志
  - `source/handled/detail` 字段
- Expected Outputs:
  - 来源 x 命令 x 结果的矩阵。
  - 失败样本及复现步骤（若存在失败）。
- Done Criteria:
  - 四类来源都完成 `prev/play_pause/next` 验证。
- Risks:
  - 本地模拟与车机实机仍可能有差异。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-CORE-026C
- Task ID: `T-S4-CORE-026C`
- Title: 浮窗与通知策略闭环
- Module ID: `M-S4-CONTROL-002`
- 这任务在做什么（白话）: 验证浮窗显示规则是否按约定执行，并确认通知可兜底控制。
- Goal: 固化“播放/暂停显示 + 手动关闭后进应用再切出重显”策略。
- Why: 这是明确验收项，生命周期切换最容易出回归。
- Dependencies: `T-S4-CORE-026A`
- Inputs:
  - `OverlayController.kt`
  - `PlaybackService.kt`
  - `AndroidManifest.xml`
- Expected Outputs:
  - 浮窗显示状态机验证结果。
  - 通知与浮窗控制一致性结论。
- Done Criteria:
  - 手动关闭后仅在“进应用再切出”路径重显。
  - 浮窗不可用时通知可稳定兜底。
- Risks:
  - 部分 ROM 悬浮窗权限行为不稳定。
- Size: M
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-RESUME-020B
- Task ID: `T-S4-RESUME-020B`
- Title: 服务侧自动续播二阶段闭环
- Module ID: `M-S4-RESUME-003`
- 这任务在做什么（白话）: 车机休眠回来后，尽量自动续播并回到上次进度。
- Goal: 将恢复逻辑推进到“可车机验收”的服务侧闭环。
- Why: 自动续播是 S4 强需求。
- Dependencies: `T-S4-CORE-026A`
- Inputs:
  - `PlaybackResumeStore.kt`
  - `PlaybackService.kt`
  - 当前恢复策略（12h 窗口/同曲 seek）
- Expected Outputs:
  - 服务重建后的恢复触发与降级策略稳定。
  - 失败场景有可定位日志和状态。
- Done Criteria:
  - 成功场景可自动续播并恢复进度。
  - 失败场景不崩溃且降级符合预期。
- Risks:
  - 会话失效和网络波动影响复现稳定性。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-VAL-032
- Task ID: `T-S4-VAL-032`
- Title: 升级 API17 回归清单并补齐 Section 4 验收模板
- Module ID: `M-S4-VALID-004`
- 这任务在做什么（白话）: 把旧清单升级成 S4 可直接执行的验收清单。
- Goal: 将 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 升级到 S4 口径。
- Why: 旧清单覆盖不到后台控制/浮窗/恢复场景。
- Dependencies: 无
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - `.ai/context/SCOPE.md`
  - `.ai/context/CURRENT_STATUS.md`
- Expected Outputs:
  - S4 场景回归清单。
  - Section 4 风险控制与验收模板。
- Done Criteria:
  - 非开发同事可按步骤执行并输出 PASS/FAIL。
- Risks:
  - 条目过细会降低执行效率。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-REG-022
- Task ID: `T-S4-REG-022`
- Title: 车机实机回归执行（S4）
- Module ID: `M-S4-VALID-004`
- 这任务在做什么（白话）: 在真实 API17 车机上按清单完整跑一遍并留证据。
- Goal: 执行 S4 全量回归并产出 PASS/FAIL/Blocker。
- Why: S4 完成标准是实机可验证。
- Dependencies: `T-S4-CORE-026B`, `T-S4-CORE-026C`, `T-S4-RESUME-020B`, `T-S4-VAL-032`, `T-S4-OBS-036`, `T-S4-OBS-037`
- Inputs:
  - S4 回归清单
  - 目标构建包与构建号
  - 车机测试窗口
- Expected Outputs:
  - 分组 PASS/FAIL/Blocker 结果。
  - 失败复现步骤 + 日志/截图/视频。
- Done Criteria:
  - 覆盖后台按键、浮窗策略、通知兜底、自动续播。
  - 至少 1 台 API17 设备执行完成。
- Risks:
  - 车机窗口不可控。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-VAL-033
- Task ID: `T-S4-VAL-033`
- Title: 实机证据回写与阶段收口
- Module ID: `M-S4-VALID-004`
- 这任务在做什么（白话）: 把实机结果写回 context，明确下一步要修什么。
- Goal: 将 `T-S4-REG-022` 结果结构化回填。
- Why: 不回写就无法形成下一轮可执行入口。
- Dependencies: `T-S4-REG-022`
- Inputs:
  - Device Report
  - 失败条目与证据
- Expected Outputs:
  - 更新 `CURRENT_STATUS/HANDOFF/TASK_QUEUE/NEXT_STEPS`。
  - 阶段结论（继续修复 or 进入下一阶段）。
- Done Criteria:
  - 文档状态与实机结论一致。
- Risks:
  - 证据不完整会导致结论不稳。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-034
- Task ID: `T-S4-OBS-034`
- Title: PostHog 事件模型与字段规范落地
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 先定义“哪些事件要上报、字段怎么命名”，避免后面乱。
- Goal: 定义关键事件、公共属性、错误码与命名规范。
- Why: 不先定 schema，后续查询和分析会很乱。
- Dependencies: 无
- Inputs:
  - `docs/POSTHOG_INSTRUMENTATION_PLAN.md`
  - `docs/S4_BACKGROUND_COMMAND_MATRIX.md`
- Expected Outputs:
  - 事件字典（10~20 核心事件 + 公共属性）。
  - 禁报清单（高频噪音/敏感信息）。
- Done Criteria:
  - 命名稳定（snake_case）且覆盖 S4 主链路。
- Risks:
  - 定义过细会抬高埋点成本。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-035
- Task ID: `T-S4-OBS-035`
- Title: API17 兼容 PostHog 上报客户端（fail-open）
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 做一个轻量上报层，失败时不影响播放。
- Goal: 基于现有网络栈实现异步上报、节流与开关控制。
- Why: 重 SDK 在 API17 风险高，需先可控可兼容。
- Dependencies: `T-S4-OBS-034`
- Inputs:
  - `OkHttp 3.12.13`
  - 事件模型文档
- Expected Outputs:
  - 轻量上报组件（异步、超时、节流、开关）。
  - 有界队列与去重策略。
- Done Criteria:
  - 上报异常不阻断 UI/Service 主流程。
- Risks:
  - 参数未确认时只能做本地假联调。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-OBS-036
- Task ID: `T-S4-OBS-036`
- Title: 关键节点埋点接线（启动/播放/后台命令/错误）
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 把定义好的事件真正接到关键代码路径上。
- Goal: 让关键路径事件可查询、可定位失败 stage。
- Why: 只有上报组件还不够，必须有真实触发点。
- Dependencies: `T-S4-OBS-035`, `T-S4-CORE-026A`
- Inputs:
  - `MainActivity.kt`
  - `PlaybackService.kt`
  - `PlaybackStateStore.kt`
- Expected Outputs:
  - 启动、播放成功/失败、后台命令结果等事件可上报。
- Done Criteria:
  - 调试日志可见发送记录，失败原因可定位。
- Risks:
  - 点位过多会带来维护与性能噪音。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-OBS-037
- Task ID: `T-S4-OBS-037`
- Title: 上报门禁与隐私策略（PII Guard + Config）
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 规定哪些能上报，哪些必须过滤，防止敏感数据泄露。
- Goal: 落地敏感字段黑名单、脱敏与总开关策略。
- Why: 不设门禁会有隐私与合规风险。
- Dependencies: `T-S4-OBS-034`, `T-S4-OBS-035`
- Inputs:
  - 当前日志口径
  - 配置入口（Settings / BuildConfig）
- Expected Outputs:
  - 上报开关策略与脱敏文档。
- Done Criteria:
  - 默认不上传凭据与大体积原文。
  - 支持快速全局关闭上报。
- Risks:
  - 规则遗漏会引发风险。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-038
- Task ID: `T-S4-OBS-038`
- Title: PostHog 查询验证与 AI 导出模板
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 验证数据能查出来，并整理给 AI 分析用的导出格式。
- Goal: 固化查询与导出流程，形成可复用排障模板。
- Why: 不验证查询，埋点价值无法闭环。
- Dependencies: `T-S4-OBS-036`, `T-S4-OBS-037`
- Inputs:
  - PostHog 事件数据
  - 典型故障场景
- Expected Outputs:
  - 查询清单 + 导出模板 + 噪音审计。
- Done Criteria:
  - 至少可导出 1 条完整 session 事件流。
- Risks:
  - 无真实参数时无法在线完成。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-OBS-039
- Task ID: `T-S4-OBS-039`
- Title: PostHog 接入参数确认（Host/Project Key/环境口径）
- Module ID: `M-S4-OBS-006`
- 这任务在做什么（白话）: 确认连哪个项目、哪个环境，避免测试数据污染生产。
- Goal: 明确 endpoint、project key、环境隔离和保留策略。
- Why: 参数不清会阻塞真实联调。
- Dependencies: 无
- Inputs:
  - `docs/POSTHOG_CONFIG_CHECKLIST.md`
  - 环境选择（Cloud/Self-host）
- Expected Outputs:
  - 可执行参数清单（dev/prod）。
- Done Criteria:
  - `OBS-035/036/038` 能用真实参数联调。
- Risks:
  - 参数长期缺失会停留在本地假联调。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UPD-040
- Task ID: `T-S4-UPD-040`
- Title: 更新源与版本比较规则落地（GitHub Releases）
- Module ID: `M-S4-UPD-007`
- 这任务在做什么（白话）: 先把“怎么判断有新版本”这件事定准。
- Goal: 实现版本元数据读取、tag 解析与比较规则。
- Why: 规则不稳会误报更新或漏检。
- Dependencies: 无
- Inputs:
  - `.github/workflows/package-mvp.yml`
  - `docs/CI_SIGNING_RELEASE_RUNBOOK.md`
- Expected Outputs:
  - 元数据模型 + 比较策略 + 过滤策略。
- Done Criteria:
  - 稳定得出“是否有更新 + 下载链接”。
- Risks:
  - 资产命名不一致会匹配失败。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UPD-041
- Task ID: `T-S4-UPD-041`
- Title: 冷启动自动检测与节流策略
- Module ID: `M-S4-UPD-007`
- 这任务在做什么（白话）: 应用启动后自动查更新，但别每次都查。
- Goal: 接入自动检测并加节流/失败回退。
- Why: 自动检测要有，但不能影响启动体验。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - `MainActivity.kt`
  - 本地持久化策略
- Expected Outputs:
  - 自动检测触发点 + 24h 节流（可配置）。
- Done Criteria:
  - 命中节流时不重复请求；失败不影响播放。
- Risks:
  - 与启动优化链路冲突。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-UPD-042
- Task ID: `T-S4-UPD-042`
- Title: 设置页手动检查更新入口与结果展示
- Module ID: `M-S4-UPD-007`
- 这任务在做什么（白话）: 在设置页给一个“立即检查更新”按钮，结果看得懂。
- Goal: 增加手动检查入口与状态反馈。
- Why: 自动检测需要手动兜底。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - `activity_main.xml`
  - `MainActivity.kt`
  - `strings.xml`
- Expected Outputs:
  - 按钮与状态文案（检测中/已最新/发现更新/失败）。
- Done Criteria:
  - 可重复手动检测并得到清晰反馈。
- Risks:
  - 文案不清会误导用户。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-UPD-043
- Task ID: `T-S4-UPD-043`
- Title: GitHub 镜像加速下载与官方回退链路
- Module ID: `M-S4-UPD-007`
- 这任务在做什么（白话）: 下载先走镜像，失败再自动切官方，减少下载失败率。
- Goal: 实现“镜像优先 + 官方回退”的容错下载链路。
- Why: 车机网络复杂，单链路失败率高。
- Dependencies: `T-S4-UPD-040`
- Inputs:
  - release 资产链接
  - 镜像策略
  - 下载路径策略
- Expected Outputs:
  - 镜像优先级 + 自动回退 + 下载日志/事件。
- Done Criteria:
  - 镜像或官方至少一条链路可完成下载。
- Risks:
  - 镜像可用性波动。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-UPD-044
- Task ID: `T-S4-UPD-044`
- Title: 安装触发与更新链路观测闭环
- Module ID: `M-S4-UPD-007`
- 这任务在做什么（白话）: 下载好 APK 后，真正拉起系统安装器，并把关键节点打点补全。
- Goal: 形成更新链路可验证闭环。
- Why: 不触发安装就不算更新闭环完成。
- Dependencies: `T-S4-UPD-041`, `T-S4-UPD-042`, `T-S4-UPD-043`, `T-S4-OBS-036`
- Inputs:
  - 下载完成 APK
  - 安装触发配置（Intent/FileProvider）
  - PostHog 事件组件
- Expected Outputs:
  - API17 可用的安装触发逻辑。
  - `update_check_* / update_download_* / update_install_*` 事件。
- Done Criteria:
  - 下载后可拉起系统安装器，且关键节点可观测。
- Risks:
  - ROM 对安装权限策略差异较大。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-UI-023
- Task ID: `T-S4-UI-023`
- Title: 长标题滚动异常修复
- Module ID: `M-S4-UX-005`
- 这任务在做什么（白话）: 让歌名太长时也能看清，不抖动。
- Goal: 修复长标题可读性问题并形成可验证策略。
- Why: 用户有反馈，但口径未最终确认。
- Dependencies: 需求口径确认
- Size: S

## T-S4-UI-024
- Task ID: `T-S4-UI-024`
- Title: 删除入口迁移到主屏
- Module ID: `M-S4-UX-005`
- 这任务在做什么（白话）: 在主屏提供“马上删歌”入口，但避免误触。
- Goal: 提供安全删除入口（含防误触）。
- Why: 用户明确要求，交互细节待定。
- Dependencies: 交互方案确认
- Size: M

## T-S4-AUDIO-025
- Task ID: `T-S4-AUDIO-025`
- Title: 均衡器/音效优化
- Module ID: `M-S4-UX-005`
- 这任务在做什么（白话）: 找到 API17 上听感更好的默认参数。
- Goal: 形成可行听感优化基线。
- Why: 是明确诉求，但不阻塞 S4 主验收。
- Dependencies: `T-S4-REG-022`
- Size: M

## Blocked Candidates
- `T-BLK-001`: 系统首页音乐卡片第三方入口能力确认（依赖车机系统能力确认）。
- `B-LRC-001`: 歌词失败回退策略口径确认（依赖产品口径确认）。

## Done (History Snapshot)
- [x] `T-S4-ARCH-017H`
- [x] `T-S4-ARCH-017A`
- [x] `T-S3-UI-013`
- [x] `T-S3-RB-008`
- [x] `T-S3-NET-009`
- [x] `T-S3-DL-010`
- [x] `T-S3-LOG-011`
