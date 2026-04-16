# PLAN

Last Updated: 2026-04-16 16:28

## Current Stage
- Stage Name: S2 IA v2 落地与交互闭环
- Scope Source: `SCOPE.md` 当前缺失，按 `PROJECT_BRIEF + CURRENT_STATUS + DECISIONS + 最新用户指令` 推导。

## Scope Validation

### In Scope (S2)
- 落地左侧 4 导航壳，顺序固定为 `Home -> Queue -> Library -> Settings`。
- Queue/Library 列表改为“单击即播放”（禁用长按主路径）。
- Queue 增加 `推荐歌曲`，默认 20 条，保留当前播放，仅替换未播放段。
- Settings 将 Emby 与 LrcApi 合并到同一配置区。
- Emby/LrcApi 均执行“测试通过自动保存，测试失败阻断对应保存”。
- 日志面板继续增强（复制/清理），并保持 API17 兼容。
- 产出可用于 API17 实机回归的安装包与验证口径。

### Out of Scope (S2)
- 多协议支持（Jellyfin/Subsonic）。
- 完整离线下载体系与本地媒体库重构。
- 后台媒体会话体系化改造（通知栏/锁屏全量能力）。

### Success Criteria
- UI 具备可见 4 导航壳，顺序与 IA 一致。
- Queue/Library 单击播放、Queue 推荐替换逻辑符合规则。
- Emby/LrcApi 同区配置可用，且“测过即存、失败阻断”生效。
- 日志复制/清理可用，不破坏播放主链路。
- `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 完成至少一轮结构化回传。

## Reality Check
- 当前 Android 前台仍为单页滚动布局（`MainActivity + activity_main.xml`），尚未形成 4 导航壳。
- Emby 测试与播放链路已可用；LrcApi 前台测试链路尚未落地。
- 构建入口稳定，API17 仍为兼容基线。

## Workstreams

### W1 IA Shell
- 目标: 把 IA v2 结构直接落到前台壳（导航顺序、页面入口、状态同步位）。
- 输出: 可切换的 Home/Queue/Library/Settings 前台框架。

### W2 Playback Interaction
- 目标: 收敛 Queue/Library 点击播放与 Queue 推荐替换未播放逻辑。
- 输出: 单击即播 + 推荐 20 条替换未播放段。

### W3 Unified Service Settings
- 目标: Emby 与 LrcApi 同区配置，并统一测试门禁自动保存策略。
- 输出: 合并配置区 + 两服务独立测试/保存状态机。

### W4 Log UX + Validation
- 目标: 完成日志复制/清理并形成 API17 回归闭环证据。
- 输出: 可回传日志能力 + 回归证据。

## Dependencies
- W1 是 W2/W3 的前置壳层。
- W2 与 W3 可并行，但共享 `MainActivity/activity_main.xml`，需串行合并避免冲突。
- W4 与 W2/W3 可并行；人工实机回归依赖 W1-W3 输出包。

## Risks & Assumptions
- 风险: 单页转 4 导航壳过程中可能引入状态同步回归。
- 风险: API17 设备性能/网络抖动会放大 UI 与播放边界问题。
- 假设: 当前优先级是“先把 IA 与交互规则做对，再做细节美化”。

## Recommended Order
1. `T-S2-UI-006`（4 导航壳）
2. `T-S2-SET-008`（统一服务配置区 + 测试门禁自动保存）
3. `T-S2-UI-007`（列表单击即播放）
4. `T-S2-UI-008`（Queue 推荐替换未播放段）
5. `T-S2-003`（日志复制/清理）
6. `T-S2-004`（API17 人工实机回归）
