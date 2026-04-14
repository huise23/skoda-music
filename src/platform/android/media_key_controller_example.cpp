#include "src/platform/android/media_key_controller.h"

#include <iostream>
#include <string>
#include <vector>

namespace {

void PrintState(const std::string& title,
                const skodamusic::platform::android::MediaKeySyncState& state) {
  std::cout << "=== " << title << " ===\n";
  std::cout << "home.has_track: " << (state.home.has_track ? "true" : "false")
            << '\n';
  std::cout << "home.title: " << state.home.title << '\n';
  std::cout << "home.artist: " << state.home.artist << '\n';
  std::cout << "home.hint: " << state.home.hint << '\n';
  std::cout << "queue.current_index: " << state.queue.current_index << '\n';
  std::cout << "queue.total_count: " << state.queue.total_count << '\n';
  std::cout << "queue.titles:";
  for (const auto& item : state.queue.titles) {
    std::cout << " [" << item << "]";
  }
  std::cout << "\n\n";
}

}  // namespace

int main() {
  skodamusic::playback::PlaybackQueue queue;
  queue.SetQueue(
      {
          {"t-1", "Song One", "Artist A"},
          {"t-2", "Song Two", "Artist B"},
          {"t-3", "Song Three", "Artist C"},
      },
      0);

  skodamusic::platform::android::MediaKeyController controller(&queue);

  PrintState("Initial", controller.CurrentState());

  const auto next_event =
      skodamusic::platform::android::MediaKeyController::FromAndroidKeyCode(87);
  const auto prev_event =
      skodamusic::platform::android::MediaKeyController::FromAndroidKeyCode(88);

  PrintState("After NEXT", controller.HandleMediaKey(next_event));
  PrintState("After NEXT", controller.HandleMediaKey(next_event));
  PrintState("After PREVIOUS", controller.HandleMediaKey(prev_event));
  return 0;
}

