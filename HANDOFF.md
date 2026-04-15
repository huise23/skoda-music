# HANDOFF

Last Updated: <!-- AUTO:LAST_UPDATED --> 2026-04-15 14:22:45

## Project
- `skoda-music`: 车机端音乐播放器（个人/vibe 项目），目标是支持 AI 持续接手。

## Objective
- 当前目标: 先完成 AI 协作工作台，再以最小任务闭环方式推进实现。

## Completed
- 协作核心文档已初始化并补齐（BRIEF/PLAN/STATUS/STEPS/DECISIONS/HANDOFF/RULES/QUEUE）。
- 已固化关键决策（Qt/C++、Android、Emby-only、媒体键、纯在线、歌词策略、UI方向）。
- 已完成 `T-001 配置骨架`（配置规范 + 配置样例文件）。
- 已完成 `T-002 配置加载接口草案`（输出对象、加载顺序、错误码、校验规则）。
- 已完成 `T-003 歌词链路状态机文档`（状态、事件、转移、超时取消、缓存降级）。
- 已完成 `T-005 配置加载错误分级说明`（Fatal/Degradable 分类、处理动作、映射策略）。
- 已完成 `T-007 配置策略修订落盘`（零启动阻断、自愈仅内存生效、聚合toast、Emby测试保存门槛）。
- 已完成 `T-004 UI 信息架构低保真草图说明`（导航、页面分区、关键流程和空态约定）。
- 已完成 `T-006 配置加载接口与错误策略联动示例`（启动自愈、保存门槛、告警降级的端到端流程）。
- 已完成 `T-008 歌词异常场景测试清单`（异常路径、预期行为、验证出口标准）。
- 已完成 `T-009 配置与UI串联验收用例清单`（配置策略与页面行为联动验收）。
- 已完成 `T-010 启动自愈与设置页提示文案规范`（启动toast、测试反馈、保存提示统一规范）。
- 已完成 `T-011 最小实现切入点建议`（文档到代码任务映射，明确首个实现切入点）。
- 已完成 `T-012 实现 I-001 配置运行时骨架`（`LoadAndSelfHeal + RepairReport`、启动聚合 toast 触发点、最小保存门槛骨架）。
- 已完成 `T-013 实现 I-002 设置页最小闭环`（设置页 Test/Save Gate 逻辑、文案对齐、启动自愈明细行构建入口）。
- 已完成 `T-014 实现 I-003 首页最小播放壳`（Home 状态壳层、Emby 未就绪提示、推荐占位区逻辑）。
- 已完成 `T-016 实现 I-004 歌词链路最小实现`（嵌入优先、异步远程、切歌取消防串歌）。
- 已完成 `T-017 实现 I-005 方向盘媒体键联动`（媒体键映射、上/下一曲、首页/队列状态同步）。
- 已完成 `T-015 GitHub Actions 打包流程配置并实测成功`（仓库创建、push 触发、artifact 产出）。
- 已完成 `T-018 实现 C3 音频焦点与前后台状态管理骨架`（前后台切换与焦点状态流）。
- 已完成 `T-020 实现主入口与模块装配`（`mvp_app` 可执行壳 + CI smoke test）。
- 已完成 `T-021 实现 Qt/QML 前台 UI 壳与现有模块桥接`（桥接层 + QML 壳 + CI smoke test）。
- 已完成 `T-022 升级 actions 运行时兼容 Node.js 24`（升级 action 版本并通过强制 Node24 验证）。
- 已完成 `T-019 GitHub Actions 增加签名与 Release 上传流程（AAB/APK）`（签名步骤 + 条件 release 上传）。
- 已完成 `T-023 签名发布操作文档与密钥检查清单`（新增维护者可执行 runbook）。
- 已完成 `T-024 真实可用 APK 产出`（Android Gradle 工程 + 真实签名发布）。
- 已完成 `T-026 实机参数确认回写`（已确认 Android `4.2.2` / `1024x600` / `autochips ac83xx`）。
- 已完成 `T-027 Android 4.2.2 兼容改造`（`minSdk=17` + `AppCompat` 主题 + Java/Kotlin `1.8` 目标 + CI 最低版本校验）。
- 已完成 `T-028 发布 runbook 对齐 API 17 基线`（`docs/CI_SIGNING_RELEASE_RUNBOOK.md` v2）。
- 已完成 `T-029 修复 CI 最低版本校验误报`（兼容 `sdkVersion/minSdkVersion` 两种 badging 输出）。
- 已解除 `B-002`（目标分辨率待确认）。
- 已达成最小可用版本里程碑：`I-001` 到 `I-005` 全部完成（骨架版）。
- 已达成“最小可用 App（控制台壳）”里程碑，可端到端演示主流程。
- 已确认 `B-004`：设置页展示启动自愈字段明细。

## In Progress
- 等待维护者执行 `M-002`：Android `4.2.2` 车机实机安装与兼容性验收。

