# CURRENT_STATUS

Last Updated: 2026-04-15 21:19:46

## Stage
- 阶段判断: 已完成 bootstrap 基础与打包链路，处于“实机回归 + 前台功能开发起步”阶段。

## Completed
- 已有 C++ 业务骨架模块（配置/首页壳/歌词/播放队列/媒体键/音频焦点）。
- 已有 Android 原生壳工程，可产出真实 APK/AAB。
- CI 打包链路可用，`minSdk` 已下调到 API 17。
- 已修复 CI `minSdk` 校验误报（兼容 `sdkVersion`/`minSdkVersion` 输出）。
- 最新已知 `Package MVP` 运行结论为成功（run id: `24440302444`）。
- 已完成 `T-S1-001`：Android 前台从静态封面改为可交互壳（含播放状态、当前曲目、播放/下一曲按钮与点击反馈）。
- 已完成 `T-S1-002`：在 Android 前台引入最小 `UiState`，页面刷新统一走 `updateState -> render`。
- 已完成 `T-S1-003`：本地 mock 队列切换已生效，点击“下一曲”会循环更新当前曲目显示。
- 已完成 `T-S1-005`：接入真实 Emby 测试/拉曲目能力（BaseURL/UserId/Token），并将曲目来源切到 Emby 请求结果。
- 已完成 `T-S1-004`：接入 Android-C++ 最小桥接（JNI + CMake + NDK），`Next` 动作与当前曲目状态由 `src/playback/playback_queue` 驱动。
- 已完成 `T-HF-EMBY-001`：Emby 登录信息本地持久化（含密码）并在启动时自动回填。
- 已完成 `T-HF-EMBY-002`：Emby 响应读取强制 UTF-8 解码，并追加曲名样例日志用于判断“测试数据 vs 实际返回数据”。

## Not Started / Unknown
- 实机回归结果（安装后功能行为）尚未形成闭环记录。

## Current Blockers
- 待人工确认: Android `4.2.2` 实机安装与启动行为。
- 待人工确认: 系统首页音乐卡片是否支持第三方入口。
- 待产品确认: 歌词失败回退策略最终口径。

## Notes
- 已完成 `ai-planning` 产物：`PLAN.md`、`TASK_BREAKDOWN.md`、`TASK_QUEUE.md`、`NEXT_STEPS.md`。
- 当前执行按 `TASK_QUEUE Ready` 单任务推进，下一任务为 `T-S1-006`。
- `SCOPE.md` 当前缺失，计划按已知上下文与用户指令推导，待后续确认。
