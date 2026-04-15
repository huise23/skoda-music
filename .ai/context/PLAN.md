# PLAN

Last Updated: 2026-04-15 18:25:30

## Current Stage
- Stage Name: S1 从“封面壳”到“可交互最小前台（Android）”
- Scope Source: `.ai/context/SCOPE.md` 缺失，当前按 `PROJECT_BRIEF + CURRENT_STATUS + 用户最新指令` 推导。

## In Scope (S1)
- Android 前台从静态封面页升级为可交互页面。
- 打通 Android 页面与现有 C++ 骨架的状态/动作链路（先最小闭环）。
- 保持 API 17（Android 4.2.2）可打包、可安装、可启动。
- 为实机验证提供可观察的交互行为（状态显示、按钮动作、错误提示）。

## Out of Scope (S1)
- 多协议支持（Jellyfin/Subsonic）。
- 完整离线下载与本地库管理。
- 大规模视觉重构与复杂动画。

## Success Criteria
- 页面不再只显示“installed successfully”，而是可操作界面。
- 至少具备：当前曲目信息、播放/下一曲动作、基础连接状态提示。
- CI 打包持续通过，`minSdk=17` 校验通过。
- 实机可安装并可执行最小交互路径。

## Workstreams

### W1 Android Frontend Shell
- 目标: 将 `activity_main.xml + MainActivity.kt` 从静态页改为交互壳。
- 输出: 页面结构、按钮事件、基础状态渲染。

### W2 Bridge Integration
- 目标: 复用现有 `QmlFrontendBridge`/播放队列能力，提供可绑定到 Android UI 的最小接口。
- 输出: Android 可调用的状态快照与动作入口（先 mock/胶水层，再替换真实调用）。

### W3 Emby Minimal Capability
- 目标: 增加“连接状态可见 + 最小播放链路可验证”。
- 输出: Emby 就绪/未就绪状态展示；最小播放触发链路。

### W4 Validation & Packaging
- 目标: 保持 CI 与实机验证闭环。
- 输出: CI 通过记录、实机安装/启动/交互结果记录模板。

## Dependency Graph
- W1 可独立先行。
- W2 依赖 W1（需要 UI 载体）。
- W3 依赖 W2（需要动作入口与状态展示位）。
- W4 贯穿全程。

## Risks & Assumptions
- 风险: `SCOPE.md` 缺失，范围可能与维护者预期有偏差。
- 风险: Android 4.2.2 实机差异可能导致 UI/线程行为问题。
- 假设: 当前 C++ 骨架接口可通过薄胶水层接入 Android 前台。
- 假设: 当前阶段允许先用最小交互闭环，再逐步补齐完整功能。

## Recommended Execution Order
1. W1（Android 交互壳）
2. W2（桥接最小闭环）
3. W3（Emby 最小能力）
4. W4（实机回归与发布）
