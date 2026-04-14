#include "src/ui/home/home_shell.h"

#include <iostream>
#include <string>
#include <vector>

namespace {

std::string ToPlaybackStateName(skodamusic::ui::home::HomeCapabilityState state) {
  return state == skodamusic::ui::home::HomeCapabilityState::kReady ? "ready"
                                                                     : "not_ready";
}

void PrintState(const std::string& title,
                const skodamusic::ui::home::HomeShellState& state) {
  std::cout << "=== " << title << " ===\n";
  std::cout << "playback_state: " << ToPlaybackStateName(state.playback_state) << '\n';
  std::cout << "controls_enabled: " << (state.controls_enabled ? "true" : "false")
            << '\n';
  std::cout << "playback_hint: " << state.playback_hint << '\n';
  std::cout << "recommendation_enabled: "
            << (state.recommendation_enabled ? "true" : "false") << '\n';
  std::cout << "recommendation_hint: " << state.recommendation_hint << '\n';
  std::cout << "recommendation_count: " << state.recommendations.size() << "\n\n";
}

}  // namespace

int main() {
  skodamusic::config::ConfigRuntime runtime;
  skodamusic::ui::home::HomeShell shell(&runtime);

  skodamusic::config::AppConfig emby_not_ready;
  emby_not_ready.lyrics.lrcapi_base_url = "http://lrcapi.local";
  PrintState("Emby not ready", shell.BuildState(emby_not_ready, {}));

  skodamusic::config::AppConfig emby_ready_no_recommendation;
  emby_ready_no_recommendation.emby.base_url = "http://emby.local";
  emby_ready_no_recommendation.emby.user_id = "user";
  emby_ready_no_recommendation.emby.access_token = "token";
  PrintState("Emby ready + empty recommendation",
             shell.BuildState(emby_ready_no_recommendation, {}));

  std::vector<skodamusic::ui::home::RecommendationItem> recommendation_list = {
      {"item-1", "Track One", "Artist A"},
      {"item-2", "Track Two", "Artist B"},
  };
  PrintState("Emby ready + recommendation",
             shell.BuildState(emby_ready_no_recommendation, recommendation_list));
  return 0;
}

