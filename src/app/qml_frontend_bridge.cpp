#include "src/app/qml_frontend_bridge.h"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <fstream>
#include <sstream>
#include <thread>
#include <unordered_map>
#include <utility>

namespace skodamusic::app {
namespace {

std::string ToLower(const std::string& value) {
  std::string out = value;
  std::transform(out.begin(), out.end(), out.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return out;
}

}  // namespace

class QmlFrontendBridge::FileConfigStore final
    : public ui::settings::ISettingsConfigStore {
 public:
  explicit FileConfigStore(std::string file_path)
      : file_path_(std::move(file_path)) {}

  bool Persist(const config::AppConfig& draft) override {
    std::ofstream out(file_path_, std::ios::trunc);
    if (!out.good()) {
      return false;
    }

    out << "emby:\n";
    out << "  base_url: \"" << draft.emby.base_url << "\"\n";
    out << "  user_id: \"" << draft.emby.user_id << "\"\n";
    out << "  access_token: \"" << draft.emby.access_token << "\"\n";
    out << "  request_timeout_ms: " << draft.emby.request_timeout_ms << "\n\n";

    out << "lyrics:\n";
    out << "  lrcapi:\n";
    out << "    base_url: \"" << draft.lyrics.lrcapi_base_url << "\"\n";
    out << "  fetch_timeout_ms: " << draft.lyrics.fetch_timeout_ms << "\n";
    out << "  cache_ttl_days: " << draft.lyrics.cache_ttl_days << "\n";
    out << "  prefer_language: \"" << draft.lyrics.prefer_language << "\"\n\n";

    out << "ui:\n";
    out << "  theme_mode: \"" << draft.ui.theme_mode << "\"\n";
    out << "  nav_layout: \"" << draft.ui.nav_layout << "\"\n";
    out << "  glass_intensity: \"" << draft.ui.glass_intensity << "\"\n";
    out << "  motion_level: \"" << draft.ui.motion_level << "\"\n";
    out << "  orientation: \"" << draft.ui.orientation << "\"\n\n";

    out << "player:\n";
    out << "  streaming_only: " << (draft.player.streaming_only ? "true" : "false")
        << "\n";
    out << "  recommendation_mode: \"" << draft.player.recommendation_mode << "\"\n\n";

    out << "controls:\n";
    out << "  steering_mapping: \"" << draft.controls.steering_mapping << "\"\n\n";

    out << "log:\n";
    out << "  level: \"" << draft.log.level << "\"\n";
    return out.good();
  }

 private:
  std::string file_path_;
};

class QmlFrontendBridge::FakeLrcApiClient final : public lyrics::ILrcApiClient {
 public:
  void FetchLyricsAsync(
      const lyrics::LyricsQuery& query,
      int timeout_ms,
      std::function<void(const lyrics::RemoteFetchResult&)> callback) override {
    {
      std::lock_guard<std::mutex> lock(mu_);
      canceled_[query.request_id] = false;
    }

    std::thread([this, query, timeout_ms, callback]() {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(std::clamp(timeout_ms / 4, 50, 250)));

      {
        std::lock_guard<std::mutex> lock(mu_);
        if (canceled_[query.request_id]) {
          callback({lyrics::RemoteFetchCode::kCanceled, "", "canceled"});
          return;
        }
      }

      if (query.lrcapi_base_url.empty()) {
        callback({lyrics::RemoteFetchCode::kFailed, "", "missing lrcapi"});
        return;
      }

      if (ToLower(query.title).find("nolyrics") != std::string::npos) {
        callback({lyrics::RemoteFetchCode::kNotFound, "", "no lyrics"});
        return;
      }

      std::ostringstream lrc;
      lrc << "[00:01.00]" << query.title << " - " << query.artist << "\n";
      lrc << "[00:05.00]This is mock remote lyrics";
      callback({lyrics::RemoteFetchCode::kOk, lrc.str(), "ok"});
    }).detach();
  }

  void CancelRequest(std::int64_t request_id) override {
    std::lock_guard<std::mutex> lock(mu_);
    canceled_[request_id] = true;
  }

