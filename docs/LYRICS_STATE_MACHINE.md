# LYRICS_STATE_MACHINE

Last Updated: 2026-04-14 09:51:01
Status: Draft v1 (T-003)

## Purpose
定义首版歌词链路状态机，覆盖“嵌入优先 -> 异步 LrcApi 拉取 -> 缓存 -> UI 刷新”。  
目标是保证主播放线程不被歌词流程阻塞。

## Scope
- 当前播放歌曲歌词加载流程
- 切歌时请求取消与防串歌
- 网络失败降级策略
- 缓存读写与 TTL 过期

## Non-Goals
- 不定义歌词编辑/手动校正功能
- 不定义滚动歌词 UI 动画细节
- 不实现具体网络库/解析库代码

## State Definition
- `Idle`: 无活动歌曲或歌词任务。
- `TrackChanged`: 收到新歌曲事件，初始化上下文。
- `CheckingMemoryCache`: 查询内存缓存。
- `CheckingDiskCache`: 查询本地缓存并判断 TTL。
- `ReadingEmbeddedLyrics`: 读取嵌入歌词（标准同步标签）。
- `EmbeddedReady`: 嵌入歌词可用，直接展示。
- `FetchingRemoteAsync`: 异步请求 LrcApi（后台线程）。
- `RemoteReady`: 远程歌词可用，写缓存并刷新 UI。
- `NoLyrics`: 当前无可用歌词。
- `Error`: 非致命错误，记录后降级到 `NoLyrics`。

## Event Definition
- `E.TrackStart(track_meta)`
- `E.TrackStop`
- `E.CacheHit(memory|disk)`
- `E.CacheMiss`
- `E.EmbeddedFound`
- `E.EmbeddedNotFound`
- `E.RemoteSuccess`
- `E.RemoteTimeout`
- `E.RemoteFail`
- `E.CancelByTrackSwitch`

## Transition Rules
1. `Idle --E.TrackStart--> TrackChanged`
2. `TrackChanged --> CheckingMemoryCache`
3. `CheckingMemoryCache --E.CacheHit--> EmbeddedReady`  
   说明: 缓存命中后直接展示并结束流程。
4. `CheckingMemoryCache --E.CacheMiss--> CheckingDiskCache`
5. `CheckingDiskCache --E.CacheHit--> EmbeddedReady`  
   说明: 磁盘命中后回填内存缓存并展示。
6. `CheckingDiskCache --E.CacheMiss--> ReadingEmbeddedLyrics`
7. `ReadingEmbeddedLyrics --E.EmbeddedFound--> EmbeddedReady`
8. `ReadingEmbeddedLyrics --E.EmbeddedNotFound--> FetchingRemoteAsync`
9. `FetchingRemoteAsync --E.RemoteSuccess--> RemoteReady`
10. `FetchingRemoteAsync --E.RemoteTimeout/E.RemoteFail--> NoLyrics`
11. `RemoteReady --> Idle`（当前曲目歌词可用，等待下一事件）
12. 任意状态 `--E.CancelByTrackSwitch--> TrackChanged`
13. 任意状态遇非致命错误进入 `Error --> NoLyrics`

## Threading Model
- 主线程:
  - 接收 `TrackStart/TrackStop`。
  - 执行缓存查询与 UI 状态更新。
- 后台线程:
  - 远程 LrcApi 请求与响应解析。
- 约束:
  - 远程请求不得阻塞主线程。
  - 切歌时必须取消旧请求，响应回调需校验 `track_id` 防串歌。

## Cache Policy
- 内存缓存: 当前会话热点歌词，优先级最高。
- 磁盘缓存: 持久缓存，遵循 `cache_ttl_days`。
- 缓存 key 建议: `track_id + artist + title + source_hash`。
- TTL 到期策略:
  - 到期视为 miss，触发远程拉取。
  - 拉取失败时可继续使用“过期缓存”作为可选降级（待确认）。

## UI Update Contract
- `TrackStart` 后立即显示“歌词加载中”占位态。
- `EmbeddedReady` 或 `RemoteReady` 到达时替换为歌词内容。
- `NoLyrics` 时展示“暂无歌词”，不弹阻塞提示。
- 多语言展示: 中文优先，其次原文（已确认）。

## Timeout and Retry
- 远程超时读取 `lyrics.fetch_timeout_ms`（默认 2000ms）。
- v1 重试策略: 不自动重试，只记录失败并降级（待确认是否需要一次快速重试）。

## Error Classification
- 非致命:
  - 无歌词
  - 远程超时/网络错误
  - 缓存读取失败
- 处理:
  - 不中断播放
  - 写日志（脱敏）
  - 更新 UI 为可理解状态

## Acceptance Checklist (Doc Level)
- 已覆盖嵌入优先策略
- 已覆盖异步拉取与取消
- 已覆盖缓存与 TTL
- 已覆盖失败降级与 UI 行为
- 已标注待确认项，不编造未确认策略

## To Confirm
- 过期缓存在远程失败时是否允许回退显示。
- 远程失败是否需要一次快速重试。
