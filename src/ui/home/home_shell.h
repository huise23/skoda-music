#pragma once

#include <string>
#include <vector>

#include "src/config/config_runtime.h"

namespace skodamusic::ui::home {

enum class HomeCapabilityState {
  kNotReady,
  kReady,
};

struct RecommendationItem {
  std::string item_id;
  std::string title;
  std::string artist;
};

struct HomeShellState {
  HomeCapabilityState playback_state = HomeCapabilityState::kNotReady;
  bool controls_enabled = false;
  std::string playback_hint;

  bool recommendation_enabled = false;
  std::string recommendation_hint;
  std::vector<RecommendationItem> recommendations;
};

class HomeShell final {
 public:
  explicit HomeShell(config::IConfigRuntime* runtime);

  HomeShellState BuildState(
      const config::AppConfig& runtime_config,
      const std::vector<RecommendationItem>& server_recommendations) const;

 private:
  config::IConfigRuntime* runtime_;
};

}  // namespace skodamusic::ui::home

