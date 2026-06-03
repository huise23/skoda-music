# NEXT_STEPS

Last Updated: 2026-06-03

## One-Line Summary
- S5 计划内可执行任务已完成：酷狗登录、内容页、点赞状态、播放 URL、100MB 缓存守卫和 API17 回归清单均已本地验证。

## Current Highest Priority
- None ready.

## Immediate Ready Tasks
- None.

## Blocked / Deferred
- `B-KG-EMBY-INGEST-001`: 点赞后将播放缓存上传到 Emby 并纳入媒体库。
  - 原因: Emby 上传并入库能力未确认。
  - 当前处理: 本阶段只记录点赞和入库阻塞状态，不执行上传。
- `T-S4-AUDIO-095`: AC83xx Native DSP 实机长播与听感验证。
  - 原因: 外部设备窗口。

## Validation Notes
- 最终本地验证已通过:
  - `git diff --check`
  - `./scripts/check_api17_guardrails.sh`
  - `gradle :app:compileDebugKotlin --no-daemon`
  - `gradle :app:assembleDebug --no-daemon`
- Review 结论:
  - 未发现阻断提交/推送的问题。
  - `KugouMusic.NET/` 保持只读参考并已加入 `.gitignore`。
  - 后续若继续扩展来源，优先拆分 `MainActivity` 中的 source UI/client 接线。
- 后续需要实机/外部验证:
  - Kugou WebApi 地址、扫码/验证码登录、session 失效、内容接口真实返回。
  - AC83xx native DSP 长播听感。
  - Emby 是否支持上传播放缓存并入库。
