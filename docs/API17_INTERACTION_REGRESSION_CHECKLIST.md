# API17 Interaction Regression Checklist (S4)

Last Updated: 2026-04-29  
Scope: `T-S4-VAL-032` (module `M-S4-VALID-004`)

## Purpose
用于 Android `4.2.2`（API 17）车机实机回归，统一 S4 阶段验收口径：
- 后台服务常驻与前后台一致性
- 方向盘/通知/浮窗控制链路
- 熄火/休眠恢复自动续播
- 更新检测与下载安装触发链路
- 关键事件与日志证据回传

## 1. Preconditions
- 设备: Android `4.2.2`（API 17），目标车机分辨率 `1024x600`。
- 构建: 标注 commit/build（建议 `master@6ed0fca` 或更新构建）。
- 网络: 可访问 Emby；可访问 GitHub（允许镜像回退）。
- 账号: 可用 Emby 账号。
- 开关: 允许前台服务通知、允许悬浮窗权限。

## 2. Scope Boundaries
- In Scope:
  - `download-only` 播放链路可用性。
  - 后台命令入口一致性（`notification/overlay/media_button/audio_focus`）。
  - 浮窗策略（播放/暂停均显示；手动关闭后“进应用再切出”重显）。
  - 恢复链路（熄火/休眠恢复后自动续播）。
  - 更新链路（检查 -> 下载 -> 安装触发）。
- Out of Scope:
  - 新需求（长标题滚动/主屏删除入口/均衡器）
  - 静默安装/root 安装

## 3. Checklist

### A. Build / Install / Launch Baseline
- [ ] A1 安装 APK 成功，无“解析包错误/版本过低”。
- [ ] A2 启动成功，无崩溃；首页左上角构建号 `#versionCode` 可见。
- [ ] A3 首帧后 UI 可交互，主按钮（Play/Pause/Next）可点击。

### B. Emby & Playback Baseline (`download-only`)
- [ ] B1 Emby 测试连接成功并能加载推荐/回退列表。
- [ ] B2 点击 Play 后进入可听播放，状态切换正确（buffering -> playing）。
- [ ] B3 Pause/Resume 可用；Next 可稳定切歌。
- [ ] B4 当前曲下载失败/缓存播放失败时自动切下一曲，不停在失败曲目。
- [ ] B5 连续切歌 10 次无崩溃；日志无持续异常刷屏。

### C. Background Command Matrix（S4 核心）
按 `docs/S4_BACKGROUND_COMMAND_MATRIX.md` 执行并回填。
- [ ] C1 `notification` 来源：`Prev / PlayPause / Next` 均可用。
- [ ] C2 `overlay` 来源：`Prev / PlayPause / Next` 均可用。
- [ ] C3 `media_button` 来源（方向盘或等效按键）：`Prev / PlayPause / Next` 均可用。
- [ ] C4 `audio_focus` 来源：后台失焦触发 pause 行为符合预期。
- [ ] C5 关键日志包含：
  - `service cmd result action=<...> source=<...> handled=<...> detail=<...>`

### D. Overlay Policy Regression
- [ ] D1 播放中显示浮窗（含歌名 + 三键）。
- [ ] D2 暂停中仍显示浮窗。
- [ ] D3 手动关闭浮窗后，不应立即自动重显。
- [ ] D4 执行“进入应用 -> 再切出”后浮窗重显。
- [ ] D5 浮窗歌名点击可拉起应用前台。
- [ ] D6 浮窗可拖动；重新显示后位置保持上次拖动结果。

### E. Resume / Recovery (Ignition-Sleep)
- [ ] E1 正在播放时进入熄火/休眠或等效场景后恢复，满足条件可自动续播。
- [ ] E2 续播后进度恢复合理（同曲目恢复，不错误 seek 到其他曲目）。
- [ ] E3 恢复失败场景不崩溃，并有可诊断日志（会话失效/网络失败）。

