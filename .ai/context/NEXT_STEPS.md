# NEXT_STEPS

Last Updated: 2026-04-23 10:20

## Highest Priority
- [ ] 完成 `T-S3-VAL-014` + `T-S3-VAL-015`，为 `T-S3-VAL-012` 提供可执行、可回填的统一入口。

## Immediate Actions
- [ ] 更新 `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`，新增 S3 场景：
  - download-only 与 30s 下载窗口行为
  - `code=4003` 等解码失败自动切歌
  - SeekBar 拖动定位与状态一致性
  - 2026-04-22 UI/歌词提交簇影响点
- [ ] 固化 Device Report 模板，约束回传字段：设备信息、APK 标识、分组结果、失败复现、日志片段、证据链接。
- [ ] 与执行人确认 API17 设备档期与网络条件，准备 `T-S3-VAL-012` 执行窗口。

## Pending Confirmation
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认。
- [ ] `B-LRC-001` 歌词失败回退策略口径确认。

## Blocked By Human Device
- [ ] `T-S3-VAL-012` API17 实机专项回归执行与证据回填。

## Deferred
- [ ] IPv6 相关链路（明确不做，车机不支持）。
- [ ] 多协议支持（Jellyfin/Subsonic）。
- [ ] 完整离线下载管理与本地媒体库体系化改造。
- [ ] 媒体会话服务化（方案3）：`MediaBrowserServiceCompat + 前台通知 + 后台媒体会话`，用于进一步提升方向盘按键兼容性。
