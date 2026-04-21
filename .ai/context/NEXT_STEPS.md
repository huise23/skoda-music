# NEXT_STEPS

Last Updated: 2026-04-21 18:05

## Highest Priority
- [ ] 执行 `T-S3-VAL-012`：API17 实机专项回归与证据回填（优选 IP + 30s 调度）。

## Immediate Actions
- [ ] 基于 `T-S3-UI-013` 新增回归：验证 `code=4003` 等解码失败自动切歌是否稳定，避免重复跳歌/死循环。
- [ ] 验证 SeekBar 拖动定位：拖动中 UI 不抖动、松手后定位生效、失败时反馈正确。
- [ ] 按检查口径覆盖首播、弱网、连续切歌、临近结束预下载、下一曲衔接至少 5 轮。

## Pending Confirmation
- [ ] `T-BLK-001` 系统首页音乐卡片第三方入口能力确认。
- [ ] `B-LRC-001` 歌词失败回退策略口径确认。

## Blocked By Human Device
- [ ] `T-S3-VAL-012` API17 实机专项回归与证据回填。

## Deferred
- [ ] IPv6 相关链路（明确不做，车机不支持）。
- [ ] 多协议支持（Jellyfin/Subsonic）。
- [ ] 完整离线下载管理与本地媒体库体系化改造。
