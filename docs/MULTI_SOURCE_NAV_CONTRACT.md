# Multi-Source Navigation Contract

Last Updated: 2026-06-03

## Purpose
- 固定 S5 第一版多来源模型和左侧一级导航契约。
- 避免把酷狗字段直接塞进 `EmbyTrack`。
- 后续 UI、队列、播放、点赞和历史状态都应携带 `MusicSource`。

## Code Contract

Added in `app/src/main/java/com/skodamusic/app/model/MainModels.kt`:

- `MusicSource`
  - `EMBY`
  - `KUGOU`
- `SourceCapability`
  - `PLAY`
  - `LIKE`
  - `RADIO`
  - `PLAYLIST`
  - `REQUIRES_LOGIN`
- `SourceNavEntry`
  - `KUGOU_RECOMMENDED_SONGS`
  - `KUGOU_RECOMMENDED_RADIO`
  - `KUGOU_DISCOVER_PLAYLISTS`
  - `PLAYBACK_QUEUE`
  - `LIKE_STATUS`
  - `SETTINGS`
- `SourcePlaybackRef`
  - stable source id, source primary id, Kugou hash/album ids/quality and login requirement.
- `SourceTrack`
  - unified track row for Emby/Kugou UI, queue and playback resolver.
- `SourcePlaylist`
  - unified playlist row for Kugou discover playlists and future source playlists.
- `SourceRadio`
  - unified radio row for Kugou recommended radio and future source radios.

## Field Boundaries

### SourceTrack
- Common fields:
  - `source`
  - `sourceTrackId`
  - `title`
  - `artist`
  - `album`
  - `coverUrl`
  - `durationMs`
  - `playbackRef`
  - `capabilities`
- Emby mapping:
  - `source = EMBY`
  - `sourceTrackId = EmbyTrack.id`
  - `title = EmbyTrack.title`
  - `artist = EmbyTrack.artist`
  - `durationMs = runtimeTicks -> ms`
  - `playbackRef.primaryId = EmbyTrack.id`
  - `capabilities = PLAY`
- Kugou recommended song / playlist song mapping:
  - `source = KUGOU`
  - `sourceTrackId = stable Kugou song id when available, otherwise hash`
  - `title = SongItem.Name` or model `Name`
  - `artist = Singer / Singers`
  - `album = AlbumName`
  - `coverUrl = Cover`
  - `durationMs = DurationSeconds * 1000` or `DurationMs`
  - `playbackRef.hash = Hash`
  - `playbackRef.albumId = AlbumId`
  - `playbackRef.albumAudioId = AlbumAudioId / MixSongId when available`
  - `playbackRef.requiresLogin = true`
  - `capabilities = PLAY + LIKE + REQUIRES_LOGIN`

### SourcePlaylist
- Kugou discover playlist mapping:
  - `source = KUGOU`
  - `sourcePlaylistId = ListId`
  - `globalId = GlobalId`
  - `title = Name`
  - `coverUrl = Cover`
  - `subtitle = CreatorName / play count text`
  - `tagId = selected playlist tag id`
  - `capabilities = PLAYLIST + REQUIRES_LOGIN`

### SourceRadio
- Kugou recommended radio mapping:
  - `source = KUGOU`
  - `sourceRadioId = fmid`
  - `title = radio name`
  - `coverUrl = image from `/fm/image`
  - `type = fm type`, default `2`
  - `capabilities = RADIO + PLAY + REQUIRES_LOGIN`

## Left Navigation IA

S5 target left navigation uses first-level entries only:

1. 推荐歌曲
   - `SourceNavEntry.KUGOU_RECOMMENDED_SONGS`
   - Default launch destination.
   - Requires Kugou login.
2. 推荐电台
   - `SourceNavEntry.KUGOU_RECOMMENDED_RADIO`
   - Requires Kugou login.
3. 发现歌单
   - `SourceNavEntry.KUGOU_DISCOVER_PLAYLISTS`
   - Uses category filters inside the page, not a Home second-level tab.
   - Requires Kugou login.
4. 播放队列
   - `SourceNavEntry.PLAYBACK_QUEUE`
   - Source-aware queue items.
5. 点赞/入库状态
   - `SourceNavEntry.LIKE_STATUS`
   - Shows source, title, action time, current state and failure reason.
   - Emby ingest remains blocked/deferred.
6. 设置
   - `SourceNavEntry.SETTINGS`
   - Keeps Emby/LrcApi settings and adds Kugou login/session/WebApi base settings.

## Current UI Migration Boundary
- Existing `activity_main.xml` has left nav ids:
  - `nav_home`
  - `nav_queue` currently `gone`
  - `nav_library`
  - `nav_settings`
- Existing `MainActivity` page constants only cover:
  - `PAGE_HOME`
  - `PAGE_LIBRARY`
  - `PAGE_SETTINGS`
  - `PAGE_EQ`
- Existing Home still contains `btn_home_tab_lyrics` and `btn_home_tab_recommend`.
- S5 UI implementation must replace the content-entry role of Home tabs with left nav entries. It may keep the Now Playing card, but should not add another Home-level second tab.
- Do not delete stable Emby playback code while introducing the source model. Emby migration should happen through adapters/resolvers.

## Implementation Order
1. Keep `EmbyTrack` as legacy backing model until source-aware playback is wired.
2. Add adapters:
   - `EmbyTrack -> SourceTrack`
   - Kugou recommended song / playlist song / radio song -> `SourceTrack`
3. Introduce source-aware queue item using `SourceTrack` and `SourcePlaybackRef`.
4. Route playback through a source resolver:
   - Emby resolver keeps existing download-only behavior.
   - Kugou resolver uses `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md` playback URL mapping.
5. Replace left nav items and default startup destination after source-aware page skeleton exists.

## Non-Goals
- No Emby upload ingest in S5 Ready tasks.
- No non-Kugou like implementation in this stage.
- No direct raw Kugou protocol port until WebApi route proves insufficient and user confirms.
- No additional Home second-level tab.
