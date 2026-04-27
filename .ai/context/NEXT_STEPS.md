# NEXT_STEPS

Last Updated: 2026-04-27

## Current Priority Module
- `M-S4-CORE-001` 核心命令与状态链路收口
- 并行优先模块: `M-S4-OBS-006` PostHog 关键事件观测链路

## Recommended Execution Mode
- 模块推进执行（`ai-module-execution`）用于 `M-S4-CORE-001`
- 模块推进执行（`ai-module-execution`）用于 `M-S4-OBS-006`
- 微任务执行（`ai-execution`）并行处理 `T-S4-VAL-032`

## Immediate Start
- [ ] 首选任务: `T-S4-CORE-026A` 车机验收（验证 `eb10b46`：自动下一曲 + Home 后音频连续性 + 焦点冲突是否消失）
- [ ] 并行任务: `T-S4-CORE-026B`（按 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 回填 notification/overlay/media_button/audio_focus 四来源结果）
- [ ] 并行任务: `T-S4-OBS-036`（检查 `SkodaPostHog` 的 `capture ok` 是否稳定出现，并补齐失败样本）
- [ ] 并行任务: `T-S4-VAL-032`
- [ ] 跟进任务: `T-S4-CORE-026C`、`T-S4-RESUME-020B`

## Why This Order
- `T-S4-CORE-026A` 是 `026C/020B/REG-022` 的共同前置，不先完成会造成后续全部阻塞。
- `T-S4-CORE-026B` 已完成观测能力与矩阵模板准备，当前投入设备执行可直接产出可复盘证据。
- `T-S4-OBS-034/039` 已完成；当前瓶颈转为 `T-S4-OBS-036/037` 的补齐与实机压测。
- 先冻结“禁报清单”可防止高频低价值事件污染数据并抬高网络开销。
- `T-S4-VAL-032` 不依赖车机窗口，先完成可减少 `T-S4-REG-022` 现场返工。
- UI/音效项虽重要，但当前不影响 S4 主验收闭环，应保持 Deferred。

## Main Blockers
- 车机可用测试窗口不可控（阻塞 `T-S4-REG-022`）。
- 恢复链路二阶段尚未形成服务侧闭环（阻塞最终验收）。
- `T-S4-OBS-036/037` 尚未完成实机压测（阻塞 `T-S4-OBS-038` 在线验收）。
- 当前环境无 `adb` 与 `gradle/gradlew`，本地无法直接完成设备日志抓取与编译回归。

## Need Confirmation
- PostHog 事件保留策略与环境隔离口径（当前内置为 `prod`，是否需额外 `dev` 项目）。
- 敏感字段边界（是否允许上报错误 message 摘要，默认禁止凭据与完整响应体）。
- 长标题滚动策略口径（Marquee/Fade/速度）确认。
- 主屏删除入口的交互与防误触细节确认。
- 音效优化的目标标准（体感/指标）确认。

## Explicitly Deferred
- `T-S4-UI-023` 长标题滚动异常修复
- `T-S4-UI-024` 删除入口迁移到主屏
- `T-S4-AUDIO-025` 均衡器/音效优化
