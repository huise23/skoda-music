#include "src/config/config_runtime.h"
#include "src/lyrics/lyrics_resolver.h"
#include "src/platform/android/audio_focus_manager.h"
#include "src/platform/android/media_key_controller.h"
#include "src/playback/playback_queue.h"
#include "src/ui/home/home_shell.h"
#include "src/ui/settings/settings_flow.h"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <fstream>
#include <iostream>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

class FileConfigStore final : public skodamusic::ui::settings::ISettingsConfigStore {
 public:
  explicit FileConfigStore(std::string file_path) : file_path_(std::move(file_path)) {}

  bool Persist(const skodamusic::config::AppConfig& draft) override {
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

class FakeLrcApiClient final : public skodamusic::lyrics::ILrcApiClient {
 public:
  void FetchLyricsAsync(
      const skodamusic::lyrics::LyricsQuery& query,
      int timeout_ms,
      std::function<void(const skodamusic::lyrics::RemoteFetchResult&)> callback) override {
    {
      std::lock_guard<std::mutex> lock(mu_);
      canceled_[query.request_id] = false;
    }

    std::thread([this, query, timeout_ms, callback]() {
      std::this_thread::sleep_for(std::chrono::milliseconds(
          std::clamp(timeout_ms / 4, 50, 250)));

      {
        std::lock_guard<std::mutex> lock(mu_);
        if (canceled_[query.request_id]) {
          callback({skodamusic::lyrics::RemoteFetchCode::kCanceled, "", "canceled"});
          return;
        }
      }

      if (query.lrcapi_base_url.empty()) {
        callback({skodamusic::lyrics::RemoteFetchCode::kFailed, "", "missing lrcapi"});
        return;
      }

      const std::string lower_title = ToLower(query.title);
      if (lower_title.find("nolyrics") != std::string::npos) {
        callback({skodamusic::lyrics::RemoteFetchCode::kNotFound, "", "no lyrics"});
        return;
      }

      std::ostringstream lrc;
      lrc << "[00:01.00]" << query.title << " - " << query.artist << "\n";
      lrc << "[00:05.00]这是模拟歌词（LrcApi）";
      callback({skodamusic::lyrics::RemoteFetchCode::kOk, lrc.str(), "ok"});
    }).detach();
  }

  void CancelRequest(std::int64_t request_id) override {
    std::lock_guard<std::mutex> lock(mu_);
    canceled_[request_id] = true;
  }

 private:
  static std::string ToLower(const std::string& value) {
    std::string out = value;
    std::transform(out.begin(), out.end(), out.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return out;
  }

  std::mutex mu_;
  std::unordered_map<std::int64_t, bool> canceled_;
};

std::vector<std::string> Split(const std::string& line) {
  std::istringstream in(line);
  std::vector<std::string> out;
  std::string token;
  while (in >> token) {
    out.push_back(token);
  }
  return out;
}

std::string JoinFrom(const std::vector<std::string>& parts, size_t start) {
  if (start >= parts.size()) {
    return "";
  }
  std::ostringstream out;
  for (size_t i = start; i < parts.size(); ++i) {
    if (i != start) {
      out << ' ';
    }
    out << parts[i];
  }
  return out.str();
}

class MvpApp final {
 public:
  MvpApp()
      : settings_flow_(&config_runtime_),
        home_shell_(&config_runtime_),
        media_key_controller_(&playback_queue_),
        config_store_("config/app.config.yaml"),
        lyrics_resolver_(&lrcapi_client_, 2000) {
    Initialize();
  }

  int Run() {
    PrintBanner();
    std::string line;
    while (true) {
      std::cout << "\nskoda-mvp> ";
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
      } else if (cmd == "status") {
        PrintStatus();
      } else if (cmd == "home") {
        PrintHome();
      } else if (cmd == "queue") {
        PrintQueue();
      } else if (cmd == "next") {
        HandleMediaKey(skodamusic::platform::android::MediaKeyEvent::kNext);
      } else if (cmd == "prev") {
        HandleMediaKey(skodamusic::platform::android::MediaKeyEvent::kPrevious);
      } else if (cmd == "settings-test") {
        RunSettingsTest();
      } else if (cmd == "settings-save") {
        RunSettingsSave();
      } else if (cmd == "set-emby") {
        HandleSetEmby(args);
      } else if (cmd == "set-lrcapi") {
        HandleSetLrcApi(args);
      } else if (cmd == "focus") {
        HandleFocus(args);
      } else if (cmd == "lyrics") {
        ResolveLyricsForCurrentTrack();
      } else if (cmd == "play") {
        HandlePlay(args);
      } else if (cmd == "quit" || cmd == "exit") {
        return 0;
      } else {
        std::cout << "未知命令，输入 help 查看可用命令。";
      }
    }
  }

 private:
  void Initialize() {
    const auto load_result = config_runtime_.LoadAndSelfHeal("config/app.config.yaml");
    runtime_config_ = load_result.config;
    if (runtime_config_.lyrics.lrcapi_base_url.empty()) {
      runtime_config_.lyrics.lrcapi_base_url = "http://lrcapi.local";
    }

    if (load_result.report.had_repairs) {
      std::cout << "[toast] " << load_result.report.toast_summary << '\n';
      for (const auto& line :
           skodamusic::ui::settings::SettingsFlow::BuildStartupRepairLines(
               load_result.report.repaired_fields)) {
        std::cout << "  - " << line << '\n';
      }
    }

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
        [](const skodamusic::platform::android::MediaKeySyncState& state) {
          std::cout << "[media-sync] index=" << state.queue.current_index
                    << " total=" << state.queue.total_count;
          if (state.home.has_track) {
            std::cout << " now=" << state.home.title;
          }
          std::cout << '\n';
        });

    audio_focus_manager_.SetActionCallback(
        [](skodamusic::platform::android::AudioFocusAction action) {
          std::cout << "[focus-action] " << FocusActionName(action) << '\n';
        });
  }

  static const char* FocusActionName(
      skodamusic::platform::android::AudioFocusAction action) {
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

  void PrintBanner() const {
    std::cout << "Skoda Music MVP App (Console Shell)\n";
    std::cout << "输入 help 查看命令。\n";
  }

  void PrintHelp() const {
    std::cout << "命令列表:\n";
    std::cout << "  help                     查看帮助\n";
    std::cout << "  status                   查看运行状态\n";
    std::cout << "  home                     查看首页状态\n";
    std::cout << "  queue                    查看播放队列\n";
    std::cout << "  play <index>             跳到队列 index\n";
    std::cout << "  next / prev              模拟方向盘上下曲\n";
    std::cout << "  set-emby <url> <uid> <token>\n";
    std::cout << "  set-lrcapi <url>\n";
    std::cout << "  settings-test            测试配置可保存状态\n";
    std::cout << "  settings-save            执行保存\n";
    std::cout << "  lyrics                   为当前曲目解析歌词\n";
    std::cout << "  focus fg|bg|gain|loss|transient|duck\n";
    std::cout << "  quit                     退出\n";
  }

  void PrintStatus() {
    const auto emby = config_runtime_.TestEmbyConnection(runtime_config_);
    const auto lrc = config_runtime_.TestLrcApiConnection(runtime_config_);
    const bool can_save = config_runtime_.CanSaveConfig(emby);
    std::cout << "[status] emby=" << emby.message << '\n';
    std::cout << "[status] lrcapi=" << lrc.message << '\n';
    std::cout << "[status] can_save=" << (can_save ? "true" : "false") << '\n';

    const auto focus = audio_focus_manager_.Snapshot();
    std::cout << "[status] focus_hint=" << focus.hint << '\n';
  }

  void PrintHome() {
    const auto state = home_shell_.BuildState(runtime_config_, recommendations_);
    std::cout << "[home] playback_hint=" << state.playback_hint << '\n';
    std::cout << "[home] recommendation_hint=" << state.recommendation_hint << '\n';
    std::cout << "[home] recommendation_count=" << state.recommendations.size() << '\n';
  }

  void PrintQueue() const {
    const auto snapshot = playback_queue_.Snapshot();
    std::cout << "[queue] current_index=" << snapshot.current_index << '\n';
    for (size_t i = 0; i < snapshot.queue.size(); ++i) {
      const auto& track = snapshot.queue[i];
      std::cout << "  [" << i << "] " << track.title << " - " << track.artist;
      if (static_cast<int>(i) == snapshot.current_index) {
        std::cout << "  <==";
      }
      std::cout << '\n';
    }
  }

  void HandleMediaKey(skodamusic::platform::android::MediaKeyEvent event) {
    media_key_controller_.HandleMediaKey(event);
  }

  void HandleSetEmby(const std::vector<std::string>& args) {
    if (args.size() < 4) {
      std::cout << "用法: set-emby <url> <uid> <token>";
      return;
    }
    runtime_config_.emby.base_url = args[1];
    runtime_config_.emby.user_id = args[2];
    runtime_config_.emby.access_token = args[3];
    std::cout << "已更新 Emby 配置。";
  }

  void HandleSetLrcApi(const std::vector<std::string>& args) {
    if (args.size() < 2) {
      std::cout << "用法: set-lrcapi <url>";
      return;
    }
    runtime_config_.lyrics.lrcapi_base_url = args[1];
    std::cout << "已更新 LrcApi 配置。";
  }

  void RunSettingsTest() const {
    const auto feedback = settings_flow_.RunTests(runtime_config_);
    std::cout << "[settings] " << feedback.emby_test_message << '\n';
    std::cout << "[settings] " << feedback.lrcapi_test_message << '\n';
    std::cout << "[settings] can_save=" << (feedback.can_save ? "true" : "false") << '\n';
  }

  void RunSettingsSave() {
    skodamusic::ui::settings::SettingsFeedback feedback;
    const bool ok = settings_flow_.TrySave(runtime_config_, &config_store_, &feedback);
    std::cout << "[save] " << feedback.save_hint_message << '\n';
    if (!feedback.lrcapi_test_message.empty()) {
      std::cout << "[save] " << feedback.lrcapi_test_message << '\n';
    }
    if (ok) {
      std::cout << "[save] 配置已落盘到 config/app.config.yaml\n";
    }
  }

  void HandleFocus(const std::vector<std::string>& args) {
    if (args.size() < 2) {
      std::cout << "用法: focus fg|bg|gain|loss|transient|duck";
      return;
    }
    const std::string& op = args[1];
    if (op == "fg") {
      audio_focus_manager_.OnEnterForeground();
    } else if (op == "bg") {
      audio_focus_manager_.OnEnterBackground();
    } else if (op == "gain") {
      audio_focus_manager_.OnFocusChanged(1);
    } else if (op == "loss") {
      audio_focus_manager_.OnFocusChanged(-1);
    } else if (op == "transient") {
      audio_focus_manager_.OnFocusChanged(-2);
    } else if (op == "duck") {
      audio_focus_manager_.OnFocusChanged(-3);
    } else {
      std::cout << "未知 focus 操作";
      return;
    }
    const auto snapshot = audio_focus_manager_.Snapshot();
    std::cout << "[focus] " << snapshot.hint << '\n';
  }

  void ResolveLyricsForCurrentTrack() {
    const auto snapshot = playback_queue_.Snapshot();
    if (!snapshot.has_track) {
      std::cout << "[lyrics] 当前无曲目";
      return;
    }

    skodamusic::lyrics::TrackLyricsContext context;
    context.track_id = snapshot.current_track.track_id;
    context.title = snapshot.current_track.title;
    context.artist = snapshot.current_track.artist;
    context.lrcapi_base_url = runtime_config_.lyrics.lrcapi_base_url;
    if (snapshot.current_track.track_id == "track-1") {
      context.embedded_lyrics = "[00:01.00]这是嵌入歌词示例";
    }

    lyrics_resolver_.StartResolve(
        context, [](const skodamusic::lyrics::LyricsSnapshot& value) {
          std::cout << "[lyrics] track=" << value.track_id << " state="
                    << static_cast<int>(value.state) << " source="
                    << static_cast<int>(value.source);
          if (!value.message.empty()) {
            std::cout << " message=" << value.message;
          }
          std::cout << '\n';
        });

    std::this_thread::sleep_for(std::chrono::milliseconds(300));
  }

  void HandlePlay(const std::vector<std::string>& args) {
    if (args.size() < 2) {
      std::cout << "用法: play <index>";
      return;
    }
    int index = 0;
    try {
      index = std::stoi(args[1]);
    } catch (...) {
      std::cout << "index 非法";
      return;
    }
    const auto snapshot = playback_queue_.Snapshot();
    playback_queue_.SetQueue(snapshot.queue, index);
    media_key_controller_.CurrentState();
    std::cout << "已切换到 index=" << index;
  }

  skodamusic::config::ConfigRuntime config_runtime_;
  skodamusic::ui::settings::SettingsFlow settings_flow_;
  skodamusic::ui::home::HomeShell home_shell_;

  skodamusic::playback::PlaybackQueue playback_queue_;
  skodamusic::platform::android::MediaKeyController media_key_controller_;
  skodamusic::platform::android::AudioFocusManager audio_focus_manager_;

  FileConfigStore config_store_;
  FakeLrcApiClient lrcapi_client_;
  skodamusic::lyrics::LyricsResolver lyrics_resolver_;

  skodamusic::config::AppConfig runtime_config_;
  std::vector<skodamusic::ui::home::RecommendationItem> recommendations_;
};

}  // namespace

int main() {
  MvpApp app;
  return app.Run();
}
