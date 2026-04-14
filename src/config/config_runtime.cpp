#include "src/config/config_runtime.h"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <optional>
#include <set>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

namespace skodamusic::config {
namespace {

constexpr char kRepairToastSummary[] =
    "部分功能配置有问题，已设置为默认值";

std::string Trim(const std::string& input) {
  const auto begin = std::find_if_not(
      input.begin(), input.end(), [](unsigned char c) { return std::isspace(c); });
  if (begin == input.end()) {
    return "";
  }
  const auto end = std::find_if_not(
      input.rbegin(), input.rend(), [](unsigned char c) { return std::isspace(c); })
                       .base();
  return std::string(begin, end);
}

std::string StripQuotes(const std::string& input) {
  if (input.size() >= 2U) {
    const char first = input.front();
    const char last = input.back();
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return input.substr(1, input.size() - 2U);
    }
  }
  return input;
}

bool StartsWith(const std::string& value, const std::string& prefix) {
  return value.size() >= prefix.size() &&
         value.compare(0, prefix.size(), prefix) == 0;
}

bool IsHttpUrl(const std::string& value) {
  return StartsWith(value, "http://") || StartsWith(value, "https://");
}

std::optional<int> ParseInt(const std::string& value) {
  try {
    size_t consumed = 0;
    const int parsed = std::stoi(value, &consumed);
    if (consumed != value.size()) {
      return std::nullopt;
    }
    return parsed;
  } catch (...) {
    return std::nullopt;
  }
}

std::optional<bool> ParseBool(const std::string& value) {
  if (value == "true") {
    return true;
  }
  if (value == "false") {
    return false;
  }
  return std::nullopt;
}

std::string JoinPath(const std::vector<std::string>& parts, const std::string& leaf) {
  std::ostringstream builder;
  for (size_t i = 0; i < parts.size(); ++i) {
    if (i != 0U) {
      builder << '.';
    }
    builder << parts[i];
  }
  if (!parts.empty()) {
    builder << '.';
  }
  builder << leaf;
  return builder.str();
}

struct SimpleYamlParseResult {
  bool ok = true;
  std::unordered_map<std::string, std::string> values;
};

SimpleYamlParseResult ParseSimpleYaml(std::istream& input) {
  SimpleYamlParseResult result;
  std::vector<std::string> sections;
  std::string line;

  while (std::getline(input, line)) {
    const size_t comment_index = line.find('#');
    if (comment_index != std::string::npos) {
      line = line.substr(0, comment_index);
    }

    if (Trim(line).empty()) {
      continue;
    }

    size_t indent = 0;
    while (indent < line.size() && line[indent] == ' ') {
      ++indent;
    }
    if (indent % 2 != 0) {
      result.ok = false;
      continue;
    }

    const size_t level = indent / 2;
    std::string payload = Trim(line.substr(indent));
    const size_t colon_index = payload.find(':');
    if (colon_index == std::string::npos) {
      result.ok = false;
      continue;
    }

    std::string key = Trim(payload.substr(0, colon_index));
    std::string raw_value = Trim(payload.substr(colon_index + 1));

    if (sections.size() > level) {
      sections.resize(level);
    }

    if (raw_value.empty()) {
      if (sections.size() == level) {
        sections.push_back(key);
      } else {
        sections[level] = key;
      }
      continue;
    }

    std::string value = StripQuotes(raw_value);
    result.values[JoinPath(sections, key)] = value;
  }

  return result;
}

void AddRepair(RepairReport& report,
               const std::string& field,
               const std::string& reason,
               const std::string& fallback_value) {
  report.had_repairs = true;
  report.repaired_fields.push_back({field, reason, fallback_value});
}

void AppendAffectedCapability(std::set<std::string>& capabilities,
                              const std::string& field) {
  if (StartsWith(field, "emby.")) {
    capabilities.insert("playback");
    capabilities.insert("recommendation");
  } else if (StartsWith(field, "lyrics.")) {
    capabilities.insert("lyrics_remote");
  } else if (StartsWith(field, "ui.")) {
    capabilities.insert("ui_rendering");
  } else if (StartsWith(field, "player.") || StartsWith(field, "controls.")) {
    capabilities.insert("playback_controls");
  } else if (StartsWith(field, "log.")) {
    capabilities.insert("logging");
  } else if (StartsWith(field, "config.")) {
    capabilities.insert("configuration");
  }
}

void RepairField(RepairReport& report,
                 std::set<std::string>& capabilities,
                 const std::string& field,
                 const std::string& reason,
                 const std::string& fallback_value) {
  AddRepair(report, field, reason, fallback_value);
  AppendAffectedCapability(capabilities, field);
}

std::string BoolToString(bool value) {
  return value ? "true" : "false";
}

template <typename StringType>
void ValidateEnum(RepairReport& report,
                  std::set<std::string>& capabilities,
                  const std::string& field,
                  const std::set<std::string>& allowed,
                  StringType* target,
                  const std::string& fallback) {
  if (allowed.find(*target) != allowed.end()) {
    return;
  }
  RepairField(report, capabilities, field, "invalid", fallback);
  *target = fallback;
}

template <typename IntType>
void ValidateIntPositive(RepairReport& report,
                         std::set<std::string>& capabilities,
                         const std::string& field,
                         IntType* target,
                         IntType fallback) {
  if (*target > 0) {
    return;
  }
  RepairField(report, capabilities, field, "invalid", std::to_string(fallback));
  *target = fallback;
}

template <typename IntType>
void ValidateIntMin(RepairReport& report,
                    std::set<std::string>& capabilities,
                    const std::string& field,
                    IntType* target,
                    IntType min_value,
                    IntType fallback) {
  if (*target >= min_value) {
    return;
  }
  RepairField(report, capabilities, field, "invalid", std::to_string(fallback));
  *target = fallback;
}

void ValidateUrlOrEmpty(RepairReport& report,
                        std::set<std::string>& capabilities,
                        const std::string& field,
                        std::string* target) {
  if (target->empty() || IsHttpUrl(*target)) {
    return;
  }
  RepairField(report, capabilities, field, "invalid", "");
  *target = "";
}

void ApplyScalar(std::unordered_map<std::string, std::string>::const_iterator it,
                 std::unordered_map<std::string, std::string>::const_iterator end,
                 const std::string& field,
                 const std::string& missing_reason,
                 const std::string& fallback,
                 std::string* target,
                 RepairReport& report,
                 std::set<std::string>& capabilities) {
  if (it == end) {
    RepairField(report, capabilities, field, missing_reason, fallback);
    *target = fallback;
    return;
  }
  *target = it->second;
}

void ApplyInt(std::unordered_map<std::string, std::string>::const_iterator it,
              std::unordered_map<std::string, std::string>::const_iterator end,
              const std::string& field,
              int fallback,
              int* target,
              RepairReport& report,
              std::set<std::string>& capabilities) {
  if (it == end) {
    RepairField(report, capabilities, field, "missing", std::to_string(fallback));
    *target = fallback;
    return;
  }
  const auto parsed = ParseInt(it->second);
  if (!parsed.has_value()) {
    RepairField(report, capabilities, field, "invalid", std::to_string(fallback));
    *target = fallback;
    return;
  }
  *target = parsed.value();
}

void ApplyBool(std::unordered_map<std::string, std::string>::const_iterator it,
               std::unordered_map<std::string, std::string>::const_iterator end,
               const std::string& field,
               bool fallback,
               bool* target,
               RepairReport& report,
               std::set<std::string>& capabilities) {
  if (it == end) {
    RepairField(report, capabilities, field, "missing", BoolToString(fallback));
    *target = fallback;
    return;
  }
  const auto parsed = ParseBool(it->second);
  if (!parsed.has_value()) {
    RepairField(report, capabilities, field, "invalid", BoolToString(fallback));
    *target = fallback;
    return;
  }
  *target = parsed.value();
}

void PopulateFromSource(const std::unordered_map<std::string, std::string>& source,
                        AppConfig& config,
                        RepairReport& report,
                        std::set<std::string>& capabilities) {
  auto end = source.end();

  ApplyScalar(source.find("emby.base_url"), end, "emby.base_url", "missing", "",
              &config.emby.base_url, report, capabilities);
  ApplyScalar(source.find("emby.user_id"), end, "emby.user_id", "missing", "",
              &config.emby.user_id, report, capabilities);
  ApplyScalar(source.find("emby.access_token"), end, "emby.access_token", "missing",
              "", &config.emby.access_token, report, capabilities);
  ApplyInt(source.find("emby.request_timeout_ms"), end, "emby.request_timeout_ms", 5000,
           &config.emby.request_timeout_ms, report, capabilities);

  ApplyScalar(source.find("lyrics.lrcapi.base_url"), end, "lyrics.lrcapi.base_url",
              "missing", "", &config.lyrics.lrcapi_base_url, report, capabilities);
  ApplyInt(source.find("lyrics.fetch_timeout_ms"), end, "lyrics.fetch_timeout_ms", 2000,
           &config.lyrics.fetch_timeout_ms, report, capabilities);
  ApplyInt(source.find("lyrics.cache_ttl_days"), end, "lyrics.cache_ttl_days", 30,
           &config.lyrics.cache_ttl_days, report, capabilities);
  ApplyScalar(source.find("lyrics.prefer_language"), end, "lyrics.prefer_language",
              "missing", "zh", &config.lyrics.prefer_language, report, capabilities);

  ApplyScalar(source.find("ui.theme_mode"), end, "ui.theme_mode", "missing", "dual",
              &config.ui.theme_mode, report, capabilities);
  ApplyScalar(source.find("ui.nav_layout"), end, "ui.nav_layout", "missing", "left",
              &config.ui.nav_layout, report, capabilities);
  ApplyScalar(source.find("ui.glass_intensity"), end, "ui.glass_intensity", "missing",
              "light", &config.ui.glass_intensity, report, capabilities);
  ApplyScalar(source.find("ui.motion_level"), end, "ui.motion_level", "missing", "low",
              &config.ui.motion_level, report, capabilities);
  ApplyScalar(source.find("ui.orientation"), end, "ui.orientation", "missing",
              "landscape", &config.ui.orientation, report, capabilities);

  ApplyBool(source.find("player.streaming_only"), end, "player.streaming_only", true,
            &config.player.streaming_only, report, capabilities);
  ApplyScalar(source.find("player.recommendation_mode"), end,
              "player.recommendation_mode", "missing", "server_first",
              &config.player.recommendation_mode, report, capabilities);

  ApplyScalar(source.find("controls.steering_mapping"), end,
              "controls.steering_mapping", "missing", "media_key",
              &config.controls.steering_mapping, report, capabilities);

  ApplyScalar(source.find("log.level"), end, "log.level", "missing", "info",
              &config.log.level, report, capabilities);
}

void ValidateAndRepair(AppConfig& config,
                       RepairReport& report,
                       std::set<std::string>& capabilities) {
  ValidateUrlOrEmpty(report, capabilities, "emby.base_url", &config.emby.base_url);
  ValidateUrlOrEmpty(report, capabilities, "lyrics.lrcapi.base_url",
                     &config.lyrics.lrcapi_base_url);

  ValidateIntPositive(report, capabilities, "emby.request_timeout_ms",
                      &config.emby.request_timeout_ms, 5000);
  ValidateIntPositive(report, capabilities, "lyrics.fetch_timeout_ms",
                      &config.lyrics.fetch_timeout_ms, 2000);
  ValidateIntMin(report, capabilities, "lyrics.cache_ttl_days",
                 &config.lyrics.cache_ttl_days, 1, 30);

  ValidateEnum(report, capabilities, "ui.theme_mode",
               {"dark", "light", "dual"}, &config.ui.theme_mode, "dual");
  ValidateEnum(report, capabilities, "ui.nav_layout",
               {"left"}, &config.ui.nav_layout, "left");
  ValidateEnum(report, capabilities, "ui.glass_intensity",
               {"light", "medium"}, &config.ui.glass_intensity, "light");
  ValidateEnum(report, capabilities, "ui.motion_level",
               {"low", "medium"}, &config.ui.motion_level, "low");
  ValidateEnum(report, capabilities, "ui.orientation",
               {"landscape"}, &config.ui.orientation, "landscape");
  ValidateEnum(report, capabilities, "player.recommendation_mode",
               {"server_first"}, &config.player.recommendation_mode, "server_first");
  ValidateEnum(report, capabilities, "controls.steering_mapping",
               {"media_key"}, &config.controls.steering_mapping, "media_key");
  ValidateEnum(report, capabilities, "log.level",
               {"debug", "info", "warn", "error"}, &config.log.level, "info");
}

}  // namespace

