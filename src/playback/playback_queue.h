#pragma once

#include <string>
#include <vector>

namespace skodamusic::playback {

struct QueueTrack {
  std::string track_id;
  std::string title;
  std::string artist;
};

struct PlaybackSnapshot {
  bool has_track = false;
  int current_index = -1;
  QueueTrack current_track;
  std::vector<QueueTrack> queue;
};

class PlaybackQueue final {
 public:
  void SetQueue(const std::vector<QueueTrack>& tracks, int start_index);

  bool MoveNext();
  bool MovePrevious();

  PlaybackSnapshot Snapshot() const;

 private:
  std::vector<QueueTrack> queue_;
  int current_index_ = -1;
};

}  // namespace skodamusic::playback

