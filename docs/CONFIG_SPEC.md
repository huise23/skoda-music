# CONFIG_SPEC

Last Updated: 2026-04-14 11:19:23
Status: Draft v1 (用于骨架阶段)

See also: `docs/CONFIG_LOADER_INTERFACE.md` (T-002)

## Purpose
定义首版播放器的最小配置集合，作为后续实现统一输入，不涉及功能代码。

## Scope
- Emby 连接配置
- LrcApi 连接配置
- UI 与性能策略配置
- 播放器基础行为配置
- 日志配置

## Config Source Priority
1. 本地配置文件 `config/app.config.yaml`
2. 环境变量（可选，命名约定待确认）

说明: 环境变量命名规范当前为“待确认”。

## Required Config (For Save Gate)
- `emby.base_url`: Emby 服务地址（保存前必填）
- `emby.user_id`: 用户标识（保存前必填）
- `emby.access_token`: 访问令牌（保存前必填）
- `lyrics.lrcapi.base_url`: LrcApi 地址（建议填写，测试失败不阻断保存）

说明：
- 启动阶段不阻断，缺失字段会在内存态回退为空值/默认值。
- 配置页保存阶段执行测试门槛（Emby 通过才允许保存）。

## Optional Config (with defaults)
- `emby.request_timeout_ms`: 默认 `5000`
- `lyrics.fetch_timeout_ms`: 默认 `2000`
- `lyrics.cache_ttl_days`: 默认 `30`（已确认 TTL 按天）
- `lyrics.prefer_language`: 默认 `zh`（已确认中文优先）
- `ui.theme_mode`: 默认 `dual`（深浅双主题）
- `ui.nav_layout`: 默认 `left`
- `ui.glass_intensity`: 默认 `light`
- `ui.motion_level`: 默认 `low`
- `ui.orientation`: 默认 `landscape`
- `player.streaming_only`: 默认 `true`（首版纯在线）
- `player.recommendation_mode`: 默认 `server_first`
- `controls.steering_mapping`: 默认 `media_key`
- `log.level`: 默认 `info`

## Security Notes
- 令牌和用户信息不得写入日志。
- 示例配置文件只放占位符，不放真实凭据。
- 本地配置文件建议加入忽略列表（待执行）。

## Validation Rules (v1)
- URL 字段必须是合法 `http/https`。
- 超时参数必须大于 0。
- `cache_ttl_days` 必须 >= 1。
- `theme_mode` 仅允许: `dark` `light` `dual`。
- `motion_level` 仅允许: `low` `medium`。

## To Confirm
- 环境变量命名前缀是否固定为 `SKODA_MUSIC_`。
- 是否需要区分开发/车机生产两套配置。