LoadResult ConfigRuntime::LoadAndSelfHeal(const std::string& file_path) {
  LoadResult result;
  std::set<std::string> capabilities;

  std::ifstream file(file_path);
  if (!file.good()) {
    RepairField(result.report, capabilities, "config.file", "missing", "default_template");
    PopulateFromSource({}, result.config, result.report, capabilities);
  } else {
    const auto parse_result = ParseSimpleYaml(file);
    if (!parse_result.ok) {
      RepairField(result.report, capabilities, "config.file", "parse_error",
                  "default_template");
      PopulateFromSource({}, result.config, result.report, capabilities);
    } else {
      PopulateFromSource(parse_result.values, result.config, result.report, capabilities);
    }
  }

  ValidateAndRepair(result.config, result.report, capabilities);
  result.report.persisted = false;
  if (result.report.had_repairs) {
    result.report.toast_summary = kRepairToastSummary;
  }

  result.report.affected_capabilities.assign(capabilities.begin(), capabilities.end());
  return result;
}

ConnectivityTestResult ConfigRuntime::TestEmbyConnection(const AppConfig& config) {
  if (config.emby.base_url.empty()) {
    return {ConnectivityTestCode::kUnreachable, "Emby base_url is required"};
  }
  if (!IsHttpUrl(config.emby.base_url)) {
    return {ConnectivityTestCode::kInvalidResponse,
            "Emby base_url must start with http/https"};
  }
  if (config.emby.user_id.empty() || config.emby.access_token.empty()) {
    return {ConnectivityTestCode::kAuthFailed,
            "Emby user_id/access_token is required"};
  }
  return {ConnectivityTestCode::kOk, "Connectivity check passed (skeleton)"};
}

ConnectivityTestResult ConfigRuntime::TestLrcApiConnection(const AppConfig& config) {
  if (config.lyrics.lrcapi_base_url.empty()) {
    return {ConnectivityTestCode::kUnreachable, "LrcApi base_url is empty"};
  }
  if (!IsHttpUrl(config.lyrics.lrcapi_base_url)) {
    return {ConnectivityTestCode::kInvalidResponse,
            "LrcApi base_url must start with http/https"};
  }
  return {ConnectivityTestCode::kOk, "Connectivity check passed (skeleton)"};
}

bool ConfigRuntime::CanSaveConfig(
    const ConnectivityTestResult& emby_test_result) {
  return emby_test_result.code == ConnectivityTestCode::kOk;
}

}  // namespace skodamusic::config

