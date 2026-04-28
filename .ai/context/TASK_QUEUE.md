# TASK_QUEUE

Last Updated: 2026-04-28

## Ready
- [ ] `T-S4-VAL-032` 升级 API17 回归清单并补齐 Section 4 验收模板（不依赖车机窗口，可立即执行）

## In Progress
- [ ] `T-S4-CORE-026A` 核心命令链路收口（已完成 `eb10b46` 热修：service focus-neutral + cache fallback 失败自动下一曲，待车机验收）
- [ ] `T-S4-CORE-026B` 后台命令矩阵自测（已落地 command trace 与矩阵模板，待设备执行并回填结果）
- [ ] `T-S4-OBS-035` API17 兼容 PostHog 上报客户端（已落地 fail-open 基线，待实机补充验证）
- [ ] `T-S4-OBS-036` 关键节点埋点接线（已补 `capture ok` 成功日志与 cache 回退失败事件，待在线联调）
- [ ] `T-S4-OBS-037` 上报门禁与隐私策略（已落地敏感字段黑名单 + 字符串截断 + 节流预算，待实机压测）
- [ ] `T-S4-UPD-041` 冷启动自动检测与节流策略（依赖 `T-S4-UPD-040`）
- [ ] `T-S4-UPD-042` 设置页手动检查更新入口与结果展示（依赖 `T-S4-UPD-040`）
- [ ] `T-S4-UPD-043` GitHub 镜像加速下载与官方回退链路（依赖 `T-S4-UPD-040`）
- [ ] `T-S4-UPD-044` 安装触发与更新链路观测闭环（代码已接线，待 CI/实机验证）

## Blocked
- [ ] `T-S4-CORE-026C` 浮窗与通知策略闭环（依赖 `T-S4-CORE-026A` 收口完成）
- [ ] `T-S4-RESUME-020B` 服务侧自动续播二阶段闭环（依赖 `T-S4-CORE-026A`）
- [ ] `T-S4-OBS-038` 查询验证与 AI 导出模板（依赖 `T-S4-OBS-036/037` 完成）
- [ ] `T-S4-REG-022` 车机实机回归执行（依赖 `026B/026C/020B/032/OBS-036/OBS-037` + 车机窗口）
- [ ] `T-S4-VAL-033` 实机证据回写与阶段收口（依赖 `T-S4-REG-022`）
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认（依赖系统能力确认）
- [ ] `B-LRC-001` 歌词失败回退策略口径确认（依赖产品口径确认）

## Done
- [x] `T-S4-UPD-040` 更新源与版本比较规则落地：新增 `AppUpdateManager`（GitHub Releases 解析 + draft/prerelease 过滤 + APK 资产选择 + 版本比较）
- [x] `T-S4-UPD-041` 冷启动自动检测与节流：`MainActivity` 启动后自动检测 + 24h/30min 冷却
- [x] `T-S4-UPD-042` 设置页手动检查入口与状态展示：新增按钮/状态文本/交互反馈
- [x] `T-S4-UPD-043` 镜像加速下载与官方回退：内置镜像候选（`ghfast.top/mirror.ghproxy.com/ghproxy.net`）+ 官方 URL 回退
- [x] `T-S4-CORE-026A-HF-20260427` 用户故障热修：自动下一曲失败 + Home 后无声卡住（提交 `eb10b46`）
- [x] `T-S4-OBS-036-HF-20260427` PostHog 上报成功可见性：新增 `capture ok event=...` 日志
- [x] `T-S4-OBS-039` PostHog 接入参数确认：已内置 US Cloud host + project key + project id 默认值
- [x] `T-S4-OBS-034` PostHog 事件模型与字段规范：新增 `POSTHOG_INSTRUMENTATION_PLAN` 与 `POSTHOG_EVENT_DICTIONARY`
- [x] `T-S4-OBS-035-PREP` API17 fail-open reporter 基线：新增 `PostHogTracker/PostHogConfigStore`（配置缺失 no-op）
- [x] `T-S4-OBS-037-PREP` 门禁基线：敏感字段过滤、字符串截断、事件预算、短窗口 coalesce
- [x] `T-S4-CORE-026B-PREP` 后台命令矩阵预备：新增 `dispatch result` 持久化与 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 执行模板
- [x] `T-S4-ARCH-017H` 前台停播热修（前台禁用 Service 焦点干预 + 构建号徽标优化）
- [x] `T-S4-ARCH-017G` 车机停播热修（忽略 transient audio focus loss 自动暂停）
- [x] `T-S4-ARCH-017F` 后台命令来源标记透传（notification/overlay/media_button/audio_focus）
- [x] `T-S4-ARCH-017E` 播放回归热修（前台 UI/硬件键恢复本地直执）
- [x] `T-S4-ARCH-017D` Service 状态上报优化（位置增量 + 播放心跳）
- [x] `T-S4-ARCH-017C` 命令上下文透传（`source/allowToast`）
- [x] `T-S4-ARCH-017A` 移除命令持久化重试（失败即失败）
- [x] `T-S3-UI-013` Home 播放模块重排 + SeekBar + 解码失败自动切歌
- [x] `T-S3-RB-008` 回滚错误实现并恢复基线
- [x] `T-S3-NET-009` CF 优选 IPv4 解析链路接线
- [x] `T-S3-DL-010` 30s 下载窗口状态机
- [x] `T-S3-LOG-011` 下载调度与优选 IP 诊断日志补齐

## Deferred
- [ ] `T-S4-UI-023` 长标题滚动异常修复（当前口径未定）
- [ ] `T-S4-UI-024` 删除入口迁移到主屏（交互细节待确认）
- [ ] `T-S4-AUDIO-025` 均衡器/音效优化（不阻塞 S4 主验收）

## Queue Notes
- 当前主线是 S4 验收闭环，不将未确认需求放入 Ready。
- PostHog 仅作为结构化事件链路，不替代本地全量原始日志。
- 更新能力新增主线：优先“检测准确 + fail-open + 镜像回退”，不引入静默安装。
- 更新链路现状：代码已接通（含安装触发），待 CI 编译与 API17/车机实机验证后再从 In Progress 移出。
- 技术红线保持：`minSdk=17`、Emby-only、IPv4-only、业务 Host 不替换。
- 推荐执行模式：核心模块用模块推进，文档与清单类任务用微任务并行推进。
- 最新待验构建：`master@eb10b46`（已推送）。
