# API17 Interaction Regression Checklist

Last Updated: 2026-04-15 22:41:00  
Scope: `T-S1-006`

## Purpose
用于 Android `4.2.2`（API 17）实机回归，统一安装、启动、前台交互、播放链路与前后台切换验证口径。

## Preconditions
- 设备: Android `4.2.2`，`1024x600`（车机目标环境）。
- 安装包: CI 产物中的 release APK（signed/unsigned 均可）。
- Emby 账号: 可用 `BaseURL + 用户名 + 密码`。
- 网络: 与 Emby 服务互通。

## Checklist

### A. Install & Launch
- [ ] A1 安装 APK 成功，无“解析包错误/版本过低”。
- [ ] A2 首次启动成功，不崩溃。
- [ ] A3 主页显示交互 UI（非 “installed successfully” 占位页）。

### B. Emby Login & Persistence
- [ ] B1 输入 `BaseURL/用户名/密码` 后点击 `Test Emby & Load Tracks` 成功。
- [ ] B2 `Emby status` 显示 connected，且包含曲目数量与来源（`recommended` 或 `library-fallback`）。
- [ ] B3 退出并重启 App，登录信息自动回填（包含密码）。

### C. Track Source & Text Rendering
- [ ] C1 `Action feedback` 出现 `tracks-source=` 与 `tracks-sample=` 日志片段。
- [ ] C2 `Current Track` 显示为 Emby 返回曲名（非本地 mock）。
- [ ] C3 中文曲名显示正常（无乱码/问号/方块字）。

### D. Real Playback Loop
- [ ] D1 点击 `Play` 后进入 buffering/streaming（或 cached）状态。
- [ ] D2 能听到实际音频输出。
- [ ] D3 点击 `Pause` 后播放停止，状态切为 paused。
- [ ] D4 再次 `Play` 能恢复播放。
- [ ] D5 播放态点击 `Next`，`Current Track` 变更且继续播放下一首。
- [ ] D6 队尾点击 `Next`，出现 end-of-queue 提示且不崩溃。

### E. Foreground / Background
- [ ] E1 播放时切到后台 30 秒，App 不崩溃。
- [ ] E2 从后台回到前台，UI 状态与实际播放状态一致。
- [ ] E3 连续执行“前台 -> 后台 -> 前台”3 次，交互仍可用。

### F. Failure Path
- [ ] F1 Emby 地址错误时，状态提示失败且不崩溃。
- [ ] F2 网络断开时，播放失败有可见反馈（stream/download fallback 错误信息）。
- [ ] F3 失败后恢复网络并重新测试，可重新进入可播放状态。

## Result Template
每台设备回传一份，可直接复制填写。

```md
### Device Report
- Device: <品牌/型号>
- Android: 4.2.2 (API 17)
- APK: <文件名/commit/run>
- Emby: <服务版本，可选>
- Test Time: <YYYY-MM-DD HH:mm>

### Checklist Result
- A Install & Launch: PASS/FAIL
- B Emby Login & Persistence: PASS/FAIL
- C Track Source & Text Rendering: PASS/FAIL
- D Real Playback Loop: PASS/FAIL
- E Foreground / Background: PASS/FAIL
- F Failure Path: PASS/FAIL

### Failed Items
1. <条目ID + 现象>
2. <条目ID + 现象>

### Logs / Evidence
- statusText: <原文>
- actionFeedback: <关键片段>
- tracks-source: <recommended/library-fallback>
- tracks-sample: <最多3条>
- screenshot/video: <可选链接或说明>

### Conclusion
- Overall: PASS/FAIL
- Blocker: YES/NO
- Notes: <补充说明>
```
