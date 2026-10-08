# 车机端 ROOT 语音接管与一键还原指南 (Car Root Voice Takeover & Revert Guide)

Last Updated: 2026-10-08

## 1. 方案背景与架构原理

斯柯达 MQB 车机（杰发科技 AutoChips AC8317 平台，Android 4.2.2 / API 17，YunOS 3.0.1）：
- **方向盘按键机制**：方向盘语音按键由 MCU 捕获后，在系统层分发为 `com.yecon.action.VOICE_START` 广播，默认被车机预装的旧版科大讯飞语音（`/system/app/SpeechClient.apk`，包名 `com.iflytek.autofly`）强行独占截获。
- **接管原理**：在车机 Root 环境下停用 `com.iflytek.autofly`，车机内的 `CarVoiceButtonReceiver` 即可高优先级捕获该事件，并通过局域网（车机连接手机热点，网关 IP `192.168.43.1:8999`）秒级唤醒手机端“一隅”语音助手。
- **双向联动**：
  - **音乐控制**：手机一隅识别语音后，向车机 `http://<car_ip>:8088/api/music/control` 下发切歌/搜索/播放指令，车机 `skoda-music` 执行酷狗曲库搜索与播放。
  - **高德导航**：手机一隅识别导航意图后，向车机 `http://<car_ip>:8088/api/navi` 发送目的地，车机 `AmapAutoBridge` 直接调用预装的高德车机版（`com.autonavi.amapauto`）标准广播与 URI 算路导航。

---

## 2. 车机端 ROOT 操作步骤

车机通过 USB 或局域网开启 ADB 连接后，在电脑或终端执行以下命令：

### 第一步：获取 Root 权限并停用原厂讯飞
```bash
# 进入 root shell
adb root
adb shell

# 停用系统预装的旧讯飞语音助手
pm disable com.iflytek.autofly
pm disable com.jsbd.vradapter

# 验证是否已停用
pm list packages -d | grep iflytek
```
> **效果**：此时按下方向盘语音键，车机屏幕不再弹出原厂讯飞语音界面，`com.yecon.action.VOICE_START` 广播将直接由 `skoda-music` 的 `CarVoiceButtonReceiver` 接收并触发手机一隅。

---

## 3. 卖车时的无损还原操作（双重保障）

在卖车或保养时，可随时 100% 恢复车机出厂状态：

### 方式 A：命令级一秒还原（推荐）
连接 ADB 执行以下命令，原厂讯飞瞬间恢复全部功能：
```bash
adb shell pm enable com.iflytek.autofly
adb shell pm enable com.jsbd.vradapter
```
然后在车机设置中卸载 `skoda-music`，车机完全恢复原貌。

### 方式 B：出厂官方线刷恢复（彻底抹除 Root 痕迹）
当前仓库中已完整保留了该车机的官方出厂线刷镜像：
- 镜像目录：`3.0.1-R-20210524.1733/`
- 关键固件：`scatter.mmcboot.ext4.xml`、`system.img.ext4`、`uImage`、`Preloader`
- 操作：将官方固件放入 SD 卡或通过刷机工具线刷，车机将完全重写所有分区，恢复到 2021-05-24 出厂初始镜像，任何 Root 痕迹与第三方应用都将被彻底清空。

---

## 4. 端口与通信协议备忘

| 端 | 端口 | 协议 | 路径 / 格式 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| **手机一隅** | `8999` | HTTP GET | `/api/agent/wake` | 车机通知手机启动倾听 |
| **车机 skoda-music** | `8088` | HTTP POST | `/api/music/control` | `{"action":"PLAY|PAUSE|NEXT|PREV|SEARCH_PLAY","keyword":"..."}` |
| **车机 skoda-music** | `8088` | HTTP POST | `/api/navi` | `{"type":"DEST|HOME|CORP|NEARBY","dest":"..."}` |
| **车机 skoda-music** | `8088` | HTTP GET | `/api/status` | 获取当前车机播放曲目与状态 |
