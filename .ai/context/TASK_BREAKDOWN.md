# TASK_BREAKDOWN

Last Updated: 2026-05-19

## Active Stage
- S4 子阶段（歌词中线改造 + 均衡器规划）

## T-S4-LRC-050
- Task ID: `T-S4-LRC-050`
- Title: 歌词三段容器交互口径与验收清单定义
- Module ID: `M-S4-LRC-008`
- Goal: 固化“上文/当前/下文”显示规则与边界行为，避免实现时反复返工。
- Why: 没有清晰口径就会陷入“看起来居中但边界不稳”的反复调参。
- Dependencies: 无
- Inputs:
  - `.ai/context/SCOPE.md`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- Expected Outputs:
  - 三段容器行为说明（正常/边界场景）。
  - 本地回归检查项（首句、末句、无歌词、加载中、超长换行）。
- Done Criteria:
  - 口径可直接指导布局与代码实现。
- Risks:
  - “永远绝对居中”在某些边界场景不可严格成立。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-LRC-051
- Task ID: `T-S4-LRC-051`
- Title: Home 歌词面板三段容器布局改造
- Module ID: `M-S4-LRC-008`
- Goal: 在 `activity_main.xml` 完成三段容器结构改造，替换当前单 `TextView + Scroll` 结构。
- Why: 布局结构是“中线稳定”的前提，没有结构改造只能继续靠滚动补偿。
- Dependencies: `T-S4-LRC-050`
- Inputs:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/strings.xml`
- Expected Outputs:
  - 新歌词容器结构（上文、当前、下文）及占位态文案入口。
- Done Criteria:
  - 布局可正常渲染，不破坏 Home 现有 tab 切换。
- Risks:
  - 容器高度分配不当会影响可读性。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-LRC-052
- Task ID: `T-S4-LRC-052`
- Title: 歌词渲染逻辑改造（按三段容器输出）
- Module ID: `M-S4-LRC-008`
- Goal: 将 `MainActivity` 歌词渲染从整段富文本改为三段分发，确保当前行始终在中线容器。
- Why: 仅改布局不足以稳定效果，核心在渲染策略与索引更新逻辑。
- Dependencies: `T-S4-LRC-051`
- Inputs:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `LyricLine` 时间轴与当前索引逻辑
- Expected Outputs:
  - 以当前索引驱动上文/当前/下文文本更新。
  - 移除或降级对旧 `Scroll+padding` 居中策略的依赖。
- Done Criteria:
  - 进度推进时当前行切换稳定，且无明显闪烁/跳变。
- Risks:
  - 高频 UI 刷新下可能出现短暂抖动。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-LRC-053
- Task ID: `T-S4-LRC-053`
- Title: 歌词改造本地回归与文档回写
- Module ID: `M-S4-LRC-008`
- Goal: 基于 `050` 清单完成本地回归并回写结论，形成后续车机验证输入。
- Why: 没有回归记录，后续车机问题难以判定是新回归还是旧问题。
- Dependencies: `T-S4-LRC-052`
- Inputs:
  - 本地运行结果
  - `docs/LYRICS_ABNORMAL_TEST_CHECKLIST.md`
  - `.ai/context/CURRENT_STATUS.md`（必要时）
- Expected Outputs:
  - 本地 PASS/FAIL 结果与边界说明。
  - 对“绝对居中不可保证”场景的明确备注。
- Done Criteria:
  - 能作为车机验证前置输入，不是口头描述。
- Risks:
  - 本地与车机字体渲染差异可能导致体感不同。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-054
- Task ID: `T-S4-AUDIO-054`
- Title: API17 Equalizer 可行性与生命周期接线分析
- Module ID: `M-S4-AUDIO-009`
- Goal: 识别 API17 上 EQ 可用性与风险，输出 session 绑定策略。
- Why: 直接实现 EQ 风险高，先做可行性分析可避免返工。
- Dependencies: 无
- Inputs:
  - `app/src/main/java/com/skodamusic/app/player/PlaybackEngine.kt`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - Android `audiofx` 约束
- Expected Outputs:
  - 可行性结论与风险表。
  - session 创建/释放时机建议。
- Done Criteria:
  - 结论可直接指导 MVP 任务拆分。
- Risks:
  - 车机 ROM 差异使“实验可行”不等于“全机型可行”。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-055
- Task ID: `T-S4-AUDIO-055`
- Title: 均衡器 MVP 方案文档与任务拆分落地
- Module ID: `M-S4-AUDIO-009`
- Goal: 把 `054` 结论沉淀为可执行 MVP 方案并生成后续实现任务。
- Why: 用户目标是“提升播放音质”，需要可执行路线，不是停留分析。
- Dependencies: `T-S4-AUDIO-054`
- Inputs:
  - `054` 分析输出
  - 现有设置页与播放器状态管理方式
- Expected Outputs:
  - EQ MVP 文档（开关、预设、自定义范围、持久化、fail-open）。
  - 下一轮实现任务（Ready/Blocked/Deferred）。
- Done Criteria:
  - 可直接进入开发执行阶段。
- Risks:
  - 若首版交互深度未定，需标注待确认而非强行拍板。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-057
- Task ID: `T-S4-AUDIO-057`
- Title: PlaybackEngine 暴露 audioSessionId 并打通生命周期接线
- Module ID: `M-S4-AUDIO-009`
- Goal: 为 EQ 接线提供稳定 session 来源，明确创建/重绑/释放时机。
- Why: 没有 session 能力，Equalizer 无法与当前 Exo 引擎可靠绑定。
- Dependencies: 无
- Inputs:
  - `app/src/main/java/com/skodamusic/app/player/PlaybackEngine.kt`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
- Expected Outputs:
  - `PlaybackEngine.audioSessionId()` 接口与 `ExoPlaybackEngine` 实现。
  - `MainActivity` 中 session 变化检测与回调接线点。
- Done Criteria:
  - 播放中可获取有效 session id（`>0`），且播放器释放后不会持有旧引用。
- Risks:
  - 部分 ROM 可能延迟分配 session，需容忍短暂 `<=0`。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-058
- Task ID: `T-S4-AUDIO-058`
- Title: EqualizerManager 最小实现（fail-open + 会话熔断）
- Module ID: `M-S4-AUDIO-009`
- Goal: 落地 `Equalizer` 创建/应用/释放与异常熔断策略，不影响主播放链路。
- Why: API17 ROM 差异大，必须把 EQ 当可失败能力处理。
- Dependencies: `T-S4-AUDIO-057`
- Inputs:
  - `T-S4-AUDIO-057` 接线结果
  - `docs/API17_EQUALIZER_MVP_PLAN.md`
- Expected Outputs:
  - `EqualizerManager`（或同等组件）及 fail-open 日志。
  - session 变化时自动重绑；失败时单会话降级。
- Done Criteria:
  - EQ 任意异常不会导致停播/闪退/切歌阻塞。
- Risks:
  - 某些 ROM 上创建成功但听感无变化，仅能记录能力状态。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-059
- Task ID: `T-S4-AUDIO-059`
- Title: EQ MVP 设置接线（开关 + 预设 + 持久化）
- Module ID: `M-S4-AUDIO-009`
- Goal: 在现有设置页提供最小 EQ 交互，并把配置接到播放会话。
- Why: 只有引擎能力没有入口，无法形成可验证 MVP。
- Dependencies: `T-S4-AUDIO-058`
- Inputs:
  - 现有设置页结构
  - `EqualizerManager` 接口
- Expected Outputs:
  - 开关 + 预设选择入口
  - 本地持久化（开关/preset）与启动恢复
- Done Criteria:
  - 设置生效且失败自动降级，不影响播放。
- Risks:
  - 预设数量在不同设备不一致，UI 需容错空列表。
- Size: M
- Suitable For Micro Execution?: No
- Suitable For Module Execution?: Yes

## T-S4-AUDIO-060
- Task ID: `T-S4-AUDIO-060`
- Title: EQ MVP 本地回归与 API17 实机验证条目补齐
- Module ID: `M-S4-AUDIO-009`
- Goal: 固化 EQ 正常与失败场景验证方式，输出可复盘证据模板。
- Why: fail-open 是否生效必须靠失败注入/实机验证，不可只看代码。
- Dependencies: `T-S4-AUDIO-059`
- Inputs:
  - `docs/API17_EQUALIZER_MVP_PLAN.md`
  - EQ MVP 落地代码
- Expected Outputs:
  - 本地回归结果（正常/异常/降级）
  - API17 车机验证条目补充
- Done Criteria:
  - 能回答“EQ 失败时播放是否完全不受影响”。
- Risks:
  - 无稳定车机窗口时只能先完成本地证据。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: Yes

## T-S4-CARRY-056
- Task ID: `T-S4-CARRY-056`
- Title: 旧阶段外部任务状态迁移与边界标注
- Module ID: `M-S4-CARRY-010`
- Goal: 保留旧任务追踪信息，并在本阶段规划中明确“非当前执行主线”。
- Why: 防止执行阶段误回切到外部依赖任务。
- Dependencies: 无
- Inputs:
  - 旧 `TASK_QUEUE/NEXT_STEPS`
- Expected Outputs:
  - Carry Forward 列表与状态标注。
- Done Criteria:
  - 旧任务不丢失，且本阶段 Ready 队列保持干净。
- Risks:
  - 若标注不清，后续执行容易混线。
- Size: S
- Suitable For Micro Execution?: Yes
- Suitable For Module Execution?: No

## Carry Forward (Out of Active Stage)
- `T-S4-CORE-026C-HF-20260429`
- `T-S4-OBS-035/036/037/038`
- `T-S4-UPD-044`
- `T-S4-REG-022`
- `T-S4-VAL-033`
- `T-BLK-001`
- `B-LRC-001`
