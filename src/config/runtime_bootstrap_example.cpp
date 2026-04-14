#include "src/config/config_runtime.h"

#include <iostream>
#include <string>

namespace skodamusic::config {

void ShowStartupToast(const std::string& message) {
  if (message.empty()) {
    return;
  }
  std::cout << "[Toast] " << message << '\n';
}

}  // namespace skodamusic::config

int main() {
  skodamusic::config::ConfigRuntime runtime;
  const auto load_result = runtime.LoadAndSelfHeal("config/app.config.yaml");

  if (load_result.report.had_repairs) {
    skodamusic::config::ShowStartupToast(load_result.report.toast_summary);
  }

  std::cout << "persisted: "
            << (load_result.report.persisted ? "true" : "false") << '\n';
  std::cout << "repairs: " << load_result.report.repaired_fields.size() << '\n';
  return 0;
}

