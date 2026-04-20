# TASK_BREAKDOWN

Last Updated: 2026-04-20 12:08

## T-S3-PLY-001
- Task ID: `T-S3-PLY-001`
- Title: ExoPlayer 2.x 版本探针与依赖固定（API17）
- Goal: 在不改 `minSdk=17` 前提下确认可用旧版 ExoPlayer，并锁定版本。
- Why: S3 方案可行性的第一前置，避免后续改造建立在错误版本假设上。
- Dependencies: 无
- Inputs:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `DECISIONS.md`（S3 新决策）
- Expected Outputs:
  - 固定 ExoPlayer 2.x 版本依赖说明（首选 + 备选降级版本）
  - API17 兼容性结论（可运行/需降级）
- Done Criteria:
  - 依赖版本已固定且被文档记录。
  - 给出后续任务使用的唯一版本基线。
- Risks:
  - 旧版依赖可用性、传递依赖冲突。
- Size: S
- Minimal Loop: Yes

## T-S3-PLY-002
- Task ID: `T-S3-PLY-002`
- Title: 播放引擎抽象与 MediaPlayer 主路径替换
- Goal: 引入统一播放引擎接口并将主播放路径切到 ExoPlayer。
- Why: 当前 `MainActivity` 直接操作 `MediaPlayer`，扩展边下边播困难。
- Dependencies: `T-S3-PLY-001`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- Expected Outputs:
  - 清晰的播放引擎边界（prepare/play/pause/release/状态回调）
  - 原 `MediaPlayer` 主路径移除或下沉为临时兜底
- Done Criteria:
  - 播放/暂停/下一曲链路在新引擎下可执行。
  - 不再通过旧 `MediaPlayer` stream 主路径触发播放。
- Risks:
  - 状态回调与现有 UI 状态机耦合，易出现状态不同步。
- Size: M
- Minimal Loop: Yes

## T-S3-DL-003
- Task ID: `T-S3-DL-003`
- Title: Download-only 边下边播链路落地
- Goal: 仅使用 download 端点实现可播即播。
- Why: 用户要求停止 stream 端点主链路，并支持“1秒级可感知起播”。
- Dependencies: `T-S3-PLY-002`
- Inputs:
  - `MainActivity.kt` 中 Emby URL 构建与播放接线路径
- Expected Outputs:
  - 仅 download 请求链路
  - 可播放内容 `>=3s` 即起播
  - 低缓冲 `<1s` 自动补缓与恢复
- Done Criteria:
  - 日志可验证无主动 stream 端点请求。
  - 弱网下可见 buffering->resume 闭环。
- Risks:
  - 阈值需在目标设备上调参；过激阈值会造成频繁补缓。
- Size: M
- Minimal Loop: Yes

## T-S3-UI-004
- Task ID: `T-S3-UI-004`
- Title: `StyledPlayerView` 接入与现有壳层融合
- Goal: 引入可美化播放器控件，保持现有 4 导航交互逻辑。
- Why: 用户明确要求支持播放器美化与优化。
- Dependencies: `T-S3-PLY-002`
- Inputs:
  - `app/src/main/res/layout/activity_main.xml`
  - `MainActivity.kt`
- Expected Outputs:
  - `StyledPlayerView` 嵌入 Home 播放区域
  - 现有按钮与状态文本与播放器状态一致
- Done Criteria:
  - UI 不破坏 Queue/Library 单击切歌逻辑。
  - 播放状态（播放/缓冲/暂停）可见且一致。
- Risks:
  - 控件接入后可能与现有按钮行为重复或冲突。
- Size: M
- Minimal Loop: Yes

## T-S3-LOG-005
- Task ID: `T-S3-LOG-005`
- Title: 登录/加载日志口径收敛
- Goal: 移除敏感与冗余日志，保留诊断必要日志。
- Why: 已确认“移除登录/加载日志”是日志口径收敛，不是功能删除。
- Dependencies: `T-S3-PLY-002`
- Inputs:
  - `MainActivity.kt` 日志输出点
  - `DECISIONS.md` 日志口径
- Expected Outputs:
  - 鉴权、payload 预览、冗余加载样本日志下线
  - 保留阶段结果、错误码、异常类型、关键耗时
- Done Criteria:
  - 运行日志中不再出现登录/加载敏感信息。
  - 故障时仍可定位到阶段与错误类型。
- Risks:
  - 过度删日志会降低排障效率。
- Size: S
- Minimal Loop: Yes

## T-S3-CACHE-006
- Task ID: `T-S3-CACHE-006`
- Title: “最近20首”缓存保留与清理治理
- Goal: 在边下边播场景下提升二次播放速度并控制空间。
- Why: 用户已明确“缓存保留最近20首”。
- Dependencies: `T-S3-DL-003`
- Inputs:
  - 缓存目录与缓存命名规则
  - `MainActivity.kt` 下载与回放路径
- Expected Outputs:
  - 最近20首保留策略
  - 过期/总量清理策略
- Done Criteria:
  - 缓存命中可用于加速二次播放。
  - 缓存文件数量可稳定限制在 20 条以内。
- Risks:
  - 清理时机不当可能删到正在播放文件。
- Size: S
- Minimal Loop: Yes

## T-S3-VAL-007
- Task ID: `T-S3-VAL-007`
- Title: API17 播放专项回归与证据回填
- Goal: 验证 S3 播放改造在目标车机可用并形成证据。
- Why: 播放改造涉及内核替换，必须有实机结果闭环。
- Dependencies: `T-S3-UI-004`, `T-S3-LOG-005`, `T-S3-CACHE-006`
- Inputs:
  - API17 实机
  - 运行日志与关键操作录像/截图
  - `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`
- Expected Outputs:
  - 首播、弱网补缓、切歌、缓存复用的 PASS/FAIL 结论
  - 缺陷清单与复现条件
- Done Criteria:
  - 至少覆盖：首次播放、连续播放、弱网补缓、下一曲、二次播放缓存命中。
  - 结果已回填到 `.ai/context`。
- Risks:
  - 强依赖人工设备时段，进度不可控。
- Size: M
- Minimal Loop: No

## Blocked Candidates (Carry Forward)

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

### B-LRC-001
- Task ID: `B-LRC-001`
- Title: 歌词失败回退策略口径确认
- Goal: 明确远程歌词失败时的提示文案、重试策略与缓存回退规则。
- Why: 当前歌词策略未定，容易在 UI 和行为上反复。
- Dependencies: 产品/维护者口径确认
- Inputs: `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md` 与产品决策
- Expected Outputs: 可执行的策略结论（含提示文案）
- Done Criteria: 形成明确“失败即提示/重试次数/是否回退缓存”结论并写入决策。
- Risks: 口径长期未确认会阻塞歌词相关开发。
- Size: S
- Minimal Loop: No
