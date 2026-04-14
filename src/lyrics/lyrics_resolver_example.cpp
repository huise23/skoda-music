#include "src/lyrics/lyrics_resolver.h"

#include <chrono>
#include <iostream>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>

namespace {

class FakeLrcApiClient final : public skodamusic::lyrics::ILrcApiClient {
 public:
  struct ResponsePlan {
    int delay_ms = 50;
    skodamusic::lyrics::RemoteFetchResult result;
  };

  void SetPlan(const std::string& track_id, const ResponsePlan& plan) {
    std::lock_guard<std::mutex> lock(mu_);
    plans_[track_id] = plan;
  }

  void FetchLyricsAsync(
      const skodamusic::lyrics::LyricsQuery& query,
      int /*timeout_ms*/,
      std::function<void(const skodamusic::lyrics::RemoteFetchResult&)> callback)
      override {
    ResponsePlan plan;
    {
      std::lock_guard<std::mutex> lock(mu_);
      const auto it = plans_.find(query.track_id);
      if (it != plans_.end()) {
        plan = it->second;
      } else {
        plan.result.code = skodamusic::lyrics::RemoteFetchCode::kNotFound;
        plan.result.message = "not found";
      }
      canceled_[query.request_id] = false;
    }

    std::thread([this, request_id = query.request_id, plan, callback]() {
      std::this_thread::sleep_for(std::chrono::milliseconds(plan.delay_ms));
      {
        std::lock_guard<std::mutex> lock(mu_);
        if (canceled_[request_id]) {
          callback({skodamusic::lyrics::RemoteFetchCode::kCanceled, "", "canceled"});
          return;
        }
      }
      callback(plan.result);
    }).detach();
  }

  void CancelRequest(std::int64_t request_id) override {
    std::lock_guard<std::mutex> lock(mu_);
    canceled_[request_id] = true;
  }

 private:
  std::mutex mu_;
  std::unordered_map<std::string, ResponsePlan> plans_;
  std::unordered_map<std::int64_t, bool> canceled_;
};

std::string ToStateName(skodamusic::lyrics::LyricsState state) {
  switch (state) {
    case skodamusic::lyrics::LyricsState::kLoading:
      return "loading";
    case skodamusic::lyrics::LyricsState::kReady:
      return "ready";
    case skodamusic::lyrics::LyricsState::kNoLyrics:
      return "no_lyrics";
    case skodamusic::lyrics::LyricsState::kError:
      return "error";
    case skodamusic::lyrics::LyricsState::kIdle:
    default:
      return "idle";
  }
}

std::string ToSourceName(skodamusic::lyrics::LyricsSource source) {
  switch (source) {
    case skodamusic::lyrics::LyricsSource::kMemoryCache:
      return "memory_cache";
    case skodamusic::lyrics::LyricsSource::kEmbedded:
      return "embedded";
    case skodamusic::lyrics::LyricsSource::kRemote:
      return "remote";
    case skodamusic::lyrics::LyricsSource::kNone:
    default:
      return "none";
  }
}

void PrintUpdate(const skodamusic::lyrics::LyricsSnapshot& snapshot) {
  std::cout << "track=" << snapshot.track_id << " request=" << snapshot.request_id
            << " state=" << ToStateName(snapshot.state)
            << " source=" << ToSourceName(snapshot.source)
            << " message=" << snapshot.message << '\n';
}

}  // namespace

int main() {
  FakeLrcApiClient client;
  client.SetPlan(
      "track-remote",
      {30, {skodamusic::lyrics::RemoteFetchCode::kOk, "[00:01.00]remote lyrics", ""}});
  client.SetPlan(
      "track-slow",
      {150, {skodamusic::lyrics::RemoteFetchCode::kOk, "[00:01.00]slow remote lyrics", ""}});
  client.SetPlan(
      "track-next",
      {20, {skodamusic::lyrics::RemoteFetchCode::kOk, "[00:01.00]next track lyrics", ""}});

  skodamusic::lyrics::LyricsResolver resolver(&client, 2000);

  skodamusic::lyrics::TrackLyricsContext embedded_track = {
      "track-embedded",
      "Embedded Song",
      "Artist A",
      "[00:01.00]embedded lyrics",
      "http://lrcapi.local",
  };
  resolver.StartResolve(embedded_track, PrintUpdate);

  skodamusic::lyrics::TrackLyricsContext remote_track = {
      "track-remote",
      "Remote Song",
      "Artist B",
      "",
      "http://lrcapi.local",
  };
  resolver.StartResolve(remote_track, PrintUpdate);

  skodamusic::lyrics::TrackLyricsContext slow_track = {
      "track-slow",
      "Slow Song",
      "Artist C",
      "",
      "http://lrcapi.local",
  };
  resolver.StartResolve(slow_track, PrintUpdate);

  skodamusic::lyrics::TrackLyricsContext next_track = {
      "track-next",
      "Next Song",
      "Artist D",
      "",
      "http://lrcapi.local",
  };
  resolver.StartResolve(next_track, PrintUpdate);

  std::this_thread::sleep_for(std::chrono::milliseconds(250));
  return 0;
}

