# HANDOFF

Last Updated: 2026-04-21 18:05

## Project Snapshot
- 项目: `skoda-music`（Android 车机播放器）
- 当前主干: `master@55dc676`（已落盘 minSdk17 红线与日志口径）
- 当前阶段: S3 播放稳态优化（CF 优选 IPv4 + 30s 下载窗口调度）

## Current Goal
- 目标 1: 推进 `T-S3-VAL-012` 实机验证并回填证据。
- 目标 2: 回归 `T-S3-UI-013`（自动切歌与可拖动进度）在 API17 设备上的稳定性。

## Plan Hand-off
- 本轮 `ai-execution` 已完成 `T-S3-NET-009`：
  - 新增设置项 `CF Preferred Reference Domain`（仅参考，不替换 Emby 业务域名）。
  - Emby 相关请求链路（鉴权/拉库/推荐/播放/下载）统一切换到支持自定义 DNS 的 OkHttp 客户端。
  - DNS 解析策略为 IPv4-only：优先使用“参考域名解析得到的候选 IPv4”，并拼接系统 IPv4 作为回退。
  - ExoPlayer 接入 `extension-okhttp`，通过 `DefaultDataSource + OkHttpDataSource` 复用同一网络策略。
- 本轮 `ai-execution` 已完成 `T-S3-DL-010`：
  - 下载控制循环切换为 30s 窗口状态机：`MAINTAIN_CURRENT_WINDOW / FINISH_CURRENT_TRACK / PREFETCH_NEXT_WINDOW / IDLE`。
  - 非临近结束时：当前曲目 `downloadedPlayableSec < 30s` 才继续下载，`>=30s` 进入 `IDLE`（暂停下载）。
  - 临近结束（`remainingPlaySec < 30s`）时：优先补完当前曲目，完成后再预下载下一曲前 `30s`。
  - 新增并落地状态机常量：`DOWNLOAD_WINDOW_SEC=30`、`DOWNLOAD_CHUNK_BYTES`、`DOWNLOAD_CONTROLLER_IDLE_MS`。
- 本轮 `ai-execution` 已完成 `T-S3-LOG-011`：
  - 下载调度日志补齐：phase 切换日志中保留 `remainingSec/currentPlayableSec/nextPlayableSec`，并新增 `IDLE` 原因日志。
  - 下载分块日志补齐：新增 `chunk start / chunk ok / skip-completed / skip-eof`，便于定位“为什么继续/暂停下载”。
  - DNS 诊断日志补齐：新增 `cache-hit/cache-refresh`、`selected IP`、`bypass/fallback` 与系统 DNS 失败原因。
- 本轮纠偏已完成 `T-S3-RB-008`：
  - 按用户要求回滚本地错误实现代码改动。
  - 仅保留 `.ai/context` 规划与决策文档更新。
- 本轮讨论确认新口径：
  - “优选域名”仅用于获取 CF 优选节点信息，不作为业务 URL。
  - Emby 业务域名保持不变（认证/拉库/播放一致）。
  - 网络链路仅做 IPv4，不做 IPv6（v6）。
  - 下载调度采用 30s 窗口规则（当前曲目与下一曲预下载联动）。
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
- 本轮 `ai-execution` 已完成 `T-S3-UI-013`：
  - `MainActivity.kt` 扩展 `PlaybackEngine.seekTo`，并接入 `SeekBar` 拖动定位（拖动中不覆盖、松手 seek）。
  - 下载/缓存两条播放回调的 `onError` 统一走自动切歌处理，新增错误分类与去重处理。
  - Home 页重排为“封面入口 + 右侧信息 + 进度条 + Prev/Play/Next”，并新增“听推荐”入口复用现有推荐逻辑。
  - 视觉约束保持：沿用现有 glass 资源与颜色，不改主题体系。

## Local Gemini Delta (Uncommitted)
- 工作区检测到 Gemini 本地改动（未提交）：
  - `MainActivity.kt`：新增“启动/进入 Queue 页自动刷新推荐”逻辑与并发/冷却保护。
  - `activity_main.xml`：导航 Home 按钮默认样式调整为新导航激活态资源。
  - `colors.xml` + 多个 drawable：切换到 glass 视觉参数（边框、透明面板、渐变）。
  - 新增 `button_nav_active.xml`、`button_nav_inactive.xml`。
  - `strings.xml`：新增 `app_name_version` 标识。
- 当前状态：仅完成差异汇总与 `.ai` 回写，尚未对这批改动做提交或回归结论。

## Recommended Next Action
1. 执行 `T-S3-VAL-012`：按专项清单完成 API17 实机回归闭环并回填证据。

## Read First In New Session
1. `.ai/context/PLAN.md`
2. `.ai/context/TASK_BREAKDOWN.md`
3. `.ai/context/TASK_QUEUE.md`
4. `.ai/context/NEXT_STEPS.md`
5. `.ai/context/HANDOFF.md`
