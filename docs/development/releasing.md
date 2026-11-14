# 开发与发布

本项目由 [LuoH-AN](https://github.com/LuoH-AN) 维护，仓库为 [lyrics-android](https://github.com/LuoH-AN/lyrics-android)。Android 构建和原生测试仅在 GitHub Actions 执行，不在开发服务器运行 Gradle。

## 测试

- `tests/`：Node 源码边界检查与歌词引擎回归测试，统一入口为 `node tests/run.cjs`，自动运行全部 `*.test.cjs`。
- `app/src/test/`：Kotlin/JUnit 与 Robolectric 测试，在 Actions 手动运行时勾选 `full_checks` 执行。日常构建跳过这批检查，不生成或上传界面截图。
- 两个工作流使用同一套 Node 回归检查，不依赖 README 或其他说明文档。

## 测试包

`build.yml` 在 `main`、`feat/**`、`dev/**` 分支推送或面向 `main` 的 PR 时运行，也可手动触发。仅官网、文档、Markdown 或网页工作流变更时跳过；包含 Android 源码变更时照常运行。通过后从 Artifacts 下载 `lyrics-android-debug`。

测试包使用 `com.luoh.music.lrc.dev` 和已有公开调试签名，与正式包分开安装。

默认测试 artifact 提供 `compact` 精简包，启用 R8 代码裁剪、混淆和资源裁剪，跳过耗时的全程序优化；手动勾选 `full_optimization` 可以生成完整优化的测试包。正式版始终完整优化，普通 `debug` 构建用于原生测试和源码调试。CI 检查签名、包名及 28 MiB 的体积预算，并保存精简包崩溃映射。详见 [APK 体积优化](apk-size.md)。

默认仅执行 `assembleCompact`，不构建重复的 debug APK、不运行 Robolectric。保留耗时很短的 Node 检查和 APK 安装信息校验。APK 大小及 SHA-256 显示在 Actions 运行摘要中，组件尺寸可在校验步骤日志中查看。

Gradle 构建缓存启用，开发分支推送也写入缓存，PR 只读。日常更改可以复用未受影响的任务产物；相同源码重跑可复用更多任务。仅使用到的扩展图标随源码保留，编译和 R8 不再处理整套图标库。首次缓存填充、实际源码修改和完全命中缓存的重跑耗时应分别记录，GitHub 排队时间另计。

## 正式版

版本维护在 `app/build.gradle.kts`，`versionCode` 必须递增。正式包名保持 `com.luoh.music.lrc`。

正式构建启用 R8 代码优化和资源裁剪；`app/proguard-rules.pro` 保留小窗 JavaScript 桥接方法，ML Kit 使用依赖自带的规则。`workflow_dispatch` 可以在开发分支验证签名包和体积，只有版本标签触发的任务会创建 Release。

正式构建同样默认只打一个 release APK；手动触发时可选 `full_checks`，额外运行 Android 检查。签名和版本检查始终执行。

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
