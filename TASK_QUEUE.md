# TASK_QUEUE

Last Updated: 2026-04-15 08:56:37

## Ready
- [ ] T-022 升级 actions 运行时兼容 Node.js 24（替换/验证 action 版本）
- [ ] T-019 GitHub Actions 增加签名与 Release 上传流程（AAB/APK）

## Backlog
- [ ] （空）

## Blocked
- [ ] B-001 系统首页音乐卡片调用能力验证（缺少车机系统对三方入口的可用性结论）
- [ ] B-002 目标分辨率细节收敛（当前仅确认横屏，具体尺寸待确认）
- [ ] B-003 歌词失败回退策略确认（远程失败时是否回退过期缓存、是否快速重试）

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
- [x] B-004 已确认：设置页展示启动自愈字段明细

## Task Definition of Done
- 任务产出必须可被下一会话直接接手。
- 任务完成后必须更新 `CURRENT_STATUS.md`、`NEXT_STEPS.md`、`HANDOFF.md`。
