#include "src/playback/playback_queue.h"

#include <algorithm>

namespace skodamusic::playback {

void PlaybackQueue::SetQueue(const std::vector<QueueTrack>& tracks, int start_index) {
  queue_ = tracks;
  if (queue_.empty()) {
    current_index_ = -1;
    return;
  }
  const int max_index = static_cast<int>(queue_.size()) - 1;
  current_index_ = std::clamp(start_index, 0, max_index);
}

bool PlaybackQueue::MoveNext() {
  if (queue_.empty() || current_index_ < 0) {
    return false;
  }
  const int max_index = static_cast<int>(queue_.size()) - 1;
  if (current_index_ >= max_index) {
    return false;
  }
  ++current_index_;
  return true;
}

bool PlaybackQueue::MovePrevious() {
  if (queue_.empty() || current_index_ <= 0) {
    return false;
  }
  --current_index_;
  return true;
}

PlaybackSnapshot PlaybackQueue::Snapshot() const {
  PlaybackSnapshot snapshot;
  snapshot.queue = queue_;
  snapshot.current_index = current_index_;
  if (current_index_ >= 0 && current_index_ < static_cast<int>(queue_.size())) {
    snapshot.has_track = true;
    snapshot.current_track = queue_[current_index_];
  }
  return snapshot;
}

}  // namespace skodamusic::playback

