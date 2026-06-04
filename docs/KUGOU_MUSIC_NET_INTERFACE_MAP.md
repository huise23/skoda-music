# KugouMusic.NET Interface Map

Last Updated: 2026-06-04

## Purpose
- 本文固定 S5 酷狗接入的唯一实现依据。
- Android 端酷狗能力必须能回溯到 `KugouMusic.NET/` 中的 client、controller、model 或 viewmodel。
- 未在本文映射的酷狗能力不得直接进入实现；先补映射或向用户确认。

## Integration Boundary
- `KugouMusic.NET/` 当前作为只读参考源码，不在 Android 仓库内修改。
- Android 不再要求用户提供 Kugou WebApi base URL；旧 WebApi 代理路径只作为历史映射参考。
- Android 直连实现必须逐项移植 `KugouMusic.NET` raw/client、signer、crypto、session 行为，不能把 `https://gateway.kugou.com` 当作旧代理路由的默认 base URL。
- 酷狗模式必须登录后可用；默认扫码登录，同时支持手机号验证码登录。
- 当前 Android 端已实现 QR direct key/check、device init、token refresh 和 direct session validation；SMS AES/RSA 和内容接口 direct 化仍待后续任务。

## Session And Auth

| App Capability | WebApi Route | Client / Raw API | Models | Notes |
| --- | --- | --- | --- | --- |
| 获取二维码 | `GET /login/qr/key` | `LoginClient.GetQrCodeAsync()` -> `RawLoginApi.GetQrKeyAsync()` | `QRCode` | `QRCode.Qrcode` 是轮询 key；`QRCode.QrcodeImg` 是可展示二维码图片。 |
| 轮询二维码状态 | `GET /login/qr/check?key=...` | `LoginClient.CheckQrStatusAsync(key)` -> `RawLoginApi.CheckQrStatusAsync(key)` | `QrLoginStatusResponse`, `QrLoginStatus` | `.NET` 状态：waiting scan、waiting confirm、success、expired；`LoginViewModel` 每 2s 轮询。 |
| 发送手机号验证码 | `POST /captcha/sent?mobile=...` | `LoginClient.SendCodeAsync(mobile)` -> `RawLoginApi.SendSmsCodeAsync(mobile)` | `SendCodeResponse` | `CaptchaController` 校验手机号非空且长度不少于 11。 |
| 手机号验证码登录 | `POST /login/cellphone` body `{ mobile, code }` | `LoginClient.LoginByMobileAsync(mobile, code)` -> `RawLoginApi.LoginByMobileAsync(...)` | `LoginResponse` | 成功时 `LoginClient` 调用 `KgSessionManager.UpdateAuth(...)` 保存 token/userId/t1。 |
| 刷新 session | `POST /login/token` | `LoginClient.RefreshSessionAsync()` -> `RawLoginApi.RefreshTokenAsync(...)` | `RefreshTokenResponse` | 本地无 token 或 `UserId == "0"` 时视为无有效登录。扫码成功后 `.NET LoginViewModel` 会先 init device 再 refresh。 |
| 注册设备 | n/a direct raw | `RegisterClient.InitDeviceAsync()` -> `RawDeviceApi.RegisterDevAsync(...)` | raw JSON with `data.dfid` | 成功后更新 `dfid/mid/uuid`；Android 按 `.NET` 的虚拟硬件字段、Playlist AES 和 RSA PKCS1 实现。 |
| 退出登录 | `POST /login/logout` | `LoginClient.LogOutAsync()` -> `KgSessionManager.Logout()` | `KgSession` | 清理 token、vip、t1、dfid 并清 cookie。 |

