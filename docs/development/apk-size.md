# APK 体积优化

优化前交付的测试包为 `6c72cc46affbdcd66e701b2c58c939b7f3a3e676` 对应的 APK，共 55,626,454 字节。其中内置 Mi Outfit 字体文件为 20,235,120 字节、压缩入 APK 后为 13,851,967 字节；离线翻译原生引擎约 16.4 MB，其余主要是未裁剪的代码。

## 字体

保留原字体中西文、支持的其他字母、标点和 200–700 的可变字重，将 CJK 文字交给 Android 系统字体回退。中文字形因此随设备的系统字体而变化；已有字体选项和字重设置继续保留。衍生字体命名为 Lyrics Outfit，文件为 510,220 字节，保留 1,546 个字符映射和原作者信息。许可见 [OFL](../licenses/lyrics-outfit-OFL.txt)。

`tools/subset-app-font.py` 使用 fontTools 4.67.0，从上述提交的原始 `app/src/main/res/font/mioutfit_variable.ttf` 生成 `app/src/main/res/font/lyrics_outfit.ttf`。原始 SHA-256 为 `498223b2a22892afd8205caf765cb45a423317ddf37c0cc7a1035aa9abfb02c1`；新文件 SHA-256 为 `6bb7a039977894e7805f0a2da6b14d8fbdbbfc8ab2d2b7399957ee391fe35d3d`。生成后的字体直接提交，Android 构建不下载字体或执行 Python 分词／字体工具。

## 测试包与正式包

新增 `compact` 构建类型，继承正式构建的代码与资源裁剪，以 `.dev` 包名和原有公开调试证书签名，方便覆盖旧测试包。它不包含调试工具。为加速日常安装验证，默认追加 `proguard-compact.pro` 的 `-dontoptimize`：仍裁剪未用代码、混淆、裁剪资源，跳过 R8 全程序优化，因此会比完整优化包稍大。手动 `full_optimization`（Gradle 的 `-PfullOptimization=true`）可恢复完整优化。普通 `debug` 类型用于 Robolectric 与源码调试，正式 `release` 类型始终完整优化并使用正式证书。

CI 默认只构建精简包，再运行 `tools/check-apk-size.py`。基线 APK 参数可选，无需额外构建普通调试包来比大小。默认精简包预算为 28 MiB，记录代码、字体、原生库及最大文件的尺寸，验证包名、签名、映射文件和 APK 完整性。`lyrics-android-debug` artifact 提供精简包，崩溃映射保留 90 天。原生测试改为手动 `full_checks`，不上传截图。

`7e19e19dd720b31bcff5afa79fc7acb5fa2ca206` 的完整构建通过 202 个 Android 检查，精简 APK 为 21,858,570 字节，相比此前交付的 55,626,454 字节测试包缩小 60.70%。同提交普通调试包为 41,997,222 字节，仅用于这次体积对照。

## 图标依赖与构建速度

用 `material-icons-core:1.7.8` 替换包含上万个矢量定义的 `material-icons-extended`，另将应用需要的 30 个扩展图标按原始路径定义保留在 `ui/icons/filled/`，共 78,175 字节 Kotlin 源码。形状和镜像规则沿用 AndroidX 原始定义；文件中的版权声明和 [Apache 2.0 许可](../licenses/material-icons-Apache-2.0.txt) 保留。

`python tools/vendor-material-icons.py --check` 可对照固定 SHA-256 的 Google Maven 源码包验证。生成工具仅在维护图标时运行，Android 构建使用提交的文件。移除整套扩展图标主要减少依赖下载、Kotlin 类路径处理和 R8 的输入，已启用 R8 的 APK 不会因此再减少十几 MB。

离线翻译与语言识别引擎保留；原生库继续直接映射加载，未使用压缩后解压到安装目录的方式换取更小下载包。新歌词源复用系统网络与编码能力。实际 APK 大小以 CI 的 `package-size/size.json` 为准，最终对比应注明是旧测试包、同提交调试包还是正式包。
