# 开发与发布

本项目由 [LuoH-AN](https://github.com/LuoH-AN) 维护，仓库为 [lyrics-android](https://github.com/LuoH-AN/lyrics-android)。Android 构建和原生测试仅在 GitHub Actions 执行，不在开发服务器运行 Gradle。

## 测试

- `tests/`：Node 源码边界检查与歌词引擎回归测试，统一入口为 `node tests/run.cjs`，自动运行全部 `*.test.cjs`。
- `app/src/test/`：Kotlin/JUnit 与 Robolectric 测试，由 GitHub Actions 运行。
- 两个工作流使用同一套 Node 回归检查，不依赖 README 或其他说明文档。

## 测试包

`build.yml` 在 `main`、`feat/**`、`dev/**` 分支推送或面向 `main` 的 PR 时运行，也可手动触发。通过后从 Artifacts 下载 `lyrics-android-debug`。

测试包使用 `com.luoh.music.lrc.dev` 和已有公开调试签名，与正式包分开安装。

## 正式版

版本维护在 `app/build.gradle.kts`，`versionCode` 必须递增。正式包名保持 `com.luoh.music.lrc`。

1. 在新仓库的 Actions Secrets 配置原有正式签名；GitHub 不会随源码迁移 Secrets。
2. `release.yml` 在 `feat/release-*` 分支或手动触发时构建、签名并验证，但不创建 Release；这一步不要求发版说明已入库。
3. 正式发布前确认 `docs/releases/<版本>.md` 文案，并将经过验证的代码与文档合入 `main`。
4. 经维护者确认后推送对应 `v<版本>` 标签。工作流检查版本和发版说明，验证 APK 后发布正式 Release，附带 `lyrics-android-<版本>-arm64-v8a.apk` 和 `SHA256SUMS`。

所需 Secrets：

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

沿用现有 PKCS12 正式密钥，保证同签名版本可覆盖升级；不得将密钥、密码或签名配置提交到仓库或上传公开 Artifacts。仓库中的 `debug.keystore` 仅供测试包使用，不是正式密钥。

正式签名证书 SHA-256：`482fae2e74bcf1cce5a4006dae7c86795b6eeb0112154cff462b20e4dd07300c`。此指纹不是私钥。工作流会校验版本、包名、非调试属性和证书；测试或验证失败不会发布。

## 参考

[原生界面结构](material-ui.md) · [歌词搜索流程](../guide/lyrics-search.md) · [来源与使用说明](../../README.md#来源与使用说明)
