# PostHog Query & AI Export Template (S4)

Last Updated: 2026-05-08
Module: `M-S4-OBS-006`
Tasks: `T-S4-OBS-038`

## Purpose
- 固化 `S4` 阶段 PostHog 在线查询与 AI 导出流程。
- 在车机或可用网络环境下，按固定步骤产出可复盘证据。
- 该文档是执行模板，不替代 `POSTHOG_EVENT_DICTIONARY.md` 和 `POSTHOG_INSTRUMENTATION_PLAN.md`。

## Preconditions
- 已部署包含 `PostHogTracker` 的构建（当前基线：`master@6ed0fca`）。
- PostHog 项目配置正确（见 `docs/POSTHOG_CONFIG_CHECKLIST.md`）。
- 至少完成一次目标场景操作（冷启动/播放/后台按键/更新检查等）。

## Required Filters
- 时间窗口：最近 `24h`（若事件稀疏可扩到 `7d`）。
- 环境：`environment=prod`（若后续启用 dev 项目，按实际调整）。
- 构建：优先按 `build_number` 过滤目标版本。

## Query Checklist

### Q1. Session Timeline (核心)
- 目标：确认单条 `session_id` 事件链是否完整。
- 过滤建议：
  - `session_id = <target_session_id>`
  - `event in [app_start, app_ready, play_start, play_success, playback_failed, background_command_received, background_command_result, resume_restore_attempt, resume_restore_success, resume_restore_failed]`
- 通过标准：
  - 能按时间顺序看到完整链路。
  - 关键失败场景存在 `stage + error_code` 字段。

### Q2. Failure Distribution by `error_code`
- 目标：聚合最近 `7d` 的主要失败类型。
- 过滤建议：
  - `event in [playback_failed, network_error, decoder_error, resume_restore_failed, update_check_failed, update_download_failed, update_install_failed]`
- 通过标准：
  - 可得到 Top N `error_code`，且无大面积空值。

### Q3. Failure Distribution by `stage`
- 目标：定位失败集中在哪个业务阶段。
- 过滤建议：
  - 同 Q2 事件集合
  - breakdown by `stage`
- 通过标准：
  - `stage` 有可聚合值（非全空、非随机文本）。

### Q4. Version Comparison
- 目标：比较不同构建的故障差异。
- 过滤建议：
  - breakdown by `app_version` / `build_number`
- 通过标准：
  - 能区分目标构建与历史构建，支持回归对比。

## AI Export Payload Template

将单条 `session_id` 导出为 JSON（或 CSV 后转 JSON）并整理为下述结构：

```json
{
  "session_meta": {
    "session_id": "<session_id>",
    "app_version": "<app_version>",
    "build_number": "<build_number>",
    "environment": "prod",
    "time_range": "<ISO8601 start/end>"
  },
  "events": [
    {
      "ts": 0,
      "event": "app_start",
      "stage": "",
      "error_code": "",
      "source": "ui",
      "track_id": "",
      "duration_ms": 0,
      "handled": "",
      "detail": ""
    }
  ]
}
```

## Export Field Rules
- 必选字段：
  - `event`
  - `event_ts_client_ms`（映射到 `ts`）
  - `session_id`
  - `app_version`
  - `build_number`
- 条件字段（有则保留）：
  - `stage`
  - `error_code`
  - `source`
  - `track_id`
  - `duration_ms`
  - `handled`
  - `detail`
- 排序规则：
  - 按 `event_ts_client_ms` 升序。
- 隐私规则：
  - 不导出 token/password/header/response_body 原文。

## Evidence Attachment Template
- 场景名称：
- 设备与系统版本：
- 构建号：
- 查询时间窗口（UTC）：
- session_id：
- 关键事件链：
- 主要 error_code / stage：
- 结论（PASS / FAIL / BLOCKER）：
- 备注（若 FAIL，附最短复现步骤）：

## Done Criteria for `T-S4-OBS-038` (Execution Layer)
- 至少导出 1 条完整 `session_id` 时间线。
- 已产出 failure by `error_code` 与 failure by `stage` 聚合截图或导出结果。
- 导出 payload 可直接用于 AI 复盘，字段完整且去敏。
