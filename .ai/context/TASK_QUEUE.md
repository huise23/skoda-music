# TASK_QUEUE

Last Updated: 2026-06-03

## Ready
- None

## Pending / Planned
- None

## Blocked

### B-KG-EMBY-INGEST-001
- Priority: P2
- Module: `M-S5-INGEST-028`
- Execution Mode: Single
- Title: 点赞后将播放缓存上传到 Emby 并纳入媒体库
- Blocked By:
  - Emby 是否支持 API 上传音频并入库未确认。
  - 若不支持，需要确认服务端代理写入媒体目录 + 触发扫描方案。
  - 100MB 缓存上限与网络空闲重试策略需后续需求确认。
- Current Requirement:
  - 本阶段只记录点赞状态和入库阻塞状态，不执行上传。

### T-S4-AUDIO-095
- Priority: P1
- Module: `M-S4-AUDIO-021`
- Execution Mode: Single
- Title: AC83xx 实机长播与听感验证
- Blocked By: external AC83xx device window
- Current Requirement:
  - 安装 native DSP APK。
  - 开启 `保真` 连续播放，确认是否消除广播感卡顿。
  - 回传 `hifi-dsp native status=... mode=... tier=... costUs=... flags=...` 日志样本。

## In Progress
- None

## Done
- `T-S4-AUDIO-080`: ExoPlayer DSP 接入落点确认
- `T-S4-AUDIO-081`: 音效模式状态模型与配置迁移
- `T-S4-AUDIO-082`: Fail-open DSP AudioProcessor 骨架实现
- `T-S4-AUDIO-083`: 轻量 DSP 模式引擎实现（Kotlin 版）
- `T-S4-AUDIO-084`: 音效子页与设置页体验替换
- `T-S4-AUDIO-085`: 模式切换联动与旧 EQ 主线下线
- `T-S4-AUDIO-086`: API17 回归清单更新
- `T-S4-AUDIO-088`: Native DSP JNI API 与 fail-open 契约
- `T-S4-AUDIO-089`: 性能档位、预算阈值与日志字段契约
- `T-S4-AUDIO-090`: Native bridge scaffold 与 no-op/bypass buffer 处理
- `T-S4-AUDIO-091`: C++ DSP 模式引擎与系数预计算
- `T-S4-AUDIO-092`: 自动降档、耗时统计与节流日志
- `T-S4-AUDIO-093`: `HiFiAudioProcessor` 热路径迁移到 native
- `T-S4-AUDIO-094`: 本地验证、guardrails 与 API17 清单更新
- `T-S4-AUDIO-096`: Native DSP 播放按钮 fail-open 状态指示
- `T-S5-KG-096`: KugouMusic.NET 接口能力映射与缺口检查
- `T-S5-SRC-097`: 多来源领域模型与左侧一级导航契约
- `T-S5-KG-098`: 酷狗登录与 session 缓存契约
- `T-S5-SRC-099`: Source-aware 队列与播放解析边界设计
- `T-S5-UI-100`: 左侧导航与默认酷狗模式页面骨架
- `T-S5-KG-101`: 酷狗登录 UI 与 session 缓存实现
- `T-S5-KG-102`: 酷狗推荐歌曲页面接入
- `T-S5-KG-103`: 酷狗推荐电台页面接入
- `T-S5-KG-104`: 发现歌单分类、歌单列表与歌曲列表接入
- `T-S5-LIKE-105`: 酷狗点赞抽象与历史/状态页
- `T-S5-VAL-106`: S5 API17 回归清单与本地验证
- `T-S5-PLAY-107`: 酷狗播放 URL 解析与 100MB 缓存守卫实现

## Superseded
- `T-S4-AUDIO-087`: Kotlin DSP API17 实机听感验证。原因：AC83xx 已反馈 Kotlin 热路径卡顿，已由 native 优化链取代；后续实机验证改走 `T-S4-AUDIO-095`。

## Recommended Execution Mode
- 当前 S5 计划内可执行任务已完成。
- 下一步需要用户确认或外部条件：Emby 上传入库能力、AC83xx 实机验证、或新增下一阶段需求。
