# S4 Background Command Matrix

Last Updated: 2026-04-27  
Scope: `T-S4-CORE-026B` (module `M-S4-CORE-001`)

## Purpose
用于统一验证四类后台命令来源在 S4 方案下是否稳定命中执行链路，并可通过日志快速定位失败点。

## Sources
- `notification`
- `overlay`
- `media_button`
- `audio_focus`（仅 pause 触发）

## Commands
- `ACTION_CMD_PREV`
- `ACTION_CMD_PLAY_PAUSE`
- `ACTION_CMD_NEXT`
- `ACTION_CMD_PLAY`
- `ACTION_CMD_PAUSE`

## Evidence Rules
- 运行时日志需出现：`service cmd result action=<...> source=<...> handled=<true/false> detail=<...>`
- 关键字段以 `handled=true` 判定“命令链路命中”。
- 若 `handled=false`，必须记录 `detail` 原因与复现路径。

## Matrix Template
将每项填写为 `PASS / FAIL / N/A`，并附关键日志片段。

| Source | Prev | PlayPause | Next | Play | Pause | Notes |
|---|---|---|---|---|---|---|
| notification |  |  |  |  |  |  |
| overlay |  |  |  |  |  |  |
| media_button |  |  |  |  |  |  |
| audio_focus | N/A | N/A | N/A | N/A |  |  |

## Failure Classification
- `controller_unavailable`: Activity 未附着 `PlaybackControlBus`（常见于进程/生命周期窗口）。
- `controller_rejected`: 控制器收到命令但执行失败（如无可播放曲目）。
- `controller_exception:*`: 控制链路抛异常，需抓取异常类型并继续定位。
- `invalid_action`: 命令 action 无效或为空。

## Minimal Execution Sequence
1. 启动应用并确认有可播放队列。
2. 切到后台，分别触发通知、浮窗、方向盘按键命令。
3. 触发一次音频焦点丢失（后台）验证 pause 路径。
4. 记录矩阵结果与日志片段，回填 `T-S4-CORE-026B`。
