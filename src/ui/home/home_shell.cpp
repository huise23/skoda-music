#include "src/ui/home/home_shell.h"

namespace skodamusic::ui::home {
namespace {

constexpr char kPlaybackNotReady[] = "播放服务未就绪，请先完成 Emby 配置";
constexpr char kRecommendationNotReady[] = "推荐内容暂不可用，请先完成 Emby 配置";
constexpr char kPlaybackReady[] = "播放服务已就绪";
constexpr char kRecommendationEmpty[] = "暂无推荐内容";

}  // namespace

HomeShell::HomeShell(config::IConfigRuntime* runtime) : runtime_(runtime) {}

HomeShellState HomeShell::BuildState(
    const config::AppConfig& runtime_config,
    const std::vector<RecommendationItem>& server_recommendations) const {
  HomeShellState state;
  if (runtime_ == nullptr) {
    state.playback_state = HomeCapabilityState::kNotReady;
    state.controls_enabled = false;
    state.playback_hint = kPlaybackNotReady;
    state.recommendation_enabled = false;
    state.recommendation_hint = kRecommendationNotReady;
    return state;
  }

  const config::ConnectivityTestResult emby_result =
      runtime_->TestEmbyConnection(runtime_config);
  const bool emby_ready = runtime_->CanSaveConfig(emby_result);

  if (!emby_ready) {
    state.playback_state = HomeCapabilityState::kNotReady;
    state.controls_enabled = false;
    state.playback_hint = kPlaybackNotReady;
    state.recommendation_enabled = false;
    state.recommendation_hint = kRecommendationNotReady;
    return state;
  }

  state.playback_state = HomeCapabilityState::kReady;
  state.controls_enabled = true;
  state.playback_hint = kPlaybackReady;
  state.recommendation_enabled = true;
  state.recommendations = server_recommendations;
  state.recommendation_hint =
      state.recommendations.empty() ? kRecommendationEmpty : "";
  return state;
}

}  // namespace skodamusic::ui::home