### Auth Source Files
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/LoginController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/CaptchaController.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/LoginViewModel.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Services/KgWebSessionMiddleware.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Services/KgWebSessionPersistence.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/LoginClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawLoginApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Session/KgSession.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Session/KgSessionManager.cs`

### Android Auth Contract
- QR direct session 当前缓存 `userid/token/nickname`、安装设备字段、`dfid/mid/uuid`、`vip_type/t1` 和 validation 状态。
- 启动时必须清理旧 `X-Kg-Session-Id` / `kg_sid` WebApi 代理缓存，再恢复 direct session。
- 默认展示扫码登录面板；扫码按钮走 direct raw QR endpoint，手机号验证码按钮在 AES/RSA direct port 完成前不得触发旧 WebApi 代理请求。
- 扫码轮询节奏参考 `.NET LoginViewModel`: 2s。
- QR success 后必须执行 `RegisterClient.InitDeviceAsync()` 和 `LoginClient.RefreshSessionAsync()` 等价流程；只有 token refresh 成功后 Android `hasSession()` 才返回 true。

## Recommended Songs

| App Capability | WebApi Route | Client / Raw API | Models | Notes |
| --- | --- | --- | --- | --- |
| 推荐歌曲 | `GET /recommend/songs` | `RecommendClient.GetRecommendedSongsAsync()` -> `RawDiscoveryApi.GetRecommendSongAsync(uid)` | `DailyRecommendResponse`, `SongItem` mapping in `DailyRecommendViewModel` | 默认酷狗首屏使用该能力；未登录时 Android 不应请求内容。 |

### Recommended Songs Source Files
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/DiscoveryController.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/RecommendClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawDiscoveryApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/DailyRecommendModels.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/DailyRecommendViewModel.cs`

## Recommended Radio

| App Capability | WebApi Route | Client / Raw API | Models | Notes |
| --- | --- | --- | --- | --- |
| 推荐电台列表 | `GET /fm/recommend` | `FmClient.GetRecommendAsync()` -> `RawFmApi.GetRecommendAsync()` | `FmRecommendResponse` | 作为左侧一级入口“推荐电台”的列表来源。 |
| 电台歌曲 | `GET /fm/songs?fmid=...&type=2&offset=-1&size=20` | `FmClient.GetSongsAsync(fmIds, type, offset, size)` -> `RawFmApi.GetSongsAsync(...)` | `FmSongResponse` | 点击电台后加载歌曲列表。 |
| 电台图片 | `GET /fm/image?fmid=...` | `FmClient.GetImagesAsync(fmIds)` -> `RawFmApi.GetImagesAsync(...)` | `FmImageResponse` | 多个 fmid 的传参方式按 `.NET` raw API 行为实现。 |

### Radio Source Files
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/FmController.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/FmClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawFmApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/FmRecommendResponse.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/FmSongResponse.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/FmImageResponse.cs`

## Discover Playlists

| App Capability | WebApi Route | Client / Raw API | Models | Notes |
| --- | --- | --- | --- | --- |
| 歌单标签分类 | `GET /playlist/tags` | `PlaylistClient.GetTagsAsync()` -> `RawPlaylistApi.GetPlaylistTagsAsync()` | `PlaylistTagCategory` | 场景/主题/语种/风格/心情/年代均来自接口返回，不手写 tag。 |
| 按 tag 推荐歌单 | `GET /top/playlist?category_id=...&page=...` | `RecommendClient.GetRecommendedPlaylistsAsync(categoryId, page, pageSize)` -> `RawDiscoveryApi.GetRecommendedPlaylistsAsync(...)` | `RecommendPlaylistResponse`, `RecommendPlaylistItem` | `DiscoverViewModel` 用 tagId 加载歌单列表。 |
| 歌单详情 | `GET /playlist/detail?ids=...` | `PlaylistClient.GetInfoAsync(playlistId)` -> `RawPlaylistApi.GetPlaylistInfoAsync(...)` | `PlaylistInfo` | 详情字段以 model 为准。 |
| 歌单歌曲 | `GET /playlist/track/all?id=...&page=...&pagesize=...` | `PlaylistClient.GetSongsAsync(playlistId, page, pageSize)` -> `RawPlaylistApi.GetPlaylistSongsAsync(...)` | `PlaylistSongResponse`, `PlaylistSong` | `DiscoverViewModel` 将 `Name/Singers/Hash/AlbumId/FileId/Cover/DurationMs` 映射为 `SongItem`。 |

### Discover Source Files
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/DiscoveryController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/PlayListController.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/PlaylistClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/RecommendClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawPlaylistApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawDiscoveryApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/PlaylistTagCategory.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/RecommendPlaylistResponse.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/PlaylistModel.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/PlaylistSongData.cs`
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/ViewModels/DiscoverViewModel.cs`

## Playback URL

| App Capability | WebApi Route | Client / Raw API | Models | Notes |
| --- | --- | --- | --- | --- |
| 歌曲播放 URL | `GET /song/url?hash=...&quality=128&album_id=...&album_audio_id=...&free_part=false` | `SongClient.GetPlayInfoAsync(hash, quality, albumId, albumAudioId, freePart)` -> `RawSongApi.GetUrlAsync(...)` | `PlayUrlData` | Android source playback ref 至少需要 `hash`，可选 `albumId/albumAudioId/quality/freePart`。不可播/VIP/试听状态以 `PlayUrlData.Status` 和返回字段为准。 |
| 歌曲图片 | `GET /song/images?...` in `SongController` | `SongClient.GetImagesAsync(...)` / `GetAudioImagesAsync(...)` | `AudioImageResponse` | 列表已有 cover 时优先使用列表字段；缺失时再补图。 |

### Playback Source Files
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/SongController.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/SongClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Protocol/Raw/RawSongApi.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/SongModel.cs`

