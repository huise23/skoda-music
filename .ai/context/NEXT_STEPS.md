# NEXT_STEPS

Last Updated: 2026-04-17 17:03

## Highest Priority
- [ ] 推进 `T-S2-004`：执行 API17 全量回归并回填结构化证据。

## Immediate Actions
- [ ] 审核并决定是否接纳 Gemini 本地改动（自动刷新推荐 + 玻璃态 UI），确认范围是否纳入当前阶段。
- [ ] 对 Gemini 改动执行一次设备回归（重点：启动、进入 Queue 自动刷新、导航按钮可读性、字体大小）。
- [ ] 触发一次 CI 打包，验证最近两次改动（Queue 推荐替换 + 日志复制/清理 + 5.0 启动修复）无回归。
- [ ] 将“Android 5.0 启动通过”结果补充到回归记录，区分 API17 与 API21 验证口径。
- [ ] 若 API17 设备就绪，开始 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md` A-F 项逐条回填。

## Pending Confirmation
- [ ] 系统首页音乐卡片第三方入口能力确认（`T-BLK-001`）。
- [ ] 歌词失败回退策略口径确认（`B-LRC-001`）。

## Blocked By Human Device
- [ ] `T-S2-004` API17 全量回归执行与证据回填。

## Deferred
- [ ] 多协议支持（Jellyfin/Subsonic）。
- [ ] 完整离线下载管理与本地媒体库体系化改造。
