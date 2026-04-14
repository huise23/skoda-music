#include "src/platform/android/audio_focus_manager.h"

#include <iostream>
#include <string>

namespace {

std::string ToFocusName(skodamusic::platform::android::AudioFocusState focus) {
  using Focus = skodamusic::platform::android::AudioFocusState;
  switch (focus) {
    case Focus::kNone:
      return "none";
    case Focus::kRequested:
      return "requested";
    case Focus::kGained:
      return "gained";
    case Focus::kTransientLoss:
      return "transient_loss";
    case Focus::kCanDuck:
      return "can_duck";
    case Focus::kLost:
      return "lost";
    default:
      return "unknown";
  }
}

std::string ToLifecycleName(skodamusic::platform::android::AppLifecycleState state) {
  return state == skodamusic::platform::android::AppLifecycleState::kForeground
             ? "foreground"
             : "background";
}

std::string ToActionName(skodamusic::platform::android::AudioFocusAction action) {
  using Action = skodamusic::platform::android::AudioFocusAction;
  switch (action) {
    case Action::kRequestFocus:
      return "request_focus";
    case Action::kAbandonFocus:
      return "abandon_focus";
    case Action::kPausePlayback:
      return "pause";
    case Action::kResumePlayback:
      return "resume";
    case Action::kDuckPlayback:
      return "duck";
    case Action::kUnduckPlayback:
      return "unduck";
    case Action::kNone:
    default:
      return "none";
  }
}

void PrintSnapshot(const std::string& title,
                   const skodamusic::platform::android::AudioFocusSnapshot& snapshot) {
  std::cout << "=== " << title << " ===\n";
  std::cout << "lifecycle: " << ToLifecycleName(snapshot.lifecycle_state) << '\n';
  std::cout << "focus: " << ToFocusName(snapshot.focus_state) << '\n';
  std::cout << "should_play: " << (snapshot.should_play ? "true" : "false") << '\n';
  std::cout << "is_ducking: " << (snapshot.is_ducking ? "true" : "false") << '\n';
  std::cout << "hint: " << snapshot.hint << "\n\n";
}

}  // namespace

int main() {
  skodamusic::platform::android::AudioFocusManager manager;
  manager.SetActionCallback([](skodamusic::platform::android::AudioFocusAction action) {
    std::cout << "[action] " << ToActionName(action) << '\n';
  });

  PrintSnapshot("enter foreground", manager.OnEnterForeground());
  PrintSnapshot("focus gain", manager.OnFocusChanged(1));
  PrintSnapshot("focus can duck", manager.OnFocusChanged(-3));
  PrintSnapshot("focus transient loss", manager.OnFocusChanged(-2));
  PrintSnapshot("focus gain", manager.OnFocusChanged(1));
  PrintSnapshot("enter background", manager.OnEnterBackground());

  return 0;
}

