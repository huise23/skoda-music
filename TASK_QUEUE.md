# TASK_QUEUE

Last Updated: 2026-04-15 14:22:45

## Ready
- [ ] （空）

## Backlog
- [ ] （空）

## Blocked
- [ ] B-001 系统首页音乐卡片调用能力验证（缺少车机系统对三方入口的可用性结论）
- [ ] B-003 歌词失败回退策略确认（远程失败时是否回退过期缓存、是否快速重试）
- [ ] B-006 待车机实机安装验证（目标设备兼容性与启动稳定性）

## Done
- [x] T-000 初始化 AI 协作核心文档（PROJECT_BRIEF/PLAN/STATUS/HANDOFF/RULES/QUEUE）
- [x] T-001 配置骨架：新增配置规范文档与样例配置文件
- [x] T-002 配置加载接口草案：新增加载顺序、结构化对象、错误码和校验规则文档
- [x] T-003 歌词链路状态机文档：新增状态、事件、转移与异步降级流程文档
- [x] T-005 配置加载错误分级说明：新增 Fatal/Degradable 分类与处理策略文档
- [x] T-007 配置策略修订：改为零启动阻断 + 启动自愈 + 聚合toast + Emby测试保存门槛
- [x] T-004 UI 信息架构低保真草图说明：新增导航、页面分区与关键流程文档
- [x] T-006 配置加载接口与错误策略联动示例：新增端到端联动示例文档
- [x] T-008 歌词异常场景测试清单：新增异常路径测试项与验证标准文档
- [x] T-009 配置与UI串联验收用例清单：新增配置-界面联动验收清单文档
- [x] T-010 启动自愈与设置页提示文案规范：新增统一提示文案规范文档
- [x] T-011 最小实现切入点建议：新增从文档到代码任务的最小切入方案文档
- [x] T-012 实现 I-001 配置运行时骨架：新增 `ConfigRuntime`、`LoadAndSelfHeal`、`RepairReport` 与启动 toast 触发示例
- [x] T-013 实现 I-002 设置页最小闭环：新增设置页 Test/Save Gate 逻辑骨架与状态示例
- [x] T-014 实现 I-003 首页最小播放壳：新增 Home 状态壳层、Emby 未就绪提示与推荐占位逻辑
- [x] T-016 实现 I-004 歌词链路最小实现：新增 LyricsResolver（嵌入优先 + 异步远程 + 切歌取消）
- [x] T-017 实现 I-005 方向盘媒体键联动：新增媒体键映射控制器与首页/队列同步状态输出
- [x] T-015 GitHub Actions Android 打包流程配置：已创建仓库并实测 Actions 打包成功
- [x] T-018 实现 C3 音频焦点与前后台状态管理骨架：新增 AudioFocusManager 状态机与动作回调
- [x] T-020 实现主入口与模块装配：新增 `mvp_app` 可执行壳并通过 CI smoke test
- [x] T-021 实现 Qt/QML 前台 UI 壳与现有模块桥接：新增桥接层、QML 壳与 CI smoke test
- [x] T-022 升级 actions 运行时兼容 Node.js 24：升级 `checkout/upload-artifact` 并通过 Node24 强制运行验证
- [x] T-019 GitHub Actions 增加签名与 Release 上传流程（AAB/APK）：新增签名步骤、Release 上传步骤与触发条件
- [x] T-023 签名发布操作文档与密钥检查清单：新增可执行 runbook 与维护者操作步骤
- [x] T-024 产出真实可用 APK：新增 Android Gradle 工程并在 CI 中构建/签名真实 APK+AAB，发布 `mvp-r17`
- [x] T-025 升级 CI setup actions 到 Node24-ready 版本并验证无弃用注记
- [x] T-026 回写实机参数确认结果：已记录 Android `4.2.2` / `1024x600` / CPU `autochips ac83xx`，并同步状态文件
- [x] T-027 Android 4.2.2 兼容改造：`minSdk` 下调到 `17`，依赖收敛为 `appcompat`，字节码目标降为 Java/Kotlin `1.8`，CI 增加 `minSdkVersion:'17'` 校验
- [x] T-028 发布 runbook 对齐 API 17 基线：补齐 `minSdk=17`/`apk_badging` 校验与 `mvp-r18` 实机验收步骤
- [x] T-029 修复 CI 最低版本校验误报：兼容 `aapt` 的 `sdkVersion`/`minSdkVersion` 输出格式
- [x] B-004 已确认：设置页展示启动自愈字段明细
- [x] B-005 已完成：Android 签名 Secrets 已配置并完成手动发布验收
- [x] B-002 已确认：目标分辨率为 `1024x600`（横屏）
- [x] B-007 已处理：版本门槛已从 `minSdk=21` 下调到 `17`，待实机安装回归验证

## Task Definition of Done
- 任务产出必须可被下一会话直接接手。
- 任务完成后必须更新 `CURRENT_STATUS.md`、`NEXT_STEPS.md`、`HANDOFF.md`。
