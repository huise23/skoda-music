# MIN_IMPLEMENTATION_ENTRYPOINTS

Last Updated: 2026-04-14 13:41:25
Status: Draft v1 (T-011)

## Purpose
在现有文档体系基础上，给出“从 0 到可运行 MVP”的最小实现切入点，避免一次性铺开。

## Implementation Strategy
- 原则: 每次只实现一条可验证链路，按“配置 -> 可播放 -> 歌词 -> 车机控制”推进。
- 目标: 尽快形成可运行闭环，再逐步补齐能力。

## Recommended First 5 Code Tasks

### I-001 配置运行时骨架（最高优先）
- 对应文档:
  - `CONFIG_LOADER_INTERFACE`
  - `CONFIG_ERROR_POLICY`
  - `CONFIG_RUNTIME_INTEGRATION_EXAMPLE`
- 产出:
  - `ConfigRuntime` 基本实现（LoadAndSelfHeal + RepairReport）
  - 启动聚合 toast 触发点
- 完成标准:
  - 无配置文件可启动
  - 非法配置可自愈并继续
  - 不写回原配置文件

### I-002 设置页最小闭环
- 对应文档:
  - `UI_IA_LOW_FIDELITY`
  - `SELF_HEAL_AND_SETTINGS_COPY_GUIDELINES`
- 产出:
  - Emby/LrcApi 输入表单
  - Test 按钮与保存按钮状态控制
- 完成标准:
  - Emby 测试失败时禁止保存
  - Emby 成功可保存
  - LrcApi 失败仅告警

### I-003 首页最小播放壳
- 对应文档:
  - `UI_IA_LOW_FIDELITY`
  - `CONFIG_UI_INTEGRATION_ACCEPTANCE`
- 产出:
  - Home 页当前播放卡与推荐占位区
  - Emby 未就绪提示态
- 完成标准:
  - 未配置时显示“播放服务未就绪”
  - 配置就绪后可进入播放链路

### I-004 歌词链路最小实现
- 对应文档:
  - `LYRICS_STATE_MACHINE`
  - `LYRICS_ABNORMAL_TEST_CHECKLIST`
- 产出:
  - 嵌入优先读取
  - 异步 LrcApi 请求与取消
- 完成标准:
  - 不阻塞主播放线程
  - 切歌不串歌
  - 失败降级为“暂无歌词”

### I-005 方向盘媒体键联动
- 对应文档:
  - `PROJECT_BRIEF`
  - `UI_IA_LOW_FIDELITY`
- 产出:
  - 上/下一曲媒体键映射
  - 首页与队列状态同步
- 完成标准:
  - 方向盘切歌成功触发播放状态变更
  - UI 同步更新

## Suggested Module Boundaries (for coding phase)
- `src/config`: 配置加载、自愈、测试门槛
- `src/ui`: 首页/设置页/队列页
- `src/playback`: 播放状态机与队列
- `src/lyrics`: 歌词解析与远程拉取
- `src/platform/android`: 媒体键与系统适配

## Verification Entry Order
1. 启动自愈 + toast
2. 设置页测试保存门槛
3. Home 可用性与状态提示
4. 歌词异常降级
5. 方向盘切歌联动

## Open Dependencies
- B-001: 系统首页卡片调用能力（不阻塞上述最小实现路径）
- B-002: 分辨率细节（先按横屏自适应）
- B-003: 歌词失败回退策略（实现时先按“不回退过期缓存、不自动重试”临时策略，待确认再调整）
