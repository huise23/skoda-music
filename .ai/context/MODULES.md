# MODULES

Last Updated: 2026-05-19

## M-S4-LRC-008
- Module ID: `M-S4-LRC-008`
- Name: Home 歌词中线容器改造
- Goal: 将歌词显示改为“上文/当前行/下文”三段结构，提升当前行居中稳定性与可读性。
- Why It Matters: 当前实现是单 `TextView` 富文本滚动，边界场景下居中稳定性与视觉一致性有限。
- In Scope:
  - `activity_main.xml` 歌词面板结构改造。
  - `MainActivity.kt` 歌词渲染逻辑拆分（上文、当前、下文）。
  - 边界场景显示规则与本地回归清单。
- Out of Scope:
  - 歌词获取链路重写（LrcApi 请求/解析/缓存策略保持现状）。
  - 新增复杂歌词动画系统。
- Dependencies: 无
- Related Files / Areas:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/res/values/strings.xml`
- Milestone / Done Criteria:
  - 当前行在标准场景稳定处于中线容器。
  - 首句/末句/无歌词/加载中/超长换行均有确定行为。
  - 不引入播放阻塞或歌词串歌回归。
- Related Tasks: `T-S4-LRC-050`, `T-S4-LRC-051`, `T-S4-LRC-052`, `T-S4-LRC-053`
- Priority: P0
- Status: Ready
- Risks:
  - 超长歌词行换行后可能造成视觉中心偏差。
- Suitable For Module Execution?: Yes

## M-S4-AUDIO-009
- Module ID: `M-S4-AUDIO-009`
- Name: 均衡器 API17 规划与 MVP 拆分
- Goal: 产出 API17 可落地的 Equalizer 设计与后续实现任务，不在本阶段直接全量上线。
- Why It Matters: “提升播放音质”已进入确认需求，但当前仓库缺少可执行技术路径。
- In Scope:
  - `Equalizer` 可行性验证与 ROM 风险识别。
  - 与 `PlaybackEngine` 的 session 生命周期接线方案。
  - MVP 范围、配置持久化策略、fail-open 策略。
- Out of Scope:
  - 完整音效中心 UI。
  - BassBoost/Virtualizer 全量联动上线（仅在规划中评估是否纳入后续）。
- Dependencies: 无（但输出将依赖 `M-S4-LRC-008` 完成后择机实施）
- Related Files / Areas:
  - `app/src/main/java/com/skodamusic/app/player/PlaybackEngine.kt`
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `docs/`（新增 EQ 规划文档）
- Milestone / Done Criteria:
  - 明确可落地方案：session 获取/绑定/释放、异常降级、MVP 功能边界。
  - 形成可执行任务并进入队列（Ready/Blocked 清晰）。
- Related Tasks: `T-S4-AUDIO-054`, `T-S4-AUDIO-055`
- Priority: P1
- Status: Ready
- Risks:
  - 不同车机 ROM 对 `audiofx` 支持不一致，可能出现创建失败或效果不生效。
- Suitable For Module Execution?: Yes

## M-S4-CARRY-010
- Module ID: `M-S4-CARRY-010`
- Name: 旧阶段任务边界维护
- Goal: 维持旧 S4 外部验收任务可追踪，但不混入本阶段 Ready。
- Why It Matters: 避免“歌词/EQ 子阶段”被外部依赖任务打断。
- In Scope:
  - 保留 `OBS/UPD/REG/VAL` 外部任务的状态与入口。
  - 在 `TASK_QUEUE/NEXT_STEPS` 中标记为 Blocked/Deferred。
- Out of Scope:
  - 执行这些外部任务本身。
- Dependencies: 无
- Related Files / Areas:
  - `.ai/context/TASK_QUEUE.md`
  - `.ai/context/NEXT_STEPS.md`
  - `.ai/context/HANDOFF.md`
- Milestone / Done Criteria:
  - 旧任务状态不丢失、且不干扰当前 Ready 队列。
- Related Tasks: `T-S4-CARRY-056`
- Priority: P2
- Status: In Progress
- Risks:
  - 若边界维护不清，执行阶段容易误切回旧主线。
- Suitable For Module Execution?: No
