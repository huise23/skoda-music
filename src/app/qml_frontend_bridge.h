#pragma once

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "src/config/config_runtime.h"
#include "src/lyrics/lyrics_resolver.h"
#include "src/platform/android/audio_focus_manager.h"
#include "src/platform/android/media_key_controller.h"
#include "src/playback/playback_queue.h"
#include "src/ui/home/home_shell.h"
#include "src/ui/settings/settings_flow.h"

namespace skodamusic::app {

enum class FrontendPage {
  kHome,
  kLibrary,
  kQueue,
  kSettings,
};

enum class FocusEvent {
  kForeground,
  kBackground,
  kGain,
  kLoss,
  kTransientLoss,
  kDuck,
};

class QmlFrontendBridge final {
 public:
  struct Snapshot {
    FrontendPage current_page = FrontendPage::kHome;
    ui::home::HomeShellState home;
    playback::PlaybackSnapshot queue;
    platform::android::MediaKeySyncState media_sync;
    platform::android::AudioFocusSnapshot audio_focus;
    lyrics::LyricsSnapshot lyrics;
    config::AppConfig draft_config;
    ui::settings::SettingsFeedback settings_feedback;
    std::vector<std::string> startup_repair_lines;
  };

  explicit QmlFrontendBridge(std::string config_path);
  ~QmlFrontendBridge();

  void Boot();

  void NavigateTo(FrontendPage page);
  bool NavigateToPageName(const std::string& page_name);

  void SetDraftEmby(const std::string& base_url,
                    const std::string& user_id,
                    const std::string& access_token);
  void SetDraftLrcApi(const std::string& base_url);

  ui::settings::SettingsFeedback RunSettingsTests();
  bool SaveSettings();

  platform::android::MediaKeySyncState NextTrack();
  platform::android::MediaKeySyncState PreviousTrack();
  bool PlayTrackAt(int index);

  void RequestLyricsForCurrentTrack();

  platform::android::AudioFocusSnapshot HandleFocusEvent(FocusEvent event);

  Snapshot SnapshotState() const;

  static const char* PageName(FrontendPage page);

 private:
  struct SharedAsyncState {
    std::mutex mu;
    lyrics::LyricsSnapshot latest_lyrics;
  };

  class FileConfigStore;
  class FakeLrcApiClient;

  config::ConfigRuntime config_runtime_;
  ui::settings::SettingsFlow settings_flow_;
  ui::home::HomeShell home_shell_;

  playback::PlaybackQueue playback_queue_;
  platform::android::MediaKeyController media_key_controller_;
  platform::android::AudioFocusManager audio_focus_manager_;

  std::string config_path_;
  std::unique_ptr<FileConfigStore> config_store_;
  std::unique_ptr<FakeLrcApiClient> lrcapi_client_;
  std::unique_ptr<lyrics::LyricsResolver> lyrics_resolver_;
  std::shared_ptr<SharedAsyncState> async_state_;

  FrontendPage current_page_ = FrontendPage::kHome;
  config::AppConfig runtime_config_;
  config::AppConfig draft_config_;
  ui::settings::SettingsFeedback settings_feedback_;
  std::vector<config::RepairedField> startup_repaired_fields_;
  std::vector<std::string> startup_repair_lines_;
  std::vector<ui::home::RecommendationItem> recommendations_;
  platform::android::MediaKeySyncState last_media_sync_;
};

}  // namespace skodamusic::app
