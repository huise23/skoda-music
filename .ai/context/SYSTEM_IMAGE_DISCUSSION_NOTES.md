# SYSTEM_IMAGE_DISCUSSION_NOTES

Last Updated: 2026-06-09

## Purpose
- 记录系统镜像只读分析、方向盘语音键、系统首页音乐卡片、高德车机调用等后续讨论材料。
- 本文件不是当前 S5 小改动执行 scope；除非用户后续明确确认，否则不进入 planning / execution。

## Image Source
- 目录: `3.0.1-R-20210524.1733/`
- `yc8317.img` 为 `0` 字节空文件。
- 实际可分析文件包括:
  - `system.img.ext4`
  - `xfdata.img.ext4`
  - `ramdisk.gz`
  - `target_files.zip`
  - `scatter.mmcboot.ext4.xml`
  - `uImage`
  - `83XX_Preloader_realchip_sd.bin`

## Confirmed Platform Facts
- 系统: YunOS `3.0.1` / Android `4.2.2` / API `17`
- 平台: AC8317 / AC83xx
- 车型/平台标识:
  - `ro.yunos.model=NAVIGATION_SKODA_MQB`
  - `ro.product.model=skoda_mqb`
  - `ro.yunos.platform.name=navigation.skoda.mqb`
- ABI: `armeabi-v7a`, `armeabi`
- 显示/项目基线仍按 1024x600 横屏、API17 处理。

## Direction Wheel Keys
- 用户已于 2026-06-09 确认：当前版本方向盘上下曲全局可用。
- 镜像线索:
  - `/system/etc/yecon_keymap.csv` 存在 `114 -> 163`、`115 -> 165`。
  - Android keycode `163` / `165` 对应 `MEDIA_NEXT` / `MEDIA_PREVIOUS`。
- 结论:
  - 上下曲可作为已验证能力，不再作为阻塞项。
  - 后续只需在回归中保持验证，不应重复扩大实现。

## Voice Button / Voice Assistant Notes
- 镜像存在:
  - `SpeechClient.apk`
  - `SpeechAdapt.apk`
  - `yecon.jar`
  - `autochips.jar`
  - `libyecon.so`
- Yecon/framework 线索:
  - `com.yecon.action.VOICE_START`
  - `com.yecon.action.VOICE_STOP`
  - `persist.sys.voice_startup`
  - `COMMON_SAVE_DATA_KEY_VOICE_STATUS`
  - `sendVoiceIndentStatus`
  - `setVoiceVolume`
- 初步判断:
  - 方向盘语音键大概率先由 Yecon/系统语音捕获，再启动讯飞语音链路。
  - 第三方 app 能否覆盖响应，取决于实机上该事件是否还会以 key event 或普通 broadcast 分发。
  - 完全替换系统语音可能需要系统权限、替换/禁用系统语音 app、或修改 keymap，不建议直接进入应用层实现。
- 推荐后续 spike:
  - 按方向盘语音键时抓 `getevent -l`。
  - 抓 `logcat` 中 `VOICE_START`、`VOICE_STOP`、`SpeechClient`、`Yecon`、`keyCode`。
  - 在本 app 中临时注册 `com.yecon.action.VOICE_START` receiver，验证是否能收到。
  - 若能收到，再评估“本 app 活跃/正在播放时接管音乐语音命令；非音乐意图转发系统/高德”。

## System Home / Music Card Notes
- 系统首页 app 线索:
  - `uShell.apk`
  - 首页包含音乐卡片资源和 `com/yunos4car/bmmediaremoute/player/IBmPlayer.aidl`。
- `IBmPlayer.aidl` 暴露能力包括:
  - `playPre()`
  - `playNext()`
  - `startPlay()`
  - `pausePlay()`
  - `stopPlay()`
  - `seekTo(int ms)`
  - `getDuration()`
  - `getPlayCurrPosition()`
  - `isPlaying()`
  - `getPlayerStatus()`
  - `registePlayerListener(...)`
  - `openAppAndContinuePlay()`
  - `openApp()`
- 初步判断:
  - 系统首页音乐卡片可能不是普通 Android notification / media session，而是 Banma/YunOS 的媒体远程 AIDL 协议。
  - 是否支持第三方 app 接入仍需实机验证绑定条件、包名/签名白名单、服务 action。
- 推荐后续 spike:
  - 从 `uShell` logcat 观察绑定目标。
  - 用 `pm dump` / `dumpsys package` 查看内置音乐、酷我、考拉等服务声明。
  - 如无白名单限制，可尝试在本 app 暴露最小 `IBmPlayer` service stub。

## Amap / Gaode Car App Notes
- 高德车机包:
  - `Auto_V2.7.8.24225_C04010328001_MQB.apk`
  - 包名线索: `com.autonavi.amapauto`
- 镜像内线索:
  - `AutoProtocolManager`
  - `specialPoiNav`
  - `selectRoute`
  - `StartNavi`
  - `stopNavi`
  - `GuideService`
  - `NaviManager`
- 初步判断:
  - 高德车机具备 POI/路线/导航内部能力。
  - 这些能力是否对第三方公开，不能仅凭字符串确认，必须实机验证 intent / service / exported 状态。
- 推荐后续设计方向:
  - 本 app 语音助手只识别音乐意图。
  - 地图位置、导航、附近搜索、路线类意图交给高德车机。
  - 优先测试标准 `geo:` / `google.navigation:` / `amapauto` 相关 intent，再考虑私有接口。

## Not In Current Scope
- 不实现完整语音助手。
- 不抢占或替换系统语音链路。
- 不接入系统首页音乐卡片 AIDL。
- 不调用高德私有导航接口。
- 不修改系统镜像、keymap、系统 app 或 ROM 文件。

## Suggested Future Tasks
- `T-VOICE-SPIKE-001`: 方向盘语音键事件分发验证。
- `T-VOICE-SPIKE-002`: 应用内音乐语音命令最小可行性验证。
- `T-AMAP-SPIKE-001`: 高德车机标准/私有 intent 拉起和导航参数验证。
- `T-HOME-CARD-SPIKE-001`: 系统首页音乐卡片 `IBmPlayer` 绑定条件验证。
