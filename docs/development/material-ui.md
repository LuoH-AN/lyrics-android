# Compose 界面结构

应用页面由 ComponentActivity.setContent 创建，根节点提供 AgoraTheme 和 AgoraMotionPolicy。ComposeNavigator 保存业务路由，内部切换使用同一 composition；Android Activity 入口将 Intent 转为同一路由。

MainActivity 保留 MediaSession、权限、歌词缓存与检索、翻译、偏移、导出业务。HomePresentationState 将业务结果交给 ComposeHomeScreen。首页播放器、权限按钮、歌词与更多菜单都由 Compose 渲染；逐字位置只更新绘制，离开首页或进入后台时停止帧循环。

SettingsCategoryContent 提供八个分类。LibraryDestination 提供翻译、API 配置、偏移记忆、来源版本、自定义歌词库和编辑器、隐私页。静态页面复用 Agora 的 Column scaffold，动态列表复用 Lazy scaffold。文件读取、解析、列表过滤和网络操作放在 IO 上；离场页面撤下监听、停止预览并取消业务协程。

ComposeNavigation 复用原文外层与内页宿主。840dp 及以上的设置采用 400dp 分类栏和独立右侧子页，分类切换使用原文 250ms Crossfade。滚动标题、列表间距、字体、配色、菜单及高度弹簧面板的源码位于 `ui/agora/`，详见 [设计来源](../design-system.md)。

系统浮窗由 LyricsOverlayService 的 WindowManager 和紧凑歌词渲染器提供。其隐藏 WebView 继续承载已有歌词匹配与缓存引擎，仅传递业务数据。应用页面没有 AndroidView、View 页面包装或 XML 布局。

验证包含 Compose 真实点击、快速导航及输入屏蔽、滚动恢复、手机和双栏导航、设置保存、主页操作、更多面板的遮罩关闭与重新打开；歌词解析、时钟、翻译及浮窗引擎回归继续保留。GitHub Actions 执行 Android 测试与 APK 编译。没有设备侧耗时或帧率结果，不能将自动化通过视为视觉和手感完全一致。
