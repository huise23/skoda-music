#include "src/ui/settings/settings_flow.h"

#include <iostream>
#include <string>

namespace {

class FakeConfigStore final : public skodamusic::ui::settings::ISettingsConfigStore {
 public:
  explicit FakeConfigStore(bool persist_ok) : persist_ok_(persist_ok) {}

  bool Persist(const skodamusic::config::AppConfig&) override {
    return persist_ok_;
  }

 private:
  bool persist_ok_ = true;
};

void PrintResult(const std::string& title,
                 const skodamusic::ui::settings::SettingsFeedback& feedback,
                 bool saved) {
  std::cout << "=== " << title << " ===\n";
  std::cout << "saved: " << (saved ? "true" : "false") << '\n';
  std::cout << "can_save: " << (feedback.can_save ? "true" : "false") << '\n';
  std::cout << "emby: " << feedback.emby_test_message << '\n';
  std::cout << "lrcapi: " << feedback.lrcapi_test_message << '\n';
  std::cout << "save_hint: " << feedback.save_hint_message << "\n\n";
}

}  // namespace

int main() {
  skodamusic::config::ConfigRuntime runtime;
  skodamusic::ui::settings::SettingsFlow flow(&runtime);

  skodamusic::config::AppConfig bad_emby;
  bad_emby.emby.base_url = "http://emby.local";
  bad_emby.lyrics.lrcapi_base_url = "http://lrcapi.local";
  skodamusic::ui::settings::SettingsFeedback blocked_feedback;
  FakeConfigStore store_ok(true);
  const bool blocked = flow.TrySave(bad_emby, &store_ok, &blocked_feedback);
  PrintResult("Emby test failed -> save blocked", blocked_feedback, blocked);

  skodamusic::config::AppConfig emby_ok_lrc_fail;
  emby_ok_lrc_fail.emby.base_url = "http://emby.local";
  emby_ok_lrc_fail.emby.user_id = "user";
  emby_ok_lrc_fail.emby.access_token = "token";
  emby_ok_lrc_fail.lyrics.lrcapi_base_url = "invalid-url";
  skodamusic::ui::settings::SettingsFeedback warn_feedback;
  const bool warned = flow.TrySave(emby_ok_lrc_fail, &store_ok, &warn_feedback);
  PrintResult("Emby pass + LrcApi fail -> save allowed with warning", warn_feedback,
              warned);

  skodamusic::config::AppConfig all_ok;
  all_ok.emby.base_url = "http://emby.local";
  all_ok.emby.user_id = "user";
  all_ok.emby.access_token = "token";
  all_ok.lyrics.lrcapi_base_url = "http://lrcapi.local";
  skodamusic::ui::settings::SettingsFeedback pass_feedback;
  const bool passed = flow.TrySave(all_ok, &store_ok, &pass_feedback);
  PrintResult("All pass -> save success", pass_feedback, passed);

  return 0;
}

