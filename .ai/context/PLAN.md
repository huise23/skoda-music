# PLAN

Last Updated: 2026-04-20 12:08

## Current Stage
- Stage Name: S3 API17 播放内核重构（第三方播放器 + Download 边下边播）
- Scope Source: `SCOPE.md` 缺失，按 `PROJECT_BRIEF + CURRENT_STATUS + DECISIONS + 最新用户指令` 推导。

## Scope Validation

### In Scope (S3)
- 在 `minSdk=17` 红线下，引入旧版 ExoPlayer 2.x 固定版本（不升 SDK）。
- 将播放链路从“stream + fallback download”改为“仅 download 端点 + 边下边播”。
- 实现“可播放内容达到 `>=3s` 即起播”与低缓冲自动补缓（`<1s`）。
- 引入 `StyledPlayerView` 并完成首轮 UI 融合（不破坏现有导航结构）。
- 收敛日志口径：移除登录/加载敏感与冗余日志，仅保留诊断必要信息。
- 缓存策略落地：保留最近 20 首并执行清理策略。

### Out of Scope (S3)
- 升级到 Media3 新主线或提升 `minSdk`。
- 多协议接入（Jellyfin/Subsonic）。
- 完整离线媒体库体系化重构。
- 车机系统首页卡片能力接入（仍受系统能力限制）。

### Success Criteria
- API17 环境可完成“点击播放 -> 可见缓冲态 -> 边下边播起播 -> 连续播放/自动补缓”闭环。
- 实际请求链路仅使用 download 端点，日志可证明无主动 stream 请求。
- `StyledPlayerView` 接入后，不破坏 Queue/Library 切歌与当前曲目状态同步。
- 缓存命中可加速二次播放，缓存数量上限为 20 且可清理。
- 登录/加载敏感日志被清理，保留错误码/异常类型/阶段结果日志。

## Reality Check
- 现状播放内核仍为 `MediaPlayer`，`MainActivity.kt` 内含 stream 直连 + download 兜底逻辑。
- 当前 UI 已是 4 导航壳，但尚未接入 `StyledPlayerView`。
- 现有日志包含鉴权与加载细节，需按新口径收敛。
- 本地环境缺少 `gradle/gradlew.bat`，构建验证需依赖 CI 或人工设备回归。

## Workstreams

### W1 播放器内核替换
- 目标: 在 API17 下将播放执行引擎替换为固定版本 ExoPlayer 2.x。
- 输出: 可稳定准备、播放、暂停、释放的统一播放引擎。

### W2 Download 边下边播链路
- 目标: 仅使用 download 端点并实现起播阈值与补缓策略。
- 输出: 可持续下载并可播即播的链路，移除 stream 主路径。

### W3 UI 与状态融合
- 目标: 接入 `StyledPlayerView`，保持现有交互壳与队列逻辑。
- 输出: 可视化播放控件 + 不回归的 Queue/Library 交互。

### W4 日志与缓存治理
- 目标: 清理敏感日志并落地“最近20首”缓存策略。
- 输出: 可审计日志口径 + 可控缓存占用。

### W5 回归验证
- 目标: 完成 API17 专项回归（首播、弱网、补缓、切歌、缓存）。
- 输出: 结构化 PASS/FAIL 证据与问题清单。

## Dependencies
- W1 是 W2/W3 的前置。
- W2 与 W4 可并行，但都会改 `MainActivity.kt`，应串行合并以减少冲突。
- W5 依赖 W1~W4 输出。

## Risks & Assumptions
- 风险: 旧版 ExoPlayer 版本与 API17 兼容性存在不确定性，需先做版本探针。
- 风险: download-only 在弱网下仍可能频繁补缓，阈值需设备上校准。
- 假设: 服务端音频已为 320k mp3 且单曲体积通常 <=10MB。
- 假设: 当前阶段优先“可听感稳定 + 可诊断”，UI 深度美化后置。

## Recommended Order
1. `T-S3-PLY-001` ExoPlayer 2.x 版本探针与依赖固定
2. `T-S3-PLY-002` 播放引擎抽象与 MediaPlayer 替换
3. `T-S3-DL-003` download-only 边下边播链路
4. `T-S3-UI-004` `StyledPlayerView` 接入与状态同步
5. `T-S3-LOG-005` 登录/加载日志收敛
6. `T-S3-CACHE-006` 最近20首缓存治理
7. `T-S3-VAL-007` API17 回归与证据回填
