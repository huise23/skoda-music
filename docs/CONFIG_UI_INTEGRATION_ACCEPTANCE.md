# CONFIG_UI_INTEGRATION_ACCEPTANCE

Last Updated: 2026-04-14 13:41:25
Status: Draft v1 (T-009)

## Purpose
定义配置策略与 UI 串联的验收用例清单，用于实现后功能联调和回归验证。

## Related Docs
- `docs/CONFIG_ERROR_POLICY.md`
- `docs/CONFIG_LOADER_INTERFACE.md`
- `docs/CONFIG_RUNTIME_INTEGRATION_EXAMPLE.md`
- `docs/UI_IA_LOW_FIDELITY.md`

## Acceptance Matrix

### A. Startup and Self-Heal
- [ ] A1 无配置文件启动
  - 预置: 删除 `config/app.config.yaml`
  - 预期: 应用启动成功；显示一条聚合 toast；进入可操作界面
  - 校验: 无启动阻断

- [ ] A2 配置文件语法错误
  - 预置: 构造非法 YAML
  - 预期: 启动成功，自愈生效，toast 提示
  - 校验: `persisted=false`（不写回文件）

- [ ] A3 部分字段非法
  - 预置: `ui.motion_level=ultra`、`lyrics.fetch_timeout_ms=-1`
  - 预期: 字段回退默认值；应用可用
  - 校验: 日志记录修复摘要（脱敏）

### B. Settings Save Gate
- [ ] B1 Emby 测试失败
  - 预置: 错误 token
  - 预期: 保存按钮禁用；输入保留；显示可修复错误

- [ ] B2 Emby 通过 + LrcApi 失败
  - 预置: Emby 正常，LrcApi 超时
  - 预期: 允许保存；提示歌词服务告警

- [ ] B3 Emby 和 LrcApi 均通过
  - 预置: 两服务均正常
  - 预期: 保存成功；服务状态显示均为 Ready

### C. Home / Player Availability
- [ ] C1 Emby 未就绪时首页行为
  - 预置: 未完成 Emby 配置
  - 预期: 首页播放卡显示“请先完成 Emby 配置”；应用其余区域可正常进入

- [ ] C2 Emby 就绪后播放入口
  - 预置: Emby 测试并保存成功
  - 预期: 首页推荐可点播；播放控制可用

- [ ] C3 方向盘切歌联动
  - 预置: 已播放状态
  - 预期: 方向盘上下曲触发后，首页卡片和队列状态同步更新

### D. Lyrics Degrade in UI
- [ ] D1 LrcApi 未就绪
  - 预置: 保存时 LrcApi 失败
  - 预期: 主播放不中断；歌词区显示降级提示

- [ ] D2 无歌词曲目
  - 预置: 嵌入与远程都无歌词
  - 预期: 显示“暂无歌词”，无阻塞弹窗

### E. UX and Messaging
- [ ] E1 启动 toast 数量
  - 预置: 多字段同时修复
  - 预期: 启动仅 1 条聚合 toast

- [ ] E2 敏感信息保护
  - 预置: 触发 Emby 鉴权失败
  - 预期: 日志与提示不含 token 明文

- [ ] E3 启动自愈明细展示
  - 预置: 启动时发生字段自愈
  - 预期: 设置页可查看自愈字段明细列表

### F. Persistence Rules
- [ ] F1 自愈不写回文件
  - 预置: 启动触发自愈
  - 预期: 原文件未被自动覆盖

- [ ] F2 保存后落盘
  - 预置: Emby 测试通过并点击保存
  - 预期: 新配置写入配置文件

## Exit Criteria
- A-F 每个分组至少通过 1 个关键用例。
- 无“启动阻断”与“保存门槛失效”问题。
- 无敏感信息泄露。
- 配置状态与 UI 展示一致。

## Open Dependencies
- B-001: 系统首页卡片调用第三方播放器能力（不影响本清单主流程，但影响外部入口验收）。
