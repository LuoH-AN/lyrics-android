# APK 体积优化

优化前交付的测试包为 `6c72cc46affbdcd66e701b2c58c939b7f3a3e676` 对应的 APK，共 55,626,454 字节。其中内置 Mi Outfit 字体文件为 20,235,120 字节、压缩入 APK 后为 13,851,967 字节；离线翻译原生引擎约 16.4 MB，其余主要是未裁剪的代码。

## 字体

保留原字体中西文、支持的其他字母、标点和 200–700 的可变字重，将 CJK 文字交给 Android 系统字体回退。中文字形因此随设备的系统字体而变化；已有字体选项和字重设置继续保留。衍生字体命名为 Lyrics Outfit，文件为 510,220 字节，保留 1,546 个字符映射和原作者信息。许可见 [OFL](../licenses/lyrics-outfit-OFL.txt)。

`tools/subset-app-font.py` 使用 fontTools 4.67.0，从上述提交的原始 `app/src/main/res/font/mioutfit_variable.ttf` 生成 `app/src/main/res/font/lyrics_outfit.ttf`。原始 SHA-256 为 `498223b2a22892afd8205caf765cb45a423317ddf37c0cc7a1035aa9abfb02c1`；新文件 SHA-256 为 `6bb7a039977894e7805f0a2da6b14d8fbdbbfc8ab2d2b7399957ee391fe35d3d`。生成后的字体直接提交，Android 构建不下载字体或执行 Python 分词／字体工具。

## 测试包与正式包

新增 `compact` 构建类型，继承正式构建的 R8 优化和资源裁剪，以 `.dev` 包名和原有公开调试证书签名，方便覆盖旧测试包。它不包含调试工具。普通 `debug` 类型用于 Robolectric 与源码调试，正式 `release` 类型继续使用正式证书。

CI 执行原生测试，构建同提交的普通调试包与精简包，再运行 `tools/check-apk-size.py`。默认精简包预算为 28 MiB，记录代码、字体、原生库及最大文件的尺寸，验证包名、签名、映射文件和 APK 完整性。`lyrics-android-debug` artifact 现在提供精简包，崩溃映射保留 90 天。

离线翻译与语言识别引擎保留；原生库继续直接映射加载，未使用压缩后解压到安装目录的方式换取更小下载包。新歌词源复用系统网络与编码能力。实际 APK 大小以 CI 的 `package-size/size.json` 为准，最终对比应注明是旧测试包、同提交调试包还是正式包。
