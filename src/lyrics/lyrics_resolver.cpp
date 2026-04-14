#include "src/lyrics/lyrics_resolver.h"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <utility>

namespace skodamusic::lyrics {
namespace {

constexpr char kLoadingMessage[] = "歌词加载中";
constexpr char kNoLyricsMessage[] = "暂无歌词";

std::string Trim(const std::string& input) {
  const auto begin = std::find_if_not(
      input.begin(), input.end(), [](unsigned char c) { return std::isspace(c); });
  if (begin == input.end()) {
    return "";
  }
  const auto end = std::find_if_not(
      input.rbegin(), input.rend(), [](unsigned char c) { return std::isspace(c); })
                       .base();
  return std::string(begin, end);
}

bool IsLikelySyncedLine(const std::string& line) {
  const std::string trimmed = Trim(line);
  if (trimmed.size() < 7U) {
    return false;
  }
  if (trimmed[0] != '[') {
    return false;
  }
  const size_t close_pos = trimmed.find(']');
  if (close_pos == std::string::npos || close_pos < 5U) {
    return false;
  }
  // Accepts forms like [01:23], [01:23.45], [01:23:45].
  return trimmed[3] == ':' || trimmed[2] == ':';
}

}  // namespace

LyricsResolver::LyricsResolver(ILrcApiClient* client, int fetch_timeout_ms)
    : client_(client),
      fetch_timeout_ms_(fetch_timeout_ms),
      runtime_state_(std::make_shared<SharedRuntime>()) {}

std::int64_t LyricsResolver::StartResolve(const TrackLyricsContext& context,
                                          const LyricsUpdateCallback& callback) {
  const std::int64_t request_id = runtime_state_->active_request_id.fetch_add(1) + 1;
  const std::int64_t previous_request_id = request_id - 1;
  if (client_ != nullptr && previous_request_id > 0) {
    client_->CancelRequest(previous_request_id);
  }

  EmitLoading(context, request_id, callback);

  const std::string cache_key = BuildCacheKey(context);
  {
    std::lock_guard<std::mutex> lock(runtime_state_->cache_mu);
    const auto it = runtime_state_->memory_cache.find(cache_key);
    if (it != runtime_state_->memory_cache.end()) {
      EmitReady(context, request_id, LyricsSource::kMemoryCache, it->second, callback);
      return request_id;
    }
  }

  if (HasUsableEmbeddedLyrics(context.embedded_lyrics)) {
    {
      std::lock_guard<std::mutex> lock(runtime_state_->cache_mu);
      runtime_state_->memory_cache[cache_key] = context.embedded_lyrics;
    }
    EmitReady(context, request_id, LyricsSource::kEmbedded, context.embedded_lyrics,
              callback);
    return request_id;
  }

  if (client_ == nullptr || context.lrcapi_base_url.empty()) {
    EmitNoLyrics(context, request_id, kNoLyricsMessage, callback);
    return request_id;
  }

  const auto runtime_state = runtime_state_;
  const LyricsQuery query = {
      context.track_id,
      context.title,
      context.artist,
      context.lrcapi_base_url,
      request_id,
  };

  // v1 strategy: no auto-retry. Return NoLyrics on timeout/failure.
  client_->FetchLyricsAsync(
      query, fetch_timeout_ms_,
      [runtime_state, callback, context, cache_key, request_id](
          const RemoteFetchResult& result) {
        if (runtime_state->active_request_id.load() != request_id) {
          return;
        }

        if (result.code == RemoteFetchCode::kOk &&
            !Trim(result.lyrics_text).empty()) {
          {
            std::lock_guard<std::mutex> lock(runtime_state->cache_mu);
            runtime_state->memory_cache[cache_key] = result.lyrics_text;
          }
          LyricsResolver::EmitReady(context, request_id, LyricsSource::kRemote,
                                    result.lyrics_text, callback);
          return;
        }

        LyricsResolver::EmitNoLyrics(context, request_id, kNoLyricsMessage, callback);
      });

  return request_id;
}

void LyricsResolver::CancelActiveRequest() {
  const std::int64_t canceled_request_id =
      runtime_state_->active_request_id.fetch_add(1) + 1;
  if (client_ != nullptr) {
    client_->CancelRequest(canceled_request_id);
  }
}

bool LyricsResolver::HasUsableEmbeddedLyrics(const std::string& text) {
  const std::string trimmed = Trim(text);
  if (trimmed.empty()) {
    return false;
  }

  std::istringstream reader(trimmed);
  std::string line;
  while (std::getline(reader, line)) {
    if (IsLikelySyncedLine(line)) {
      return true;
    }
  }
  return false;
}

std::string LyricsResolver::BuildCacheKey(const TrackLyricsContext& context) {
  return context.track_id + "|" + context.artist + "|" + context.title;
}

void LyricsResolver::EmitLoading(const TrackLyricsContext& context,
                                 std::int64_t request_id,
                                 const LyricsUpdateCallback& callback) {
  if (!callback) {
    return;
  }
  callback({
      context.track_id,
      request_id,
      LyricsState::kLoading,
      LyricsSource::kNone,
      "",
      kLoadingMessage,
  });
}

void LyricsResolver::EmitReady(const TrackLyricsContext& context,
                               std::int64_t request_id,
                               LyricsSource source,
                               const std::string& text,
                               const LyricsUpdateCallback& callback) {
  if (!callback) {
    return;
  }
  callback({
      context.track_id,
      request_id,
      LyricsState::kReady,
      source,
      text,
      "",
  });
}

void LyricsResolver::EmitNoLyrics(const TrackLyricsContext& context,
                                  std::int64_t request_id,
                                  const std::string& message,
                                  const LyricsUpdateCallback& callback) {
  if (!callback) {
    return;
  }
  callback({
      context.track_id,
      request_id,
      LyricsState::kNoLyrics,
      LyricsSource::kNone,
      "",
      message,
  });
}

}  // namespace skodamusic::lyrics
