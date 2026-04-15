# NEXT_STEPS

Last Updated: 2026-04-15 22:41:00

## Highest Priority
- [ ] 按 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` 执行一轮 Android 4.2.2 实机回归并回传结果。

## Immediate Actions
- [ ] 触发一次 CI 打包，验证 `T-S1-001~005 + T-S1-004` 改动后 APK 构建稳定。
- [ ] 在 Android 4.2.2 实机验证 Emby 拉曲目 + Next(native) + 播放状态切换。
- [ ] 验证 Emby 登录信息重启后自动回填（BaseURL/用户名/密码）。
- [ ] 对比 `tracks-sample` 日志与界面显示，确认“非中文曲名”来自服务端元数据还是客户端解码问题。
- [ ] 实机验证真实流播放链路：`登录 -> Play -> Pause -> Next(播放态)`。
- [ ] 实机核对 `statusText` 中的来源标识（`recommended` / `library-fallback`）是否符合服务端返回。

## Pending Confirmation
- [ ] 实机安装并回传 Android 4.2.2 行为结果（安装/启动/前后台/交互）。
- [ ] 系统首页音乐卡片第三方入口能力确认。
- [ ] 歌词失败回退策略口径确认。

## Deferred
- [ ] 复杂视觉动效与大规模 UI 重构（非当前阶段重点）。
