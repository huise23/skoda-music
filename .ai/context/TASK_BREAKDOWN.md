# TASK_BREAKDOWN

Last Updated: 2026-04-26

## Active Stage: S4 车机后台控制落地（方案1 / Legacy 稳态）

## T-S4-CORE-026
- Task ID: `T-S4-CORE-026`
- Title: 后台控制稳定化大闭环
- Goal: 在 API17 上完成“后台服务 + 方向盘按键 + 通知 + 浮窗 + 恢复链路”一体化稳定。
- Why: 车机场景要求一次进入可持续使用，不能依赖频繁切回应用。
- Dependencies: 无（当前主任务）
- Inputs:
  - `PlaybackService/PlaybackControlBus/PlaybackStateStore/OverlayController`
  - `MainActivity` 现有播放与状态上报接线
  - 用户确认策略（失败即失败、不记录待执行命令）
- Expected Outputs:
  - 播放真源逐步迁移到 Service 的可联调版本
  - 前台/后台控制路径行为一致
  - 车机可执行的稳定性验证清单与结果
- Done Criteria:
  - 后台控制链路在车机可稳定使用
  - 无“命令持久化重放”逻辑残留
  - 浮窗与通知控制行为符合已确认策略
- Risks:
  - 车机测试窗口不连续，回归周期可能拉长
- Size: L
- Minimal Loop: No

## T-S4-REG-022
- Task ID: `T-S4-REG-022`
- Title: 车机实机回归与证据回填
- Goal: 对 S4 核心能力进行实机回归并回写结论。
- Why: 该阶段验收必须以车机实际表现为准。
- Dependencies: `T-S4-CORE-026`
- Inputs:
  - 车机回归记录
  - 日志与复现步骤
- Expected Outputs:
  - PASS/FAIL/Blocker 结论
  - `.ai/context` 状态与下一阶段建议
- Done Criteria:
  - 后台按键、浮窗策略、恢复续播有实机结论
- Risks:
  - 无法长期占用车机环境
- Size: M
- Minimal Loop: No

## T-S4-UX-027
- Task ID: `T-S4-UX-027`
- Title: 主屏删除入口 + 长标题滚动 + 听感优化
- Goal: 落地用户确认的三项体验改进。
- Why: 均为当前车机可用性直接相关问题。
- Dependencies: `T-S4-REG-022`（不阻断主链路可并行评估）
- Inputs:
  - 主屏交互现状
  - 播放状态机与队列行为
  - 音效参数与系统能力
- Expected Outputs:
  - 主屏可直接删除当前不想听曲目（含二次确认与跳下一首）
  - 长标题滚动稳定可见
  - 可量化的音效优化方案与默认参数
- Done Criteria:
  - 三项问题均有明确实现或技术结论
- Risks:
  - 不同车机音频栈差异导致听感结论不一致
- Size: M
- Minimal Loop: No

## Done (History Snapshot)
- [x] `T-S4-ARCH-017A` 移除命令持久化重试（失败即失败）
- [x] `T-S3-UI-013` Home 播放模块重排 + 可拖动进度 + 解码失败自动切歌
- [x] `T-S3-RB-008` 回滚错误实现并恢复基线
- [x] `T-S3-NET-009` CF 优选 IPv4 解析链路接线（Emby 域名保持不变）
- [x] `T-S3-DL-010` 30s 下载窗口调度状态机
- [x] `T-S3-LOG-011` 下载调度与优选 IP 诊断日志补齐
