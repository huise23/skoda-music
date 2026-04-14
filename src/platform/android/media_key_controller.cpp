#include "src/platform/android/media_key_controller.h"

#include <utility>

namespace skodamusic::platform::android {
namespace {

constexpr int kAndroidKeyCodeMediaNext = 87;
constexpr int kAndroidKeyCodeMediaPrevious = 88;

constexpr char kHintNoTrack[] = "当前无可播放曲目";

}  // namespace

MediaKeyController::MediaKeyController(playback::PlaybackQueue* queue) : queue_(queue) {}

MediaKeyEvent MediaKeyController::FromAndroidKeyCode(int key_code) {
  if (key_code == kAndroidKeyCodeMediaNext) {
    return MediaKeyEvent::kNext;
  }
  if (key_code == kAndroidKeyCodeMediaPrevious) {
    return MediaKeyEvent::kPrevious;
  }
  return MediaKeyEvent::kUnknown;
}

void MediaKeyController::SetSyncCallback(
    std::function<void(const MediaKeySyncState&)> callback) {
  sync_callback_ = std::move(callback);
}

MediaKeySyncState MediaKeyController::HandleMediaKey(MediaKeyEvent event) {
  if (queue_ != nullptr) {
    if (event == MediaKeyEvent::kNext) {
      queue_->MoveNext();
    } else if (event == MediaKeyEvent::kPrevious) {
      queue_->MovePrevious();
    }
  }

  MediaKeySyncState state = BuildState();
  if (sync_callback_) {
    sync_callback_(state);
  }
  return state;
}

MediaKeySyncState MediaKeyController::CurrentState() const {
  return BuildState();
}

MediaKeySyncState MediaKeyController::BuildState() const {
  MediaKeySyncState state;
  if (queue_ == nullptr) {
    state.home.hint = kHintNoTrack;
    return state;
  }

  const playback::PlaybackSnapshot snapshot = queue_->Snapshot();
  state.queue.current_index = snapshot.current_index;
  state.queue.total_count = static_cast<int>(snapshot.queue.size());
  state.queue.titles.reserve(snapshot.queue.size());
  for (const auto& track : snapshot.queue) {
    state.queue.titles.push_back(track.title);
  }

  if (!snapshot.has_track) {
    state.home.hint = kHintNoTrack;
    return state;
  }

  state.home.has_track = true;
  state.home.title = snapshot.current_track.title;
  state.home.artist = snapshot.current_track.artist;
  state.home.hint = "";
  return state;
}

}  // namespace skodamusic::platform::android
