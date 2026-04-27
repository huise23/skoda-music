# PLAN

Last Updated: 2026-04-27

## Current Stage
- Stage Name: S4 车机后台控制落地（方案1 / Legacy 稳态）
- Scope Source: `.ai/context/SCOPE.md`（2026-04-26）
- Stage Goal: 在 API17 红线下完成“后台可控 + 浮窗可控 + 恢复可控”的可实机验收闭环。

## Scope Validation

### In Scope
- `ForegroundService` 持续承载后台播放状态与生命周期。
- `ACTION_MEDIA_BUTTON + RemoteControlClient` 后台方向盘按键链路。
- 浮窗权限、显示策略、手动关闭后的再显示策略。
- 通知控制条作为后台兜底入口。
- 熄火/休眠恢复自动续播与可诊断降级。
- 关键节点结构化事件上报（PostHog）用于诊断与回归证据聚合。
- 车机实机回归与证据回填。

### Out of Scope
- 新后端/新协议接入（仍为 Emby-only）。
- 切换 MediaSession 为本阶段主链路。
- 大规模 UI 视觉改版。
- 未确认项直接进入 Ready（系统首页卡片入口、歌词失败策略）。

### Scope Gaps (Need Confirmation)
- PostHog 接入口径待确认（Cloud / Self-host、Host、Project Key、环境隔离）。
- 事件保留与访问策略待确认（数据保留周期、AI 导出权限边界）。
- 长标题滚动修复策略细节（Marquee/Fade/速度）未定。
- 主屏删除入口具体交互与防误触细节未定。
- 均衡器/音效优化目标未量化（默认参数与评价标准待确认）。

## Major Work Blocks

### W1 核心链路收口
- 目标: 收敛 Service/Activity 的命令与状态职责，消除“前台/后台行为漂移”。
- 输出: 可稳定联调的核心控制链路。

### W2 后台控制面联调
- 目标: 通知、媒体键、浮窗三条后台入口行为一致。
- 输出: 各入口命令分发与反馈一致的结果矩阵。

### W3 恢复链路闭环
- 目标: 熄火/休眠恢复自动续播可执行、可降级、可诊断。
- 输出: 服务侧恢复状态机与失败降级规则。

### W4 PostHog 结构化事件链路
- 目标: 集成 PostHog 关键节点事件上报，形成可检索、可聚合、可供 AI 分析的事件流。
- 输出: API17 兼容的轻量埋点层、事件字典、上报策略与验证方案。

### W5 回归证据闭环
- 目标: 标准化 S4 回归清单并完成车机实机执行/回写。
- 输出: PASS/FAIL/Blocker 证据与 context 回填。

### W6 体验增强暂缓项
- 目标: 管理主屏删除入口、长标题滚动、音效优化等并行需求。
- 输出: Deferred/Blocked 管理，不污染当前 Ready。

## Module Order / Dependency Graph
- `M-S4-CORE-001 -> M-S4-CONTROL-002 -> M-S4-RESUME-003 -> M-S4-VALID-004`
- `M-S4-OBS-006` 与 `M-S4-CORE-001` 并行推进，并在 `M-S4-VALID-004` 前完成最小闭环。
- `M-S4-UX-005` 并行存在，但当前保持 Deferred。
- 车机实机执行依赖 `M-S4-CORE-001/002/003`，并建议带上 `M-S4-OBS-006` 的关键事件链路。

## Risks & Assumptions
- 风险: 本地缺少 `gradlew`，无法在当前环境完成编译安装验证。
- 风险: 车机测试窗口不连续，`T-S4-REG-022` 可能长时间阻塞。
- 风险: 前后台音频焦点策略仍可能受不同车机 ROM 差异影响。
- 风险: 引入第三方 SDK 存在 API17 兼容与包体膨胀风险。
- 风险: 高频埋点若无节流会造成噪音与网络负担。
- 假设: 允许前台服务常驻通知。
- 假设: 目标车机可授予悬浮窗权限。
- 假设: PostHog 采用“结构化事件”而非全量文本日志上报。

## Stage Milestones
- M1: 完成核心链路收口并形成可联调构建（代码里程碑）。
- M2: 完成通知/媒体键/浮窗后台一致性验证（代码+日志里程碑）。
- M3: 完成熄火/休眠恢复服务侧闭环（功能里程碑）。
- M4: 完成 PostHog 最小关键事件链路（schema + 上报 + 验证）并可查询。
- M5: 完成 API17 实机回归与证据回写（阶段验收里程碑）。

## Recommended Path
1. 优先推进 `M-S4-CORE-001`（模块执行），收口核心命令链路。
2. 并行推进 `M-S4-OBS-006`，先完成事件模型、API17 兼容上报层与埋点门禁。
3. 并行完成 `T-S4-VAL-032`（微任务），补齐 S4 回归清单与验收模板。
4. 进入 `M-S4-CONTROL-002` 与 `M-S4-RESUME-003`，形成可上车机版本。
5. 触发 `T-S4-REG-022` 实机回归，完成 `T-S4-VAL-033` 证据回填。
