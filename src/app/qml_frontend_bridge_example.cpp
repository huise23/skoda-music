#include "src/app/qml_frontend_bridge.h"

#include <chrono>
#include <iostream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

namespace {

std::vector<std::string> Split(const std::string& line) {
  std::istringstream in(line);
  std::vector<std::string> parts;
  std::string token;
  while (in >> token) {
    parts.push_back(token);
  }
  return parts;
}

const char* SaveButtonLabel(
    skodamusic::ui::settings::SaveButtonState value) {
  return value == skodamusic::ui::settings::SaveButtonState::kEnabled ? "enabled"
                                                                       : "disabled";
}

const char* LyricsStateLabel(skodamusic::lyrics::LyricsState state) {
  using State = skodamusic::lyrics::LyricsState;
  switch (state) {
    case State::kIdle:
      return "idle";
    case State::kLoading:
      return "loading";
    case State::kReady:
      return "ready";
    case State::kNoLyrics:
      return "no_lyrics";
    case State::kError:
      return "error";
    default:
      return "unknown";
  }
}

skodamusic::app::FocusEvent ParseFocusEvent(const std::string& value,
                                            bool* ok) {
  *ok = true;
  if (value == "fg") {
    return skodamusic::app::FocusEvent::kForeground;
  }
  if (value == "bg") {
    return skodamusic::app::FocusEvent::kBackground;
  }
  if (value == "gain") {
    return skodamusic::app::FocusEvent::kGain;
  }
  if (value == "loss") {
    return skodamusic::app::FocusEvent::kLoss;
  }
  if (value == "transient") {
    return skodamusic::app::FocusEvent::kTransientLoss;
  }
  if (value == "duck") {
    return skodamusic::app::FocusEvent::kDuck;
  }
  *ok = false;
  return skodamusic::app::FocusEvent::kGain;
}

void PrintStatus(const skodamusic::app::QmlFrontendBridge::Snapshot& state) {
  std::cout << "[page] " << skodamusic::app::QmlFrontendBridge::PageName(state.current_page)
            << '\n';
  std::cout << "[home] playback_hint=" << state.home.playback_hint << '\n';
  std::cout << "[home] recommendation_hint=" << state.home.recommendation_hint << '\n';
  std::cout << "[queue] current_index=" << state.queue.current_index
            << " total=" << state.queue.queue.size() << '\n';
  if (state.queue.has_track) {
    std::cout << "[queue] now_playing=" << state.queue.current_track.title << " - "
              << state.queue.current_track.artist << '\n';
  }
  std::cout << "[settings] save_button="
            << SaveButtonLabel(state.settings_feedback.save_button_state) << '\n';
  std::cout << "[settings] emby_test=" << state.settings_feedback.emby_test_message
            << '\n';
  std::cout << "[settings] lrcapi_test=" << state.settings_feedback.lrcapi_test_message
            << '\n';
  std::cout << "[lyrics] state=" << LyricsStateLabel(state.lyrics.state)
            << " message=" << state.lyrics.message << '\n';
  std::cout << "[focus] hint=" << state.audio_focus.hint << '\n';
}

void PrintHelp() {
  std::cout << "commands:\n";
  std::cout << "  help\n";
  std::cout << "  status\n";
  std::cout << "  nav home|library|queue|settings\n";
  std::cout << "  next | prev | play <index>\n";
  std::cout << "  set-emby <url> <uid> <token>\n";
  std::cout << "  set-lrcapi <url>\n";
  std::cout << "  test | save\n";
  std::cout << "  lyrics\n";
  std::cout << "  focus fg|bg|gain|loss|transient|duck\n";
  std::cout << "  quit\n";
}

}  // namespace

int main() {
  skodamusic::app::QmlFrontendBridge bridge("config/app.config.yaml");
  bridge.Boot();

  PrintHelp();
  std::string line;
  while (true) {
    std::cout << "\nqml-bridge> ";
    if (!std::getline(std::cin, line)) {
      return 0;
    }

    const auto args = Split(line);
    if (args.empty()) {
      continue;
    }

    const std::string& cmd = args[0];
    if (cmd == "help") {
      PrintHelp();
      continue;
    }
    if (cmd == "status") {
      PrintStatus(bridge.SnapshotState());
      continue;
    }
    if (cmd == "nav") {
      if (args.size() < 2 || !bridge.NavigateToPageName(args[1])) {
        std::cout << "usage: nav home|library|queue|settings";
      }
      continue;
    }
    if (cmd == "next") {
      bridge.NextTrack();
      continue;
    }
    if (cmd == "prev") {
      bridge.PreviousTrack();
      continue;
    }
    if (cmd == "play") {
      if (args.size() < 2) {
        std::cout << "usage: play <index>";
        continue;
      }
      try {
        const int index = std::stoi(args[1]);
        if (!bridge.PlayTrackAt(index)) {
          std::cout << "queue is empty";
        }
      } catch (...) {
        std::cout << "index is invalid";
      }
      continue;
    }
    if (cmd == "set-emby") {
      if (args.size() < 4) {
        std::cout << "usage: set-emby <url> <uid> <token>";
        continue;
      }
      bridge.SetDraftEmby(args[1], args[2], args[3]);
      continue;
    }
    if (cmd == "set-lrcapi") {
      if (args.size() < 2) {
        std::cout << "usage: set-lrcapi <url>";
        continue;
      }
      bridge.SetDraftLrcApi(args[1]);
      continue;
    }
    if (cmd == "test") {
      bridge.RunSettingsTests();
      PrintStatus(bridge.SnapshotState());
      continue;
    }
    if (cmd == "save") {
      bridge.SaveSettings();
      PrintStatus(bridge.SnapshotState());
      continue;
    }
    if (cmd == "lyrics") {
      bridge.RequestLyricsForCurrentTrack();
      std::this_thread::sleep_for(std::chrono::milliseconds(300));
      PrintStatus(bridge.SnapshotState());
      continue;
    }
    if (cmd == "focus") {
      if (args.size() < 2) {
        std::cout << "usage: focus fg|bg|gain|loss|transient|duck";
        continue;
      }
      bool ok = false;
      const auto event = ParseFocusEvent(args[1], &ok);
      if (!ok) {
        std::cout << "usage: focus fg|bg|gain|loss|transient|duck";
        continue;
      }
      bridge.HandleFocusEvent(event);
      PrintStatus(bridge.SnapshotState());
      continue;
    }
    if (cmd == "quit" || cmd == "exit") {
      return 0;
    }

    std::cout << "unknown command";
  }
}
