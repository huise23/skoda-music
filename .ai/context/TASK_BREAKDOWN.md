# TASK_BREAKDOWN

Last Updated: 2026-04-15 18:25:30

## T-S1-001
- Task ID: `T-S1-001`
- Title: Android 前台交互壳改造（替换封面页）
- Goal: 将静态封面页改为可交互最小页面。
- Why: 当前页面不可操作，无法承载后续播放功能验证。
- Dependencies: 无
- Inputs: `app/src/main/res/layout/activity_main.xml`, `MainActivity.kt`
- Expected Outputs: 新布局（状态区+操作区）与按钮事件占位逻辑。
- Done Criteria:
  - 页面包含播放状态、当前曲目、播放/下一曲按钮。
  - 启动不崩溃，按钮点击有可见反馈。
- Risks: API 17 兼容控件选择受限。
- Size: M
- Minimal Loop: Yes

## T-S1-002
- Task ID: `T-S1-002`
- Title: Android UI 状态模型（最小 ViewState）
- Goal: 建立 Activity 内可维护的最小状态模型。
- Why: 避免在页面逻辑中散落临时字符串和硬编码。
- Dependencies: `T-S1-001`
- Inputs: `MainActivity.kt`
- Expected Outputs: 状态数据结构 + 单点渲染函数。
- Done Criteria:
  - 状态更新只通过单一入口。
  - UI 文本/按钮启用状态可由模型驱动。
- Risks: 若后续接桥接层，模型字段可能需要扩展。
- Size: S
- Minimal Loop: Yes

## T-S1-003
- Task ID: `T-S1-003`
- Title: 播放动作最小闭环（本地 mock）
- Goal: 完成“播放/下一曲”在 Android 前台的最小动作闭环。
- Why: 先验证前台交互可行，再接入真实后端链路。
- Dependencies: `T-S1-002`
- Inputs: `MainActivity.kt`
- Expected Outputs: 本地队列切换/状态刷新逻辑。
- Done Criteria:
  - 点击“下一曲”可切换曲目显示。
  - 点击“播放/暂停”可切换状态文本。
- Risks: mock 逻辑与真实桥接接口命名可能不一致。
- Size: S
- Minimal Loop: Yes

## T-S1-004
- Task ID: `T-S1-004`
- Title: Android-C++ 桥接最小接线
- Goal: 将前台动作映射到现有骨架能力（优先队列与媒体键同步状态）。
- Why: 从 UI mock 进入真实模块，减少双轨逻辑。
- Dependencies: `T-S1-003`
- Inputs: `src/app/qml_frontend_bridge.*`, Android 入口代码
- Expected Outputs: 可调用的桥接接口与状态回读通道（最小实现）。
- Done Criteria:
  - UI 可触发至少一个真实模块动作。
  - UI 可展示至少一个来自真实模块的状态字段。
- Risks: Android 与 C++ 集成方式当前未完全定型。
- Size: L
- Minimal Loop: No

## T-S1-005
- Task ID: `T-S1-005`
- Title: Emby 连接状态可视化
- Goal: 在前台展示 Emby 就绪/未就绪状态和提示。
- Why: 实机排障和可用性验证需要明确状态反馈。
- Dependencies: `T-S1-002`
- Inputs: 配置运行时状态、UI 状态模型
- Expected Outputs: Emby 状态区与提示文案。
- Done Criteria:
  - 未配置/配置失败/可用 三态可区分。
  - 提示文案与当前决策一致。
- Risks: 真实连通性检测策略需与后端接口统一。
- Size: M
- Minimal Loop: Yes

## T-S1-006
- Task ID: `T-S1-006`
- Title: API 17 实机交互回归脚本化清单
- Goal: 固化本阶段实机验证步骤和结果记录格式。
- Why: 避免每次验证口径漂移。
- Dependencies: `T-S1-001`, `T-S1-003`
- Inputs: 现有 runbook 与 UI 功能点
- Expected Outputs: 验证清单文档更新 + 结果模板。
- Done Criteria:
  - 覆盖安装、启动、基础交互、前后台切换。
  - 可直接用于下一轮实机回传。
- Risks: 实机环境不可控导致结论延迟。
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
