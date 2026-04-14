# CONFIG_RUNTIME_INTEGRATION_EXAMPLE

Last Updated: 2026-04-14 12:40:26
Status: Draft v1 (T-006)

## Purpose
给出配置加载接口与错误策略的联动示例，说明从启动到保存的完整流程。  
本文件用于指导实现，不包含具体业务代码。

## Related Docs
- `docs/CONFIG_SPEC.md`
- `docs/CONFIG_LOADER_INTERFACE.md`
- `docs/CONFIG_ERROR_POLICY.md`

## Scenario A: Cold Boot with Broken Config
前提:
- `config/app.config.yaml` 缺失或语法损坏

流程:
1. 调用 `LoadAndSelfHeal(file_path)`
2. 解析失败 -> 触发自愈，构建运行时默认配置
3. 返回 `LoadResult { config, report }`
4. `report.had_repairs = true`
5. 展示 `report.toast_summary`（一条聚合 toast）
6. 应用继续启动

输出示例:
```json
{
  "had_repairs": true,
  "repaired_fields": [
    {"field": "emby.base_url", "reason": "missing", "fallback_value": ""},
    {"field": "ui.motion_level", "reason": "invalid", "fallback_value": "low"}
  ],
  "affected_capabilities": ["playback", "recommendation"],
  "toast_summary": "部分功能配置有问题，已设置为默认值",
  "persisted": false
}
```

## Scenario B: Settings Save Gate (Emby Required)
前提:
- 用户在设置页填写 Emby/LrcApi 配置并点击“测试后保存”

流程:
1. 调用 `TestEmbyConnection(config)`
2. 调用 `TestLrcApiConnection(config)`
3. `CanSaveConfig(emby_result)`
4. 若 Emby 失败 -> 禁止保存；保留输入；提示失败原因
5. 若 Emby 成功 -> 允许保存
6. LrcApi 失败时只提示警告，不阻止保存

判定示例:
```text
Emby: kAuthFailed   -> CanSaveConfig = false
LrcApi: kTimeout    -> warning only
Save button: disabled
```

```text
Emby: kOk           -> CanSaveConfig = true
LrcApi: kTimeout    -> warning only
Save button: enabled
```

## Scenario C: Startup After Partial Valid Config
前提:
- 配置文件可解析，但部分字段非法

流程:
1. `LoadAndSelfHeal` 读取并校验
2. 非法字段按规则回退默认值
3. 继续启动并显示聚合 toast
4. 设置页可查看当前生效值（自愈明细展示策略待确认）

## Pseudocode (Concept)
```cpp
auto result = runtime.LoadAndSelfHeal("config/app.config.yaml");
ApplyRuntimeConfig(result.config);
if (result.report.had_repairs) {
  ShowToast(result.report.toast_summary);  // one aggregated toast
}

auto emby_test = runtime.TestEmbyConnection(draft_config);
auto lrc_test = runtime.TestLrcApiConnection(draft_config);
bool can_save = runtime.CanSaveConfig(emby_test);

if (!can_save) {
  ShowValidationError(emby_test.message);
  KeepDraftInputs();
} else {
  PersistConfig(draft_config);
  if (lrc_test.code != ConnectivityTestCode::kOk) {
    ShowWarning("歌词服务测试失败，已保存基础配置");
  }
}
```

## UX Contract Summary
- 启动:
  - 不阻断
  - 仅一条聚合 toast
  - 不自动写回配置文件
- 保存:
  - Emby 测试通过才允许保存
  - LrcApi 失败仅告警
  - 失败保留输入，便于修正

## Validation Checklist (Implementation Ready)
- [ ] 启动时无配置文件仍可进入应用
- [ ] 启动时只出现一条 toast
- [ ] 修复后 `persisted=false`
- [ ] Emby 失败时保存按钮禁用
- [ ] Emby 成功时允许保存
- [ ] LrcApi 失败不阻断保存
- [ ] 日志输出脱敏

## To Confirm
- 设置页是否展示 `repaired_fields` 详细列表（与 B-004 关联）。
