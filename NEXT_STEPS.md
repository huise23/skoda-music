# NEXT_STEPS

Last Updated: 2026-04-15 10:34:12

## Priority
- [ ] M-002: 在目标车机实机安装 `mvp-r17` 并记录兼容性结果（安装/启动/前后台）

## Delegatable To AI
- [ ] 维护状态文件并生成 handoff 摘要

## Backlog
- [ ] （空）

## Requires Manual Confirmation
- [ ] 在目标车机安装 `skoda-music-mvp-signed.apk`（release: `mvp-r17`）
- [ ] 记录是否可启动、是否闪退、是否出现权限/解析错误
- [ ] 反馈设备 Android 版本与安装结果，决定是否下调 `minSdk`
- [ ] 系统首页音乐卡片是否支持调用三方播放器
- [ ] 车机目标分辨率范围（仅横屏已确认）
- [ ] 歌词远程失败回退策略（是否回退过期缓存，是否快速重试）
