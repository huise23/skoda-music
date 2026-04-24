# TASK_BREAKDOWN

Last Updated: 2026-04-23 09:25

## Active Stage: S3 收尾（回归证据闭环）

## T-S3-VAL-014
- Task ID: `T-S3-VAL-014`
- Title: API17 回归清单升级（覆盖 2026-04-22 提交簇）
- Goal: 更新 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`，覆盖 S3 新行为（download-only、30s 窗口、SeekBar、解码失败自动切歌、歌词 loading）。
- Why: 当前清单停留在 `T-S1-006` 口径，无法完整验证 S3 收尾改动。
- Dependencies: 无
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - `CURRENT_STATUS.md` 中 S3 已完成功能列表
  - 最近提交簇 `476ac0e -> 1fa5137`
- Expected Outputs:
  - 一份可直接用于 API17 实机执行的升级清单
  - 明确每个条目的 PASS/FAIL 观察点
- Done Criteria:
  - 清单包含 S3 关键能力验证项
  - 清单可被非开发同事按步骤执行
- Risks:
  - 条目过细会降低现场执行效率
- Size: S
- Minimal Loop: Yes

## T-S3-VAL-015
- Task ID: `T-S3-VAL-015`
- Title: 设备报告模板固化（证据字段标准化）
- Goal: 形成统一 Device Report 模板，约束日志、截图、失败条目回传格式。
- Why: 实机结果若不标准化，后续复盘与回写会反复返工。
- Dependencies: `T-S3-VAL-014`
- Inputs:
  - 升级后的 API17 回归清单
  - 现有 `Result Template` 字段
- Expected Outputs:
  - 标准化报告模板（可复制填写）
  - 最小证据集合定义（日志片段、关键动作、失败复现步骤）
- Done Criteria:
  - 模板能覆盖 PASS/FAIL 与 Blocker 判断
  - 模板字段与 `.ai/context` 回写需求对齐
- Risks:
  - 字段过多导致回传不完整
- Size: S
- Minimal Loop: Yes

## T-S3-VAL-012
- Task ID: `T-S3-VAL-012`
- Title: API17 实机专项回归执行（S3 收尾）
- Goal: 在目标 API17 设备执行全量回归并收集结构化证据。
- Why: 当前阶段完成标准是“真实设备可验证”，不是静态推断。
- Dependencies: `T-S3-VAL-015`
- Inputs:
  - 升级后的回归清单
  - 设备报告模板
  - 目标安装包（对应 `HEAD=1fa5137` 或后续指定构建）
- Expected Outputs:
  - 分组 PASS/FAIL 结果
  - 失败条目复现步骤与证据
  - 是否阻断发布的结论
- Done Criteria:
  - 至少 1 台 API17 设备完成完整回归
  - 覆盖首播、连续切歌、弱网、临近结束预下载、前后台切换
- Risks:
  - 设备/网络条件不可控导致结果波动
- Size: M
- Minimal Loop: No

## T-S3-VAL-016
- Task ID: `T-S3-VAL-016`
- Title: 回归结果复盘与 Context 回写
- Goal: 将 `T-S3-VAL-012` 证据回填到 `.ai/context` 并更新队列状态。
- Why: 不回写就无法形成下一轮可持续执行输入。
- Dependencies: `T-S3-VAL-012`
- Inputs:
  - Device Report
  - 日志/截图/失败条目
- Expected Outputs:
  - 更新 `CURRENT_STATUS.md`（结果与阻塞）
  - 更新 `HANDOFF.md`（下一轮入口）
  - 更新 `NEXT_STEPS.md` 与 `TASK_QUEUE.md`
- Done Criteria:
  - `.ai/context` 与实机结果一致
  - 下一轮 Ready 明确且可执行
- Risks:
  - 证据不完整导致结论不稳
- Size: S
- Minimal Loop: Yes

## Blocked Candidates (Carry Forward)

### T-BLK-001
- Task ID: `T-BLK-001`
- Title: 系统首页音乐卡片第三方入口接入
- Goal: 验证并接入系统首页卡片唤起能力。
- Why: 属于车机场景关键入口。
- Dependencies: 设备系统能力确认
- Inputs: 车机系统文档/实测
- Expected Outputs: 能力结论与接入实现（若支持）
- Done Criteria: 能明确“支持/不支持”并有证据。
- Risks: 完全受制于系统封闭能力。
- Size: M
- Minimal Loop: No

### B-LRC-001
- Task ID: `B-LRC-001`
- Title: 歌词失败回退策略口径确认
- Goal: 明确远程歌词失败时的提示文案、重试策略与缓存回退规则。
- Why: 当前歌词策略未定，容易在 UI 和行为上反复。
- Dependencies: 产品/维护者口径确认
- Inputs: `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md` 与产品决策
- Expected Outputs: 可执行的策略结论（含提示文案）
- Done Criteria: 形成明确“失败即提示/重试次数/是否回退缓存”结论并写入决策。
- Risks: 口径长期未确认会阻塞歌词相关开发。
- Size: S
- Minimal Loop: No

## Done (History Snapshot)
- [x] `T-S3-RB-008` 回滚错误实现并恢复基线
- [x] `T-S3-NET-009` CF 优选 IPv4 解析链路接线（Emby 域名保持不变）
- [x] `T-S3-DL-010` 30s 下载窗口调度状态机
- [x] `T-S3-LOG-011` 下载调度与优选 IP 诊断日志补齐
- [x] `T-S3-UI-013` Home 播放模块重排 + 可拖动进度 + 解码失败自动切歌
