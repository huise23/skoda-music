# SELF_HEAL_AND_SETTINGS_COPY_GUIDELINES

Last Updated: 2026-04-14 13:41:25
Status: Draft v1 (T-010)

## Purpose
定义“启动自愈”和“设置页测试保存”相关提示文案规范，确保提示一致、可理解、不过度打扰。

## Principles
- 明确: 告诉用户发生了什么、是否影响当前使用。
- 可执行: 给出下一步动作（例如去设置页测试）。
- 非阻塞: 避免恐慌式措辞，不影响主流程。
- 脱敏: 不暴露 token 或敏感连接信息。

## Startup Toast Rules
- 数量: 启动阶段仅 1 条聚合 toast。
- 时机: 自愈完成后首次进入主界面时展示。
- 文案模板（推荐）:
  - `部分功能配置有问题，已设置为默认值`
- 时长建议: 2.5s - 3.5s（轻提示）。
- 禁止:
  - 连续多条 toast
  - 包含敏感字段值
  - “崩溃/失败/致命”类高压措辞

## Settings Page Message Rules

### Emby Test Result
- 通过:
  - `Emby 连接测试通过，可保存配置`
- 失败:
  - `Emby 连接测试失败，请检查地址、用户ID或令牌`
- 保存门槛:
  - Emby 失败时保存按钮禁用，按钮旁提示:
    - `需先通过 Emby 测试`

### LrcApi Test Result
- 通过:
  - `歌词服务连接正常`
- 失败（非阻断）:
  - `歌词服务测试失败，可先保存基础配置`
- 失败后辅助提示:
  - `保存后可继续播放，歌词远程补全暂不可用`

### Save Result
- 保存成功:
  - `配置已保存`
- 保存失败（Emby 未通过）:
  - `未保存：请先通过 Emby 测试`

## Runtime Status Copy
- Emby 未就绪:
  - `播放服务未就绪，请先完成 Emby 配置`
- LrcApi 未就绪:
  - `歌词服务未就绪，远程歌词补全暂不可用`
- 歌词不可用:
  - `暂无歌词`

## Error Detail Copy (Settings Detail Panel)
用于可选的明细区域（若启用 B-004）：
- 标题:
  - `启动时已自动修复以下配置`
- 行项模板:
  - `{field}：{reason}，已回退为 {fallback}`
- 示例:
  - `ui.motion_level：值无效，已回退为 low`
  - `lyrics.fetch_timeout_ms：值过小，已回退为 2000`

## Tone and Terminology
- 语气: 中性、简短、可操作。
- 用词:
  - 使用“未就绪/暂不可用/请检查”
  - 避免“故障/崩溃/致命”等词。

## Localization Notes
- 首版默认中文文案。
- 后续若支持多语言，先做 key 化，不在代码硬编码长文案。

## Do / Don't
- Do:
  - 提供下一步动作（去设置测试、检查地址/令牌）
  - 统一术语（测试通过、可保存、未就绪）
- Don't:
  - 泄露隐私信息
  - 过多技术细节
  - 在驾驶场景反复弹出长提示

## Confirmed
- 设置页展示启动自愈明细（B-004 已确认）。
