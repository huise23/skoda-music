# PROJECT_BRIEF

Last Updated: 2026-04-15 17:12:11

## Project
- Name: `skoda-music`
- Type: Android 车机音乐播放器（个人项目）
- Description: 以 C++ 业务骨架为核心，配套 Android 原生壳打包与实机验证，目标是逐步演进为可用车机播放器。

## Known Goals
- 短期目标: 在 Android `4.2.2`（API 17）设备稳定安装、启动并完成最小可交互页面。
- 中期目标: 接入 Emby 最小可用播放链路（可播放、可切歌、可展示基础状态）。
- 协作目标: 使用 `.ai/context/` 文件在新会话中快速恢复上下文并持续推进。

## Technical Context
- Core modules: C++（`src/`）已具备配置、首页壳、歌词、播放队列、媒体键、音频焦点等骨架。
- Android shell: Kotlin + AppCompat（`app/`），当前为最小启动页面。
- CI/CD: GitHub Actions `Package MVP` 打包 APK/AAB，包含 `minSdk` 校验与签名发布流程。

## Constraints
- 目标实机参数: Android `4.2.2`（API 17），`1024x600` 横屏，CPU `autochips ac83xx`。
- 设备资源约束: ARMv7 / 2G RAM，优先稳定和低开销。
- 当前 Android 壳配置: `minSdk = 17`，Java/Kotlin 目标 `1.8`。

## Confirmed Scope
- 首版协议: Emby only。
- 控制策略: 系统媒体键映射方向盘上下曲。
- 网络策略: 纯在线。

## Open Items
- 待确认: 系统首页音乐卡片是否支持第三方播放器入口。
- 待确认: 歌词远程失败时是否回退过期缓存及快速重试策略。
- 待确认: Android 壳从“封面页”迁移到“可交互播放页”的具体优先级细节。
