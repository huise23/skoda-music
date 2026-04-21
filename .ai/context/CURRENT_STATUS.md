# CURRENT_STATUS

Last Updated: 2026-04-21 16:40

## Stage
- 阶段判断: S1/S2 基础交互已完成，当前处于 S3「API17 播放稳态优化（CF 优选 IPv4 + 30s 下载窗口调度）」执行阶段。

## Completed
- 已有 C++ 业务骨架模块（配置/首页壳/歌词/播放队列/媒体键/音频焦点）。
- Android 壳工程可产出真实 APK/AAB，`minSdk=17`。
- CI 打包链路稳定，最近三次关键构建均成功：`24490460185`、`24491061385`、`24492874887`。
- 已完成 `T-S1-001~006`（交互壳、状态模型、最小动作、JNI桥接、Emby接入、API17回归清单）。
- 已完成 `T-HF-EMBY-001/002`（凭据持久化、UTF-8 解码与诊断日志）。
- 已完成 `T-HF-PLY-001/002/003`（MediaPlayer 真实播放、推荐策略、stream/download 双路径）。
- 已完成 `T-HF-LOG-001`：底部运行日志面板 + 全屏日志。
- 已完成 `T-UI-001`：Android 前台 UI 首版重设计（单页壳）。
- 已完成 planning 清洗：IA v2 单一口径已写入文档与任务队列。
- 已完成 `T-S2-UI-006`：前台升级为 4 导航壳（Home/Queue/Library/Settings）并接入最小切页逻辑。
- 已完成 `T-S2-SET-008`：Settings 同区加入 Emby + LrcApi 配置；两者均改为“测试通过自动保存、失败阻断对应保存”。
- 已完成 `T-S2-UI-007`：Queue/Library 占位区改为可点击曲目列表，单击即播放并刷新当前曲目高亮。
- 已按用户要求移除 Home 顶部标题图卡（截图对应区域）。
- 已完成 `T-S2-UI-008`：Queue “Recommend 20” 按钮接线；保留当前播放项，仅替换未播放段；请求失败保持原队列并提示。
- 已清理 `[Image #1]` 对应残留文案资源（`Skoda Music MVP / Android interactive shell / MVP • Real Emby`）。
- 已完成 `T-S2-003`：全屏日志面板新增“复制/清理”，并保持预览区与全屏区同步刷新。
- 已完成 Android 5.0 启动闪退修复：`bg_main_gradient.xml` 角度改为 API 兼容值（`140 -> 135`）。
- 用户回传验证：Android 5.0 启动测试已通过。
- 已完成 `T-S3-PLY-001`：锁定 ExoPlayer 旧版依赖（首选 `2.17.1`，备选 `2.16.1`）并写入 Android 构建配置。
- 已完成 `T-S3-PLY-002`：引入 `PlaybackEngine` 抽象与 `ExoPlaybackEngine` 实现，主播放链路不再直接调用 `MediaPlayer`。
- 已完成 `T-S3-DL-003`：主播放链路改为 download-only（不再主动请求 stream 端点），并配置 3s 起播 / 1s 补缓阈值。
- 已完成 `T-S3-NET-009`：接入 CF 优选 IPv4 解析链路（参考域名->候选 IPv4），业务请求 Host 保持 Emby 域名；鉴权/拉库/推荐/播放/下载统一走可注入 DNS 的 OkHttp 客户端并支持系统 DNS 回退。
- 已完成 `T-S3-DL-010`：下载控制线程按 30s 窗口状态机运行（`MAINTAIN_CURRENT_WINDOW / FINISH_CURRENT_TRACK / PREFETCH_NEXT_WINDOW / IDLE`），满足“当前可播 <30s 下载、>=30s 暂停；剩余播放 <30s 先补完当前再预下下一曲前30s”口径。
- 已完成 `T-S3-LOG-011`：补齐下载调度与优选 IPv4 诊断日志（phase 切换、暂停原因、chunk 开始/成功/跳过、DNS cache-hit/refresh、优选命中与系统回退原因）。