### F. Update Check / Download / Install Trigger
- [ ] F1 冷启动自动检测遵循节流（成功 24h，失败 30min），不阻断主流程。
- [ ] F2 设置页“检查更新”可手动触发并展示状态（最新/有新版本/失败）。
- [ ] F3 发现新版本后可下载 APK（镜像优先 + 官方回退）。
- [ ] F4 下载完成可触发系统安装器。
- [ ] F5 更新失败时可看到结构化失败信息（`failed_stage/failed_url/attempt_urls`）。

### G. Observability Evidence
- [ ] G1 `SkodaPostHog` 可见关键上报日志（含 `capture ok event=...`）。
- [ ] G2 关键节点至少覆盖：
  - `app_start/app_ready`
  - `play_start/play_success/playback_failed`
  - `background_command_received/background_command_result`
  - `update_check_* / update_download_* / update_install_*`
- [ ] G3 敏感字段未明文上报（token/password/response body 等）。

### H. Failure & Degrade Path
- [ ] H1 Emby 配置错误时有清晰失败反馈，不崩溃。
- [ ] H2 网络断开时播放/更新失败路径可见，恢复网络后可继续操作。
- [ ] H3 浮窗权限不可用时，通知控制条仍可兜底。

## 4. Risk Control & Acceptance Checklist (Section 4)

### 4.1 Risk Gates（任一命中即 Blocker）
- [ ] R1 后台命令矩阵出现“多数 handled=false 且不可复现定位”。
- [ ] R2 浮窗策略不符合已确认规则（关闭后乱重显或无法重显）。
- [ ] R3 熄火/休眠恢复后无法自动续播且无可诊断降级路径。
- [ ] R4 更新链路在主流网络场景下持续失败且无回退解释。
- [ ] R5 回归过程中出现崩溃/ANR/连续卡死。

### 4.2 Evidence Minimum（最小回传集）
- [ ] EVD1 设备信息 + 构建号（`#versionCode`）。
- [ ] EVD2 背景命令矩阵表（四来源）。
- [ ] EVD3 至少 1 条失败样本（含复现步骤 + 日志关键片段）。
- [ ] EVD4 更新链路样本（至少 1 次检测结果；若失败附 `failed_stage`）。
- [ ] EVD5 至少 1 份截图或短视频说明关键现象。

### 4.3 Acceptance Decision
- `PASS`: 无 Blocker，且 A~H 关键项通过。
- `PASS with Risks`: 无 Blocker，但存在可接受风险并已有追踪项。
- `FAIL`: 命中任一 Blocker，或关键链路不可复现/不可诊断。

## 5. Result Template
每台设备回传一份，可直接复制填写。

```md
### Device Report
- Device: <品牌/型号>
- Android: 4.2.2 (API 17)
- APK: <文件名/commit/build>
- Build Badge: <#versionCode>
- Test Time: <YYYY-MM-DD HH:mm>
- Tester: <姓名或角色>

### Group Result
- A Build/Launch: PASS/FAIL
- B Playback Baseline: PASS/FAIL
- C Background Matrix: PASS/FAIL
- D Overlay Policy: PASS/FAIL
- E Resume/Recovery: PASS/FAIL
- F Update Chain: PASS/FAIL
- G Observability: PASS/FAIL
- H Failure/Degrade: PASS/FAIL

### Section 4 Decision
- Risk Gate Triggered: YES/NO
- Triggered Risk IDs: <R1/R2/... 或 None>
- Evidence Minimum Complete: YES/NO
- Final Decision: PASS / PASS with Risks / FAIL

### Failed Items
1. <条目ID + 现象>
2. <条目ID + 现象>

### Key Logs / Evidence
- command_result: <action/source/handled/detail>
- playback_error: <error_code/stage/request_id>
- update_failed: <failed_stage/failed_url/attempt_urls>
- posthog: <capture ok 或失败样本>
- screenshot/video: <说明或路径>

### Conclusion
- Blocker: YES/NO
- Must Fix Before Next Round: <列表>
- Notes: <补充>
```
