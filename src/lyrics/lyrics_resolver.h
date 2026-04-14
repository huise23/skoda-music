#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace skodamusic::lyrics {

enum class LyricsState {
  kIdle,
  kLoading,
  kReady,
  kNoLyrics,
  kError,
};

enum class LyricsSource {
  kNone,
  kMemoryCache,
  kEmbedded,
  kRemote,
};

struct TrackLyricsContext {
  std::string track_id;
  std::string title;
  std::string artist;
  std::string embedded_lyrics;
  std::string lrcapi_base_url;
};

struct LyricsQuery {
  std::string track_id;
  std::string title;
  std::string artist;
  std::string lrcapi_base_url;
  std::int64_t request_id = 0;
};

enum class RemoteFetchCode {
  kOk,
  kNotFound,
  kTimeout,
  kFailed,
  kCanceled,
};

struct RemoteFetchResult {
  RemoteFetchCode code = RemoteFetchCode::kFailed;
  std::string lyrics_text;
  std::string message;
};

struct LyricsSnapshot {
  std::string track_id;
  std::int64_t request_id = 0;
  LyricsState state = LyricsState::kIdle;
  LyricsSource source = LyricsSource::kNone;
  std::string text;
  std::string message;
};

using LyricsUpdateCallback = std::function<void(const LyricsSnapshot&)>;

class ILrcApiClient {
 public:
  virtual ~ILrcApiClient() = default;

  virtual void FetchLyricsAsync(const LyricsQuery& query,
                                int timeout_ms,
                                std::function<void(const RemoteFetchResult&)> callback) = 0;

  virtual void CancelRequest(std::int64_t request_id) = 0;
};

class LyricsResolver final {
 public:
  explicit LyricsResolver(ILrcApiClient* client, int fetch_timeout_ms = 2000);

  std::int64_t StartResolve(const TrackLyricsContext& context,
                            const LyricsUpdateCallback& callback);

  void CancelActiveRequest();

 private:
  struct SharedRuntime {
    std::atomic<std::int64_t> active_request_id = 0;
    std::mutex cache_mu;
    std::unordered_map<std::string, std::string> memory_cache;
  };

  static bool HasUsableEmbeddedLyrics(const std::string& text);
  static std::string BuildCacheKey(const TrackLyricsContext& context);

  static void EmitLoading(const TrackLyricsContext& context,
                          std::int64_t request_id,
                          const LyricsUpdateCallback& callback);

  static void EmitReady(const TrackLyricsContext& context,
                        std::int64_t request_id,
                        LyricsSource source,
                        const std::string& text,
                        const LyricsUpdateCallback& callback);

  static void EmitNoLyrics(const TrackLyricsContext& context,
                           std::int64_t request_id,
                           const std::string& message,
                           const LyricsUpdateCallback& callback);

  ILrcApiClient* client_;
  int fetch_timeout_ms_ = 2000;
  std::shared_ptr<SharedRuntime> runtime_state_;
};

}  // namespace skodamusic::lyrics
