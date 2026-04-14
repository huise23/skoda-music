# DECISIONS

## 2026-04-13 - 项目定位与协作方式
- Context: 项目需要长期 AI 协作，且会频繁新开会话与上下文压缩。
- Decision: 使用 Markdown + Git 的项目记忆系统作为主上下文载体。
- Alternatives considered: 纯任务工具、纯聊天记录。
- Why this option: 文本稳定、可版本化、可迁移，最适合 AI 接手续作。
- Follow-up impact: 每轮必须更新状态文件，避免上下文漂移。

## 2026-04-13 - 技术栈与平台
- Context: 车机设备资源有限（ARMv7/2G 内存），需稳定低开销。
- Decision: 首版采用 Qt/C++，目标环境按 Android 车机适配。
- Alternatives considered: Android 原生、WebView。
- Why this option: 性能可控且可做系统适配，满足低资源场景。
- Follow-up impact: 后续优先定义适配层接口，避免平台耦合扩散。

## 2026-04-13 - 协议与推荐策略
- Context: 首版需快速形成可用 MVP。
- Decision: 仅支持 Emby；推荐采用服务端优先。
- Alternatives considered: 同时支持 Jellyfin/Subsonic；端侧推荐。
- Why this option: 降低首版复杂度，优先交付可用链路。
- Follow-up impact: Provider 层保留抽象接口，后续可扩协议。

## 2026-04-13 - 控制与网络策略
- Context: 驾驶场景操作优先级高，需快速稳定切歌。
- Decision: 方向盘控制走系统媒体键映射；首版纯在线。
- Alternatives considered: CAN/MCU 定制接口；离线下载。
- Why this option: 集成成本与风险最低，MVP 更快落地。
- Follow-up impact: 保留可扩展点，不阻塞当前执行。

## 2026-04-13 - 歌词处理策略
- Context: 部分音频有嵌入歌词，缺失时需要自动补全。
- Decision: 嵌入歌词优先读取（仅标准同步标签）；无歌词时异步请求 LrcApi；有嵌入即不请求；不得阻塞主播放进程。
- Alternatives considered: 始终远程请求；同步阻塞拉取；全格式一次性支持。
- Why this option: 首屏快、流畅性好、实现复杂度可控。
- Follow-up impact: 需要 LyricsResolver 处理取消/超时/缓存刷新。

## 2026-04-13 - UI 方向
- Context: 需要玻璃拟态风格但避免卡顿。
- Decision: 轻玻璃拟态 + 低动效 + 左侧导航 + 横屏 + 中密度 + 深浅双主题 + 暖灰橙配色。
- Alternatives considered: 底部 Tab、重玻璃、高动效。
- Why this option: 在风格和性能间平衡，降低车机端掉帧风险。
- Follow-up impact: UI 组件需先定义 token，再进入实现。

## 2026-04-13 - LrcApi 部署边界
- Context: 用户已自建 LrcApi 服务。
- Decision: 项目只提供配置入口，不负责部署流程。
- Alternatives considered: 在项目中集成部署脚本。
- Why this option: 缩小首版范围，避免无关运维复杂度。
- Follow-up impact: 配置骨架任务优先落地。

## 2026-04-14 - 配置策略改为零启动阻断
- Context: 首次启动通常没有 Emby 等字段，启动阻断会导致不可用。
- Decision: 启动阶段不阻断；配置异常执行启动自愈（回退为空值/默认值），仅内存生效，不覆写文件；启动显示一条聚合 toast。
- Alternatives considered: 启动阶段 Fatal 阻断；自愈后立即写回文件。
- Why this option: 符合首次使用流程，避免卡死在启动前，用户可先进入配置向导完成配置。
- Follow-up impact: 保存门槛调整为“Emby 连通+鉴权通过才允许保存”；LrcApi 测试失败仅告警不阻断。

## 2026-04-14 - 设置页展示启动自愈明细
- Context: 启动已采用自愈策略，需要可见性帮助用户修复配置。
- Decision: 设置页展示“启动自愈字段明细”。
- Alternatives considered: 不展示明细，仅toast提示。
- Why this option: 提升可维护性与可诊断性，减少用户重复排障成本。
- Follow-up impact: 文案规范与验收清单需包含明细展示要求。
