# NEXT_STEPS

Last Updated: 2026-05-29

## One-Line Summary
- 应用内 EQ 固定 10 段直写试验已本地完成，下一步是推送/打包后进行 API17 实机验证。

## Current Highest Priority
- `T-S4-AUDIO-079`: 基于 API17 实机结果决定是否长期保留 10 段直写，或后续切换最近频点/插值映射。

## What To Verify On Device
1. EQ 子页固定显示 10 段：`31/62/125/250/500/1k/2k/4k/8k/16k`。
2. 滑杆视觉是否接近系统 EQ：窄轨道、窄 thumb，不再是粗滑块。
3. 右侧固定中文预设是否正确：默认/流行/摇滚/爵士/古典/舞曲/人声/低音增强/高音增强/自定义。
4. 切换预设后 10 个滑杆是否立即变化。
5. 拖动滑杆后右侧“自定义”是否高亮。
6. 若某些 band 真实写入失败，是否只提示失败频点，不闪退、不停播。
7. 失败 band 是否不会在重启后反复触发失败配置。

## Validation Already Done
- `gradle :app:assembleDebug` 通过。
- `./scripts/check_api17_guardrails.sh` 通过。
- `git diff --check` 通过。

## Blocked / Waiting
- `T-S4-AUDIO-079` 等待 API17 实机证据。

## Recommended Next Action
- 推送当前版本供实机验证；实机回传后进入 `T-S4-AUDIO-079` 决策。
