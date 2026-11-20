# 开发与发布

本项目由 [LuoH-AN](https://github.com/LuoH-AN) 维护，仓库为 [lyrics-android](https://github.com/LuoH-AN/lyrics-android)。Android 构建和原生测试仅在 GitHub Actions 执行，不在开发服务器运行 Gradle。

## 测试

- `tests/`：Node 源码边界检查与歌词引擎回归测试，统一入口为 `node tests/run.cjs`，自动运行全部 `*.test.cjs`。
- `app/src/test/`：Kotlin/JUnit 与 Robolectric 测试，在 Actions 手动运行时勾选 `full_checks` 执行。日常构建跳过这批检查，不生成或上传界面截图。
- 两个 APK 工作流默认跳过全部 Node、JUnit 和 Robolectric 测试，不生成截图；只有手动勾选 `full_checks` 才运行测试。日常交付使用快速 `nightly` CI，不在开发环境运行测试或 Gradle。

## Nightly 包

`build.yml` 在所有分支的每次推送、面向 `main` 的 PR 更新时运行，也可手动触发。官网、文档和 Markdown 变更同样构建，每次推送的构建不会被后续提交取消。通过后从 Artifacts 下载 `lyrics-android-nightly-<短 commit>`。

Nightly 的应用名称为「桌面歌词·Nightly」，使用 `com.luoh.music.lrc.dev` 和仓库内已有的 `debug.keystore` 签名，可覆盖旧 Debug／测试包，与正式包分开安装。`versionName` 使用所构建 commit 的短哈希（`git rev-parse --short=7 HEAD`），例如 `a1b2c3d`，不再附加正式版本号或 `-test`。PR 构建使用 PR 的实际提交。正式版版本号独立维护。

`nightly` 构建类型沿用原 `compact` 精简包的 R8 代码裁剪、混淆和资源裁剪，默认跳过耗时的全程序优化；手动勾选 `full_optimization` 可以生成完整优化的 Nightly 包。正式版 CI 同样默认跳过全程序优化，普通 `debug` 构建用于原生测试和源码调试。CI 检查 Debug 签名、包名、commit 版本号及 28 MiB 的体积预算，并保存 `lyrics-android-nightly-mapping-<短 commit>` 崩溃映射。详见 [APK 体积优化](apk-size.md)。

默认仅执行 `assembleNightly -PnightlyVersion=<短 commit>`，不构建重复的 debug APK、不运行 Node 或 Robolectric 测试。省略 `nightlyVersion` 时，Gradle 从当前 Git 提交读取短哈希。APK 大小、commit 版本号及 SHA-256 显示在 Actions 运行摘要中，组件尺寸可在校验步骤日志中查看。

需要提交代码但暂不构建时，在该次 commit 信息中加入 `[skip ci]`；GitHub 会跳过自动 push／PR 工作流。后续普通提交仍自动构建，也可手动触发 Nightly。

Gradle 构建缓存启用，开发分支推送也写入缓存，PR 只读。日常更改可以复用未受影响的任务产物；相同源码重跑可复用更多任务。仅使用到的扩展图标随源码保留，编译和 R8 不再处理整套图标库。首次缓存填充、实际源码修改和完全命中缓存的重跑耗时应分别记录，GitHub 排队时间另计。

关闭每次构建后的 Gradle 缓存清理任务，避免为清理临时 runner 再启动一次 Gradle；缓存仍正常恢复、保存，由 GitHub 的缓存容量与访问保留机制管理。

2026-11-14 的 GitHub Actions 实测（从 job 开始到完成，包含上传和缓存保存，不含排队）：

| 场景 | 耗时 | 构建记录 |
| --- | --- | --- |
| 原流程：202 个 Android 检查、debug 和完整优化 compact | 5 分 43 秒 | [43564686125](https://github.com/LuoH-AN/lyrics-android/actions/runs/43564686125) |
| 日常 compact 首次执行较快的 R8 裁剪，编译任务命中缓存 | 2 分 07 秒 | [43566406619](https://github.com/LuoH-AN/lyrics-android/actions/runs/43566406619) |
| 最终流程：编译和裁剪任务命中缓存 | 51 秒 | [43567209232](https://github.com/LuoH-AN/lyrics-android/actions/runs/43567209232) |

最后一轮 job 于 06:37:02 UTC 开始，APK 上传于 06:37:44 完成，job 于 06:37:53 完成。51 秒是此次缓存命中的成绩，不能代表每次 Android 源码或依赖变更；完整冷缓存构建未测。所有 Android 构建均在 GitHub Actions 完成。

## 正式版

版本维护在 `app/build.gradle.kts`，`versionCode` 必须递增。正式包名保持 `com.luoh.music.lrc`。

正式 CI 默认运行 `assembleRelease -PfastRelease=true`，保留代码裁剪、混淆、资源裁剪和正式签名，跳过 R8 全程序优化；手动勾选 `full_optimization` 可恢复完整优化。`app/proguard-rules.pro` 保留小窗 JavaScript 桥接方法，ML Kit 使用依赖自带的规则。`workflow_dispatch` 可以在开发分支验证签名包和体积，只有版本标签触发的任务会创建 Release。

正式构建默认只打一个 release APK，产物上传不额外压缩 APK；手动触发时可选 `full_checks`，额外运行 Node 和 Android 检查。签名和版本检查始终执行。

1. 在新仓库的 Actions Secrets 配置原有正式签名；GitHub 不会随源码迁移 Secrets。
2. `release.yml` 在 `feat/release-*` 分支或手动触发时构建、签名并验证，但不创建 Release。
3. 正式发布前将经过验证的代码合入 `main`。
4. 经维护者确认后推送对应 `v<版本>` 标签。工作流检查版本，验证 APK 后发布正式 Release；发版说明由 GitHub 自动生成。

所需 Secrets：

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

沿用现有 PKCS12 正式密钥，保证同签名版本可覆盖升级；不得将密钥、密码或签名配置提交到仓库或上传公开 Artifacts。仓库中的 `debug.keystore` 仅供测试包使用，不是正式密钥。

正式签名证书 SHA-256：`482fae2e74bcf1cce5a4006dae7c86795b6eeb0112154cff462b20e4dd07300c`。此指纹不是私钥。工作流会校验版本、包名、非调试属性和证书；测试或验证失败不会发布。

正式构建保留崩溃定位所需的行号信息。Actions 同时保存 `lyrics-android-mapping-<版本>`，其中的 R8 `mapping.txt` 用于还原该版本的混淆代码位置，保留 90 天，可下载长期归档。统计事件与后台查看说明见 [使用情况统计](usage-analytics.md)。

## 参考

[原生界面结构](material-ui.md) · [歌词搜索流程](../guide/lyrics-search.md) · [项目来源](../../README.md#从哪里来)
