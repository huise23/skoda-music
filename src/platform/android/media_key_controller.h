#pragma once

#include <functional>
#include <string>
#include <vector>

#include "src/playback/playback_queue.h"

namespace skodamusic::platform::android {

enum class MediaKeyEvent {
  kUnknown,
  kNext,
  kPrevious,
};

struct HomeNowPlayingState {
  bool has_track = false;
  std::string title;
  std::string artist;
  std::string hint;
};

struct QueueSyncState {
  int current_index = -1;
  int total_count = 0;
  std::vector<std::string> titles;
};

struct MediaKeySyncState {
  HomeNowPlayingState home;
  QueueSyncState queue;
};

class MediaKeyController final {
 public:
  explicit MediaKeyController(playback::PlaybackQueue* queue);

  static MediaKeyEvent FromAndroidKeyCode(int key_code);

  void SetSyncCallback(std::function<void(const MediaKeySyncState&)> callback);

  MediaKeySyncState HandleMediaKey(MediaKeyEvent event);

  MediaKeySyncState CurrentState() const;

 private:
  MediaKeySyncState BuildState() const;

  playback::PlaybackQueue* queue_;
  std::function<void(const MediaKeySyncState&)> sync_callback_;
};

}  // namespace skodamusic::platform::android

