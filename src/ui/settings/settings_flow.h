#pragma once

#include <string>
#include <vector>

#include "src/config/config_runtime.h"

namespace skodamusic::ui::settings {

enum class SaveButtonState {
  kDisabled,
  kEnabled,
};

struct SettingsFeedback {
  SaveButtonState save_button_state = SaveButtonState::kDisabled;
  bool can_save = false;
  bool show_lrcapi_warning = false;
  std::string emby_test_message;
  std::string lrcapi_test_message;
  std::string save_hint_message;
  std::vector<config::RepairedField> startup_repaired_fields;
};

class ISettingsConfigStore {
 public:
  virtual ~ISettingsConfigStore() = default;
  virtual bool Persist(const config::AppConfig& draft) = 0;
};

class SettingsFlow final {
 public:
  explicit SettingsFlow(config::IConfigRuntime* runtime);

  SettingsFeedback RunTests(const config::AppConfig& draft) const;

  bool TrySave(const config::AppConfig& draft,
               ISettingsConfigStore* store,
               SettingsFeedback* out_feedback) const;

  static std::vector<std::string> BuildStartupRepairLines(
      const std::vector<config::RepairedField>& repaired_fields);

 private:
  config::IConfigRuntime* runtime_;
};

}  // namespace skodamusic::ui::settings

