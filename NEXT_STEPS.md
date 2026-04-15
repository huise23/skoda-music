# NEXT_STEPS

Last Updated: 2026-04-15 09:33:40

## Priority
- [ ] M-001: 按 `docs/CI_SIGNING_RELEASE_RUNBOOK.md` 完成签名 Secrets 配置与手动发布验收

## Delegatable To AI
- [ ] 维护状态文件并生成 handoff 摘要

## Backlog
- [ ] （空）

## Requires Manual Confirmation
- [ ] 在仓库 Secrets 中配置 Android 签名参数（`ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`）
- [ ] 手动触发 `Package MVP` 工作流并设置 `publish_release=true`
- [ ] 在 GitHub Releases 页面确认签名产物上传成功
- [ ] 系统首页音乐卡片是否支持调用三方播放器
- [ ] 车机目标分辨率范围（仅横屏已确认）
- [ ] 歌词远程失败回退策略（是否回退过期缓存，是否快速重试）
