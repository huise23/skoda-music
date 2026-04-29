# NEXT_STEPS

Last Updated: 2026-04-29

## Current Priority Module
- `M-S4-CORE-001` 核心命令与状态链路收口
- 并行优先模块: `M-S4-CONTROL-002` 后台控制面一致性（浮窗交互增强已落地待车机验收）
- 并行优先模块: `M-S4-OBS-006` PostHog 关键事件观测链路
- 新增并行模块: `M-S4-UPD-007` 更新检测与镜像加速下载

## Recommended Execution Mode
- 模块推进执行（`ai-module-execution`）用于 `M-S4-CORE-001`
- 模块推进执行（`ai-module-execution`）用于 `M-S4-OBS-006`
- 模块推进执行（`ai-module-execution`）用于 `M-S4-UPD-007`
- 模块推进执行（`ai-module-execution`）用于 `M-S4-VALID-004`（进入车机窗口后执行 `REG-022/VAL-033`）

## Immediate Start
- [ ] 首选任务: `T-S4-CORE-026C-HF-20260429`（车机验证：浮窗歌名点击回应用、拖动后位置持久化、右上角关闭行为）
- [ ] 首选任务: `T-S4-UPD-044`（执行更新链路 CI/实机验收：检查->下载->安装触发->PostHog 事件）
- [ ] 首选任务: `T-S4-CORE-026A` 车机验收（验证 `eb10b46`：自动下一曲 + Home 后音频连续性 + 焦点冲突是否消失）
- [ ] 并行任务: `T-S4-CORE-026B`（按 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 回填 notification/overlay/media_button/audio_focus 四来源结果）
- [ ] 并行任务: `T-S4-OBS-036`（检查 `SkodaPostHog` 的 `capture ok` 是否稳定出现，并补齐失败样本）
- [ ] 并行任务: `T-S4-REG-022` 执行准备（已具备 S4 清单模板，等待车机窗口）
- [ ] 跟进任务: `T-S4-CORE-026C`、`T-S4-RESUME-020B`

## Why This Order
- `T-S4-UPD-040` 是更新链路单点前置，不先确定版本规则会导致后续检测/下载全部返工。
- `T-S4-UPD-041/042` 可在不阻塞播放主链路的前提下并行推进，产出可感知能力。
- `T-S4-UPD-043` 需依赖版本源确定后再接入镜像回退，避免下载错误资产。
- `T-S4-UPD-040/041/042/043` 已完成代码落地，当前应优先执行 `T-S4-UPD-044` 做真实链路验收与缺陷收敛。
- `T-S4-CORE-026A` 是 `026C/020B/REG-022` 的共同前置，不先完成会造成后续全部阻塞。
- `T-S4-CORE-026B` 已完成观测能力与矩阵模板准备，当前投入设备执行可直接产出可复盘证据。
- `T-S4-OBS-034/039` 已完成；当前瓶颈转为 `T-S4-OBS-036/037` 的补齐与实机压测。
- 先冻结“禁报清单”可防止高频低价值事件污染数据并抬高网络开销。
- `T-S4-VAL-032` 已完成，`T-S4-REG-022` 可直接复用新清单执行并减少现场返工。
- UI/音效项虽重要，但当前不影响 S4 主验收闭环，应保持 Deferred。

## Main Blockers
- 车机可用测试窗口不可控（阻塞 `T-S4-REG-022`）。
- 恢复链路二阶段尚未形成服务侧闭环（阻塞最终验收）。
- `T-S4-OBS-036/037` 尚未完成实机压测（阻塞 `T-S4-OBS-038` 在线验收）。
- 当前环境无 `adb` 与 `gradle/gradlew`，本地无法直接完成设备日志抓取与编译回归。
- GitHub 镜像域名可用性存在波动，需要在 `T-S4-UPD-044` 中做失败样本留证。
- 低版本车机（Android 4.2.2）对 GitHub TLS 证书链兼容性差，更新检测可能持续失败；需在 `T-S4-UPD-044` 验证并评估元数据中转方案。

## Need Confirmation
- PostHog 事件保留策略与环境隔离口径（当前内置为 `prod`，是否需额外 `dev` 项目）。
- 敏感字段边界（是否允许上报错误 message 摘要，默认禁止凭据与完整响应体）。
- 更新检测周期与网络策略（默认 24h、是否仅 Wi-Fi）。
- GitHub 镜像列表与优先级（当前内置 `ghfast.top -> mirror.ghproxy.com -> ghproxy.net -> 官方`，是否允许设置页自定义）。
- 长标题滚动策略口径（Marquee/Fade/速度）确认。
- 主屏删除入口的交互与防误触细节确认。
- 音效优化的目标标准（体感/指标）确认。

## Explicitly Deferred
- `T-S4-UI-023` 长标题滚动异常修复
- `T-S4-UI-024` 删除入口迁移到主屏
- `T-S4-AUDIO-025` 均衡器/音效优化
