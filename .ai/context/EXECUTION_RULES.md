# EXECUTION_RULES

## Read First
每次执行前必须先读并先总结理解：
1. `PROJECT_BRIEF.md`
2. `PLAN.md`
3. `CURRENT_STATUS.md`
4. `DECISIONS.md`
5. `HANDOFF.md`
6. `TASK_QUEUE.md`

## Execution Discipline
1. 每轮只做一个最小闭环任务，不一次性展开大功能。
2. 不得擅自推翻已确认决策；若要变更必须先记录原因并确认。
3. 发现冲突、信息缺失、依赖不明确时，先说明再行动。
4. 对未知内容使用“待确认”或“假设”标记，不编造需求。

## Update Discipline
1. 每轮结束必须更新：
   - `CURRENT_STATUS.md`
   - `NEXT_STEPS.md`
   - `HANDOFF.md`
   - `TASK_QUEUE.md`
2. 所有关键决策必须追加到 `DECISIONS.md`。
3. 新会话或上下文压缩后，优先从 HANDOFF 与核心文档恢复状态。

## Quality Baseline
1. 优先可验证和可回滚的小步提交。
2. 配置与接口先于复杂实现。
3. 在车机低资源约束下优先稳定、低卡顿。
