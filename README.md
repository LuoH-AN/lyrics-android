<div align="center">
  <img src="docs/assets/app-icon.svg" width="88" alt="桌面歌词">

# 桌面歌词

简洁的 Android 桌面歌词应用，由 [LuoH-AN](https://github.com/LuoH-AN) 维护。

[下载正式版](https://github.com/LuoH-AN/lyrics-android/releases/latest) · [反馈问题](https://github.com/LuoH-AN/lyrics-android/issues)

</div>

让歌词跟着音乐，出现在你想看的地方。通过 Jetpack Compose Material 界面和可拖动的桌面歌词，同步显示正在播放的歌词；支持逐字高亮、双语显示、自定义歌词，以及字号、颜色和背景调整。不需要额外账号，配合你常用的音乐播放器即可使用。

<p align="center">
  <img src="https://i.luoh.org/file/wZxhwij4.webp" width="23%" alt="桌面歌词应用预览 1">
  <img src="https://i.luoh.org/file/hcpv0wyj.webp" width="23%" alt="桌面歌词应用预览 2">
  <img src="https://i.luoh.org/file/mMNccDZa.webp" width="23%" alt="桌面歌词应用预览 3">
  <img src="https://i.luoh.org/file/8tn8gupD.webp" width="23%" alt="桌面歌词应用预览 4">
</p>

## 开始使用

1. 下载并安装最新版本 APK。
2. 按提示授予通知使用权和悬浮窗权限。
3. 播放音乐，点击主页中控台「⋯」左侧的歌词图标开启桌面歌词；具体调整在「⋯ → 设置」里。

主页控制栏下滑收起、上滑展开，也可以点击顶部横条或歌曲信息切换。收起后保留圆形封面、歌曲信息和右侧两个按钮；主页背景随专辑封面呈现柔和的渐变，播放时缓慢流动。

需要 Android 8.0 及以上、64 位 ARM 设备和 Android System WebView。播放器需提供标准 MediaSession；如桌面歌词被系统清理，请放宽应用的后台省电限制。

## 来源与使用说明

本项目基于 [Tcrrrry](https://github.com/tcrrry)（B站 @Tcrrrry）的 [desktop-lyrics](https://github.com/tcrrry/desktop-lyrics) 继续开发，感谢原作者的工作。

社区：[LINUX DO](https://linux.do/)。

Compose 页面组件参考并移植自 [Agora](https://github.com/newo-ether/Agora)，保留其 [GPL-3.0 许可](app/src/main/kotlin/com/luoh/music/lrc/ui/agora/LICENSE)；迁移说明见 [Agora Compose 页面迁移](docs/agora-compose-migration.md)。

其余新增内容采用 [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans) 协议（署名、非商业使用）；上游及第三方代码保留原有声明。
