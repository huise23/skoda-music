# TASK_BREAKDOWN

Last Updated: 2026-04-16 16:28

## T-S2-IA-001
- Task ID: `T-S2-IA-001`
- Title: IA v2 文档清洗与单一口径固化
- Goal: 清理旧冲突口径，形成唯一 IA 基线。
- Why: 旧文档通过 override 叠加，执行阶段易误读。
- Dependencies: 无
- Inputs:
  - `docs/UI_IA_LOW_FIDELITY.md`
  - `.ai/context/DECISIONS.md`
- Expected Outputs: IA 文档与决策文档一致，不再存在冲突叙述。
- Done Criteria:
  - 导航顺序、点击播放、推荐刷新、设置保存规则仅保留一套说法。
  - 不再保留“LrcApi 失败不阻断保存”的旧描述。
- Risks: 文档领先代码，短期会有实现差距。
- Size: S
- Minimal Loop: Yes

## T-S2-UI-006
- Task ID: `T-S2-UI-006`
- Title: 前台升级为 4 导航壳（Home -> Queue -> Library -> Settings）
- Goal: 从单页滚动布局升级为 IA v2 对应的 4 导航壳。
- Why: 后续交互规则需要页面边界与状态边界。
- Dependencies: `T-S2-IA-001`
- Inputs:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- Expected Outputs: 可切换四页入口，顺序固定。
- Done Criteria:
  - 导航顺序与 IA 一致。
  - 页面切换不破坏现有播放链路。
- Risks: 结构改造幅度大，易引入控件绑定回归。
- Size: M
- Minimal Loop: Yes

## T-S2-SET-008
- Task ID: `T-S2-SET-008`
- Title: 统一服务配置区（Emby + LrcApi）与测试门禁自动保存
- Goal: 将 Emby/LrcApi 合并在同一配置区，并统一保存规则。
- Why: 用户已确认“LrcApi 放到 Emby 配置一起”，且两者规则一致。
- Dependencies: `T-S2-UI-006`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
- Expected Outputs: 合并配置区 + 两服务各自测试通过自动保存、失败阻断。
- Done Criteria:
  - Emby/LrcApi 在同一配置区展示。
  - 两者均满足“测试通过即自动保存，失败阻断对应保存”。
  - 各测各存，互不等待。
- Risks: 当前 LrcApi 测试链路缺失，需要新增最小测试实现。
- Size: M
- Minimal Loop: Yes

## T-S2-UI-007
- Task ID: `T-S2-UI-007`
- Title: Queue/Library 列表单击即播放（禁用长按主路径）
- Goal: 收敛为驾驶场景低误触的单击主交互。
- Why: 用户明确要求默认点击播放，不走长按。
- Dependencies: `T-S2-UI-006`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - 列表渲染组件（Queue/Library）
- Expected Outputs: 两页列表统一单击即播放。
- Done Criteria:
  - Queue 单击行可立即切播并同步高亮。
  - Library 单击行可立即切歌。
- Risks: 若列表组件抽象不足，可能出现重复逻辑。
- Size: M
- Minimal Loop: Yes

## T-S2-UI-008
- Task ID: `T-S2-UI-008`
- Title: Queue 推荐歌曲（默认 20）并替换未播放段
- Goal: 在不打断当前播放下刷新后续待播。
- Why: 用户要求“刷新队列，默认20条，保留当前播放”。
- Dependencies: `T-S2-UI-007`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
- Expected Outputs: Queue 头部新增推荐按钮与替换逻辑。
- Done Criteria:
  - 点击后保留当前播放项。
  - 当前播放后的待播队列被推荐结果（20条）替换。
  - 请求失败时保留原队列并提示。
- Risks: Native 队列 API 可能缺少“仅替换未播放段”的原子操作。
- Size: M
- Minimal Loop: Yes

## T-S2-003
- Task ID: `T-S2-003`
- Title: 日志面板交互增强（复制/清理）
- Goal: 提升现场排障回传效率。
- Why: 当前可看不可快速回传。
- Dependencies: `T-HF-LOG-001`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/layout/dialog_runtime_logs.xml`
  - `app/src/main/res/values/strings.xml`
- Expected Outputs: 全屏日志支持复制与清理。
- Done Criteria:
  - 可一键复制当前日志文本。
  - 可清空日志并同步预览区与全屏区。
- Risks: API17 剪贴板行为需实机确认。
- Size: S
- Minimal Loop: Yes

## T-S2-004
- Task ID: `T-S2-004`
- Title: API17 全量回归执行与证据回填
- Goal: 按清单完成实机验证并形成结构化证据。
- Why: 当前最大缺口是“可运行但缺完整实机证明”。
- Dependencies: `T-S2-UI-006`, `T-S2-SET-008`, `T-S2-UI-007`, `T-S2-UI-008`, `T-S2-003`
- Inputs:
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
  - CI 产物 APK
  - 实机日志与截图
- Expected Outputs: 一份完整回归报告（PASS/FAIL、失败条目、关键日志）。
- Done Criteria:
  - 清单 A-F 项完成并有结论。
  - 至少包含一次失败路径验证与恢复验证。
- Risks: 依赖人工实机时段，节奏不可控。
- Size: M
- Minimal Loop: No

## T-S2-005
- Task ID: `T-S2-005`
- Title: 回归结果固化与交接更新
- Goal: 将 S2 回归结论写回上下文与 runbook。
- Why: 防止验证结果丢失。
- Dependencies: `T-S2-004`
- Inputs:
  - `.ai/context/*`
  - `docs/CI_SIGNING_RELEASE_RUNBOOK.md`
- Expected Outputs: 状态文件与交接信息更新、必要文档回写。
- Done Criteria:
  - `CURRENT_STATUS/NEXT_STEPS/HANDOFF/TASK_QUEUE` 与事实一致。
  - runbook 引用最新回归结论。
- Risks: 多文件同步时容易遗漏。
- Size: S
- Minimal Loop: Yes

## Blocked Candidates (Pending Confirmation)

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