 private:
  std::mutex mu_;
  std::unordered_map<std::int64_t, bool> canceled_;
};

QmlFrontendBridge::QmlFrontendBridge(std::string config_path)
    : settings_flow_(&config_runtime_),
      home_shell_(&config_runtime_),
      media_key_controller_(&playback_queue_),
      config_path_(std::move(config_path)),
      config_store_(std::make_unique<FileConfigStore>(config_path_)),
      lrcapi_client_(std::make_unique<FakeLrcApiClient>()),
      lyrics_resolver_(std::make_unique<lyrics::LyricsResolver>(lrcapi_client_.get(),
                                                                2000)),
      async_state_(std::make_shared<SharedAsyncState>()) {}

QmlFrontendBridge::~QmlFrontendBridge() = default;

void QmlFrontendBridge::Boot() {
  const auto load_result = config_runtime_.LoadAndSelfHeal(config_path_);
  runtime_config_ = load_result.config;
  if (runtime_config_.lyrics.lrcapi_base_url.empty()) {
    runtime_config_.lyrics.lrcapi_base_url = "http://lrcapi.local";
  }
  draft_config_ = runtime_config_;

  startup_repaired_fields_ = load_result.report.repaired_fields;
  startup_repair_lines_ = ui::settings::SettingsFlow::BuildStartupRepairLines(
      startup_repaired_fields_);

  playback_queue_.SetQueue(
      {
          {"track-1", "Morning Drive", "Skoda Band"},
          {"track-2", "NoLyrics Demo", "Skoda Band"},
          {"track-3", "Night Cruise", "Skoda Band"},
      },
      0);

  recommendations_ = {
      {"rec-1", "Road Mix 1", "Server"},
      {"rec-2", "Road Mix 2", "Server"},
  };

  media_key_controller_.SetSyncCallback(
      [this](const platform::android::MediaKeySyncState& state) {
        last_media_sync_ = state;
      });
  last_media_sync_ = media_key_controller_.CurrentState();

  {
    std::lock_guard<std::mutex> lock(async_state_->mu);
    async_state_->latest_lyrics = {};
    async_state_->latest_lyrics.state = lyrics::LyricsState::kIdle;
  }

  settings_feedback_ = settings_flow_.RunTests(draft_config_);
  settings_feedback_.startup_repaired_fields = startup_repaired_fields_;
  current_page_ = FrontendPage::kHome;
}

void QmlFrontendBridge::NavigateTo(FrontendPage page) {
  current_page_ = page;
}

bool QmlFrontendBridge::NavigateToPageName(const std::string& page_name) {
  const std::string key = ToLower(page_name);
  if (key == "home") {
    current_page_ = FrontendPage::kHome;
    return true;
  }
  if (key == "library") {
    current_page_ = FrontendPage::kLibrary;
    return true;
  }
  if (key == "queue") {
    current_page_ = FrontendPage::kQueue;
    return true;
  }
  if (key == "settings") {
    current_page_ = FrontendPage::kSettings;
    return true;
  }
  return false;
}

void QmlFrontendBridge::SetDraftEmby(const std::string& base_url,
                                     const std::string& user_id,
                                     const std::string& access_token) {
  draft_config_.emby.base_url = base_url;
  draft_config_.emby.user_id = user_id;
  draft_config_.emby.access_token = access_token;
}

void QmlFrontendBridge::SetDraftLrcApi(const std::string& base_url) {
  draft_config_.lyrics.lrcapi_base_url = base_url;
}

ui::settings::SettingsFeedback QmlFrontendBridge::RunSettingsTests() {
  settings_feedback_ = settings_flow_.RunTests(draft_config_);
  settings_feedback_.startup_repaired_fields = startup_repaired_fields_;
  return settings_feedback_;
}

bool QmlFrontendBridge::SaveSettings() {
  const bool saved =
      settings_flow_.TrySave(draft_config_, config_store_.get(), &settings_feedback_);
  settings_feedback_.startup_repaired_fields = startup_repaired_fields_;
  if (saved) {
    runtime_config_ = draft_config_;
  }
  return saved;
}

platform::android::MediaKeySyncState QmlFrontendBridge::NextTrack() {
  last_media_sync_ =
      media_key_controller_.HandleMediaKey(platform::android::MediaKeyEvent::kNext);
  return last_media_sync_;
}

platform::android::MediaKeySyncState QmlFrontendBridge::PreviousTrack() {
  last_media_sync_ = media_key_controller_.HandleMediaKey(
      platform::android::MediaKeyEvent::kPrevious);
  return last_media_sync_;
}

bool QmlFrontendBridge::PlayTrackAt(int index) {
  const auto snapshot = playback_queue_.Snapshot();
  if (snapshot.queue.empty()) {
    return false;
  }
  playback_queue_.SetQueue(snapshot.queue, index);
  last_media_sync_ = media_key_controller_.CurrentState();
  return true;
}

void QmlFrontendBridge::RequestLyricsForCurrentTrack() {
  const auto snapshot = playback_queue_.Snapshot();
  if (!snapshot.has_track) {
    std::lock_guard<std::mutex> lock(async_state_->mu);
    async_state_->latest_lyrics = {
        "",
        0,
        lyrics::LyricsState::kNoLyrics,
        lyrics::LyricsSource::kNone,
        "",
        "No active track",
    };
    return;
  }

  lyrics::TrackLyricsContext context;
  context.track_id = snapshot.current_track.track_id;
  context.title = snapshot.current_track.title;
  context.artist = snapshot.current_track.artist;
  context.lrcapi_base_url = runtime_config_.lyrics.lrcapi_base_url;
  if (context.track_id == "track-1") {
    context.embedded_lyrics = "[00:01.00]Embedded lyrics sample";
  }

  const auto state = async_state_;
  lyrics_resolver_->StartResolve(
      context, [state](const lyrics::LyricsSnapshot& value) {
        std::lock_guard<std::mutex> lock(state->mu);
        state->latest_lyrics = value;
      });
}

platform::android::AudioFocusSnapshot QmlFrontendBridge::HandleFocusEvent(
    FocusEvent event) {
  if (event == FocusEvent::kForeground) {
    return audio_focus_manager_.OnEnterForeground();
  }
  if (event == FocusEvent::kBackground) {
    return audio_focus_manager_.OnEnterBackground();
  }
  if (event == FocusEvent::kGain) {
    return audio_focus_manager_.OnFocusChanged(1);
  }
  if (event == FocusEvent::kLoss) {
    return audio_focus_manager_.OnFocusChanged(-1);
  }
  if (event == FocusEvent::kTransientLoss) {
    return audio_focus_manager_.OnFocusChanged(-2);
  }
  return audio_focus_manager_.OnFocusChanged(-3);
}

QmlFrontendBridge::Snapshot QmlFrontendBridge::SnapshotState() const {
  Snapshot snapshot;
  snapshot.current_page = current_page_;
  snapshot.home = home_shell_.BuildState(runtime_config_, recommendations_);
  snapshot.queue = playback_queue_.Snapshot();
  snapshot.media_sync = last_media_sync_;
  snapshot.audio_focus = audio_focus_manager_.Snapshot();
  snapshot.draft_config = draft_config_;
  snapshot.settings_feedback = settings_feedback_;
  snapshot.settings_feedback.startup_repaired_fields = startup_repaired_fields_;
  snapshot.startup_repair_lines = startup_repair_lines_;
  {
    std::lock_guard<std::mutex> lock(async_state_->mu);
    snapshot.lyrics = async_state_->latest_lyrics;
  }
  return snapshot;
}

const char* QmlFrontendBridge::PageName(FrontendPage page) {
  if (page == FrontendPage::kHome) {
    return "home";
  }
  if (page == FrontendPage::kLibrary) {
    return "library";
  }
  if (page == FrontendPage::kQueue) {
    return "queue";
  }
  return "settings";
}

}  // namespace skodamusic::app
