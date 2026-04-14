# CONFIG_ERROR_POLICY

Last Updated: 2026-04-14 13:41:25
Status: Draft v2 (T-007)

## Purpose
定义配置加载阶段的零阻断策略。  
核心规则：启动阶段不阻断，配置异常由启动自愈修复（仅内存态），并以聚合 toast 提示。

## Core Policy
- 启动阶段不因配置问题失败。
- 配置问题在启动时自动回退为空值或默认值。
- 修复结果仅在内存生效，不覆写配置文件。
- 启动后显示一条聚合 toast，提示对应功能配置异常已回退默认值。

## Two-Stage Model

### Stage A: Boot Self-Heal (Always Continue)
适用问题：
- 配置文件不存在
- 配置文件解析失败
- 字段缺失
- 字段值非法（格式、范围、枚举）

处理动作：
- 使用默认模板或字段默认值创建运行时配置对象。
- 记录修复字段与受影响功能。
- 应用继续启动，不中断。

### Stage B: Save Gate (Before Persist)
配置页点击保存前执行测试门槛：
- Emby 连通 + 鉴权通过 -> 允许保存
- Emby 测试失败 -> 禁止保存
- LrcApi 测试失败 -> 仅告警，不阻止保存

失败处理：
- 保留用户输入
- 禁止保存按钮（仅 Emby 未通过时）
- 显示可修复原因

## Self-Heal Rules
- 缺失字段: 回退默认值或空值（按 `CONFIG_SPEC` 定义）。
- 非法字段: 回退默认值。
- 敏感字段（如 token）无有效值时保留空值，等待用户在配置页填写。
- 环境变量非法覆盖值忽略，保留文件值或默认值。

## Toast and UX Contract
- 启动阶段只弹一条聚合 toast。
- 推荐文案：
  - `部分功能配置有问题，已设置为默认值`
- toast 不展示敏感信息。
- 详细修复项在日志和设置页中查看（设置页明细待实现）。

## Capability Impact (Runtime)
- Emby 配置未就绪: 播放相关功能不可用，但应用可启动。
- LrcApi 配置未就绪: 歌词远程补全不可用，不影响主播放链路。
- UI/日志等可选项异常: 回退默认值后继续运行。

## Logging and Security
- 记录修复摘要与字段路径，级别 `warn`。
- 所有敏感字段脱敏输出（`***`）。
- 不打印明文 `access_token`、用户敏感标识。

## Example Cases
- 配置文件不存在 -> 使用默认运行时配置启动 + toast
- `emby.base_url` 为空 -> 回退为空值并标记播放能力未就绪 + toast
- `ui.motion_level = "ultra"` -> 回退 `low` + toast
- `lyrics.fetch_timeout_ms = -10` -> 回退 `2000` + toast

## Confirmed
- 设置页展示“本次启动自愈字段明细”。

## To Confirm
- toast 文案是否需要本地化多语言版本。
