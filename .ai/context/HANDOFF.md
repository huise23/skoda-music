# HANDOFF

Last Updated: 2026-04-20 13:02

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@55dc676`（已落盘 minSdk17 红线与日志口径）
- 当前阶段: S3 播放内核重构（ExoPlayer 2.x + download 边下边播）

## Current Goal
- 目标 1: 完成 `T-S3-UI-004/LOG-005/CACHE-006`，形成可回归版本。
- 目标 2: 推进 `T-S3-VAL-007` 实机验证并回填证据。

## Plan Hand-off
- 本轮 `ai-execution` 已完成 `T-S3-DL-003`：
  - 主播放 URL 改为 `buildEmbyDownloadUrl(...)`，不再主动使用 stream 端点。
  - 缓冲策略改为 `>=3s` 起播、`<1s` 重缓冲（通过 Exo LoadControl）。
  - 下载缓存候选 URL 收敛为 Download 单端点。
- 本轮 `ai-execution` 已完成 `T-S3-PLY-002`：
  - `MainActivity.kt` 新增 `PlaybackEngine` 抽象和 `ExoPlaybackEngine` 实现
  - `start/pause/stream playback/cache playback` 主链路切至引擎接口
  - 释放逻辑统一为 `releasePlayer -> playbackEngine.release()`
- 本轮 `ai-execution` 已完成 `T-S3-PLY-001`：
  - `app/build.gradle.kts` 引入 ExoPlayer 固定依赖
  - 首选版本 `2.17.1`，备选降级版本 `2.16.1`
  - 为后续 `T-S3-PLY-002` 播放内核替换提供依赖基线
- 本轮 `ai-planning` 已完成 S3 规划落盘：
  - `PLAN.md` 切换到 S3（旧版 ExoPlayer + download-only + 边下边播）
  - `TASK_BREAKDOWN.md` 新增 `T-S3-PLY-001~T-S3-VAL-007`
  - `TASK_QUEUE.md` 生成新 Ready/Blocked 队列
  - `NEXT_STEPS.md` 更新为 S3 执行入口
- 本轮决策确认：
  - `minSdk=17` 红线不可动
  - 播放链路改为 download-only
  - 使用 `StyledPlayerView`
  - 缓存保留最近 20 首
- 说明: 本地环境缺少 `gradle/gradlew.bat`，编译型验证需走 CI 或设备实测。

## Local Gemini Delta (Uncommitted)
- 工作区检测到 Gemini 本地改动（未提交）：
  - `MainActivity.kt`：新增“启动/进入 Queue 页自动刷新推荐”逻辑与并发/冷却保护。
  - `activity_main.xml`：导航 Home 按钮默认样式调整为新导航激活态资源。
  - `colors.xml` + 多个 drawable：切换到 glass 视觉参数（边框、透明面板、渐变）。
  - 新增 `button_nav_active.xml`、`button_nav_inactive.xml`。
  - `strings.xml`：新增 `app_name_version` 标识。
- 当前状态：仅完成差异汇总与 `.ai` 回写，尚未对这批改动做提交或回归结论。

## Recommended Next Action
1. 先执行 `T-S3-UI-004`：接入 `StyledPlayerView` 并保持现有交互壳稳定。
2. 紧接执行 `T-S3-LOG-005`：按既定口径清理登录/加载敏感与冗余日志。
3. 再执行 `T-S3-CACHE-006`，准备 `T-S3-VAL-007` 实机回归。

## Read First In New Session
1. `.ai/context/PLAN.md`
2. `.ai/context/TASK_BREAKDOWN.md`
3. `.ai/context/TASK_QUEUE.md`
4. `.ai/context/NEXT_STEPS.md`
5. `.ai/context/HANDOFF.md`
