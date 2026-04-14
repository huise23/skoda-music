#pragma once

#include <functional>
#include <string>

namespace skodamusic::platform::android {

enum class AppLifecycleState {
  kForeground,
  kBackground,
};

enum class AudioFocusState {
  kNone,
  kRequested,
  kGained,
  kTransientLoss,
  kCanDuck,
  kLost,
};

enum class AudioFocusAction {
  kNone,
  kRequestFocus,
  kAbandonFocus,
  kPausePlayback,
  kResumePlayback,
  kDuckPlayback,
  kUnduckPlayback,
};

struct AudioFocusSnapshot {
  AppLifecycleState lifecycle_state = AppLifecycleState::kForeground;
  AudioFocusState focus_state = AudioFocusState::kNone;
  bool should_play = false;
  bool is_ducking = false;
  std::string hint;
};

class AudioFocusManager final {
 public:
  AudioFocusManager();

  void SetActionCallback(std::function<void(AudioFocusAction)> callback);

  AudioFocusSnapshot OnEnterForeground();
  AudioFocusSnapshot OnEnterBackground();

  AudioFocusSnapshot OnFocusChanged(int android_focus_change_code);

  AudioFocusSnapshot Snapshot() const;

 private:
  AudioFocusSnapshot EmitAction(AudioFocusAction action, const std::string& hint);

  std::function<void(AudioFocusAction)> action_callback_;
  AudioFocusSnapshot snapshot_;
};

}  // namespace skodamusic::platform::android