## Next Actions
<!-- AUTO:NEXT_ACTIONS_START -->
- [ ] 触发发布包含 `minSdk=17` 的新版本（建议 `mvp-r18`）
- [ ] 安装 release `mvp-r18`（或最新）的 `skoda-music-mvp-signed.apk` 到目标车机
- [ ] 回传安装/启动结果（成功、解析失败/版本过低、闪退、权限问题）
- [ ] 确认最新 `Package MVP` run 中 `Verify APK minSdk is API 17` 步骤通过
<!-- AUTO:NEXT_ACTIONS_END -->

## Decisions Already Made
- 详见 `DECISIONS.md`（不要擅自推翻）。

## Constraints
- 设备资源有限（ARMv7/2G RAM，可用约 0.95G），优先稳定和低卡顿。
- 目标设备已确认：Android `4.2.2`、`1024x600` 横屏、CPU `autochips ac83xx`。
- 当前代码已将 Android 壳 `minSdk` 下调为 `17` 以覆盖 Android `4.2.2`（API 17）。
- LrcApi 已自建，项目侧只提供配置与调用能力。

## Important Files
<!-- AUTO:CHANGED_FILES_START -->
- `PROJECT_BRIEF.md`
- `PLAN.md`
- `CURRENT_STATUS.md`
- `NEXT_STEPS.md`
- `DECISIONS.md`
- `HANDOFF.md`
- `EXECUTION_RULES.md`
- `TASK_QUEUE.md`
- `docs/CONFIG_SPEC.md`
- `docs/CONFIG_LOADER_INTERFACE.md`
- `docs/LYRICS_STATE_MACHINE.md`
- `docs/CONFIG_ERROR_POLICY.md`
- `docs/UI_IA_LOW_FIDELITY.md`
- `docs/CONFIG_RUNTIME_INTEGRATION_EXAMPLE.md`
- `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md`
- `docs/CONFIG_UI_INTEGRATION_ACCEPTANCE.md`
- `docs/SELF_HEAL_AND_SETTINGS_COPY_GUIDELINES.md`
- `docs/MIN_IMPLEMENTATION_ENTRYPOINTS.md`
- `config/app.config.example.yaml`
- `src/config/config_runtime.h`
- `src/config/config_runtime.cpp`
- `src/config/runtime_bootstrap_example.cpp`
- `src/ui/settings/settings_flow.h`
- `src/ui/settings/settings_flow.cpp`
- `src/ui/settings/settings_flow_example.cpp`
- `src/ui/home/home_shell.h`
- `src/ui/home/home_shell.cpp`
- `src/ui/home/home_shell_example.cpp`
- `src/lyrics/lyrics_resolver.h`
- `src/lyrics/lyrics_resolver.cpp`
- `src/lyrics/lyrics_resolver_example.cpp`
- `src/playback/playback_queue.h`
- `src/playback/playback_queue.cpp`
- `src/platform/android/media_key_controller.h`
- `src/platform/android/media_key_controller.cpp`
- `src/platform/android/media_key_controller_example.cpp`
- `src/platform/android/audio_focus_manager.h`
- `src/platform/android/audio_focus_manager.cpp`
- `src/platform/android/audio_focus_manager_example.cpp`
- `src/app/mvp_app.cpp`
- `src/app/qml_frontend_bridge.h`
- `src/app/qml_frontend_bridge.cpp`
- `src/app/qml_frontend_bridge_example.cpp`
- `src/ui/qml/Main.qml`
- `.github/workflows/package-mvp.yml`
- `docs/CI_SIGNING_RELEASE_RUNBOOK.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
<!-- AUTO:CHANGED_FILES_END -->

## Risks / Open Questions
- 系统首页音乐卡片是否支持第三方播放器调用: 待确认。
- 环境变量命名规范是否固定: 待确认（见 `docs/CONFIG_SPEC.md`）。
- 配置错误在启动阶段统一走“自愈继续启动”，不再阻断。
- 歌词远程失败时是否回退过期缓存/是否快速重试: 待确认（见 `docs/LYRICS_STATE_MACHINE.md`）。
- 当前 YAML 解析为最小骨架实现（自研简化解析），后续可替换为成熟 YAML 库实现以提升鲁棒性。
- Android 4.2.2 兼容性仍需实机回归验证（安装、启动、前后台切换）。
- GitHub 仓库: `https://github.com/huise23/skoda-music`
- Actions 运行: `https://github.com/huise23/skoda-music/actions/runs/24433338344`（success，签名与 release 发布已执行）
- 最新发布: `https://github.com/huise23/skoda-music/releases/tag/mvp-r17`
- 维护者操作文档: `docs/CI_SIGNING_RELEASE_RUNBOOK.md`（v2，已对齐 API 17）

## Instructions For Next AI Session
- 先读: `PROJECT_BRIEF.md` `PLAN.md` `CURRENT_STATUS.md` `DECISIONS.md` `HANDOFF.md` `TASK_QUEUE.md`。
- 先输出对当前状态的理解，再只选择一个 Ready 任务执行。
- 本轮结束前必须更新 STATUS/STEPS/HANDOFF/QUEUE。
