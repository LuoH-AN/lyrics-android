# 桌面歌词

Android 歌词应用，支持悬浮歌词、逐字动画、翻译和本地歌词导入。

支持 Android 8.0+、arm64-v8a。需要通知使用权读取播放器信息，悬浮歌词另需悬浮窗权限。

桌面歌词解锁时可直接拖动；点锁定后，整个歌词区域支持点击穿透。需要调整位置时，在通知栏点「解锁并移动」，或在应用的桌面歌词设置中解锁。Android 12 及以上锁定时会按系统要求降低窗口不透明度，以允许触摸穿透。

[正式版](https://github.com/LuoH-AN/lyrics-android/releases/latest) · [Nightly](https://github.com/LuoH-AN/lyrics-android/actions/workflows/build.yml) · [反馈](https://github.com/LuoH-AN/lyrics-android/issues)

Nightly 在每次提交后构建，以短 commit 作为版本号，签名与 Debug 相同。在成功构建的 Artifacts 中下载 APK。

## 构建

需要 JDK 17 和 Android SDK 36。

```sh
./gradlew assembleNightly
```

安装包：`app/build/outputs/apk/nightly/app-nightly.apk`。

## 来源与许可

源自 [Tcrrrry/desktop-lyrics](https://github.com/tcrrry/desktop-lyrics)，由 [LuoH-AN](https://github.com/LuoH-AN) 继续开发。

页面组件来自 [Agora](https://github.com/newo-ether/Agora)，保留 [GPL-3.0](app/src/main/kotlin/com/luoh/music/lrc/ui/agora/LICENSE)。其余新增内容采用 [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)，第三方内容保留[各自许可](licenses/)。
