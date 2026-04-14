# CONFIG_LOADER_INTERFACE

Last Updated: 2026-04-14 11:19:23
Status: Draft v2 (T-007)

See also: `docs/CONFIG_ERROR_POLICY.md` (T-007)

## Purpose
定义配置加载器接口草案，用于读取 `config/app.config.yaml` 并输出结构化配置对象。  
本文件仅定义接口与约定，不包含业务实现代码。

## Inputs
- 主输入文件: `config/app.config.yaml`
- 参考样例: `config/app.config.example.yaml`
- 可选覆盖源: 环境变量（命名规范待确认）

## Output Object (Concept)
```cpp
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
  std::string theme_mode = "dual";      // dark | light | dual
  std::string nav_layout = "left";
  std::string glass_intensity = "light";
  std::string motion_level = "low";     // low | medium
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
  std::string level = "info";           // debug | info | warn | error
};

struct AppConfig {
  EmbyConfig emby;
  LyricsConfig lyrics;
  UiConfig ui;
  PlayerConfig player;
  ControlsConfig controls;
  LogConfig log;
};
```

## Interface Draft (Concept)
```cpp
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
  bool persisted = false;  // fixed: boot self-heal is memory-only
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
  ConnectivityTestCode code;
  std::string message;
};

class IConfigRuntime {
 public:
  virtual ~IConfigRuntime() = default;
  virtual LoadResult LoadAndSelfHeal(
      const std::string& file_path) = 0;
  virtual ConnectivityTestResult TestEmbyConnection(
      const AppConfig& config) = 0;
  virtual ConnectivityTestResult TestLrcApiConnection(
      const AppConfig& config) = 0;
  virtual bool CanSaveConfig(
      const ConnectivityTestResult& emby_test_result) = 0;
};
```

## Loading Order
1. 读取 YAML 文件并解析。
2. 将缺省值填充到未设置字段。
3. 应用可选环境变量覆盖（待确认命名规范）。
4. 执行字段校验并进行启动自愈（回退为空值或默认值）。
5. 返回 `LoadResult`（包含 `AppConfig + RepairReport`）。
6. 展示一条聚合 toast（由 `RepairReport.toast_summary` 生成）。

## Validation Rules (v2)
- 启动阶段不阻断，所有校验失败都进入“修复并继续”路径。
- 配置页保存阶段执行门槛校验。
- URL 字段必须是 `http` 或 `https`。
- 超时字段必须 > 0。
- `cache_ttl_days` 必须 >= 1。
- 枚举值必须在允许集合内。

## Error Handling Contract
- 启动阶段:
  - 不返回致命阻断错误。
  - 所有异常写入 `RepairReport.repaired_fields`。
- 保存阶段:
  - `CanSaveConfig` 仅由 Emby 测试结果决定。
  - 规则: Emby 连通+鉴权通过才允许保存。
- LrcApi 测试失败只告警，不阻止保存。
- 日志中必须脱敏，不打印 token。

## Non-Goals
- 不定义配置热更新。
- 不定义多环境配置切换机制。
- 不实现具体 YAML 库绑定细节（待后续任务确定）。

## To Confirm
- 环境变量是否采用固定前缀 `SKODA_MUSIC_`。
- 是否允许空字符串覆盖 YAML 字段。