## Like / Favorite

| App Capability | WebApi / Client Route | Client / Service | Models | Notes |
| --- | --- | --- | --- | --- |
| 判断登录 | no direct WebApi requirement | `UserClient.IsLoggedIn()` | `KgSession` | token 非空且 `UserId != "0"`。 |
| 加载用户歌单 | available through `UserClient.GetPlaylistsAsync(...)`; WebApi controller routes user playlists outside current S5 UI scope | `UserClient.GetPlaylistsAsync()` -> `RawUserApi.GetAllListAsync(...)` | `UserPlaylistResponse` | `FavoritePlaylistService` 用它查找“我喜欢”歌单。 |
| 加载我喜欢歌曲 | no direct route dedicated to like list | `PlaylistClient.GetSongsAsync(likePlaylist.ListCreateId, pageSize: 1000)` | `PlaylistSongResponse` | 本地优先缓存，远端刷新失败时保留缓存。 |
| 点赞歌曲 | no dedicated favorite toggle route; implemented as add to like playlist | `FavoritePlaylistService.ToggleLikeAsync(...)` -> `PlaylistClient.AddSongsAsync("2", songs)` | `AddSongResponse` | `LikeListIdForAction = "2"`；添加字段为 `Name/Hash/AlbumId/MixSongId`。 |
| 取消点赞 | no dedicated favorite toggle route; implemented as remove from like playlist | `FavoritePlaylistService.ToggleLikeAsync(...)` -> `PlaylistClient.RemoveSongsAsync("2", fileIds)` | `RemoveSongResponse` | 取消需要 `hash -> fileId` 缓存。 |
| 收藏数 | `GET /favorite/count?mixsongids=...` | `UserClient.GetFavoriteCountAsync(...)` -> `RawUserApi.GetFavoriteCountAsync(...)` | raw JSON | 只能作为展示/辅助，不是点赞 toggle。 |

### Like Source Files
- `KugouMusic.NET/src/Apps/KugouAvaloniaPlayer/Services/FavoritePlaylistService.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/UserClient.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Clients/PlaylistClient.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/UserController.cs`
- `KugouMusic.NET/src/Apps/KgWebApi.Net/Controllers/PlayListController.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/UserPlaylistModels.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/AddSongResponse.cs`
- `KugouMusic.NET/src/Libraries/KuGou.Net/Abstractions/Models/RemoveSongResponse.cs`

### Android Like Contract
- 本阶段先抽象 like 状态，真实账号侧点赞仅支持 Kugou。
- 若 Android 第一版只记录本地点赞状态，UI 文案不得暗示已同步到酷狗账号。
- 若实现酷狗账号侧点赞，必须按 `FavoritePlaylistService` 的“我喜欢歌单 + add/remove tracks”流程做，不自造 favorite toggle 接口。
- Emby 入库仍保持 `B-KG-EMBY-INGEST-001` 阻塞，不在本任务实现。

## Confirmed Gaps / Pending Confirmation
- 没有发现“上传播放缓存到 Emby 并入库”的 Kugou 相关依据；该能力仍属于 Emby 侧阻塞项。
- 没有独立的酷狗“歌曲点赞 toggle WebApi route”；可用依据是 `FavoritePlaylistService` 通过用户歌单和 `PlaylistClient.AddSongsAsync/RemoveSongsAsync` 操作“我喜欢”。
- Android 直连不是 base URL 替换：`KgHttpTransport` 默认 `https://gateway.kugou.com`，但登录还使用 `login-user.kugou.com`、`login.user.kugou.com`、`loginserviceretry.kugou.com` 等 host。
- Direct port 已补齐登录 session validation 所需的 `KgSigner`、`KgCrypto`、device/dfid/mid/token refresh 行为；内容接口 direct 化仍需继续移植各 raw API 的具体参数和解析。
- Android 当前旧 `KugouWebApiClient` 仍是代理路由 client，后续需被 direct client 替代或重命名，不得重新暴露用户填写地址。
