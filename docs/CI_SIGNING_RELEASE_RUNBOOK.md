# CI_SIGNING_RELEASE_RUNBOOK

Last Updated: 2026-04-15 12:51:57
Status: Draft v2 (T-028)

## Purpose
给出“签名 + Release 上传”在当前仓库中的可执行操作手册，确保维护者可一次完成：
- 配置签名 Secrets
- 手动触发发布
- 验证发布产物
- 验证 APK 最低系统版本与目标车机一致

配套实机交互回归清单见：
- `docs/API17_INTERACTION_REGRESSION_CHECKLIST.md`

## Current Workflow Scope
- 工作流文件: `.github/workflows/package-mvp.yml`
- 触发方式:
  - `push` 到 `main/master`（仅构建，不发 release）
  - `push tag v*`（可发 release）
  - `workflow_dispatch` + `publish_release=true`（可发 release）
- 当前 APK/AAB 为 Gradle 真实构建产物（`assembleRelease` + `bundleRelease`），可用于 Android 安装与分发。
- 当前兼容性基线:
  - 目标实机：Android `4.2.2`（API 17）
  - 应用配置：`app/build.gradle.kts` 中 `minSdk = 17`
  - CI 校验：`aapt dump badging` 输出必须包含 `sdkVersion:'17'` 或 `minSdkVersion:'17'`

## Required Secrets
在 GitHub 仓库 `Settings -> Secrets and variables -> Actions -> New repository secret` 中新增：

1. `ANDROID_KEYSTORE_BASE64`
- JKS/keystore 文件的 Base64 文本（单行）

2. `ANDROID_KEYSTORE_PASSWORD`
- keystore 密码

3. `ANDROID_KEY_ALIAS`
- 签名别名（alias）

4. `ANDROID_KEY_PASSWORD`
- alias 对应私钥密码

## Step-by-Step (You Need To Do)

### 1. 准备签名文件（如果你还没有）
PowerShell 示例：

```powershell
keytool -genkeypair `
  -v `
  -keystore .\release.jks `
  -alias skoda-release `
  -keyalg RSA `
  -keysize 2048 `
  -validity 3650
```

### 2. 生成 Base64（用于 Secret）
PowerShell 示例：

```powershell
$bytes = [System.IO.File]::ReadAllBytes(".\release.jks")
[Convert]::ToBase64String($bytes) | Set-Clipboard
```

把剪贴板内容保存为 `ANDROID_KEYSTORE_BASE64`。

### 3. 配置 GitHub Secrets
进入仓库页面：
- `Settings`
- `Secrets and variables`
- `Actions`
- `New repository secret`

依次创建上面 4 个 secret，名称必须完全一致。

### 4. 手动触发一次发布验证（推荐）
进入：
- `Actions`
- `Package MVP`
- `Run workflow`

设置：
- `Branch`: `master`
- `publish_release`: `true`

点击 `Run workflow`。

建议本轮发布标签使用：`mvp-r18`（用于区分 `minSdk=17` 兼容改造后的首个实机验收版本）。

### 5. 验证结果
运行成功后检查：
1. Actions job 中 `Restore Android keystore`、`Sign APK and AAB`、`Publish GitHub Release` 不是 `skipped`。
2. `Releases` 页面出现新 release（workflow_dispatch 默认为 `mvp-r<run_number>`，若 tag 触发则为实际 tag）。
3. release 附件包含：
  - `skoda-music-mvp-signed.apk`（或 unsigned 回退）
  - `skoda-music-mvp-signed.aab`（或 unsigned 回退）
4. `dist/release/apk_badging.txt` 中包含 `sdkVersion:'17'` 或 `minSdkVersion:'17'`（CI 已内置校验，缺失会直接失败）。
5. 在 Android `4.2.2` 实机安装 APK，不出现“解析包时出现问题/版本过低”。

## Optional: Tag Release Flow
如果你想按版本号发版（而不是 run 编号）：

```powershell
git tag v0.1.0
git push origin v0.1.0
```

工作流会按 tag 名创建/更新 release。

## Troubleshooting
- 签名步骤被跳过:
  - 检查 4 个 secrets 是否都已配置且非空。
- `base64: invalid input`:
  - 重新生成 Base64，确保是完整单行文本。
- `jarsigner` 失败:
  - 核对 `ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 是否匹配 keystore。
- 发布步骤被跳过:
  - 确认是 `publish_release=true` 或 tag 触发。
- CI 报 `Verify APK minSdk is API 17` 失败:
  - 检查 `app/build.gradle.kts` 是否仍为 `minSdk = 17`。
  - 检查是否被其他构建变体覆盖了 `minSdkVersion`。

## Security Notes
- 不要把 `release.jks`、密码、Base64 文本提交进仓库。
- 建议定期轮换签名密码与 key，并同步更新 secrets。
