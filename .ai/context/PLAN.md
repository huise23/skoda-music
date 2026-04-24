# PLAN

Last Updated: 2026-04-23 09:25

## Current Stage
- Stage Name: S3 收尾 - API17 回归证据闭环（覆盖 2026-04-22 提交簇）
- Scope Source: `.ai/context/SCOPE.md`（2026-04-23）

## Scope Validation

### In Scope
- 将当前阶段目标收敛为“可验证闭环”：
  - 回归用例覆盖 `476ac0e -> 1fa5137` 的 UI/歌词关键变更。
  - API17 实机验证并沉淀可复盘证据。
  - 将实机结果回写到 `.ai/context`，形成下一阶段输入。
- 维持既有技术口径不变：`minSdk=17`、Emby-only、IPv4-only、download-only。

### Out of Scope
- 新协议/新后端接入（Jellyfin/Subsonic 等）。
- 绕过 `minSdk=17` 的依赖升级策略。
- 完整离线媒体库与大规模架构重构。
- 未确认需求直接进入开发（系统首页卡片入口、歌词失败策略）。

### Success Criteria
- 规划层面存在可执行 Ready（非空），且任务均在当前 scope 内。
- API17 回归任务具备明确输入、执行清单、证据模板、完成标准。
- `.ai/context` 中阶段描述、任务拆分与队列状态一致。

## Workstreams

### W1 回归准备（文档与入口）
- 目标: 把“实机要测什么、怎么回传证据”固定下来。
- 输出: 更新版 API17 回归清单 + 设备报告模板。

### W2 API17 实机执行
- 目标: 在目标车机执行 S3 收尾回归并收集证据。
- 输出: PASS/FAIL 条目、失败复现步骤、关键日志/截图/视频。

### W3 证据复盘与上下文回写
- 目标: 将 W2 结果结构化回填到 `.ai/context`，刷新队列状态。
- 输出: `CURRENT_STATUS/HANDOFF/NEXT_STEPS` 的一致更新。

### W4 阻塞项管理
- 目标: 管理未确认口径，防止其混入 Ready。
- 输出: 保持 `T-BLK-001/B-LRC-001` 在 Blocked 并持续跟踪。

## Dependency Graph
- `W1 -> W2 -> W3`
- `W4` 与主线并行，但不阻断 `W1`。
- `W2` 依赖人工设备与网络环境。

## Risks & Assumptions
- 风险: 无本地 Gradle 可执行入口，编译型校验无法在当前环境完成。
- 风险: 车机网络波动导致一次性回归结论不稳定。
- 假设: 目标设备可执行 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 中条目。
- 假设: 可回传最小证据（日志片段 + 至少 1 份截图/视频说明）。

## Recommended Order
1. `T-S3-VAL-014`：升级 API17 回归清单，纳入 S3 新行为验证。
2. `T-S3-VAL-015`：固化设备报告模板与证据字段。
3. `T-S3-VAL-012`：执行 API17 实机专项回归并收集证据（人工设备）。
4. `T-S3-VAL-016`：回填证据并更新 context 与队列。
