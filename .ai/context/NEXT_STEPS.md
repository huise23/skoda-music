# NEXT_STEPS

Last Updated: 2026-04-26

## Highest Priority
- [ ] 车机联合验证 `T-S4-MEDIA-018 + T-S4-RESUME-020`：确认后台方向盘按键与熄火/休眠续播恢复。

## Immediate Actions
- [ ] 继续 `T-S4-ARCH-017`：将播放真源从 `MainActivity` 迁移到 `PlaybackService`。
- [ ] 补齐 `T-S4-RESUME-020` 二阶段：服务进程重建场景的自动恢复闭环（当前为 Activity 侧恢复）。
- [ ] 继续 `T-S4-OVL-019`：完成浮窗权限与显示策略的车机联调。
- [ ] 继续 `T-S4-NOTIFY-021`：通知三键在后台长稳验证（含重复点击/高频切歌）。

## Validation Focus (Car Head Unit)
- [ ] 后台播放稳定性（连续切应用、长时间播放）。
- [ ] 方向盘后台 `上一曲/下一曲/播放暂停` 一致性。
- [ ] 浮窗显示规则：播放/暂停可见；手动关闭后“进应用再切出”再显示。
- [ ] 熄火/休眠恢复后的自动续播。

## Backlog (Confirmed by User)
- [ ] 长标题滚动异常修复。
- [ ] 删除入口迁移到主屏（“不好听立即删”）。
- [ ] 均衡器/音效优化。
