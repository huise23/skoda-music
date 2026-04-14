#include "src/ui/settings/settings_flow.h"

#include <sstream>
#include <utility>

namespace skodamusic::ui::settings {
namespace {

constexpr char kEmbyTestPassed[] = "Emby 连接测试通过，可保存配置";
constexpr char kEmbyTestFailed[] =
    "Emby 连接测试失败，请检查地址、用户ID或令牌";
constexpr char kSaveNeedsEmby[] = "需先通过 Emby 测试";
constexpr char kSaveBlockedByEmby[] = "未保存：请先通过 Emby 测试";
constexpr char kSaveSuccess[] = "配置已保存";
constexpr char kSaveFailed[] = "未保存：配置写入失败";
constexpr char kLrcApiPassed[] = "歌词服务连接正常";
constexpr char kLrcApiFailed[] = "歌词服务测试失败，可先保存基础配置";
constexpr char kLrcApiFollowup[] =
    "保存后可继续播放，歌词远程补全暂不可用";

}  // namespace

SettingsFlow::SettingsFlow(config::IConfigRuntime* runtime) : runtime_(runtime) {}

SettingsFeedback SettingsFlow::RunTests(const config::AppConfig& draft) const {
  SettingsFeedback feedback;
  if (runtime_ == nullptr) {
    feedback.emby_test_message = kEmbyTestFailed;
    feedback.save_hint_message = kSaveNeedsEmby;
    feedback.lrcapi_test_message = kLrcApiFailed;
    feedback.show_lrcapi_warning = true;
    return feedback;
  }

  const config::ConnectivityTestResult emby_result =
      runtime_->TestEmbyConnection(draft);
  const config::ConnectivityTestResult lrcapi_result =
      runtime_->TestLrcApiConnection(draft);

  feedback.can_save = runtime_->CanSaveConfig(emby_result);
  feedback.save_button_state = feedback.can_save
                                   ? SaveButtonState::kEnabled
                                   : SaveButtonState::kDisabled;
  feedback.emby_test_message =
      feedback.can_save ? kEmbyTestPassed : kEmbyTestFailed;
  feedback.save_hint_message =
      feedback.can_save ? "" : kSaveNeedsEmby;

  if (lrcapi_result.code == config::ConnectivityTestCode::kOk) {
    feedback.lrcapi_test_message = kLrcApiPassed;
  } else {
    feedback.lrcapi_test_message = kLrcApiFailed;
    feedback.show_lrcapi_warning = true;
  }

  return feedback;
}

bool SettingsFlow::TrySave(const config::AppConfig& draft,
                           ISettingsConfigStore* store,
                           SettingsFeedback* out_feedback) const {
  SettingsFeedback feedback = RunTests(draft);
  if (!feedback.can_save) {
    feedback.save_hint_message = kSaveBlockedByEmby;
    if (out_feedback != nullptr) {
      *out_feedback = std::move(feedback);
    }
    return false;
  }

  if (store == nullptr || !store->Persist(draft)) {
    feedback.save_hint_message = kSaveFailed;
    if (feedback.show_lrcapi_warning) {
      feedback.lrcapi_test_message = kLrcApiFollowup;
    }
    if (out_feedback != nullptr) {
      *out_feedback = std::move(feedback);
    }
    return false;
  }

  feedback.save_hint_message = kSaveSuccess;
  if (feedback.show_lrcapi_warning) {
    feedback.lrcapi_test_message = kLrcApiFollowup;
  }
  if (out_feedback != nullptr) {
    *out_feedback = std::move(feedback);
  }
  return true;
}

std::vector<std::string> SettingsFlow::BuildStartupRepairLines(
    const std::vector<config::RepairedField>& repaired_fields) {
  std::vector<std::string> lines;
  lines.reserve(repaired_fields.size());

  for (const auto& field : repaired_fields) {
    std::ostringstream line;
    line << field.field << "：" << field.reason << "，已回退为 "
         << field.fallback_value;
    lines.push_back(line.str());
  }

  return lines;
}

}  // namespace skodamusic::ui::settings

