# NEXT_STEPS

Last Updated: 2026-04-15 14:22:45

## Priority
- [ ] M-005: 复跑 `Package MVP`，确认 `Verify APK minSdk is API 17` 步骤通过
- [ ] M-004: 按 `docs/CI_SIGNING_RELEASE_RUNBOOK.md` 触发包含 `minSdk=17` 的新 APK/AAB（建议 tag: `mvp-r18`）
- [ ] M-002: 在 Android `4.2.2` 目标车机实机安装新版本并记录结果（安装提示/启动/前后台）

## Delegatable To AI
- [ ] 维护状态文件并生成 handoff 摘要
- [ ] 校对 `apk_badging.txt` 中 `minSdkVersion` 是否为 `17`

## Backlog
- [ ] （空）

## Requires Manual Confirmation
- [ ] 在目标车机安装 `skoda-music-mvp-signed.apk`（release: `mvp-r18` 或最新）
- [ ] 记录是否可启动、是否闪退、是否出现“解析包时出现问题/版本过低”等安装错误
- [ ] 系统首页音乐卡片是否支持调用三方播放器
- [ ] 歌词远程失败回退策略（是否回退过期缓存，是否快速重试）
