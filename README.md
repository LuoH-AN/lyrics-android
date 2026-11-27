# 桌面歌词

Android 歌词应用，支持悬浮歌词、逐字动画、翻译和本地歌词导入。

支持 Android 8.0+、arm64-v8a。需要通知使用权读取播放器信息，悬浮歌词另需悬浮窗权限。

桌面歌词解锁时可直接拖动；点锁定后，整个歌词区域支持点击穿透。需要调整位置时，点通知栏的锁形图标，或进入「设置 → 桌面歌词外观 → 解锁」。Android 12 及以上锁定时会按系统要求降低窗口不透明度，以允许触摸穿透。

Android 13 及以上开启桌面歌词时会请求发送通知权限；通知被关闭或划掉时，可在「桌面歌词外观」或「权限与开关」中点「桌面歌词通知」开启或恢复通知。通知使用权与发送通知权限相互独立，未允许发送通知时仍可从应用内解锁歌词。

[正式版](https://github.com/LuoH-AN/lyrics-android/releases/latest) · [Nightly](https://github.com/LuoH-AN/lyrics-android/releases/tag/nightly) · [反馈](https://github.com/LuoH-AN/lyrics-android/issues)

默认分支的最新提交构建成功后会更新 Nightly 预发布版，可以直接在 Release 页面下载 APK。Nightly 以短提交哈希作为版本号，签名与 Debug 相同。

## 构建

需要 JDK 17 和 Android SDK 36。

```sh
./gradlew assembleNightly
```

安装包：`app/build/outputs/apk/nightly/app-nightly.apk`。

## 代码结构

Android 入口在 `com.luoh.music.lrc`，功能代码按包组织。

| 包 | 职责 |
| --- | --- |
| `lyrics` | 歌词模型、解析、时间轴和 LRC 导出 |
| `data.lyrics` | 歌词源查询、歌曲匹配、缓存和自定义歌词 |
| `data.translation` | 补充翻译、API 配置及密钥存储 |
| `playback`、`overlay` | 媒体会话、悬浮窗布局、歌词渲染和通知 |
| `ui.home`、`ui.home.pop` | 首页播放状态、播放控件、歌词列表和逐字动画 |
| `ui.navigation`、`ui.settings`、`ui.library` | Compose 路由、设置页、歌词管理和翻译配置 |
| `ui.theme`、`ui.share`、`update`、`diagnostics` | 主题、歌词分享、应用更新和诊断 |

运行回归检查可使用 `node tests/run.cjs` 和 `sh gradlew testDebugUnitTest`。

## 来源与许可

源自 [Tcrrrry/desktop-lyrics](https://github.com/tcrrry/desktop-lyrics)，由 [LuoH-AN](https://github.com/LuoH-AN) 继续开发。

页面组件来自 [Agora](https://github.com/newo-ether/Agora)，保留 [GPL-3.0](app/src/main/kotlin/com/luoh/music/lrc/ui/agora/LICENSE)。其余新增内容采用 [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)，第三方内容保留[各自许可](licenses/)。