## Not Started / Unknown
- API17 全量实机回归结果尚未按清单完整回传（目前仅关键播放链路已验证）。
- 系统首页音乐卡片第三方入口能力仍未知。

## Current Blockers
- `T-S3-VAL-012` API17 播放专项回归与证据回填（需人工实机执行并回传）。
- `T-BLK-001` 系统首页音乐卡片第三方入口接入（待系统能力确认）。
- `B-LRC-001` 歌词失败回退策略口径（待产品确认）。
- 本地缺少 `gradle/gradlew.bat`，无法完成构建型验证（需 CI 或外部环境）。

## Notes
- `.ai/context/SCOPE.md` 仍缺失。
- `T-S2-IA-001` 已完成（IA 文档与 planning 单一口径清洗）。
- 当前 Ready 主线: `T-S3-VAL-012`（需 API17 实机）。
- 本地无法直接执行 Gradle 构建验证（环境缺少 `gradle/gradlew.bat`），仅完成静态绑定核对。
- 新增硬约束: `minSdk=17` 为红线，不允许通过升级 SDK/依赖版本绕过兼容问题。
- 新增口径: “移除登录/加载”在本项目中指“移除相关敏感/冗余日志”，非移除登录与加载功能本身。
- 诊断补充: 首播卡顿当前只确认链路等待累积问题，`NAT` 可能为放大因素但未被确认为唯一根因。
- 规划补充: 已生成 S3 任务拆分与队列（`T-S3-PLY-001~T-S3-VAL-007`），Ready 可直接进入 execution。
- 执行补充: `T-S3-RB-008` 与 `T-S3-NET-009` 已闭环，下一任务为 `T-S3-DL-010`。
- 讨论纠偏: “优选域名”不作为业务域名，仅作 CF 优选节点参考；业务请求仍走用户 Emby 域名。
- 新增硬约束: 不做 IPv6（v6）链路，车机环境不支持。
- 调度口径: 当前曲目按“已下载可播时长 30s 窗口”暂停/恢复；曲目剩余播放 <30s 时先补完当前再预下下一曲前30s。
- 执行补充: `T-S3-DL-010` 已落地，下载状态机与阈值常量已在 `MainActivity.kt` 接线（`DOWNLOAD_WINDOW_SEC=30`）。
- 执行补充: `T-S3-LOG-011` 已落地，日志可直接支撑 `T-S3-VAL-012` 的实机复盘与证据留存。
- 本地处置: 本轮错误实现已按用户要求回滚。

## Local Pending Changes (Gemini)
- 当前工作区存在一批未提交的 Gemini 本地改动（UI 玻璃态视觉 + 推荐页自动刷新）。
- 代码行为变更（`MainActivity.kt`）：
  - 启动时自动尝试刷新推荐数据（当队列为空且 Emby 凭据完整）。
  - 进入 Queue 页时自动尝试刷新推荐数据（同上）。
  - 自动刷新增加并发保护与冷却窗口（`queueAutoRefreshInFlight` + `15s cooldown`）。
  - `requestTracksFromEmby` 改为支持 `onFinished` 回调，用于释放自动刷新状态。
- 资源/UI 变更：
  - `colors.xml` 视觉基色重定义为玻璃态风格，新增 `glass_*` 与 `white`。
  - 多个 drawable 改为玻璃态边框/圆角参数（`panel_card/field_surface/button_secondary/chip_pill` 等）。
  - 新增导航按钮资源：`button_nav_active.xml`、`button_nav_inactive.xml`。
  - `activity_main.xml` 导航 Home 按钮默认样式改为 `button_nav_active`，文字改白色。
  - `bg_main_gradient.xml` 改为新的蓝色线性渐变参数。
  - `strings.xml` 新增 `app_name_version = v1.0.0 Glass`。
