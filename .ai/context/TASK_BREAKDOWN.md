# TASK_BREAKDOWN

Last Updated: 2026-04-21 18:05

## T-S3-RB-008
- Task ID: `T-S3-RB-008`
- Title: 回滚错误实现并恢复基线
- Goal: 撤销本轮错误理解下的本地代码改动，恢复稳定基线。
- Why: 当前实现方向与用户确认口径冲突，继续叠加会扩大偏差。
- Dependencies: 无
- Inputs:
  - 本地 Git 工作区差异
  - 用户指令“本地已有代码改动直接回滚”
- Expected Outputs:
  - 代码改动回滚完成
  - 仅保留 `.ai/context` 规划文档更新
- Done Criteria:
  - `git status --short` 不含业务代码残留变更
- Risks:
  - 误回滚用户自身改动
- Size: S
- Minimal Loop: Yes

## T-S3-NET-009
- Task ID: `T-S3-NET-009`
- Title: CF 优选 IPv4 解析链路接线（Emby 域名保持不变）
- Goal: 用参考域名得到优选 IPv4 节点，并用于 Emby 域名请求的解析优选。
- Why: 用户目标是“加速自己 Emby URL”，不是更换业务域名。
- Dependencies: `T-S3-RB-008`
- Inputs:
  - `MainActivity.kt` 当前下载/播放请求链路
  - 用户配置项（Emby Base URL + CF 优选参考域名）
- Expected Outputs:
  - IPv4-only 候选 IP 获取、排序与回退机制
  - 请求日志可追踪命中 IP 与回退路径
- Done Criteria:
  - 请求 Host 仍是 Emby 域名
  - 优选失败可自动回退系统 DNS
- Risks:
  - 参考域名不可用导致优选收益不稳定
- Size: M
- Minimal Loop: Yes

## T-S3-DL-010
- Task ID: `T-S3-DL-010`
- Title: 30s 下载窗口调度状态机
- Goal: 严格按用户定义控制当前曲目与下一曲预下载。
- Why: 需要在“不卡顿”和“不浪费带宽”间按固定规则平衡。
- Dependencies: `T-S3-NET-009`
- Inputs:
  - 当前曲目时长、已播放时长、已下载估算时长
  - 下一曲曲目信息与下载任务管理
- Expected Outputs:
  - 当前曲目：`downloadedPlayableSec < 30` 继续下载，`> 30` 暂停下载
  - 临近结束：`remainingPlaySec < 30` 时，先补完当前曲目，再预下载下一曲前 30s
- Done Criteria:
  - 状态切换符合规则，且无永久停下载/重复拉起抖动
- Risks:
  - 可播秒数估算偏差导致触发点不准
- Size: L
- Minimal Loop: No

## T-S3-LOG-011
- Task ID: `T-S3-LOG-011`
- Title: 下载调度与优选 IP 诊断日志补齐
- Goal: 让“为什么下载暂停/恢复、为什么切换节点”可追踪。
- Why: 该阶段调度逻辑复杂，没有日志无法实机快速定位。
- Dependencies: `T-S3-DL-010`
- Inputs:
  - 现有 runtime log 面板
  - 调度状态机事件
- Expected Outputs:
  - 关键日志：优选命中 IP、回退原因、`downloadedPlayableSec`、`remainingPlaySec`、下一曲预下载阶段
- Done Criteria:
  - 复盘日志可还原一次完整播放周期的决策过程
- Risks:
  - 日志过量影响可读性
- Size: S
- Minimal Loop: Yes

## T-S3-VAL-012
- Task ID: `T-S3-VAL-012`
- Title: API17 实机专项回归（优选 IP + 30s 调度）
- Goal: 验证新策略在目标车机上的稳定性与体感收益。
- Why: 该优化高度依赖真实网络环境，必须实机闭环。
- Dependencies: `T-S3-LOG-011`
- Inputs:
  - API17 车机设备
  - Runtime 日志、关键操作录屏/截图
- Expected Outputs:
  - 首播、弱网、切歌、临近结束预下载、下一曲衔接的 PASS/FAIL 结果
- Done Criteria:
  - 至少覆盖 5 轮连续切歌场景且无严重阻断
- Risks:
  - 设备环境不可控导致结果波动
- Size: M
- Minimal Loop: No

## T-S3-UI-013
- Task ID: `T-S3-UI-013`
- Title: Home 播放模块重排 + 可拖动进度 + 解码失败自动切歌
- Goal: 按用户截图参考重排 Home 播放模块（不改颜色/玻璃风格），并落地解码失败自动切下一首与可拖动进度条。
- Why: 当前车机出现 `MediaCodecAudioRenderer/code=4003` 失败场景，需从“失败即停”改为“自动跳过”，同时使播放交互更贴近车机主流样式。
- Dependencies: `T-S3-LOG-011`
- Inputs:
  - 用户提供截图（模块框架参考）
  - `MainActivity.kt` 播放引擎与回调链路
  - `activity_main.xml` 现有 Home 页面布局
- Expected Outputs:
  - `PlaybackEngine` 增加 `seekTo` 并接线 `SeekBar` 拖动定位
  - 下载/缓存链路 `onError` 统一进入自动切歌处理
  - Home 模块调整为“封面入口 + 信息区 + 进度条 + 三键控制”结构
  - 保持现有玻璃态资源与配色不变
- Done Criteria:
  - `code=4003` 等解码失败场景触发自动切下一首
  - 进度条可拖动并在松手后生效
  - UI 结构符合用户参考方向，且无主题资源重构
- Risks:
  - 无本地 `gradlew`，只能做静态核对，编译/实机验证需外部环境
- Size: M
- Minimal Loop: Yes

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
