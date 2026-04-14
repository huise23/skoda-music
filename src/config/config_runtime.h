#pragma once

#include <string>
#include <vector>

namespace skodamusic::config {

struct EmbyConfig {
  std::string base_url;
  std::string user_id;
  std::string access_token;
  int request_timeout_ms = 5000;
};

struct LyricsConfig {
  std::string lrcapi_base_url;
  int fetch_timeout_ms = 2000;
  int cache_ttl_days = 30;
  std::string prefer_language = "zh";
};

struct UiConfig {
  std::string theme_mode = "dual";
  std::string nav_layout = "left";
  std::string glass_intensity = "light";
  std::string motion_level = "low";
  std::string orientation = "landscape";
};

struct PlayerConfig {
  bool streaming_only = true;
  std::string recommendation_mode = "server_first";
};

struct ControlsConfig {
  std::string steering_mapping = "media_key";
};

struct LogConfig {
  std::string level = "info";
};

struct AppConfig {
  EmbyConfig emby;
  LyricsConfig lyrics;
  UiConfig ui;
  PlayerConfig player;
  ControlsConfig controls;
  LogConfig log;
};

enum class ConfigErrorCode {
  kValidationError,
  kUnsupportedValue,
};

struct RepairedField {
  std::string field;
  std::string reason;
  std::string fallback_value;
};

struct RepairReport {
  bool had_repairs = false;
  std::vector<RepairedField> repaired_fields;
  std::vector<std::string> affected_capabilities;
  std::string toast_summary;
  bool persisted = false;
};

struct LoadResult {
  AppConfig config;
  RepairReport report;
};

enum class ConnectivityTestCode {
  kOk,
  kUnreachable,
  kAuthFailed,
  kInvalidResponse,
  kTimeout,
};

struct ConnectivityTestResult {
  ConnectivityTestCode code = ConnectivityTestCode::kInvalidResponse;
  std::string message;
};

class IConfigRuntime {
 public:
  virtual ~IConfigRuntime() = default;

  virtual LoadResult LoadAndSelfHeal(const std::string& file_path) = 0;

  virtual ConnectivityTestResult TestEmbyConnection(
      const AppConfig& config) = 0;

  virtual ConnectivityTestResult TestLrcApiConnection(
      const AppConfig& config) = 0;

  virtual bool CanSaveConfig(
      const ConnectivityTestResult& emby_test_result) = 0;
};

class ConfigRuntime final : public IConfigRuntime {
 public:
  LoadResult LoadAndSelfHeal(const std::string& file_path) override;
  ConnectivityTestResult TestEmbyConnection(
      const AppConfig& config) override;
  ConnectivityTestResult TestLrcApiConnection(
      const AppConfig& config) override;
  bool CanSaveConfig(const ConnectivityTestResult& emby_test_result) override;
};

}  // namespace skodamusic::config

